package app.ascend.mobile.core.scoring

import app.ascend.mobile.core.model.*
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** WP03 supplies entries from its formula/feasibility registry through this interface. */
interface FormulaRegistry {
    fun supports(id: String, mode: String, calibrationRequired: Boolean): Boolean
}

data class Measurement(val value: Double, val confidence: Double, val view: MeasurementView = MeasurementView.FRONT)
data class BenchmarkReview(
    val purpose: String,
    val definitionCompatibility: String,
    val ageApplicability: String,
    val runtimeEligible: Boolean,
)
interface BenchmarkRegistry { fun review(id: String): BenchmarkReview? }

class InvalidScoringConfig(message: String) : IllegalArgumentException(message)

/** JSON is accepted only from the runtime reference-models trust domain. */
class ScoringEngine private constructor(
    private val root: JsonObject,
    val configHash: String,
) {
    companion object {
        private const val SUPPORTED_SCHEMA_VERSION = "1.0.0"
        private val supportedAgeApplicability = setOf(
            "ADULT_EVIDENCE",
            "ADOLESCENT_EVIDENCE",
            "AGE_INVARIANT_VALIDATED",
        )
        private val supportedMeasurementModes = setOf("AUTOMATIC", "ASSISTED", "MANUAL")
        private val supportedValueTypes = setOf(
            "RATIO",
            "DEGREE",
            "NORMALIZED_DISTANCE",
            "CATEGORICAL",
            "CONTINUOUS_VISUAL",
        )

        fun loadRuntimeModel(
            path: String,
            bytes: ByteArray,
            formulas: FormulaRegistry,
            benchmarks: BenchmarkRegistry,
        ): ScoringEngine {
            if (!Regex("(?:^|/)reference-models/(?:v[^/]+/)?[^/]+\\.json$").containsMatchIn(path.replace('\\', '/')) ||
                path.endsWith(".draft.json")
            ) {
                throw InvalidScoringConfig("Runtime model path required")
            }

            val root = try {
                Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            } catch (_: Exception) {
                throw InvalidScoringConfig("Invalid JSON model")
            }
            fun fail(message: String): Nothing = throw InvalidScoringConfig(message)

            val allowedRootFields = setOf(
                "schemaVersion",
                "modelVersion",
                "status",
                "referenceModel",
                "enabledMetricIds",
                "categories",
                "metrics",
                "rankThresholds",
                "scoreScaleVersion",
                "coveragePolicyVersion",
                "extremaSelectionVersion",
                "provenance",
            )
            if (root.keys - allowedRootFields != emptySet<String>()) fail("Unknown model fields")
            if (root.string("schemaVersion") != SUPPORTED_SCHEMA_VERSION) fail("Unsupported schema version")
            if (root.string("status") != "VALIDATED_RELEASE") fail("Draft model is non-scorable")
            for (key in listOf("modelVersion", "scoreScaleVersion", "coveragePolicyVersion", "extremaSelectionVersion")) {
                if (root.string(key).isNullOrBlank()) fail("Missing $key")
            }
            if (root.string("referenceModel") !in listOf("MALE", "FEMALE")) fail("Invalid reference model")

            val categories = root.obj("categories") ?: fail("Missing categories")
            if (categories.keys != Category.entries.map { it.name }.toSet()) fail("Category set mismatch")
            val categoryObjects = categories.mapValues { (_, element) ->
                element as? JsonObject ?: fail("Invalid category configuration")
            }
            val categoryFields = setOf("weight", "minimumCoverage")
            categoryObjects.values.forEach { category ->
                if (category.keys != categoryFields) fail("Invalid category fields")
            }
            val categoryWeights = categoryObjects.values.map {
                it.number("weight") ?: fail("Invalid category weight")
            }
            if (categoryWeights.any { !it.isFinite() || it <= 0.0 } ||
                kotlin.math.abs(categoryWeights.sum() - 1.0) > 1e-9
            ) {
                fail("Category weights must sum to one")
            }
            categoryObjects.values.forEach { category ->
                val minimumCoverage = category.number("minimumCoverage") ?: fail("Invalid coverage")
                if (!minimumCoverage.isFinite() || minimumCoverage !in 0.0..1.0) fail("Invalid coverage")
            }

            val enabledElements = root.array("enabledMetricIds") ?: fail("Missing enabled metrics")
            val ids = enabledElements.map { element ->
                val primitive = element as? JsonPrimitive
                if (primitive == null || !primitive.isString || primitive.content.isBlank()) fail("Invalid enabled metric ID")
                primitive.content
            }
            val metricElements = root.array("metrics") ?: fail("Missing metrics")
            val metrics = metricElements.map { it as? JsonObject ?: fail("Invalid metric definition") }
            if (ids.isEmpty() || ids.size != ids.toSet().size ||
                ids.toSet() != metrics.mapNotNull { it.string("id") }.toSet() ||
                ids.size != metrics.size
            ) {
                fail("Enabled metric IDs must match metric definitions")
            }

            val provenance = root.obj("provenance") ?: fail("Missing provenance")
            val provenanceFields = setOf("researchDatasetVersion", "reviewState", "approvedBy", "notes")
            if (provenance.keys - provenanceFields != emptySet<String>()) fail("Unknown provenance fields")
            if (provenance.string("researchDatasetVersion").isNullOrBlank()) fail("Missing research dataset version")
            val reviewers = provenance.array("approvedBy")?.map { element ->
                val primitive = element as? JsonPrimitive
                if (primitive == null || !primitive.isString || primitive.content.isBlank()) fail("Invalid reviewer")
                primitive.content
            } ?: fail("Model review incomplete")
            if (provenance.string("reviewState") != "APPROVED" ||
                reviewers.size < 2 ||
                reviewers.toSet().size < 2
            ) {
                fail("Model review incomplete")
            }

            val metricFields = setOf(
                "id",
                "category",
                "valueType",
                "formulaOrExtractorId",
                "measurementMode",
                "calibrationRequired",
                "reliabilityStatus",
                "metricWeight",
                "minimumConfidence",
                "requiredForCompletion",
                "extremaEligible",
                "scoreScaleId",
                "measurementUncertainty",
                "minimumMeaningfulScoreDelta",
                "ageApplicability",
                "benchmarkIds",
                "reliabilityTolerance",
                "tierRules",
                "scoreCurve",
            )

            metrics.forEach { metric ->
                if (metric.keys - metricFields != emptySet<String>()) fail("Unknown metric fields")
                val id = metric.string("id") ?: fail("Metric lacks ID")
                if (id.isBlank()) fail("Metric lacks ID")
                if (metric.string("category") !in categoryObjects.keys) fail("Invalid category for $id")

                val valueType = metric.string("valueType") ?: fail("Missing value type for $id")
                if (valueType !in supportedValueTypes) fail("Unsupported value type for $id")

                val mode = metric.string("measurementMode") ?: fail("Missing mode for $id")
                val calibrationRequired = metric.bool("calibrationRequired") ?: fail("Missing calibration flag for $id")
                if (mode !in supportedMeasurementModes ||
                    metric.string("reliabilityStatus") != "VALIDATED" ||
                    !formulas.supports(metric.string("formulaOrExtractorId") ?: "", mode, calibrationRequired)
                ) {
                    fail("Unsupported metric $id")
                }

                val metricWeight = metric.number("metricWeight") ?: fail("Invalid metric weight/confidence $id")
                val minimumConfidence = metric.number("minimumConfidence") ?: fail("Invalid metric weight/confidence $id")
                if (!metricWeight.isFinite() || metricWeight <= 0.0 ||
                    !minimumConfidence.isFinite() || minimumConfidence !in 0.0..1.0
                ) {
                    fail("Invalid metric weight/confidence $id")
                }
                if (metric.bool("requiredForCompletion") == null || metric.bool("extremaEligible") == null) {
                    fail("Missing completion/extrema flag for $id")
                }

                val ageApplicability = metric.string("ageApplicability")
                if (ageApplicability !in supportedAgeApplicability) fail("Age applicability unresolved for $id")

                val benchmarkElements = metric.array("benchmarkIds") ?: fail("Missing benchmarks for $id")
                val benchmarkIds = benchmarkElements.map { element ->
                    val primitive = element as? JsonPrimitive
                    if (primitive == null || !primitive.isString || primitive.content.isBlank()) fail("Invalid benchmark for $id")
                    primitive.content
                }
                if (benchmarkIds.isEmpty() || benchmarkIds.size != benchmarkIds.toSet().size) {
                    fail("Invalid benchmarks for $id")
                }
                benchmarkIds.forEach { benchmarkId ->
                    val review = benchmarks.review(benchmarkId) ?: fail("Unknown benchmark $benchmarkId")
                    if (!review.runtimeEligible ||
                        review.definitionCompatibility != "exact" ||
                        review.ageApplicability != ageApplicability ||
                        review.purpose !in setOf("AESTHETIC_PREFERENCE", "COMMUNITY_CONVENTION")
                    ) {
                        fail("Benchmark not approved for $id")
                    }
                }

                for (optionalNonNegative in listOf("measurementUncertainty", "reliabilityTolerance")) {
                    val element = metric[optionalNonNegative]
                    if (element != null && element != JsonNull) {
                        val value = metric.number(optionalNonNegative) ?: fail("Invalid $optionalNonNegative for $id")
                        if (!value.isFinite() || value < 0.0) fail("Invalid $optionalNonNegative for $id")
                    }
                }

                val rules = metric.array("tierRules")?.map {
                    it as? JsonObject ?: fail("Invalid tier rule for $id")
                } ?: fail("Missing tiers for $id")
                if (rules.map { it.string("tier") }.toSet() != Tier.entries.map { it.name }.toSet() ||
                    rules.size != Tier.entries.size
                ) {
                    fail("Tier set invalid for $id")
                }
                rules.forEach { rule ->
                    if (rule.keys - setOf("tier", "ruleType", "params") != emptySet<String>()) fail("Invalid tier fields for $id")
                    if (rule.string("ruleType") != "VALUE_RANGE") fail("Unsupported tier rule for $id")
                    val params = rule.obj("params") ?: fail("Missing tier params")
                    val min = params.number("min") ?: fail("Missing min")
                    val max = params.number("max") ?: fail("Missing max")
                    if (!min.isFinite() || !max.isFinite() || min > max) fail("Invalid tier interval")
                }
                val ordered = rules.sortedBy { it.obj("params")!!.number("min")!! }
                if (ordered.zipWithNext().any { (a, b) ->
                        a.obj("params")!!.number("max")!! >= b.obj("params")!!.number("min")!!
                    }
                ) {
                    fail("Overlapping tiers for $id")
                }

                val curve = metric.obj("scoreCurve") ?: fail("Missing curve")
                if (curve.keys - setOf("type", "params") != emptySet<String>()) fail("Invalid score curve fields for $id")
                if (curve.string("type") != "PIECEWISE_LINEAR") fail("Unsupported curve for $id")
                val anchors = curve.obj("params")?.array("anchors")?.map {
                    it as? JsonObject ?: fail("Invalid anchor for $id")
                } ?: fail("Missing anchors")
                if (anchors.size < 2 ||
                    anchors.any {
                        val value = it.number("value")
                        val score = it.number("score")
                        value == null || !value.isFinite() ||
                            score == null || !score.isFinite() || score !in 0.0..100.0
                    } ||
                    anchors.zipWithNext().any { (a, b) -> a.number("value")!! >= b.number("value")!! }
                ) {
                    fail("Invalid anchors for $id")
                }

                if (metric.bool("extremaEligible") == true) {
                    val delta = metric.number("minimumMeaningfulScoreDelta")
                    if (metric.string("scoreScaleId") != root.string("scoreScaleVersion") ||
                        delta == null || !delta.isFinite() || delta < 0.0
                    ) {
                        fail("Extrema calibration missing for $id")
                    }
                }
            }

            val rankElement = root["rankThresholds"]
            val ranks = when (rankElement) {
                null, JsonNull -> null
                is JsonArray -> rankElement.map { it as? JsonObject ?: fail("Invalid rank mapping") }
                else -> fail("Invalid rank mapping")
            }
            if (ranks != null) {
                val expected = if (root.string("referenceModel") == "MALE") {
                    listOf("Sub 5", "LTN", "MTN", "HTN", "Chadlite", "Chad", "True Adam")
                } else {
                    listOf("Sub 5", "LTB", "MTB", "HTB", "Stacylite", "Stacy", "True Eve")
                }
                if (ranks.any { it.keys != setOf("label", "minimumOverall") } ||
                    ranks.map { it.string("label") } != expected ||
                    ranks.firstOrNull()?.number("minimumOverall") != 0.0 ||
                    ranks.any {
                        val minimum = it.number("minimumOverall")
                        minimum == null || !minimum.isFinite() || minimum !in 0.0..10.0
                    } ||
                    ranks.map { it.number("minimumOverall") }
                        .zipWithNext()
                        .any { (a, b) -> a == null || b == null || a >= b }
                ) {
                    fail("Invalid rank mapping")
                }
            }

            val hash = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
            return ScoringEngine(root, hash)
        }
    }

    fun score(scanId: String, measurements: Map<String, Measurement>, versions: AnalysisVersions): AnalysisOutcome {
        if (versions.metricConfigHash != configHash ||
            versions.referenceModelVersion != root.string("modelVersion") ||
            versions.scoreScaleVersion != root.string("scoreScaleVersion") ||
            versions.coveragePolicyVersion != root.string("coveragePolicyVersion") ||
            versions.extremaSelectionVersion != root.string("extremaSelectionVersion")
        ) {
            throw InvalidScoringConfig("Version mismatch")
        }

        val definitions = root.array("metrics")!!.map { it.jsonObject }.associateBy { it.string("id")!! }
        val ids = root.array("enabledMetricIds")!!.map { it.jsonPrimitive.content }
        val unavailable = mutableSetOf<String>()
        val results = ids.map { id ->
            val config = definitions.getValue(id)
            val input = measurements[id]
            if (input == null ||
                !input.value.isFinite() ||
                !input.confidence.isFinite() ||
                input.confidence !in 0.0..1.0 ||
                input.confidence < config.number("minimumConfidence")!!
            ) {
                unavailable += id
                MetricResult.Unavailable(id, "Missing or unreliable measurement")
            } else {
                val tier = config.array("tierRules")!!.map { it.jsonObject }.singleOrNull {
                    val params = it.obj("params")!!
                    input.value >= params.number("min")!! && input.value <= params.number("max")!!
                }?.string("tier")

                if (tier == null) {
                    unavailable += id
                    MetricResult.Unavailable(id, "Outside configured tier domains")
                } else {
                    val anchors = config.obj("scoreCurve")!!
                        .obj("params")!!
                        .array("anchors")!!
                        .map { it.jsonObject }
                    val score = when {
                        input.value <= anchors.first().number("value")!! -> anchors.first().number("score")!!
                        input.value >= anchors.last().number("value")!! -> anchors.last().number("score")!!
                        else -> {
                            val (a, b) = anchors.zipWithNext().first { (_, next) ->
                                input.value <= next.number("value")!!
                            }
                            a.number("score")!! +
                                (input.value - a.number("value")!!) *
                                (b.number("score")!! - a.number("score")!!) /
                                (b.number("value")!! - a.number("value")!!)
                        }
                    }
                    MetricResult.Available(
                        id,
                        Category.valueOf(config.string("category")!!),
                        input.view,
                        input.value,
                        Tier.valueOf(tier),
                        score,
                        input.confidence,
                        config.number("metricWeight")!!,
                        config.string("scoreScaleId"),
                    )
                }
            }
        }

        val categories = Category.entries.associateWith { category ->
            val subset = ids.filter { definitions.getValue(it).string("category") == category.name }
            val total = subset.sumOf { definitions.getValue(it).number("metricWeight")!! }
            val valid = results.filterIsInstance<MetricResult.Available>().filter { it.category == category }
            val weight = valid.sumOf { it.weightUsed }
            val coverage = if (total == 0.0) 0.0 else weight / total
            val minimum = root.obj("categories")!!.obj(category.name)!!.number("minimumCoverage")!!
            if (weight == 0.0 ||
                coverage < minimum ||
                subset.any {
                    it in unavailable && definitions.getValue(it).bool("requiredForCompletion") == true
                }
            ) {
                null
            } else {
                CategoryResult(
                    category,
                    valid.sumOf { it.hiddenScore0To100 * it.weightUsed } / weight / 10.0,
                    coverage,
                    versions.coveragePolicyVersion,
                )
            }
        }

        if (categories.values.any { it == null }) {
            return AnalysisOutcome.InsufficientReliableMeasurements(
                scanId,
                unavailable,
                "Not enough reliable measurements",
            )
        }

        val completed = categories.mapValues { it.value!! }
        val overall = completed.entries.sumOf { (category, result) ->
            result.score0To10 * root.obj("categories")!!.obj(category.name)!!.number("weight")!!
        }
        val ranks = root.array("rankThresholds")?.map { it.jsonObject }
        val rank = ranks?.lastOrNull { overall >= it.number("minimumOverall")!! }?.string("label")

        val eligible = results.filterIsInstance<MetricResult.Available>().filter {
            val config = definitions.getValue(it.metricId)
            config.bool("extremaEligible") == true &&
                config.string("scoreScaleId") == versions.scoreScaleVersion
        }
        fun ties(target: Double): Set<String> = eligible.filter {
            val delta = definitions.getValue(it.metricId).number("minimumMeaningfulScoreDelta")!!
            kotlin.math.abs(it.hiddenScore0To100 - target) <= delta
        }.map { it.metricId }.sorted().toCollection(linkedSetOf())

        val strongest = if (eligible.size >= 2) ties(eligible.maxOf { it.hiddenScore0To100 }) else emptySet()
        val weakest = if (eligible.size >= 2) ties(eligible.minOf { it.hiddenScore0To100 }) else emptySet()

        return AnalysisOutcome.Complete(
            scanId,
            ReferenceModel.valueOf(root.string("referenceModel")!!),
            versions,
            ids.toSet(),
            results,
            completed,
            overall,
            rank,
            strongest,
            weakest,
        )
    }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.number(key: String): Double? =
    (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

private fun JsonObject.bool(key: String): Boolean? =
    (get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

private fun JsonObject.obj(key: String) = get(key) as? JsonObject
private fun JsonObject.array(key: String) = get(key) as? JsonArray
