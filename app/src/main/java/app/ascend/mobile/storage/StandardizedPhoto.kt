package app.ascend.mobile.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import app.ascend.mobile.core.data.CaptureCrop
import app.ascend.mobile.core.data.PhotoQuality
import app.ascend.mobile.core.data.QualityStatistics
import app.ascend.mobile.core.model.CaptureView
import app.ascend.mobile.core.data.FaceObservation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Resource bounds and output/blur-grid dimensions are versioned separately from CV thresholds. */
data class ImageStoragePolicy(
    val version: String,
    val maxEncodedBytes: Int,
    val maxSourceDimension: Int,
    val maxSourcePixels: Long,
    val maxDecodedPixels: Long,
    val outputLongEdge: Int,
    val qualityGridLongEdge: Int,
) {
    init {
        require(version.isNotBlank())
        require(maxEncodedBytes in 1..32_000_000 && maxSourceDimension in 3..32768)
        require(maxSourcePixels in 9..268_435_456 && maxDecodedPixels in 9..16_777_216)
        require(outputLongEdge in 3..4096 && qualityGridLongEdge in 3..512)
        require(outputLongEdge.toLong() * outputLongEdge <= maxDecodedPixels)
    }
}

/** WP07/WP09 provide implementations; null means unavailable, never a fabricated valid face. */
fun interface FaceValidationHook {
    suspend fun inspect(view: CaptureView, standardizedBitmap: Bitmap): FaceObservation?
}

internal class InvalidPhoto(val reason: app.ascend.mobile.core.model.RetakeReason) : IllegalArgumentException()

internal data class StandardizedPhoto(val bitmap: Bitmap, val png: ByteArray, val statistics: QualityStatistics)

internal class PhotoNormalizer(private val policy: ImageStoragePolicy) {
    fun normalize(encoded: ByteArray, crop: CaptureCrop): StandardizedPhoto {
        require(encoded.size in 1..policy.maxEncodedBytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp") ||
            bounds.outWidth !in 3..policy.maxSourceDimension || bounds.outHeight !in 3..policy.maxSourceDimension ||
            bounds.outWidth.toLong() * bounds.outHeight > policy.maxSourcePixels
        ) throw InvalidPhoto(app.ascend.mobile.core.model.RetakeReason.INVALID_IMAGE)
        var sample = 1
        while (ceil(bounds.outWidth.toDouble() / sample) * ceil(bounds.outHeight.toDouble() / sample) > policy.maxDecodedPixels) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
            ?: throw InvalidPhoto(app.ascend.mobile.core.model.RetakeReason.INVALID_IMAGE)
        try {
            val orientation = ExifInterface(ByteArrayInputStream(encoded)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try { return crop(oriented, crop) } finally { if (oriented !== decoded) oriented.recycle() }
        } finally { decoded.recycle() }
    }

    private fun crop(source: Bitmap, crop: CaptureCrop): StandardizedPhoto {
        // ContentScale.Crop, zoom about viewport centre, then translation in viewport fractions.
        val aspect = crop.viewportAspectRatio.toDouble()
        val baseWidth = min(source.width.toDouble(), source.height * aspect)
        val baseHeight = baseWidth / aspect
        val width = baseWidth / crop.scale
        val height = baseHeight / crop.scale
        val left = (source.width - width) / 2 - crop.offsetXFraction * width
        val top = (source.height - height) / 2 - crop.offsetYFraction * height
        if (left < -0.01 || top < -0.01 || left + width > source.width + 0.01 || top + height > source.height + 0.01)
            throw InvalidPhoto(app.ascend.mobile.core.model.RetakeReason.INVALID_CROP)
        val x = left.toInt().coerceAtLeast(0)
        val y = top.toInt().coerceAtLeast(0)
        val w = width.toInt().coerceIn(1, source.width - x)
        val h = height.toInt().coerceIn(1, source.height - y)
        if (w < 3 || h < 3) throw InvalidPhoto(app.ascend.mobile.core.model.RetakeReason.INSUFFICIENT_RESOLUTION)
        val section = Bitmap.createBitmap(source, x, y, w, h)
        try {
            val factor = min(1.0, policy.outputLongEdge.toDouble() / max(w, h))
            val scaled = Bitmap.createScaledBitmap(section, max(3, (w * factor).toInt()), max(3, (h * factor).toInt()), true)
            val output = scaled.copy(Bitmap.Config.ARGB_8888, false)
            if (scaled !== section) scaled.recycle()
            try {
                val pixels = IntArray(output.width * output.height)
                output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
                if (pixels.any { Color.alpha(it) != 255 }) throw InvalidPhoto(app.ascend.mobile.core.model.RetakeReason.INVALID_IMAGE)
                val gridFactor = min(1.0, policy.qualityGridLongEdge.toDouble() / max(output.width, output.height))
                val grid = Bitmap.createScaledBitmap(output, max(3, (output.width * gridFactor).toInt()), max(3, (output.height * gridFactor).toInt()), true)
                val stats = try {
                    val colors = IntArray(grid.width * grid.height)
                    grid.getPixels(colors, 0, grid.width, 0, 0, grid.width, grid.height)
                    PhotoQuality.statistics(DoubleArray(colors.size) { i ->
                        val c = colors[i]
                        (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255
                    }, grid.width, grid.height)
                } finally { if (grid !== output) grid.recycle() }
                val bytes = ByteArrayOutputStream()
                check(output.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                require(bytes.size() <= policy.maxEncodedBytes)
                return StandardizedPhoto(output, bytes.toByteArray(), stats)
            } catch (failure: Throwable) { output.recycle(); throw failure }
        } finally { if (section !== source) section.recycle() }
    }
}
