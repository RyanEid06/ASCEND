package app.ascend.mobile.vision

import android.graphics.Bitmap
import android.graphics.ColorSpace
import app.ascend.mobile.core.front.FixtureOrigin
import app.ascend.mobile.core.visual.VisualImage

/** Call only with the current bounded, oriented, unmirrored final-crop bitmap from local storage.
 * Ownership remains with the caller. The returned pixel snapshot must be closed after extraction.
 * This does not decode files, persist plaintext, infer anatomy or admit real-photo scoring.
 */
internal object VisualBitmapAdapter {
    fun snapshot(bitmap: Bitmap, imageRevision: String, origin: FixtureOrigin): VisualImage {
        require(!bitmap.isRecycled && bitmap.config == Bitmap.Config.ARGB_8888)
        require(bitmap.colorSpace == ColorSpace.get(ColorSpace.Named.SRGB))
        require(bitmap.width in 2..4096 && bitmap.height in 2..4096 &&
            bitmap.width.toLong() * bitmap.height <= VisualImage.MAX_PIXELS)
        val pixels = IntArray(bitmap.width * bitmap.height)
        try {
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return VisualImage.fromArgb(bitmap.width, bitmap.height, imageRevision, origin, pixels)
        } finally { pixels.fill(0) }
    }
}
