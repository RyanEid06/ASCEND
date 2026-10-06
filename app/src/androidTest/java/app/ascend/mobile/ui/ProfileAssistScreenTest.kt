package app.ascend.mobile.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.MainActivity
import app.ascend.mobile.core.geometry.PixelResolution
import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.core.profile.*
import app.ascend.mobile.ui.profile.*
import app.ascend.mobile.ui.theme.AscendTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Synthetic-only visual/interaction evidence. No private face photo or screenshot. */
class ProfileAssistScreenTest {
    @Test fun explicitOrientationGuidesAndAccessibleConfirmationRenderWithoutScoreEditing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val image = Bitmap.createBitmap(180, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val state = mutableStateOf<ProfileAssistState>(ProfileAssistState.Ready("synthetic", "synthetic-image", image, null))
        try {
            instrumentation.runOnMainSync { activity.setContent { AscendTheme {
                ProfileAssistScreen(state.value, {}, { side, facing ->
                    state.value = (state.value as ProfileAssistState.Ready).copy(session = ProfileAssistSession.preview(
                        "synthetic-image", PixelResolution(180, 240), side, facing), display = ProfileAssistSession.preview(
                        "synthetic-image", PixelResolution(180, 240), side, facing))
                }, { index -> state.value = (state.value as ProfileAssistState.Ready).copy(activeIndex = index) }, {}, {
                    val current = state.value as ProfileAssistState.Ready
                    val source = current.session!!
                    val point = current.activePoint!!
                    val next = source.confirm(point, source.policy.zones.getValue(point).missingPointAnchor, source.revisionToken)
                    state.value = current.copy(session = next, display = next, activeIndex = (current.activeIndex + 1).coerceAtMost(11))
                }, {}, {}, {})
            } } }
            instrumentation.waitForIdleSync()
            fun node(text: String, current: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (current == null) return null
                if (current.text?.toString() == text) return current
                repeat(current.childCount) { node(text, current.getChild(it))?.let { value -> return value } }
                return null
            }
            fun scroll(current: AccessibilityNodeInfo?): Boolean {
                if (current == null) return false
                if (current.isScrollable && current.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                repeat(current.childCount) { if (scroll(current.getChild(it))) return true }
                return false
            }
            fun awaitText(text: String): AccessibilityNodeInfo {
                repeat(60) {
                    node(text, instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }
                    if (it % 10 == 0) scroll(instrumentation.uiAutomation.rootInActiveWindow)
                    Thread.sleep(100)
                }
                error("Expected profile text: $text")
            }
            fun click(text: String) {
                var current = awaitText(text)
                while (!current.isClickable) current = requireNotNull(current.parent)
                assertTrue(current.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync()
            }
            click("Left side"); click("Nose points right"); click("Confirm side and direction")
            awaitText("Left profile · nose points right")
            awaitText("Confirm point")
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(context.filesDir, "wp09-synthetic-profile.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            click("Confirm point")
            assertEquals(1L, (state.value as ProfileAssistState.Ready).session!!.revisionToken)
            assertEquals(1, (state.value as ProfileAssistState.Ready).activeIndex)
            click("Previous")
            assertEquals(0, (state.value as ProfileAssistState.Ready).activeIndex)
            awaitText("Measurements are not available for this preview.")
        } finally { instrumentation.runOnMainSync { activity.finish() }; image.recycle() }
    }
}
