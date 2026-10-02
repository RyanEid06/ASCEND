package app.ascend.mobile.core.integration

import app.ascend.mobile.core.geometry.GeometryFailureCode
import app.ascend.mobile.core.geometry.GeometryFrame
import app.ascend.mobile.core.geometry.GeometryMeasurementResult
import app.ascend.mobile.core.geometry.FormulaIds
import app.ascend.mobile.core.geometry.RepresentativeFormulaRegistry
import app.ascend.mobile.core.geometry.SyntheticGeometryFixtures
import app.ascend.mobile.core.model.AnalysisOutcome
import app.ascend.mobile.core.model.AnalysisVersions
import app.ascend.mobile.core.model.Category
import app.ascend.mobile.core.model.MeasurementView
import app.ascend.mobile.core.model.MetricResult
import app.ascend.mobile.core.scoring.BenchmarkRegistry
import app.ascend.mobile.core.scoring.BenchmarkReview
import app.ascend.mobile.core.scoring.FormulaRegistry
import app.ascend.mobile.core.scoring.Measurement
import app.ascend.mobile.core.scoring.ScoringEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryScoringIntegrationTest {
    private data class FlowMetric(
        val id: String,
        val category: Category,
        val formulaId: String,
        val mode: String,
        val view: MeasurementView,
        val frame: () -> GeometryFrame,
    )

    private val metrics = listOf(
        FlowMetric(
            "integration.harmony",
            Category.HARMONY,
            FormulaIds.FACIAL_ELONGATION,
            "AUTOMATIC",
            MeasurementView.FRONT,
            SyntheticGeometryFixtures::front,
        ),
        FlowMetric(
            "integration.dimorphism",
            Category.DIMORPHISM,
            FormulaIds.JAW_TO_CHEEK_WIDTH,
            "AUTOMATIC",
            MeasurementView.FRONT,
            SyntheticGeometryFixtures::front,
        ),
        FlowMetric(
            "integration.angularity",
            Category.ANGULARITY,
            FormulaIds.NASOFRONTAL_ANGLE,
            "ASSISTED",
            MeasurementView.PROFILE,
            SyntheticGeometryFixtures::profile,
        ),
        FlowMetric(
            "integration.misc",
            Category.MISC,
            FormulaIds.NASOLABIAL_ANGLE,
            "ASSISTED",
            MeasurementView.PROFILE,
            SyntheticGeometryFixtures::profile,
        ),
    )

    private val scoringFormulaRegistry = object : FormulaRegistry {
        override fun supports(id: String, mode: String, calibrationRequired: Boolean): Boolean =
            RepresentativeFormulaRegistry.registry.supports(id, mode, calibrationRequired)
    }

    private val benchmarks = object : BenchmarkRegistry {
        override fun review(id: String): BenchmarkReview? =
            if (id == "integration-reviewed") {
                BenchmarkReview(
                    purpose = "AESTHETIC_PREFERENCE",
                    definitionCompatibility = "exact",
                    ageApplicability = "ADULT_EVIDENCE",
                    runtimeEligible = true,
                )
            } else {
                null
            }
    }

    private fun metricJson(metric: FlowMetric): String = """
        {
          "id":"${metric.id}",
          "category":"${metric.category.name}",
          "valueType":"${if (metric.mode == "ASSISTED") "DEGREE" else "RATIO"}",
          "formulaOrExtractorId":"${metric.formulaId}",
          "measurementMode":"${metric.mode}",
          "calibrationRequired":false,
          "reliabilityStatus":"VALIDATED",
          "metricWeight":1.0,
          "minimumConfidence":0.75,
          "requiredForCompletion":true,
          "extremaEligible":true,
          "scoreScaleId":"integration-scale-v1",
          "minimumMeaningfulScoreDelta":0.0,
          "ageApplicability":"ADULT_EVIDENCE",
          "benchmarkIds":["integration-reviewed"],
          "tierRules":[
            {"tier":"T1","ruleType":"VALUE_RANGE","params":{"min":0.0,"max":39.999999}},
            {"tier":"T2","ruleType":"VALUE_RANGE","params":{"min":40.0,"max":79.999999}},
            {"tier":"T3","ruleType":"VALUE_RANGE","params":{"min":80.0,"max":119.999999}},
            {"tier":"T4","ruleType":"VALUE_RANGE","params":{"min":120.0,"max":159.999999}},
            {"tier":"T5","ruleType":"VALUE_RANGE","params":{"min":160.0,"max":1000.0}}
          ],
          "scoreCurve":{
            "type":"PIECEWISE_LINEAR",
            "params":{"anchors":[{"value":0.0,"score":0.0},{"value":1000.0,"score":100.0}]}
          }
        }
    """.trimIndent()

    private fun config(): String {
        val ids = metrics.joinToString(",") { "\"\${it.id}\"" }
        val definitions = metrics.joinToString(",") { metricJson(it) }
        return """
            {
              "schemaVersion":"1.0.0",
              "modelVersion":"integration-v1",
              "status":"VALIDATED_RELEASE",
              "referenceModel":"MALE",
              "enabledMetricIds":[\$ids],
              "categories":{
                "HARMONY":{"weight":0.25,"minimumCoverage":1.0},
                "DIMORPHISM":{"weight":0.25,"minimumCoverage":1.0},
                "ANGULARITY":{"weight":0.25,"minimumCoverage":1.0},
                "MISC":{"weight":0.25,"minimumCoverage":1.0}
              },
              "metrics":[\$definitions],
              "rankThresholds":null,
              "scoreScaleVersion":"integration-scale-v1",
              "coveragePolicyVersion":"integration-coverage-v1",
              "extremaSelectionVersion":"integration-extrema-v1",
              "provenance":{
                "researchDatasetVersion":"integration-fixture",
                "reviewState":"APPROVED",
                "approvedBy":["Ryan","Eddy"]
              }
            }
        """.trimIndent()
    }

    private fun engine(): ScoringEngine = ScoringEngine.loadRuntimeModel(
        path = "reference-models/v1/integration-fixture.json",
        bytes = config().toByteArray(),
        formulas = scoringFormulaRegistry,
        benchmarks = benchmarks,
    )

    private fun versions(hash: String) = AnalysisVersions(
        appVersion = "integration-test",
        analysisEngineVersion = "integration-test",
        frontLandmarkModelVersion = "synthetic-front",
        profileExtractorVersion = "synthetic-profile",
        referenceModelVersion = "integration-v1",
        scoringModelVersion = "integration-scorer",
        metricConfigHash = hash,
        enabledMetricSetVersion = "integration-set-v1",
        coveragePolicyVersion = "integration-coverage-v1",
        scoreScaleVersion = "integration-scale-v1",
        extremaSelectionVersion = "integration-extrema-v1",
        recommendationVersion = "none",
    )

    @Test
    fun geometryMeasurementsFlowIntoDeterministicScoring() {
        val engine = engine()
        val measurements = metrics.associate { metric ->
            val geometry = RepresentativeFormulaRegistry.registry.measure(
                metric.id,
                metric.formulaId,
                metric.frame(),
            ) as GeometryMeasurementResult.Available
            metric.id to Measurement(
                value = geometry.rawValue,
                confidence = geometry.confidence0To1,
                view = metric.view,
            )
        }

        val first = engine.score("integration-scan", measurements, versions(engine.configHash))
        assertTrue(first is AnalysisOutcome.Complete)
        first as AnalysisOutcome.Complete
        assertEquals(metrics.map { it.id }.toSet(), first.enabledMetricIds)
        assertTrue(first.metrics.all { it is MetricResult.Available })
        assertEquals(
            first,
            engine.score("integration-scan", measurements, versions(engine.configHash)),
        )
    }

    @Test
    fun geometryQualityFailurePropagatesToScoringCoverageFailure() {
        val rejected = RepresentativeFormulaRegistry.registry.measure(
            "integration.harmony",
            FormulaIds.FACIAL_ELONGATION,
            SyntheticGeometryFixtures.front(confidence = 0.5),
        ) as GeometryMeasurementResult.Unavailable
        assertEquals(GeometryFailureCode.LOW_CONFIDENCE, rejected.failureCode)

        val engine = engine()
        val measurements = metrics.drop(1).associate { metric ->
            val geometry = RepresentativeFormulaRegistry.registry.measure(
                metric.id,
                metric.formulaId,
                metric.frame(),
            ) as GeometryMeasurementResult.Available
            metric.id to Measurement(geometry.rawValue, geometry.confidence0To1, metric.view)
        }

        assertTrue(
            engine.score("integration-scan", measurements, versions(engine.configHash)) is
                AnalysisOutcome.InsufficientReliableMeasurements,
        )
    }
}
