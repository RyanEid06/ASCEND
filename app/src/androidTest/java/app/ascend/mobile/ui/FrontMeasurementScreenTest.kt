package app.ascend.mobile.ui

import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.MainActivity
import app.ascend.mobile.ui.debug.MeasurementDebugEntry
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Debug-only synthetic screen smoke; uses Android's accessibility API without another UI dependency. */
class FrontMeasurementScreenTest {
    @Test fun syntheticInspectorShowsOverlaysAndCleanConfidenceFailures() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync { activity.setContent { MeasurementDebugEntry(onBack = {}) } }
            instrumentation.waitForIdleSync()
            fun node(text: String, current: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (current == null) return null
                if (current.text?.toString()?.contains(text) == true) return current
                repeat(current.childCount) { index -> node(text, current.getChild(index))?.let { return it } }
                return null
            }
            fun awaitText(text: String): AccessibilityNodeInfo {
                repeat(50) {
                    node(text, instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }
                    Thread.sleep(100)
                }
                error("Expected synthetic inspector text: $text")
            }
            fun click(text: String) {
                var current = awaitText(text)
                while (!current.isClickable) current = requireNotNull(current.parent)
                assertTrue(current.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            }
            awaitText("Front measurement inspector")
            awaitText("Correction revision: 0")
            // Capture only the explicitly synthetic diagram, never a consented input.
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(context.filesDir, "wp08-synthetic-debug.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            click("Remove confidence")
            awaitText("Unavailable: LOW_CONFIDENCE")
            click("Restore confidence")
            click("Apply bounded synthetic correction")
            awaitText("Correction revision: 1")
            awaitText("Mode: ASSISTED")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
