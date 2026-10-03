package app.ascend.mobile.core.data

import app.ascend.mobile.core.model.CaptureView
import app.ascend.mobile.core.model.RetakeReason
import org.junit.Assert.*
import org.junit.Test

class PhotoQualityTest {
    // Synthetic test policy only; these values are not product/CV defaults.
    private val policy = PhotoQualityPolicy("test", 100, 120, 0.2, 0.8, 0.01, 0.1, 10.0, 10.0, 10.0, 70.0, 100.0)

    @Test fun flatImageHasNoSharpness() {
        val stats = PhotoQuality.statistics(DoubleArray(25) { 0.5 }, 5, 5)
        assertEquals(0.5, stats.meanLuma, 0.0)
        assertEquals(0.0, stats.laplacianVariance, 0.0)
        assertEquals(setOf(RetakeReason.TOO_BLURRY), PhotoQuality.evaluate(100, 120, stats, policy))
    }

    @Test fun sharpGridHasVariance() {
        val stats = PhotoQuality.statistics(DoubleArray(25) { if (it % 2 == 0) 1.0 else 0.0 }, 5, 5)
        assertTrue(stats.laplacianVariance > 0.01)
    }

    @Test fun checksAllQualityReasonsTogether() {
        assertEquals(setOf(RetakeReason.TOO_DARK, RetakeReason.TOO_BLURRY, RetakeReason.INSUFFICIENT_RESOLUTION),
            PhotoQuality.evaluate(99, 120, QualityStatistics(0.1, 0.0), policy))
        assertEquals(setOf(RetakeReason.TOO_BRIGHT), PhotoQuality.evaluate(100, 120, QualityStatistics(0.9, 1.0), policy))
    }

    @Test fun inclusiveThresholdsPass() {
        assertTrue(PhotoQuality.evaluate(100, 120, QualityStatistics(0.2, 0.01), policy).isEmpty())
        assertTrue(PhotoQuality.evaluate(100, 120, QualityStatistics(0.8, 0.01), policy).isEmpty())
    }

    @Test fun absentHookOrPartialObservationIsPending() {
        assertTrue(FaceQuality.evaluate(CaptureView.FRONT, null, policy).pending)
        assertTrue(FaceQuality.evaluate(CaptureView.FRONT, FaceObservation(1), policy).pending)
    }

    @Test fun validatesCountWithoutPose() {
        assertEquals(setOf(RetakeReason.NO_FACE), FaceQuality.evaluate(CaptureView.FRONT, FaceObservation(0), policy).reasons)
        assertEquals(setOf(RetakeReason.MULTIPLE_FACES), FaceQuality.evaluate(CaptureView.FRONT, FaceObservation(2), policy).reasons)
    }

    @Test fun frontAndProfileHaveDifferentYawRules() {
        val front = FaceObservation(1, 0.5, 0.5, 0.0, 0.0, 0.0)
        assertFalse(FaceQuality.evaluate(CaptureView.FRONT, front, policy).pending)
        assertTrue(FaceQuality.evaluate(CaptureView.FRONT, front, policy).reasons.isEmpty())
        assertEquals(setOf(RetakeReason.INVALID_POSE), FaceQuality.evaluate(CaptureView.PROFILE, front, policy).reasons)
        assertTrue(FaceQuality.evaluate(CaptureView.PROFILE, front.copy(yaw = -90.0), policy).reasons.isEmpty())
    }

    @Test fun centeringPitchAndRollAreChecked() {
        assertEquals(setOf(RetakeReason.OFF_CENTER, RetakeReason.INVALID_POSE),
            FaceQuality.evaluate(CaptureView.FRONT, FaceObservation(1, 0.8, 0.5, 0.0, 20.0, 20.0), policy).reasons)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFinitePixels() { PhotoQuality.statistics(DoubleArray(9) { Double.NaN }, 3, 3) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFinitePose() { FaceObservation(1, yaw = Double.NaN) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedGrid() { PhotoQuality.statistics(DoubleArray(8), 3, 3) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteCrop() { CaptureCrop(viewportAspectRatio = Float.NaN) }
}
