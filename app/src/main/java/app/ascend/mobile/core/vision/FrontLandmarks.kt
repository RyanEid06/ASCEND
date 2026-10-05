package app.ascend.mobile.core.vision

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.MeasurementView
import kotlin.math.*

const val FRONT_PIPELINE_VERSION = "wp07-front-v1"
const val FRONT_POLICY_VERSION = "wp07-preview-v1-unvalidated"
const val FRONT_MODEL_SHA256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
const val FRONT_MODEL_VERSION = "mediapipe-face-landmarker-float16-v1:$FRONT_MODEL_SHA256"
const val FRONT_PROVIDER_VERSION = "mediapipe-tasks-vision-1.0.0"
const val FRONT_MESH_SIZE = 478

/** Provider output is deliberately untrusted until processed. z is model-relative, never millimetres. */
data class MeshPoint(val x: Double, val y: Double, val z: Double)
data class RawFrontFace(val points: List<MeshPoint>, val columnMajorTransform: DoubleArray?)
enum class FrontFailure { NO_FACE, MULTIPLE_FACES, INVALID_MESH, POSE_UNAVAILABLE, MODEL_UNAVAILABLE, INFERENCE_FAILED }
enum class FrontQualityIssue { CONFIDENCE_UNAVAILABLE, POSE_OUT_OF_RANGE, INSUFFICIENT_RESOLUTION, OFF_CENTER }
sealed interface FrontExtraction {
    data class Detected(val snapshot: FrontLandmarkSnapshot) : FrontExtraction
    data class Failed(val reason: FrontFailure) : FrontExtraction
}

/** Technical preview gates only; these are not validated measurement admission thresholds. */
private fun issues(points: List<MeshPoint>, pose: PoseDeviation, resolution: PixelResolution): Set<FrontQualityIssue> = buildSet {
    add(FrontQualityIssue.CONFIDENCE_UNAVAILABLE)
    if (abs(pose.yawDegrees) > 20 || abs(pose.pitchDegrees) > 20 || abs(pose.rollDegrees) > 30) add(FrontQualityIssue.POSE_OUT_OF_RANGE)
    if (resolution.shortEdge < 256) add(FrontQualityIssue.INSUFFICIENT_RESOLUTION)
    val x = (points.minOf { it.x } + points.maxOf { it.x }) / 2
    val y = (points.minOf { it.y } + points.maxOf { it.y }) / 2
    if (abs(x - 0.5) > 0.25 || abs(y - 0.5) > 0.25) add(FrontQualityIssue.OFF_CENTER)
}

data class FrontLandmarkSnapshot(
    val resolution: PixelResolution,
    val points: List<MeshPoint>,
    val pose: PoseDeviation,
    val sourceImageSha256: String,
    val modelVersion: String = FRONT_MODEL_VERSION,
    val providerVersion: String = FRONT_PROVIDER_VERSION,
    val pipelineVersion: String = FRONT_PIPELINE_VERSION,
    val policyVersion: String = FRONT_POLICY_VERSION,
) {
    init {
        require(points.size == FRONT_MESH_SIZE)
        require(points.all { it.x.isFinite() && it.x in 0.0..1.0 && it.y.isFinite() && it.y in 0.0..1.0 && it.z.isFinite() })
        require(sourceImageSha256.matches(Regex("[0-9a-f]{64}")))
        require(listOf(modelVersion, providerVersion, pipelineVersion, policyVersion).all { it.isNotBlank() && it.length <= 256 })
        require(listOf(pose.yawDegrees, pose.pitchDegrees, pose.rollDegrees).all { it in -180.0..180.0 })
        require(distance(toIsotropic(468), toIsotropic(473)) > 1e-6)
    }
    val landmarkConfidence0To1: Double? get() = null
    val qualityIssues: Set<FrontQualityIssue> get() = issues(points, pose, resolution)
    val rollOrigin: Point2 get() = Point2((points[468].x + points[473].x) / 2, (points[468].y + points[473].y) / 2)
    private fun toIsotropic(index: Int) = Point2(points[index].x * resolution.width / resolution.shortEdge,
        points[index].y * resolution.height / resolution.shortEdge)
    private fun isotropicOrigin() = Point2(rollOrigin.x * resolution.width / resolution.shortEdge,
        rollOrigin.y * resolution.height / resolution.shortEdge)
    fun analysisPoint(index: Int): Point2 = rotate(toIsotropic(index), -pose.rollDegrees, isotropicOrigin())
    fun imagePoint(analysisPoint: Point2): Point2 = rotate(analysisPoint, pose.rollDegrees, isotropicOrigin()).let {
        Point2(it.x * resolution.shortEdge / resolution.width, it.y * resolution.shortEdge / resolution.height)
    }
}

object FrontLandmarkProcessor {
    fun process(faces: List<RawFrontFace>, resolution: PixelResolution, sourceImageSha256: String): FrontExtraction {
        if (faces.isEmpty()) return FrontExtraction.Failed(FrontFailure.NO_FACE)
        if (faces.size != 1) return FrontExtraction.Failed(FrontFailure.MULTIPLE_FACES)
        val face = faces.single()
        val points = face.points
        if (points.size != FRONT_MESH_SIZE || points.any { !it.x.isFinite() || it.x !in 0.0..1.0 || !it.y.isFinite() || it.y !in 0.0..1.0 || !it.z.isFinite() }) {
            return FrontExtraction.Failed(FrontFailure.INVALID_MESH)
        }
        val estimated = face.columnMajorTransform?.let(PoseEstimator::fromColumnMajor)
            ?: return FrontExtraction.Failed(FrontFailure.POSE_UNAVAILABLE)
        // The model matrix is in camera coordinates. Roll for image-space geometry comes from
        // the iris baseline in isotropic image coordinates (including the y-down sign convention).
        val a = points[468]; val b = points[473]
        val dx = (b.x - a.x) * resolution.width
        val dy = (b.y - a.y) * resolution.height
        if (hypot(dx, dy) <= 1e-6 || dx <= 0) return FrontExtraction.Failed(FrontFailure.INVALID_MESH)
        val roll = Math.toDegrees(atan2(dy, dx))
        return FrontExtraction.Detected(FrontLandmarkSnapshot(resolution, points.toList(), estimated.copy(rollDegrees = roll), sourceImageSha256))
    }
}

object PoseEstimator {
    /** Euler Rz*Ry*Rx from the provider's column-major similarity transform; no perspective correction. */
    fun fromColumnMajor(matrix: DoubleArray): PoseDeviation? {
        if (matrix.size != 16 || matrix.any { !it.isFinite() }) return null
        if (abs(matrix[3]) > 1e-5 || abs(matrix[7]) > 1e-5 || abs(matrix[11]) > 1e-5 || abs(matrix[15] - 1) > 1e-5) return null
        val columns = List(3) { column -> DoubleArray(3) { row -> matrix[column * 4 + row] } }
        val lengths = columns.map { column -> sqrt(column.sumOf { it * it }) }
        if (lengths.any { it <= 1e-6 } || lengths.any { abs(it / lengths[0] - 1) > 0.05 }) return null
        val r = columns.mapIndexed { index, column -> column.map { it / lengths[index] } }
        for (a in 0..2) for (b in a + 1..2) if (abs((0..2).sumOf { r[a][it] * r[b][it] }) > 0.05) return null
        val determinant = r[0][0] * (r[1][1] * r[2][2] - r[2][1] * r[1][2]) -
            r[1][0] * (r[0][1] * r[2][2] - r[2][1] * r[0][2]) + r[2][0] * (r[0][1] * r[1][2] - r[1][1] * r[0][2])
        if (determinant < 0.95) return null
        val yaw = asin((-r[0][2]).coerceIn(-1.0, 1.0))
        if (abs(cos(yaw)) < 1e-6) return null // Euler singularity: fail instead of guessing pitch.
        return PoseDeviation(Math.toDegrees(yaw), Math.toDegrees(atan2(r[1][2], r[2][2])), Math.toDegrees(atan2(r[0][1], r[0][0])))
    }
}

/** WP08 owns the reviewed anatomy-to-mesh mapping; unsupported anatomy is left absent. */
data class FrontGeometryMapping(val version: String, val indices: Map<LandmarkId, Int>, val modelVersion: String = FRONT_MODEL_VERSION) {
    init { require(version.isNotBlank() && modelVersion.isNotBlank() && indices.isNotEmpty() && indices.values.all { it in 0 until FRONT_MESH_SIZE }) }
}
object FrontGeometryAdapter {
    fun frame(snapshot: FrontLandmarkSnapshot, mapping: FrontGeometryMapping, calibratedConfidence: Map<Int, Double>): GeometryFrame? {
        if (snapshot.modelVersion != mapping.modelVersion) return null
        if (snapshot.qualityIssues.any { it != FrontQualityIssue.CONFIDENCE_UNAVAILABLE }) return null
        if (mapping.indices.values.any { calibratedConfidence[it]?.let { c -> c.isFinite() && c in 0.0..1.0 } != true }) return null
        return GeometryFrame(MeasurementView.FRONT, mapping.indices.mapValues { (_, index) ->
            LandmarkObservation(Point2(snapshot.points[index].x, snapshot.points[index].y), calibratedConfidence.getValue(index))
        }, snapshot.pose, snapshot.resolution, snapshot.rollOrigin)
    }
}

/** Fit, never stretch/crop/mirror. Both photo and overlay use this one viewport calculation. */
class FrontOverlayTransform private constructor(val snapshot: FrontLandmarkSnapshot, val width: Double, val height: Double, val left: Double, val top: Double) {
    fun project(analysisPoint: Point2): Point2 = snapshot.imagePoint(analysisPoint).let { Point2(left + it.x * width, top + it.y * height) }
    companion object {
        fun fit(snapshot: FrontLandmarkSnapshot, viewportWidth: Double, viewportHeight: Double): FrontOverlayTransform {
            require(viewportWidth.isFinite() && viewportHeight.isFinite() && viewportWidth > 0 && viewportHeight > 0)
            val scale = min(viewportWidth / snapshot.resolution.width, viewportHeight / snapshot.resolution.height)
            val width = snapshot.resolution.width * scale; val height = snapshot.resolution.height * scale
            return FrontOverlayTransform(snapshot, width, height, (viewportWidth - width) / 2, (viewportHeight - height) / 2)
        }
    }
}
