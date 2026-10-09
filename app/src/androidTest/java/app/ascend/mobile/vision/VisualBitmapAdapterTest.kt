package app.ascend.mobile.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ascend.mobile.core.front.FixtureOrigin
import app.ascend.mobile.core.visual.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualBitmapAdapterTest {
    @Test fun snapshotsSrgbPixelsWithoutTakingCallerBitmapOwnership() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(100, 100, 100))
            VisualBitmapAdapter.snapshot(bitmap, "current-crop", FixtureOrigin.SYNTHETIC).use { image ->
                bitmap.eraseColor(Color.BLACK)
                assertEquals(100.0 / 255, image.luminance(0, 0), 1e-12)
                assertEquals(32, image.width); assertEquals("current-crop", image.imageRevision)
            }
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle() }
    }

    @Test fun bitmapOriginCannotBypassRealPhotoAdmission() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.GRAY)
            VisualBitmapAdapter.snapshot(bitmap, "local-real", FixtureOrigin.CONSENTED_LOCAL).use { image ->
                val report = VisualFeatureExtractor(VisualPolicy("synthetic", "synthetic only", 8, 8, 0.8, 0.01))
                    .extract(image, VisualCaptureQuality(VISUAL_QUALITY_METHOD, VisualQuality.USABLE, VisualQuality.USABLE), emptyMap())
                assertTrue(report.observations.all { it.failure == VisualFailure.UNVALIDATED_CAPTURE && it.value == null })
            }
        } finally { bitmap.recycle() }
    }

    @Test fun rejectsTransparentWrongFormatAndNonSrgbBitmaps() {
        val bitmaps = listOf(
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
            Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565),
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, false, ColorSpace.get(ColorSpace.Named.DISPLAY_P3)),
        )
        try {
            bitmaps[0].eraseColor(Color.TRANSPARENT)
            for (bitmap in bitmaps) {
                try { VisualBitmapAdapter.snapshot(bitmap, "bad", FixtureOrigin.SYNTHETIC).close(); fail("Invalid bitmap accepted") }
                catch (_: IllegalArgumentException) { }
            }
        } finally { bitmaps.forEach(Bitmap::recycle) }
    }
}
