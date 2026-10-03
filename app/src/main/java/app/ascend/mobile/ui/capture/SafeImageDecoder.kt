package app.ascend.mobile.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

sealed interface PreviewDecodeResult {
    data class Success(
        val image: ImageBitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
    ) : PreviewDecodeResult

    data class Failure(val message: String) : PreviewDecodeResult
}

object SafeImageDecoder {
    private const val MAX_SOURCE_EDGE = 24_000
    private const val MAX_SOURCE_PIXELS = 120_000_000L
    private const val MAX_PREVIEW_EDGE = 2_048

    suspend fun decodeForPreview(
        context: Context,
        uriString: String,
    ): PreviewDecodeResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return@withContext PreviewDecodeResult.Failure("This image could not be opened.")

        val resolver = context.contentResolver
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        if (mimeType != null && !mimeType.startsWith("image/")) {
            return@withContext PreviewDecodeResult.Failure("Choose a supported image file.")
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        val boundsDecoded = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        }.isSuccess

        if (!boundsDecoded || bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@withContext PreviewDecodeResult.Failure("This image is unreadable or unsupported.")
        }

        val pixelCount = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (
            bounds.outWidth > MAX_SOURCE_EDGE ||
            bounds.outHeight > MAX_SOURCE_EDGE ||
            pixelCount > MAX_SOURCE_PIXELS
        ) {
            return@withContext PreviewDecodeResult.Failure("This image is too large to process safely.")
        }

        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_PREVIEW_EDGE) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = try {
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return@withContext PreviewDecodeResult.Failure("This image could not be decoded safely.")

        val orientation = try {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val oriented = applyOrientation(decoded, orientation)
        PreviewDecodeResult.Success(
            image = oriented.asImageBitmap(),
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
        )
    }

    private fun applyOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL) return bitmap

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true,
            )
        }.getOrElse { bitmap }
    }
}
