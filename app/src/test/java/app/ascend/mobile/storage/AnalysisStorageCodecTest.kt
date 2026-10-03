package app.ascend.mobile.storage

import app.ascend.mobile.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisStorageCodecTest {
    @Test fun preservesHistoricalProvenanceAndUnavailableMetrics() {
        val original = fixture("scan")
        assertEquals(original, AnalysisStorageCodec.decode(AnalysisStorageCodec.encode(original)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSnapshotVersion() {
        val text = AnalysisStorageCodec.encode(fixture("scan")).toString(Charsets.UTF_8)
        AnalysisStorageCodec.decode(text.replace("\"formatVersion\":1", "\"formatVersion\":2").toByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechecksCoreScoreInvariants() {
        val text = AnalysisStorageCodec.encode(fixture("scan")).toString(Charsets.UTF_8)
        AnalysisStorageCodec.decode(text.replace("\"overall\":8.0", "\"overall\":11.0").toByteArray())
    }

    companion object {
        fun fixture(id: String) = AnalysisOutcome.Complete(id, ReferenceModel.MALE,
            AnalysisVersions("app", "geometry", "front", "profile", "reference", "scoring", "hash", "enabled", "coverage", "scale", "extrema", "recommendations"),
            setOf("available", "unavailable"), listOf(
                MetricResult.Available("available", Category.HARMONY, MeasurementView.FRONT, 1.25, Tier.T2, 80.0, 0.9, 1.0, "scale"),
                MetricResult.Unavailable("unavailable", "not_visible"),
            ), Category.entries.associateWith { CategoryResult(it, 8.0, 0.9, "coverage") },
            8.0, null, setOf("available"), setOf("available"))
    }
}
