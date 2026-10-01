package app.ascend.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisContractTest {
    private val versions = AnalysisVersions(
        appVersion = "0.1.0",
        analysisEngineVersion = "engine-test",
        frontLandmarkModelVersion = "front-test",
        profileExtractorVersion = "profile-test",
        referenceModelVersion = "reference-test",
        scoringModelVersion = "scoring-test",
        metricConfigHash = "hash-test",
        enabledMetricSetVersion = "enabled-test",
        coveragePolicyVersion = "coverage-test",
        scoreScaleVersion = "scale-test",
        extremaSelectionVersion = "extrema-test",
        recommendationVersion = "recommendation-test",
    )

    private fun metric(id: String, scale: String? = "scale-test") = MetricResult.Available(
        metricId = id,
        category = Category.HARMONY,
        view = MeasurementView.FRONT,
        measuredValue = 1.2,
        tier = Tier.T2,
        hiddenScore0To100 = 82.0,
        confidence0To1 = 0.9,
        weightUsed = 1.0,
        scoreScaleVersion = scale,
    )

    private fun categories() = Category.entries.associateWith {
        CategoryResult(it, 8.2, 1.0, "coverage-test")
    }

    private fun complete(
        metrics: List<MetricResult> = listOf(metric("eye"), metric("nose")),
        strongest: Set<String> = setOf("eye"),
        weakest: Set<String> = setOf("nose"),
    ) = AnalysisOutcome.Complete(
        scanId = "scan-1",
        referenceModel = ReferenceModel.MALE,
        versions = versions,
        enabledMetricIds = setOf("eye", "nose"),
        metrics = metrics,
        categories = categories(),
        overallScore0To10 = 8.2,
        rankLabel = null,
        strongestMetricIds = strongest,
        weakestMetricIds = weakest,
    )

    @Test fun unavailableMetricCannotBeSelectedAsAnExtremum() {
        expectInvalid {
            complete(metrics = listOf(metric("eye"), MetricResult.Unavailable("nose", "blur")))
        }
    }

    @Test fun incompatibleScoreScaleCannotBeSelectedAsAnExtremum() {
        expectInvalid { complete(metrics = listOf(metric("eye"), metric("nose", "other-scale"))) }
    }

    @Test fun completedResultPreservesDeterministicTieSets() {
        val result = complete(strongest = setOf("eye", "nose"), weakest = setOf("eye", "nose"))
        assertEquals(setOf("eye", "nose"), result.strongestMetricIds)
        assertEquals("extrema-test", result.versions.extremaSelectionVersion)
    }

    @Test fun incompleteResultHasNoOverallScore() {
        val result: AnalysisOutcome = AnalysisOutcome.InsufficientReliableMeasurements(
            scanId = "scan-1", unavailableMetricIds = setOf("nose"), reason = "Low confidence"
        )
        assertTrue(result !is AnalysisOutcome.Complete)
    }

    @Test fun invalidScoreAndConfidenceAreRejected() {
        expectInvalid { metric("eye").copy(hiddenScore0To100 = Double.NaN) }
        expectInvalid { metric("eye").copy(confidence0To1 = 1.1) }
        expectInvalid { complete().copy(overallScore0To10 = Double.POSITIVE_INFINITY) }
    }

    @Test fun completeSessionRequiresCompletionTime() {
        expectInvalid {
            ScanSession("scan-1", ScanOwner.Guest, ReferenceModel.MALE, ScanState.COMPLETE, 1, 2)
        }
    }

    private inline fun expectInvalid(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
