package app.ascend.mobile.core.vision

import app.ascend.mobile.core.geometry.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class FrontLandmarkPipelineTest {
    private val resolution = PixelResolution(600, 800)
    private fun mesh() = List(478) { MeshPoint(0.5, 0.5, 0.0) }.toMutableList().apply {
        this[468] = MeshPoint(0.4, 0.5, 0.0)
        this[473] = MeshPoint(0.6, 0.5, 0.0)
        this[10] = MeshPoint(0.5, 0.2, 0.0)
    }
    private fun matrix() = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
    private fun detected(points: List<MeshPoint> = mesh(), transform: DoubleArray? = matrix()) =
        FrontLandmarkProcessor.process(listOf(RawFrontFace(points, transform)), resolution, "a".repeat(64))
    private fun snapshot() = (detected() as FrontExtraction.Detected).snapshot

    @Test fun rejectsZeroAndMultipleFaces() {
        assertEquals(FrontFailure.NO_FACE, (FrontLandmarkProcessor.process(emptyList(), resolution, "a".repeat(64)) as FrontExtraction.Failed).reason)
        assertEquals(FrontFailure.MULTIPLE_FACES, (FrontLandmarkProcessor.process(List(2) { RawFrontFace(mesh(), matrix()) }, resolution, "a".repeat(64)) as FrontExtraction.Failed).reason)
    }
    @Test fun rejectsMalformedMeshAndUnavailablePose() {
        assertEquals(FrontFailure.INVALID_MESH, (detected(mesh().dropLast(1)) as FrontExtraction.Failed).reason)
        assertEquals(FrontFailure.INVALID_MESH, (detected(mesh().apply { this[0] = MeshPoint(Double.NaN, 0.5, 0.0) }) as FrontExtraction.Failed).reason)
        assertEquals(FrontFailure.INVALID_MESH, (detected(mesh().apply { this[0] = MeshPoint(1.1, 0.5, 0.0) }) as FrontExtraction.Failed).reason)
        assertEquals(FrontFailure.POSE_UNAVAILABLE, (detected(transform = null) as FrontExtraction.Failed).reason)
        assertEquals(FrontFailure.POSE_UNAVAILABLE, (detected(transform = DoubleArray(16)) as FrontExtraction.Failed).reason)
    }
    @Test fun extractsColumnMajorYawAndPitchAndRejectsNonRigidMatrix() {
        val radians = Math.toRadians(15.0)
        val yaw = matrix().apply { this[0] = cos(radians); this[2] = -sin(radians); this[8] = sin(radians); this[10] = cos(radians) }
        assertEquals(15.0, PoseEstimator.fromColumnMajor(yaw)!!.yawDegrees, 1e-8)
        val pitch = matrix().apply { this[5] = cos(radians); this[6] = sin(radians); this[9] = -sin(radians); this[10] = cos(radians) }
        assertEquals(15.0, PoseEstimator.fromColumnMajor(pitch)!!.pitchDegrees, 1e-8)
        assertNull(PoseEstimator.fromColumnMajor(matrix().apply { this[0] = 2.0 }))
    }
    @Test fun normalizesAspectAndInverseRestoresExactImageCoordinates() {
        val snapshot = snapshot()
        val normalized = snapshot.analysisPoint(10)
        assertEquals(0.5, normalized.x, 1e-9)
        assertEquals(800.0 / 600.0 * 0.2, normalized.y, 1e-9)
        val image = snapshot.imagePoint(normalized)
        assertEquals(0.5, image.x, 1e-9)
        assertEquals(0.2, image.y, 1e-9)
    }
    @Test fun removesRollAndOverlayUsesSameTransform() {
        val origin = Point2(0.5, 2.0 / 3.0)
        val rotated = mesh().map { p ->
            val point = rotate(Point2(p.x, p.y * 4.0 / 3.0), 12.0, origin)
            MeshPoint(point.x, point.y * 3.0 / 4.0, p.z)
        }
        val result = (detected(rotated) as FrontExtraction.Detected).snapshot
        assertEquals(12.0, result.pose.rollDegrees, 1e-8)
        assertEquals(0.5, result.analysisPoint(10).x, 1e-8)
        assertEquals(0.2 * 4.0 / 3.0, result.analysisPoint(10).y, 1e-8)
        val fit = FrontOverlayTransform.fit(result, 1000.0, 1000.0)
        val displayed = fit.project(result.analysisPoint(10))
        assertEquals(125.0 + rotated[10].x * 750.0, displayed.x, 1e-7)
        assertEquals(rotated[10].y * 1000.0, displayed.y, 1e-7)
    }
    @Test fun geometryDoesNotInventConfidenceAndMatchesNormalizedMeshWhenReviewed() {
        val snapshot = snapshot()
        assertNull(snapshot.landmarkConfidence0To1)
        assertTrue(FrontQualityIssue.CONFIDENCE_UNAVAILABLE in snapshot.qualityIssues)
        val mapping = FrontGeometryMapping("synthetic-test", mapOf(Landmarks.MENTON to 10))
        assertNull(FrontGeometryAdapter.frame(snapshot, mapping, emptyMap()))
        val frame = FrontGeometryAdapter.frame(snapshot, mapping, mapOf(10 to 0.95))!!
        assertEquals(snapshot.analysisPoint(10), frame.normalizedPoint(Landmarks.MENTON))
        assertEquals(0.95, frame.landmarks.getValue(Landmarks.MENTON).confidence0To1, 0.0)
    }
    @Test fun poorPoseAndResolutionAreFlaggedAndCannotEnterGeometry() {
        val radians = Math.toRadians(35.0)
        val yaw = matrix().apply { this[0] = cos(radians); this[2] = -sin(radians); this[8] = sin(radians); this[10] = cos(radians) }
        val snapshot = (detected(transform = yaw) as FrontExtraction.Detected).snapshot
        assertTrue(FrontQualityIssue.POSE_OUT_OF_RANGE in snapshot.qualityIssues)
        assertNull(FrontGeometryAdapter.frame(snapshot, FrontGeometryMapping("test", mapOf(Landmarks.MENTON to 10)), mapOf(10 to 1.0)))
        val small = (FrontLandmarkProcessor.process(listOf(RawFrontFace(mesh(), matrix())), PixelResolution(100, 200), "a".repeat(64)) as FrontExtraction.Detected).snapshot
        assertTrue(FrontQualityIssue.INSUFFICIENT_RESOLUTION in small.qualityIssues)
    }
    @Test fun codecRoundTripsAndRejectsCorruptionOrUnknownVersion() {
        val snapshot = snapshot()
        val bytes = FrontLandmarkCodec.encode(snapshot)
        val restored = FrontLandmarkCodec.decode(bytes)
        assertEquals(snapshot, restored)
        assertThrows(IllegalArgumentException::class.java) { FrontLandmarkCodec.decode(bytes.copyOf(bytes.size - 1)) }
        assertThrows(IllegalArgumentException::class.java) { FrontLandmarkCodec.decode(bytes + byteArrayOf(0)) }
        assertThrows(IllegalArgumentException::class.java) { FrontLandmarkCodec.decode(bytes.clone().apply { this[3] = 99 }) }
    }
    @Test fun mappingCannotConsumeAnotherModelVersion() {
        val older = snapshot().copy(modelVersion = "unrelated-model")
        assertNull(FrontGeometryAdapter.frame(older,
            FrontGeometryMapping("synthetic-test", mapOf(Landmarks.MENTON to 10)), mapOf(10 to 0.95)))
    }
    @Test fun corruptedDerivedCacheCanBeRecomputedWhileStrictDecoderStillRejectsIt() {
        val valid = FrontLandmarkCodec.encode(snapshot())
        assertEquals(snapshot(), FrontLandmarkCodec.decodeCached(valid))
        assertNull(FrontLandmarkCodec.decodeCached(valid.copyOf(10)))
        assertThrows(IllegalArgumentException::class.java) { FrontLandmarkCodec.decode(valid.copyOf(10)) }
    }
}
