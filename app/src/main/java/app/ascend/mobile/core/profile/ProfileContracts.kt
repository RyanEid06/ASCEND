package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide

/** Lane-private spike; not a frozen WP09/storage API or a production capture policy. */
internal const val PROFILE_EXPERIMENT_VERSION = "wp10-profile-synthetic-v1"

// DEMO_LOCAL permits point confirmation on a local photo, never measurement/scoring admission.
internal enum class ProfileOrigin { SYNTHETIC, CONSENTED_LOCAL, DEMO_LOCAL }
internal enum class ProfilePointSource { AUTOMATIC, ASSISTED }

internal data class ProfilePoint(
    val point: Point2,
    val confidence: Double?,
    val source: ProfilePointSource,
    val confirmed: Boolean = false,
) {
    init {
        require(point.x in 0.0..1.0 && point.y in 0.0..1.0)
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
        require(source != ProfilePointSource.ASSISTED || confirmed)
    }
}

internal data class ProfileProvider(val extractorVersion: String, val modelVersion: String?, val modelSha256: String?) {
    init {
        require(extractorVersion.isNotBlank())
        require((modelVersion == null) == (modelSha256 == null))
        require(modelVersion == null || modelVersion.isNotBlank())
        require(modelSha256 == null || modelSha256.matches(Regex("[a-f0-9]{64}")))
    }
}

/** Anatomical side is independent of screen-facing direction; coordinates never silently mirror. */
internal data class ProfileInput(
    val imageRevision: String,
    val resolution: PixelResolution,
    val faceShortEdgePixels: Int?,
    val origin: ProfileOrigin,
    val side: ProfileSide?,
    val orientationConfirmed: Boolean,
    val orientationMethodVersion: String?,
    val faceCount: Int?,
    val residualPose: PoseDeviation?,
    val rollOrigin: Point2,
    val overallConfidence: Double?,
    val confidenceMethodVersion: String,
    val provider: ProfileProvider?,
    val points: Map<LandmarkId, ProfilePoint>,
) {
    init {
        require(imageRevision.isNotBlank() && confidenceMethodVersion.isNotBlank())
        require(faceShortEdgePixels == null || faceShortEdgePixels in 1..resolution.shortEdge)
        require(faceCount == null || faceCount >= 0)
        require(rollOrigin.x in 0.0..1.0 && rollOrigin.y in 0.0..1.0)
        require(overallConfidence == null || (overallConfidence.isFinite() && overallConfidence in 0.0..1.0))
        require(!orientationConfirmed || (side != null && !orientationMethodVersion.isNullOrBlank()))
        require(orientationMethodVersion == null || orientationMethodVersion.isNotBlank())
        require(points.keys.all { it in ProfileCatalog.semanticIds })
        require(points.values.none { it.source == ProfilePointSource.AUTOMATIC } || provider != null)
    }

    fun isotropic(point: Point2) = Point2(
        point.x * resolution.width / resolution.shortEdge,
        point.y * resolution.height / resolution.shortEdge,
    )

    fun normalized(point: Point2): Point2 = rotate(
        isotropic(point), -requireNotNull(residualPose).rollDegrees, isotropic(rollOrigin),
    )

    fun imagePoint(normalized: Point2): Point2 {
        val raw = rotate(normalized, requireNotNull(residualPose).rollDegrees, isotropic(rollOrigin))
        return Point2(raw.x * resolution.shortEdge / resolution.width, raw.y * resolution.shortEdge / resolution.height)
    }
}

/** Explicit test limits only. No real-photo policy is supplied by this experiment. */
internal data class ProfilePolicy(
    val version: String,
    val evidence: String,
    val minimumAutomaticConfidence: Double,
    val minimumImageShortEdgePixels: Int,
    val minimumFaceShortEdgePixels: Int,
    val poseLimits: PoseLimits,
    val confirmationRequired: Set<LandmarkId>,
) {
    init {
        require(version.isNotBlank() && evidence.isNotBlank())
        require(minimumAutomaticConfidence.isFinite() && minimumAutomaticConfidence in 0.0..1.0)
        require(minimumImageShortEdgePixels > 0 && minimumFaceShortEdgePixels > 0)
        require(confirmationRequired.all { it in ProfileCatalog.semanticIds })
    }
}

internal enum class ProfileFailure {
    UNSUPPORTED_DEFINITION, POLICY_REQUIRED, SYNTHETIC_ONLY, ORIENTATION_REQUIRED,
    FACE_COUNT_UNAVAILABLE, NOT_ONE_FACE, POSE_UNAVAILABLE, POSE_OUT_OF_RANGE,
    INSUFFICIENT_RESOLUTION, MISSING_POINT, CONFIRMATION_REQUIRED, LOW_CONFIDENCE,
    DEGENERATE_GEOMETRY,
}

internal data class ProfileMetricResult(
    val metricId: String,
    val formulaId: String?,
    val unit: String?,
    val value: Double?,
    val confidence: Double?,
    val mode: MeasurementMode,
    val failure: ProfileFailure?,
    val reliability: ReliabilityStatus,
    val sourcePoints: Map<LandmarkId, Point2>,
    val softTissueProxy: Boolean,
) {
    init {
        require((value != null) == (failure == null))
        require(value == null || value.isFinite())
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
        require(failure == null || (mode == MeasurementMode.UNSUPPORTED && confidence == null))
        require(value == null || (formulaId != null && unit != null && sourcePoints.isNotEmpty()))
        require(reliability != ReliabilityStatus.VALIDATED)
    }
}

internal data class ProfileReport(
    val experimentVersion: String,
    val imageRevision: String,
    val policyVersion: String?,
    val side: ProfileSide?,
    val metrics: List<ProfileMetricResult>,
)
