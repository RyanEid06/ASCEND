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
        fun loadRuntimeModel(
            path: String,
            bytes: ByteArray,
            formulas: FormulaRegistry,
            benchmarks: BenchmarkRegistry,
        ): ScoringEngine {
            if (!Regex("(?:^|/)reference-models/(?:v[^/]+/)?[^/]+\\.json$").containsMatchIn(path.replace('\\', '/')) ||
                path.endsWith(".draft.json")) throw InvalidScoringConfig("Runtime model path required")
            val root = try { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }
                catch (e: Exception) { throw InvalidScoringConfig("Invalid JSON model") }
            fun fail(message: String): Nothing = throw InvalidScoringConfig(message)
            if (root.string("status") != "VALIDATED_RELEASE") fail("Draft model is non-scorable")
            if (root.keys - setOf("schemaVersion", "modelVersion", "status", "referenceModel", "enabledMetricIds", "categories", "metrics", "rankThresholds", "scoreScaleVersion", "coveragePolicyVersion", "extremaSelectionVersion", "provenance") != emptySet<String>()) fail("Unknown model fields")
            for (key in listOf("schemaVersion", "modelVersion", "scoreScaleVersion", "coveragePolicyVersion", "extremaSelectionVersion"))
                if (root.string(key).isNullOrBlank()) fail("Missing $key")
            if (root.string("referenceModel") !in listOf("MALE", "FEMALE")) fail("Invalid reference model")
            val categories = root.obj("categories") ?: fail("Missing categories")
            if (categories.keys != Category.entries.map { it.name }.toSet()) fail("Category set mismatch")
            val weights = categories.values.map { it.jsonObject.number("weight") ?: fail("Invalid category weight") }
            if (weights.any { !it.isFinite() || it <= 0 } || kotlin.math.abs(weights.sum() - 1.0) > 1e-9) fail("Category weights must sum to one")
            categories.values.forEach { if ((it.jsonObject.number("minimumCoverage") ?: -1.0) !in 0.0..1.0) fail("Invalid coverage") }
            val ids = root.array("enabledMetricIds")?.map { it.jsonPrimitive.content } ?: fail("Missing enabled metrics")
            val metrics = root.array("metrics")?.map { it.jsonObject } ?: fail("Missing metrics")
            if (ids.isEmpty() || ids.size != ids.toSet().size || ids.toSet() != metrics.mapNotNull { it.string("id") }.toSet() || ids.size != metrics.size)
                fail("Enabled metric IDs must match metric definitions")
            val provenance = root.obj("provenance") ?: fail("Missing provenance")
            if (provenance.string("reviewState") != "APPROVED" || (provenance.array("approvedBy")?.size ?: 0) < 2) fail("Model review incomplete")
            metrics.forEach { metric ->
                val id = metric.string("id") ?: fail("Metric lacks ID")
                if (metric.string("category") !in categories.keys) fail("Invalid category for $id")
                val mode = metric.string("measurementMode") ?: fail("Missing mode for $id")
                if (mode == "UNSUPPORTED" || metric.string("reliabilityStatus") != "VALIDATED" ||
                    !formulas.supports(metric.string("formulaOrExtractorId") ?: "", mode, metric.bool("calibrationRequired") ?: true)) fail("Unsupported metric $id")
                if ((metric.number("metricWeight") ?: 0.0) <= 0 || (metric.number("minimumConfidence") ?: -1.0) !in 0.0..1.0) fail("Invalid metric weight/confidence $id")
                if (metric.string("ageApplicability") == "UNKNOWN") fail("Age applicability unresolved for $id")
                val benchmarkIds = metric.array("benchmarkIds")?.map { it.jsonPrimitive.content } ?: fail("Missing benchmarks for $id")
                if (benchmarkIds.isEmpty()) fail("Missing benchmarks for $id")
                benchmarkIds.forEach { benchmarkId ->
                    val review = benchmarks.review(benchmarkId) ?: fail("Unknown benchmark $benchmarkId")
                    if (!review.runtimeEligible || review.definitionCompatibility != "exact" ||
                        review.ageApplicability != metric.string("ageApplicability") ||
                        review.purpose !in setOf("AESTHETIC_PREFERENCE", "COMMUNITY_CONVENTION")) fail("Benchmark not approved for $id")
                }
                val rules = metric.array("tierRules")?.map { it.jsonObject } ?: fail("Missing tiers for $id")
                if (rules.map { it.string("tier") }.toSet() != Tier.entries.map { it.name }.toSet() || rules.size != Tier.entries.size) fail("Tier set invalid for $id")
                rules.forEach { rule ->
                    if (rule.string("ruleType") != "VALUE_RANGE") fail("Unsupported tier rule for $id")
                    val p = rule.obj("params") ?: fail("Missing tier params")
                    if ((p.number("min") ?: fail("Missing min")) > (p.number("max") ?: fail("Missing max"))) fail("Invalid tier interval")
                }
                val ordered = rules.sortedBy { it.obj("params")!!.number("min")!! }
                if (ordered.zipWithNext().any { (a,b) -> a.obj("params")!!.number("max")!! >= b.obj("params")!!.number("min")!! }) fail("Overlapping tiers for $id")
                val curve = metric.obj("scoreCurve") ?: fail("Missing curve")
                if (curve.string("type") != "PIECEWISE_LINEAR") fail("Unsupported curve for $id")
                val anchors = curve.obj("params")?.array("anchors")?.map { it.jsonObject } ?: fail("Missing anchors")
                if (anchors.size < 2 || anchors.any { it.number("value") == null || (it.number("score") ?: -1.0) !in 0.0..100.0 } ||
                    anchors.zipWithNext().any { (a, b) -> a.number("value")!! >= b.number("value")!! }) fail("Invalid anchors for $id")
                if (metric.bool("extremaEligible") == true && (metric.string("scoreScaleId") != root.string("scoreScaleVersion") ||
                    metric.number("minimumMeaningfulScoreDelta") == null)) fail("Extrema calibration missing for $id")
            }
            val ranks = root.array("rankThresholds")?.map { it.jsonObject }
            if (ranks != null) {
                val expected = if (root.string("referenceModel") == "MALE") listOf("Sub 5", "LTN", "MTN", "HTN", "Chadlite", "Chad", "True Adam")
                    else listOf("Sub 5", "LTB", "MTB", "HTB", "Stacylite", "Stacy", "True Eve")
                if (ranks.map { it.string("label") } != expected || ranks.firstOrNull()?.number("minimumOverall") != 0.0 ||
                    ranks.any { (it.number("minimumOverall") ?: -1.0) !in 0.0..10.0 } ||
                    ranks.map { it.number("minimumOverall") }.zipWithNext().any { (a,b) -> a == null || b == null || a >= b }) fail("Invalid rank mapping")
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            return ScoringEngine(root, hash)
        }
    }

    fun score(scanId: String, measurements: Map<String, Measurement>, versions: AnalysisVersions): AnalysisOutcome {
        if (versions.metricConfigHash != configHash || versions.referenceModelVersion != root.string("modelVersion") ||
            versions.scoreScaleVersion != root.string("scoreScaleVersion") || versions.coveragePolicyVersion != root.string("coveragePolicyVersion") ||
            versions.extremaSelectionVersion != root.string("extremaSelectionVersion")) throw InvalidScoringConfig("Version mismatch")
        val definitions = root.array("metrics")!!.map { it.jsonObject }.associateBy { it.string("id")!! }
        val ids = root.array("enabledMetricIds")!!.map { it.jsonPrimitive.content }
        val unavailable = mutableSetOf<String>()
        val results = ids.map { id ->
            val config = definitions.getValue(id)
            val input = measurements[id]
            if (input == null || !input.value.isFinite() || !input.confidence.isFinite() || input.confidence !in 0.0..1.0 ||
                input.confidence < config.number("minimumConfidence")!!) {
                unavailable += id
                MetricResult.Unavailable(id, "Missing or unreliable measurement")
            } else {
                val tier = config.array("tierRules")!!.map { it.jsonObject }.singleOrNull {
                    val p = it.obj("params")!!
                    input.value >= p.number("min")!! && input.value <= p.number("max")!!
                }?.string("tier")
                if (tier == null) {
                    unavailable += id
                    MetricResult.Unavailable(id, "Outside configured tier domains")
                } else {
                    val anchors = config.obj("scoreCurve")!!.obj("params")!!.array("anchors")!!.map { it.jsonObject }
                    val score = when {
                        input.value <= anchors.first().number("value")!! -> anchors.first().number("score")!!
                        input.value >= anchors.last().number("value")!! -> anchors.last().number("score")!!
                        else -> {
                            val (a,b) = anchors.zipWithNext().first { (_, b) -> input.value <= b.number("value")!! }
                            a.number("score")!! + (input.value-a.number("value")!!) *
                                (b.number("score")!!-a.number("score")!!) / (b.number("value")!!-a.number("value")!!)
                        }
                    }
                    MetricResult.Available(id, Category.valueOf(config.string("category")!!), input.view,
                        input.value, Tier.valueOf(tier), score, input.confidence, config.number("metricWeight")!!,
                        config.string("scoreScaleId"))
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
            if (weight == 0.0 || coverage < minimum || subset.any { it in unavailable && definitions.getValue(it).bool("requiredForCompletion") == true }) null
            else CategoryResult(category, valid.sumOf { it.hiddenScore0To100 * it.weightUsed } / weight / 10.0, coverage, versions.coveragePolicyVersion)
        }
        if (categories.values.any { it == null }) return AnalysisOutcome.InsufficientReliableMeasurements(scanId, unavailable, "Not enough reliable measurements")
        val completed = categories.mapValues { it.value!! }
        val overall = completed.entries.sumOf { (category, result) -> result.score0To10 * root.obj("categories")!!.obj(category.name)!!.number("weight")!! }
        val ranks = root.array("rankThresholds")?.map { it.jsonObject }
        val rank = ranks?.lastOrNull { overall >= it.number("minimumOverall")!! }?.string("label")
        val eligible = results.filterIsInstance<MetricResult.Available>().filter {
            val c = definitions.getValue(it.metricId)
            c.bool("extremaEligible") == true && c.string("scoreScaleId") == versions.scoreScaleVersion
        }
        fun ties(target: Double): Set<String> = eligible.filter {
            val delta = definitions.getValue(it.metricId).number("minimumMeaningfulScoreDelta")!!
            kotlin.math.abs(it.hiddenScore0To100 - target) <= delta
        }.map { it.metricId }.sorted().toCollection(linkedSetOf())
        val strongest = if (eligible.size >= 2) ties(eligible.maxOf { it.hiddenScore0To100 }) else emptySet()
        val weakest = if (eligible.size >= 2) ties(eligible.minOf { it.hiddenScore0To100 }) else emptySet()
        return AnalysisOutcome.Complete(scanId, ReferenceModel.valueOf(root.string("referenceModel")!!), versions,
            ids.toSet(), results, completed, overall, rank, strongest, weakest)
    }
}

private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.number(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull
private fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.obj(key: String) = get(key) as? JsonObject
private fun JsonObject.array(key: String) = get(key) as? JsonArray
