package app.ascend.mobile.core.scoring

import app.ascend.mobile.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ScoringEngineTest {
    private val formulas = object : FormulaRegistry {
        override fun supports(id: String, mode: String, calibrationRequired: Boolean) = id == "fixture-ratio" && mode == "AUTOMATIC" && !calibrationRequired
    }
    private val benchmarks = object : BenchmarkRegistry {
        override fun review(id: String) = if (id == "reviewed-fixture") BenchmarkReview("AESTHETIC_PREFERENCE", "exact", "ADULT_EVIDENCE", true) else null
    }
    private val path = "reference-models/v1/fixture.json"
    private fun config(status: String = "VALIDATED_RELEASE", metricCount: Int = 4): String {
        val categories = Category.entries.joinToString(",") { "\"${it.name}\":{\"weight\":0.25,\"minimumCoverage\":1}" }
        val metrics = Category.entries.take(metricCount).mapIndexed { index, category ->
            """{"id":"m$index","category":"${category.name}","valueType":"RATIO","formulaOrExtractorId":"fixture-ratio","measurementMode":"AUTOMATIC","calibrationRequired":false,"reliabilityStatus":"VALIDATED","metricWeight":1,"minimumConfidence":0.5,"requiredForCompletion":true,"extremaEligible":true,"scoreScaleId":"scale-v1","minimumMeaningfulScoreDelta":0,"ageApplicability":"ADULT_EVIDENCE","benchmarkIds":["reviewed-fixture"],"tierRules":[{"tier":"T1","ruleType":"VALUE_RANGE","params":{"min":0,"max":0.9}},{"tier":"T2","ruleType":"VALUE_RANGE","params":{"min":1,"max":1.9}},{"tier":"T3","ruleType":"VALUE_RANGE","params":{"min":2,"max":2.9}},{"tier":"T4","ruleType":"VALUE_RANGE","params":{"min":3,"max":3.9}},{"tier":"T5","ruleType":"VALUE_RANGE","params":{"min":4,"max":5}}],"scoreCurve":{"type":"PIECEWISE_LINEAR","params":{"anchors":[{"value":0,"score":0},{"value":5,"score":100}]}}} """.trim()
        }
        val ids = (0 until metricCount).joinToString(",") { "\"m$it\"" }
        return """{"schemaVersion":"1","modelVersion":"fixture-v1","status":"$status","referenceModel":"MALE","enabledMetricIds":[$ids],"categories":{$categories},"metrics":[${metrics.joinToString(",")}],"rankThresholds":null,"scoreScaleVersion":"scale-v1","coveragePolicyVersion":"coverage-v1","extremaSelectionVersion":"extrema-v1","provenance":{"researchDatasetVersion":"fixture","reviewState":"APPROVED","approvedBy":["Ryan","Eddy"]}}"""
    }
    private fun load(source: String = config()) = ScoringEngine.loadRuntimeModel(path, source.toByteArray(), formulas, benchmarks)
    private fun versions(hash: String) = AnalysisVersions("app", "engine", "front", "profile", "fixture-v1", "scoring", hash,
        "set", "coverage-v1", "scale-v1", "extrema-v1", "recommendations")

    @Test fun draftAndResearchCannotScore() {
        assertThrows(InvalidScoringConfig::class.java) { load(config(status = "DRAFT_NOT_RELEASE_SCORABLE")) }
        assertThrows(InvalidScoringConfig::class.java) {
            ScoringEngine.loadRuntimeModel("reference-data/v1/benchmarks.csv", config().toByteArray(), formulas, benchmarks)
        }
    }

    @Test fun fixtureProducesDeterministicScoresAndExtrema() {
        val engine = load()
        val inputs = mapOf("m0" to Measurement(4.0, 1.0), "m1" to Measurement(3.0, 1.0),
            "m2" to Measurement(2.0, 1.0), "m3" to Measurement(1.0, 1.0))
        val first = engine.score("scan", inputs, versions(engine.configHash)) as AnalysisOutcome.Complete
        assertEquals(first, engine.score("scan", inputs, versions(engine.configHash)))
        assertEquals(5.0, first.overallScore0To10, 0.0)
        assertEquals(setOf("m0"), first.strongestMetricIds)
        assertEquals(setOf("m3"), first.weakestMetricIds)
        assertNull(first.rankLabel)
    }

    @Test fun lowConfidenceAndMissingRequiredMetricFailCoverage() {
        val engine = load()
        val result = engine.score("scan", mapOf("m0" to Measurement(4.0, 0.1)), versions(engine.configHash))
        assertTrue(result is AnalysisOutcome.InsufficientReliableMeasurements)
    }

    @Test fun unsupportedFormulaFailsClosed() {
        assertThrows(InvalidScoringConfig::class.java) { load(config().replace("fixture-ratio", "unknown")) }
    }
}
