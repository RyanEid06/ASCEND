package app.ascend.mobile.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepresentativeFormulasTest {
    private val registry = RepresentativeFormulaRegistry.registry

    @Test fun frontRatiosProduceExpectedSyntheticValues() {
        val frame = SyntheticGeometryFixtures.front()
        val elongation = registry.measure("FQ_H_005", FormulaIds.FACIAL_ELONGATION, frame) as GeometryMeasurementResult.Available
        val lower = registry.measure("FQ_H_033", FormulaIds.LOWER_FACE_SUBDIVISION, frame) as GeometryMeasurementResult.Available
        val jaw = registry.measure("FQ_H_032", FormulaIds.JAW_TO_CHEEK_WIDTH, frame) as GeometryMeasurementResult.Available
        assertEquals(1.6, elongation.rawValue, 1e-9)
        assertEquals(28.5714285714, lower.rawValue, 1e-6)
        assertEquals(80.0, jaw.rawValue, 1e-9)
    }

    @Test fun rollNormalizationKeepsVerticalHorizontalRatioStable() {
        val base = SyntheticGeometryFixtures.front()
        val pivot = centroid(base.landmarks.values.map { it.point })!!
        val rolledPoints = base.landmarks.mapValues { rotate(it.value.point, 10.0, pivot) }
        val rolled = SyntheticGeometryFixtures.front(
            pose = PoseDeviation(rollDegrees = 10.0),
            points = rolledPoints,
        )
        val normal = registry.measure("m", FormulaIds.FACIAL_ELONGATION, base) as GeometryMeasurementResult.Available
        val corrected = registry.measure("m", FormulaIds.FACIAL_ELONGATION, rolled) as GeometryMeasurementResult.Available
        assertEquals(normal.rawValue, corrected.rawValue, 1e-9)
    }

    @Test fun profileAnglesAreAvailableAndAssisted() {
        val result = registry.measure("FQ_H_038", FormulaIds.NASOFRONTAL_ANGLE, SyntheticGeometryFixtures.profile())
        assertTrue(result is GeometryMeasurementResult.Available)
        result as GeometryMeasurementResult.Available
        assertTrue(result.rawValue in 0.0..180.0)
        assertEquals(MeasurementMode.ASSISTED, result.measurementMode)
    }

    @Test fun qualityFailuresFailClosed() {
        val lowConfidence = registry.measure(
            "m",
            FormulaIds.FACIAL_ELONGATION,
            SyntheticGeometryFixtures.front(confidence = 0.5),
        ) as GeometryMeasurementResult.Unavailable
        assertEquals(GeometryFailureCode.LOW_CONFIDENCE, lowConfidence.failureCode)

        val badPose = registry.measure(
            "m",
            FormulaIds.FACIAL_ELONGATION,
            SyntheticGeometryFixtures.front(pose = PoseDeviation(yawDegrees = 20.0)),
        ) as GeometryMeasurementResult.Unavailable
        assertEquals(GeometryFailureCode.POSE_OUT_OF_RANGE, badPose.failureCode)

        val lowResolution = registry.measure(
            "m",
            FormulaIds.FACIAL_ELONGATION,
            SyntheticGeometryFixtures.front(resolution = PixelResolution(640, 1080)),
        ) as GeometryMeasurementResult.Unavailable
        assertEquals(GeometryFailureCode.INSUFFICIENT_RESOLUTION, lowResolution.failureCode)
    }
}
