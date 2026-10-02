package app.ascend.mobile.core.geometry

import app.ascend.mobile.core.model.MeasurementView

const val GEOMETRY_ENGINE_VERSION: String = "wp03-geometry-v1"

@JvmInline
value class LandmarkId(val value: String) {
    init { require(value.isNotBlank()) }
    override fun toString(): String = value
}

object Landmarks {
    val TRICHION = LandmarkId("trichion")
    val GLABELLA = LandmarkId("glabella")
    val BROW_LEVEL = LandmarkId("brow_level")
    val NASION = LandmarkId("nasion")
    val SUPRATIP = LandmarkId("supratip")
    val PRONASALE = LandmarkId("pronasale")
    val SUBNASALE = LandmarkId("subnasale")
    val COLUMELLA = LandmarkId("columella")
    val MENTON = LandmarkId("menton")
    val POGONION = LandmarkId("pogonion")
    val SUBLABIALE = LandmarkId("sublabiale")
    val LABRALE_SUPERIUS = LandmarkId("labrale_superius")
    val LABRALE_INFERIUS = LandmarkId("labrale_inferius")
    val STOMION = LandmarkId("stomion")
    val CUPIDS_BOW = LandmarkId("cupids_bow")
    val ZYGION_LEFT = LandmarkId("zygion_left")
    val ZYGION_RIGHT = LandmarkId("zygion_right")
    val GONION_LEFT = LandmarkId("gonion_left")
    val GONION_RIGHT = LandmarkId("gonion_right")
    val MEDIAL_CANTHUS_LEFT = LandmarkId("medial_canthus_left")
    val MEDIAL_CANTHUS_RIGHT = LandmarkId("medial_canthus_right")
    val LATERAL_CANTHUS_LEFT = LandmarkId("lateral_canthus_left")
    val LATERAL_CANTHUS_RIGHT = LandmarkId("lateral_canthus_right")
    val PUPIL_LEFT = LandmarkId("pupil_left")
    val PUPIL_RIGHT = LandmarkId("pupil_right")
    val UPPER_EYELID_LEFT = LandmarkId("upper_eyelid_left")
    val UPPER_EYELID_RIGHT = LandmarkId("upper_eyelid_right")
    val LOWER_EYELID_LEFT = LandmarkId("lower_eyelid_left")
    val LOWER_EYELID_RIGHT = LandmarkId("lower_eyelid_right")
    val INNER_BROW_LEFT = LandmarkId("inner_brow_left")
    val INNER_BROW_RIGHT = LandmarkId("inner_brow_right")
    val BROW_ARCH_LEFT = LandmarkId("brow_arch_left")
    val BROW_ARCH_RIGHT = LandmarkId("brow_arch_right")
    val OUTER_BROW_LEFT = LandmarkId("outer_brow_left")
    val OUTER_BROW_RIGHT = LandmarkId("outer_brow_right")
    val ALARE_LEFT = LandmarkId("alare_left")
    val ALARE_RIGHT = LandmarkId("alare_right")
    val MOUTH_CORNER_LEFT = LandmarkId("mouth_corner_left")
    val MOUTH_CORNER_RIGHT = LandmarkId("mouth_corner_right")
    val CHIN_LEFT = LandmarkId("chin_left")
    val CHIN_RIGHT = LandmarkId("chin_right")
    val TRAGUS = LandmarkId("tragus")
    val RAMUS_REFERENCE = LandmarkId("ramus_reference")
    val MANDIBULAR_BORDER_REFERENCE = LandmarkId("mandibular_border_reference")
    val CERVICAL_POINT = LandmarkId("cervical_point")
    val NECK_POINT = LandmarkId("neck_point")
    val NOSE_BRIDGE_UPPER = LandmarkId("nose_bridge_upper")
    val NOSE_BRIDGE_LOWER = LandmarkId("nose_bridge_lower")
}

data class LandmarkObservation(
    val point: Point2,
    val confidence0To1: Double,
) {
    init { require(confidence0To1.isFinite() && confidence0To1 in 0.0..1.0) }
}

/** Pose values are residual deviations from the requested FRONT or PROFILE target, not raw head yaw. */
data class PoseDeviation(
    val yawDegrees: Double = 0.0,
    val pitchDegrees: Double = 0.0,
    val rollDegrees: Double = 0.0,
) {
    init { require(listOf(yawDegrees, pitchDegrees, rollDegrees).all(Double::isFinite)) }
}

data class PixelResolution(val width: Int, val height: Int) {
    init { require(width > 0 && height > 0) }
    val shortEdge: Int get() = minOf(width, height)
}

data class GeometryFrame(
    val view: MeasurementView,
    val landmarks: Map<LandmarkId, LandmarkObservation>,
    val pose: PoseDeviation = PoseDeviation(),
    val resolution: PixelResolution,
    val rollOrigin: Point2? = null,
    /** Only set after an independently validated real-world scale calibration. */
    val millimetersPerNormalizedUnit: Double? = null,
) {
    init {
        require(millimetersPerNormalizedUnit == null || (millimetersPerNormalizedUnit.isFinite() && millimetersPerNormalizedUnit > 0.0))
    }

    /**
     * Landmark detectors report x/y independently normalized by image width/height.
     * Convert them into isotropic short-edge units before Euclidean geometry so
     * portrait/landscape aspect ratio cannot distort ratios, angles, or roll correction.
     */
    private fun toIsotropic(point: Point2): Point2 {
        val scale = resolution.shortEdge.toDouble()
        return Point2(
            x = point.x * resolution.width / scale,
            y = point.y * resolution.height / scale,
        )
    }

    private val normalizationOrigin: Point2 by lazy {
        rollOrigin?.let(::toIsotropic)
            ?: centroid(landmarks.values.map { toIsotropic(it.point) })
            ?: Point2(
                0.5 * resolution.width / resolution.shortEdge.toDouble(),
                0.5 * resolution.height / resolution.shortEdge.toDouble(),
            )
    }

    fun normalizedPoint(id: LandmarkId): Point2? = landmarks[id]?.point
        ?.let(::toIsotropic)
        ?.let { rotate(it, -pose.rollDegrees, normalizationOrigin) }
}

enum class MeasurementMode { AUTOMATIC, ASSISTED, MANUAL, UNSUPPORTED }
enum class ReliabilityStatus { UNVALIDATED, SYNTHETIC_VERIFIED, VALIDATED, UNSUPPORTED }
enum class ConfidencePropagation { MINIMUM_REQUIRED_LANDMARK }
enum class LightingDependency { EVEN_LIGHTING, SUFFICIENT_CONTRAST, NO_HARD_SHADOW_ON_REQUIRED_LANDMARKS }

data class PoseLimits(
    val maxAbsYawDeviationDegrees: Double,
    val maxAbsPitchDeviationDegrees: Double,
    val maxAbsRollDegrees: Double,
) {
    init {
        require(listOf(maxAbsYawDeviationDegrees, maxAbsPitchDeviationDegrees, maxAbsRollDegrees).all { it.isFinite() && it >= 0.0 })
    }

    fun accepts(pose: PoseDeviation): Boolean =
        kotlin.math.abs(pose.yawDegrees) <= maxAbsYawDeviationDegrees &&
            kotlin.math.abs(pose.pitchDegrees) <= maxAbsPitchDeviationDegrees &&
            kotlin.math.abs(pose.rollDegrees) <= maxAbsRollDegrees
}

data class ConfidenceRule(
    val minimumLandmarkConfidence0To1: Double,
    val propagation: ConfidencePropagation = ConfidencePropagation.MINIMUM_REQUIRED_LANDMARK,
) {
    init { require(minimumLandmarkConfidence0To1.isFinite() && minimumLandmarkConfidence0To1 in 0.0..1.0) }
}

enum class GeometryFailureCode {
    WRONG_VIEW,
    INSUFFICIENT_RESOLUTION,
    POSE_OUT_OF_RANGE,
    MISSING_LANDMARK,
    LOW_CONFIDENCE,
    CALIBRATION_REQUIRED,
    DEGENERATE_GEOMETRY,
    UNSUPPORTED_FORMULA,
}

sealed interface GeometryMeasurementResult {
    val metricId: String
    val formulaId: String
    val geometryEngineVersion: String

    data class Available(
        override val metricId: String,
        override val formulaId: String,
        val rawValue: Double,
        val normalizedValue: Double? = null,
        val confidence0To1: Double,
        val sourceLandmarks: Set<LandmarkId>,
        val measurementMode: MeasurementMode,
        override val geometryEngineVersion: String = GEOMETRY_ENGINE_VERSION,
    ) : GeometryMeasurementResult {
        init {
            require(metricId.isNotBlank() && formulaId.isNotBlank() && geometryEngineVersion.isNotBlank())
            require(rawValue.isFinite())
            require(normalizedValue == null || normalizedValue.isFinite())
            require(confidence0To1.isFinite() && confidence0To1 in 0.0..1.0)
            require(sourceLandmarks.isNotEmpty())
        }
    }

    data class Unavailable(
        override val metricId: String,
        override val formulaId: String,
        val failureCode: GeometryFailureCode,
        val detail: String,
        val sourceLandmarks: Set<LandmarkId>,
        override val geometryEngineVersion: String = GEOMETRY_ENGINE_VERSION,
    ) : GeometryMeasurementResult {
        init {
            require(metricId.isNotBlank() && formulaId.isNotBlank() && detail.isNotBlank() && geometryEngineVersion.isNotBlank())
        }
    }
}

interface GeometryFormula {
    val formulaId: String
    val view: MeasurementView
    val requiredLandmarks: Set<LandmarkId>
    val mode: MeasurementMode
    val calibrationRequired: Boolean
    val minimumShortEdgePixels: Int
    val poseLimits: PoseLimits
    val confidenceRule: ConfidenceRule

    fun evaluate(metricId: String, frame: GeometryFrame): GeometryMeasurementResult
}

internal data class PreparedGeometry(
    val metricId: String,
    val formula: GeometryFormula,
    val frame: GeometryFrame,
    val points: Map<LandmarkId, Point2>,
    val confidence0To1: Double,
)

internal fun GeometryFormula.prepare(metricId: String, frame: GeometryFrame): Pair<PreparedGeometry?, GeometryMeasurementResult.Unavailable?> {
    require(metricId.isNotBlank())
    fun unavailable(code: GeometryFailureCode, detail: String) = GeometryMeasurementResult.Unavailable(
        metricId = metricId,
        formulaId = formulaId,
        failureCode = code,
        detail = detail,
        sourceLandmarks = requiredLandmarks,
    )

    if (frame.view != view) return null to unavailable(GeometryFailureCode.WRONG_VIEW, "Expected $view but received ${frame.view}")
    if (frame.resolution.shortEdge < minimumShortEdgePixels) {
        return null to unavailable(GeometryFailureCode.INSUFFICIENT_RESOLUTION, "Short edge ${frame.resolution.shortEdge}px is below ${minimumShortEdgePixels}px")
    }
    if (!poseLimits.accepts(frame.pose)) return null to unavailable(GeometryFailureCode.POSE_OUT_OF_RANGE, "Pose deviation exceeds formula limits")
    if (calibrationRequired && frame.millimetersPerNormalizedUnit == null) {
        return null to unavailable(GeometryFailureCode.CALIBRATION_REQUIRED, "Validated scale calibration is required")
    }

    val missing = requiredLandmarks.filterNot(frame.landmarks::containsKey)
    if (missing.isNotEmpty()) return null to unavailable(GeometryFailureCode.MISSING_LANDMARK, "Missing landmarks: ${missing.joinToString()}")

    val observations = requiredLandmarks.map { frame.landmarks.getValue(it) }
    val minimumConfidence = observations.minOf { it.confidence0To1 }
    if (minimumConfidence < confidenceRule.minimumLandmarkConfidence0To1) {
        return null to unavailable(GeometryFailureCode.LOW_CONFIDENCE, "Minimum landmark confidence $minimumConfidence is below ${confidenceRule.minimumLandmarkConfidence0To1}")
    }

    val points = requiredLandmarks.associateWith { frame.normalizedPoint(it)!! }
    return PreparedGeometry(metricId, this, frame, points, minimumConfidence) to null
}
