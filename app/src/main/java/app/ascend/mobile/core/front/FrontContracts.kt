package app.ascend.mobile.core.front

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.MeasurementView
import kotlinx.serialization.Serializable

const val FRONT_CONTRACT_VERSION = "wp08-front-v1"

@Serializable
enum class FixtureOrigin { SYNTHETIC, CONSENTED_LOCAL }

@Serializable
data class FrontPoint(val x: Double, val y: Double, val confidence: Double?) {
    init {
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
    }
    fun point() = Point2(x, y)
}

/** Clockwise in a y-down image; residual yaw/pitch from FRONT, never raw model Euler angles. */
@Serializable
data class FrontPose(val yaw: Double, val pitch: Double, val roll: Double) {
    init { require(listOf(yaw, pitch, roll).all(Double::isFinite)) }
    fun geometry() = PoseDeviation(yaw, pitch, roll)
}

/** WP07 supplies oriented, unmirrored, final-crop coordinates; no MediaPipe indexes cross this seam. */
@Serializable
data class FrontInput(
    val contractVersion: String,
    val imageRevision: String,
    val width: Int,
    val height: Int,
    val faceShortEdgePixels: Int?,
    val origin: FixtureOrigin,
    val modelVersion: String,
    val modelArtifactSha256: String,
    val extractorVersion: String,
    val confidenceMethodVersion: String,
    val faceCount: Int?,
    val overallConfidence: Double?,
    val residualPose: FrontPose?,
    val landmarks: Map<String, FrontPoint>,
    val rollOrigin: FrontPoint,
) {
    init {
        require(contractVersion == FRONT_CONTRACT_VERSION)
        require(listOf(imageRevision, modelVersion, extractorVersion, confidenceMethodVersion).all(String::isNotBlank))
        require(modelArtifactSha256.matches(Regex("[a-f0-9]{64}")))
        require(width > 0 && height > 0)
        require(faceShortEdgePixels == null || faceShortEdgePixels in 1..minOf(width, height))
        require(faceCount == null || faceCount >= 0)
        require(overallConfidence == null || (overallConfidence.isFinite() && overallConfidence in 0.0..1.0))
        require(landmarks.keys.all { it in FrontCatalog.semanticIds })
    }

    fun geometryFrame(): GeometryFrame = GeometryFrame(
        view = MeasurementView.FRONT,
        landmarks = landmarks.filterValues { it.confidence != null }.mapKeys { LandmarkId(it.key) }.mapValues {
            LandmarkObservation(it.value.point(), minOf(requireNotNull(it.value.confidence), overallConfidence ?: 0.0))
        },
        pose = requireNotNull(residualPose).geometry(),
        resolution = PixelResolution(width, height),
        rollOrigin = rollOrigin.point(),
    )
}

/** No WP08 production defaults. Evidence identifies the reviewed policy, or an explicitly synthetic test policy. */
@Serializable
data class FrontPolicy(
    val version: String,
    val evidence: String,
    val syntheticOnly: Boolean,
    val minimumConfidence: Double,
    val minimumImageShortEdgePixels: Int,
    val minimumFaceShortEdgePixels: Int,
    val maximumYaw: Double,
    val maximumPitch: Double,
    val maximumRoll: Double,
) {
    init {
        require(version.isNotBlank() && evidence.isNotBlank())
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0)
        require(minimumImageShortEdgePixels > 0 && minimumFaceShortEdgePixels > 0)
        require(listOf(maximumYaw, maximumPitch, maximumRoll).all { it.isFinite() && it >= 0.0 })
    }
    fun poseLimits() = PoseLimits(maximumYaw, maximumPitch, maximumRoll)
}

enum class FrontFailure {
    POLICY_REQUIRED, POLICY_NOT_VALIDATED_FOR_CAPTURE, FACE_COUNT_UNAVAILABLE, NOT_ONE_FACE,
    POSE_UNAVAILABLE, INSUFFICIENT_RESOLUTION, POSE_OUT_OF_RANGE, MISSING_LANDMARK,
    LOW_CONFIDENCE, CALIBRATION_REQUIRED, DEGENERATE_GEOMETRY, UNSUPPORTED_DEFINITION,
}

data class FrontMetricResult(
    val metricId: String,
    val formulaId: String?,
    val unit: String?,
    val value: Double?,
    val confidence: Double?,
    val failure: FrontFailure?,
    val mode: MeasurementMode,
    val sourcePoints: Map<String, Point2>,
) {
    init {
        require((failure == null) == (value != null))
        require(value == null || value.isFinite())
    }
}

data class FrontReport(
    val contractVersion: String,
    val input: FrontInput,
    val policyVersion: String?,
    val geometryVersion: String,
    val metrics: List<FrontMetricResult>,
)
