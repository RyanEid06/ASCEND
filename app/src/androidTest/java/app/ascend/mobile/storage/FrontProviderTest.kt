package app.ascend.mobile.storage

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.core.vision.*
import app.ascend.mobile.vision.MediaPipeFrontLandmarker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic blank image tests actual bundled model loading and caller bitmap ownership. */
class FrontProviderTest {
    @Test fun blankImageFailsWithoutRecyclingCallerBitmap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.GRAY)
            val result = MediaPipeFrontLandmarker(context).extract(bitmap, "0".repeat(64))
            assertEquals(FrontExtraction.Failed(FrontFailure.NO_FACE), result)
            assertFalse("Provider must preserve the display bitmap", bitmap.isRecycled)
            assertEquals(Color.GRAY, bitmap.getPixel(150, 200))
        } finally { if (!bitmap.isRecycled) bitmap.recycle() }
    }
}
