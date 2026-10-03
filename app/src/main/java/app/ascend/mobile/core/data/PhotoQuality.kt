package app.ascend.mobile.core.data

import app.ascend.mobile.core.model.CaptureView
import app.ascend.mobile.core.model.RetakeReason
import kotlin.math.abs

/** Supplied by a reviewed CV policy; no production thresholds are fabricated here. */
data class PhotoQualityPolicy(
    val version: String,
    val minWidth: Int,
    val minHeight: Int,
    val minMeanLuma: Double,
    val maxMeanLuma: Double,
    val minLaplacianVariance: Double,
    val maxCenterOffset: Double,
    val maxAbsPitch: Double,
    val maxAbsRoll: Double,
    val frontMaxAbsYaw: Double,
    val profileMinAbsYaw: Double,
    val profileMaxAbsYaw: Double,
) {
    init {
        require(version.isNotBlank() && minWidth > 0 && minHeight > 0)
        require(minMeanLuma.isFinite() && maxMeanLuma.isFinite() && minMeanLuma in 0.0..maxMeanLuma && maxMeanLuma <= 1.0)
        require(minLaplacianVariance.isFinite() && minLaplacianVariance >= 0)
        require(maxCenterOffset in 0.0..0.5)
        require(maxAbsPitch in 0.0..90.0 && maxAbsRoll in 0.0..180.0)
        require(frontMaxAbsYaw in 0.0..90.0 && profileMinAbsYaw in 0.0..profileMaxAbsYaw && profileMaxAbsYaw <= 180.0)
    }
}

data class QualityStatistics(val meanLuma: Double, val laplacianVariance: Double)

/** Input grid is fixed by image policy, making blur thresholds reproducible across resolutions. */
object PhotoQuality {
    fun statistics(luma: DoubleArray, width: Int, height: Int): QualityStatistics {
        require(width >= 3 && height >= 3 && width.toLong() * height == luma.size.toLong())
        require(luma.all { it.isFinite() && it in 0.0..1.0 })
        var sum = 0.0
        var sumSquares = 0.0
        val count = (width - 2).toLong() * (height - 2)
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val laplacian = luma[i - width] + luma[i + width] + luma[i - 1] + luma[i + 1] - 4 * luma[i]
            sum += laplacian
            sumSquares += laplacian * laplacian
        }
        return QualityStatistics(luma.average(), (sumSquares / count - (sum / count) * (sum / count)).coerceAtLeast(0.0))
    }

    fun evaluate(width: Int, height: Int, stats: QualityStatistics, policy: PhotoQualityPolicy): Set<RetakeReason> = buildSet {
        require(stats.meanLuma.isFinite() && stats.meanLuma in 0.0..1.0)
        require(stats.laplacianVariance.isFinite() && stats.laplacianVariance >= 0)
        if (width < policy.minWidth || height < policy.minHeight) add(RetakeReason.INSUFFICIENT_RESOLUTION)
        if (stats.meanLuma < policy.minMeanLuma) add(RetakeReason.TOO_DARK)
        if (stats.meanLuma > policy.maxMeanLuma) add(RetakeReason.TOO_BRIGHT)
        if (stats.laplacianVariance < policy.minLaplacianVariance) add(RetakeReason.TOO_BLURRY)
    }
}

/** Coordinates refer to the standardized image, never the uncropped source. Angles are degrees. */
data class FaceObservation(
    val count: Int,
    val centerX: Double? = null,
    val centerY: Double? = null,
    val yaw: Double? = null,
    val pitch: Double? = null,
    val roll: Double? = null,
) {
    init {
        require(count >= 0)
        require(centerX == null || centerX in 0.0..1.0)
        require(centerY == null || centerY in 0.0..1.0)
        require(listOf(yaw, pitch, roll).all { it == null || (it.isFinite() && it in -180.0..180.0) })
    }
}

data class FaceValidation(val pending: Boolean, val reasons: Set<RetakeReason>)

object FaceQuality {
    fun evaluate(view: CaptureView, observation: FaceObservation?, policy: PhotoQualityPolicy): FaceValidation {
        if (observation == null) return FaceValidation(true, emptySet())
        if (observation.count != 1) return FaceValidation(false, setOf(
            if (observation.count == 0) RetakeReason.NO_FACE else RetakeReason.MULTIPLE_FACES,
        ))
        val reasons = buildSet {
            val x = observation.centerX
            val y = observation.centerY
            if (x != null && y != null && (abs(x - 0.5) > policy.maxCenterOffset || abs(y - 0.5) > policy.maxCenterOffset)) add(RetakeReason.OFF_CENTER)
            val yaw = observation.yaw
            val pitch = observation.pitch
            val roll = observation.roll
            if (pitch != null && abs(pitch) > policy.maxAbsPitch || roll != null && abs(roll) > policy.maxAbsRoll) add(RetakeReason.INVALID_POSE)
            if (yaw != null && when (view) {
                CaptureView.FRONT -> abs(yaw) > policy.frontMaxAbsYaw
                CaptureView.PROFILE -> abs(yaw) !in policy.profileMinAbsYaw..policy.profileMaxAbsYaw
            }) add(RetakeReason.INVALID_POSE)
        }
        return FaceValidation(
            listOf(observation.centerX, observation.centerY, observation.yaw, observation.pitch, observation.roll).any { it == null },
            reasons,
        )
    }
}
