package app.ascend.mobile.core.scoring

import app.ascend.mobile.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ScoringEngineTest {
    private data class MetricSpec(
        val id: String,
        val category: Category,
        val weight: Double = 1.0,
        val required: Boolean = true,
        val extrema: Boolean = true,
        val delta: Double = 0.0,
        val scoreScaleId: String? = "scale-v1",
        val benchmarkIds: List<String> = listOf("reviewed-fixture"),
    )

    private val defaultMetrics = Category.entries.mapIndexed { index, category ->
        MetricSpec("m$index", category)
    }

    private val formulas = object : FormulaRegistry {
        override fun supports(id: String, mode: String, calibrationRequired: Boolean) =
            id == "fixture-ratio" && mode == "AUTOMATIC" && !calibrationRequired
    }

    private val benchmarks = object : BenchmarkRegistry {
        override fun review(id: String) =
            if (id == "reviewed-fixture") {
                BenchmarkReview("AESTHETIC_PREFERENCE", "exact", "ADULT_EVIDENCE", true)
            } else {
                null
            }
    }

    private val path = "reference-models/v1/fixture.json"

    private fun metricJson(spec: MetricSpec): String {
        val benchmarkJson = spec.benchmarkIds.joinToString(",") { "\"$it\"" }
        val scoreScale = spec.scoreScaleId?.let { "\"$it\"" } ?: "null"
        return """{"id":"${spec.id}","category":"${spec.category.name}","valueType":"RATIO","formulaOrExtractorId":"fixture-ratio","measurementMode":"AUTOMATIC","calibrationRequired":false,"reliabilityStatus":"VALIDATED","metricWeight":${spec.weight},"minimumConfidence":0.5,"requiredForCompletion":${spec.required},"extremaEligible":${spec.extrema},"scoreScaleId":$scoreScale,"minimumMeaningfulScoreDelta":${spec.delta},"ageApplicability":"ADULT_EVIDENCE","benchmarkIds":[$benchmarkJson],"tierRules":[{"tier":"T1","ruleType":"VALUE_RANGE","params":{"min":0,"max":0.9}},{"tier":"T2","ruleType":"VALUE_RANGE","params":{"min":1,"max":1.9}},{"tier":"T3","ruleType":"VALUE_RANGE","params":{"min":2,"max":2.9}},{"tier":"T4","ruleType":"VALUE_RANGE","params":{"min":3,"max":3.9}},{"tier":"T5","ruleType":"VALUE_RANGE","params":{"min":4,"max":5}}],"scoreCurve":{"type":"PIECEWISE_LINEAR","params":{"anchors":[{"value":0,"score":0},{"value":5,"score":100}]}}}"""
    }

    private fun config(
        status: String = "VALIDATED_RELEASE",
        schemaVersion: String = "1.0.0",
        metrics: List<MetricSpec> = defaultMetrics,
        categoryWeights: Map<Category, Double> = Category.entries.associateWith { 0.25 },
        categoryCoverage: Map<Category, Double> = Category.entries.associateWith { 1.0 },
        rankThresholdsJson: String = "null",
        approvedBy: List<String> = listOf("Ryan", "Eddy"),
    ): String {
        val categories = Category.entries.joinToString(",") { category ->
            "\"${category.name}\":{\"weight\":${categoryWeights.getValue(category)},\"minimumCoverage\":${categoryCoverage.getValue(category)}}"
        }
        val ids = metrics.joinToString(",") { "\"${it.id}\"" }
        val metricDefinitions = metrics.joinToString(",") { metricJson(it) }
        val reviewers = approvedBy.joinToString(",") { "\"$it\"" }
        return """{"schemaVersion":"$schemaVersion","modelVersion":"fixture-v1","status":"$status","referenceModel":"MALE","enabledMetricIds":[$ids],"categories":{$categories},"metrics":[$metricDefinitions],"rankThresholds":$rankThresholdsJson,"scoreScaleVersion":"scale-v1","coveragePolicyVersion":"coverage-v1","extremaSelectionVersion":"extrema-v1","provenance":{"researchDatasetVersion":"fixture","reviewState":"APPROVED","approvedBy":[$reviewers]}}"""
    }

    private fun load(
        source: String = config(),
        benchmarkRegistry: BenchmarkRegistry = benchmarks,
    ) = ScoringEngine.loadRuntimeModel(path, source.toByteArray(), formulas, benchmarkRegistry)

    private fun versions(hash: String) = AnalysisVersions(
        "app",
        "engine",
        "front",
        "profile",
        "fixture-v1",
        "scoring",
        hash,
        "set",
        "coverage-v1",
        "scale-v1",
        "extrema-v1",
        "recommendations",
    )

    private fun defaultInputs() = mapOf(
        "m0" to Measurement(4.0, 1.0),
        "m1" to Measurement(3.0, 1.0),
        "m2" to Measurement(2.0, 1.0),
        "m3" to Measurement(1.0, 1.0),
    )

    @Test fun draftAndResearchCannotScore() {
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(status = "DRAFT_NOT_RELEASE_SCORABLE"))
        }
        assertThrows(InvalidScoringConfig::class.java) {
            ScoringEngine.loadRuntimeModel(
                "reference-data/v1/benchmarks.csv",
                config().toByteArray(),
                formulas,
                benchmarks,
            )
        }
    }

    @Test fun unsupportedSchemaAndDuplicateReviewersFailClosed() {
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(schemaVersion = "2.0.0"))
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(approvedBy = listOf("Ryan", "Ryan")))
        }
    }

    @Test fun invalidCategoryWeightsAndMetricMembershipFailClosed() {
        val badWeights = Category.entries.associateWith { 0.25 }.toMutableMap().apply {
            this[Category.HARMONY] = 0.30
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(categoryWeights = badWeights))
        }

        val mismatchedIds = config().replace(
            """"enabledMetricIds":["m0","m1","m2","m3"]""",
            """"enabledMetricIds":["m0","m1","m2","missing"]""",
        )
        assertThrows(InvalidScoringConfig::class.java) { load(mismatchedIds) }
    }

    @Test fun benchmarkReviewGateRejectsBadEvidenceAndDuplicateIds() {
        val populationNorms = object : BenchmarkRegistry {
            override fun review(id: String) =
                BenchmarkReview("POPULATION_NORM", "exact", "ADULT_EVIDENCE", true)
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(), populationNorms)
        }

        val duplicateBenchmarks = defaultMetrics.toMutableList().apply {
            this[0] = this[0].copy(benchmarkIds = listOf("reviewed-fixture", "reviewed-fixture"))
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(metrics = duplicateBenchmarks))
        }
    }

    @Test fun unsupportedFormulaFailsClosed() {
        assertThrows(InvalidScoringConfig::class.java) {
            load(config().replace("fixture-ratio", "unknown"))
        }
    }

    @Test fun fixtureProducesDeterministicScoresInterpolationTiersAndExtrema() {
        val engine = load()
        val first = engine.score("scan", defaultInputs(), versions(engine.configHash)) as AnalysisOutcome.Complete
        assertEquals(first, engine.score("scan", defaultInputs(), versions(engine.configHash)))
        assertEquals(5.0, first.overallScore0To10, 0.0)
        assertEquals(setOf("m0"), first.strongestMetricIds)
        assertEquals(setOf("m3"), first.weakestMetricIds)
        assertNull(first.rankLabel)

        val available = first.metrics.filterIsInstance<MetricResult.Available>().associateBy { it.metricId }
        assertEquals(Tier.T5, available.getValue("m0").tier)
        assertEquals(80.0, available.getValue("m0").hiddenScore0To100, 0.0)
        assertEquals(Tier.T4, available.getValue("m1").tier)
        assertEquals(Tier.T3, available.getValue("m2").tier)
        assertEquals(Tier.T2, available.getValue("m3").tier)

        val interpolatedInputs = defaultInputs().toMutableMap().apply {
            this["m2"] = Measurement(2.5, 1.0)
        }
        val interpolated = engine.score("scan", interpolatedInputs, versions(engine.configHash)) as AnalysisOutcome.Complete
        val m2 = interpolated.metrics.filterIsInstance<MetricResult.Available>().single { it.metricId == "m2" }
        assertEquals(Tier.T3, m2.tier)
        assertEquals(50.0, m2.hiddenScore0To100, 0.0)
    }

    @Test fun weightedMetricsAndConfigDrivenCountAreUsed() {
        val metrics = defaultMetrics + MetricSpec("m4", Category.HARMONY, weight = 3.0)
        val engine = load(config(metrics = metrics))
        val inputs = mapOf(
            "m0" to Measurement(1.0, 1.0),
            "m1" to Measurement(2.0, 1.0),
            "m2" to Measurement(3.0, 1.0),
            "m3" to Measurement(4.0, 1.0),
            "m4" to Measurement(5.0, 1.0),
        )
        val result = engine.score("scan", inputs, versions(engine.configHash)) as AnalysisOutcome.Complete
        assertEquals(5, result.enabledMetricIds.size)
        assertEquals(8.0, result.categories.getValue(Category.HARMONY).score0To10, 0.0)
        assertEquals(6.5, result.overallScore0To10, 0.0)
    }

    @Test fun coverageAllowsOptionalMissingOnlyAboveConfiguredThreshold() {
        val metrics = defaultMetrics.map {
            if (it.id == "m0") it.copy(required = false) else it
        } + MetricSpec("m4", Category.HARMONY, required = false)

        val coverage50 = Category.entries.associateWith { 1.0 }.toMutableMap().apply {
            this[Category.HARMONY] = 0.5
        }
        val inputs = defaultInputs()

        val permissive = load(config(metrics = metrics, categoryCoverage = coverage50))
        val complete = permissive.score("scan", inputs, versions(permissive.configHash)) as AnalysisOutcome.Complete
        assertEquals(0.5, complete.categories.getValue(Category.HARMONY).weightedCoverage0To1, 0.0)

        val coverage75 = coverage50.toMutableMap().apply {
            this[Category.HARMONY] = 0.75
        }
        val strict = load(config(metrics = metrics, categoryCoverage = coverage75))
        val insufficient = strict.score("scan", inputs, versions(strict.configHash))
        assertTrue(insufficient is AnalysisOutcome.InsufficientReliableMeasurements)
    }

    @Test fun lowConfidenceAndMissingRequiredMetricFailCoverage() {
        val engine = load()
        val result = engine.score(
            "scan",
            mapOf("m0" to Measurement(4.0, 0.1)),
            versions(engine.configHash),
        )
        assertTrue(result is AnalysisOutcome.InsufficientReliableMeasurements)
    }

    @Test fun uncertaintyTiesAreDeterministicAndNegativeDeltaFailsClosed() {
        val tieMetrics = defaultMetrics.map { it.copy(delta = 5.0) }
        val engine = load(config(metrics = tieMetrics))
        val inputs = defaultInputs().toMutableMap().apply {
            this["m1"] = Measurement(3.9, 1.0)
        }
        val result = engine.score("scan", inputs, versions(engine.configHash)) as AnalysisOutcome.Complete
        assertEquals(linkedSetOf("m0", "m1"), result.strongestMetricIds)
        assertEquals(linkedSetOf("m3"), result.weakestMetricIds)

        val invalid = defaultMetrics.toMutableList().apply {
            this[0] = this[0].copy(delta = -1.0)
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(metrics = invalid))
        }
    }

    @Test fun invalidOptionalCalibrationFieldsFailClosedBeforeScoring() {
        val negativeDelta = defaultMetrics.toMutableList().apply {
            this[0] = this[0].copy(extrema = false, delta = -1.0, scoreScaleId = null)
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(metrics = negativeDelta))
        }

        val blankScale = defaultMetrics.toMutableList().apply {
            this[0] = this[0].copy(extrema = false, scoreScaleId = "")
        }
        assertThrows(InvalidScoringConfig::class.java) {
            load(config(metrics = blankScale))
        }
    }

    @Test fun rankMappingRequiresCompleteOrderedThresholds() {
        val ranks = """[
            {"label":"Sub 5","minimumOverall":0},
            {"label":"LTN","minimumOverall":2},
            {"label":"MTN","minimumOverall":4},
            {"label":"HTN","minimumOverall":5},
            {"label":"Chadlite","minimumOverall":6},
            {"label":"Chad","minimumOverall":7},
            {"label":"True Adam","minimumOverall":9}
        ]""".trimIndent()
        val engine = load(config(rankThresholdsJson = ranks))
        val result = engine.score("scan", defaultInputs(), versions(engine.configHash)) as AnalysisOutcome.Complete
        assertEquals("HTN", result.rankLabel)

        assertThrows(InvalidScoringConfig::class.java) {
            load(config(rankThresholdsJson = "\"disabled\""))
        }
    }

    @Test fun versionMismatchFailsClosed() {
        val engine = load()
        assertThrows(InvalidScoringConfig::class.java) {
            engine.score(
                "scan",
                defaultInputs(),
                versions(engine.configHash).copy(scoreScaleVersion = "wrong-scale"),
            )
        }
    }
}
