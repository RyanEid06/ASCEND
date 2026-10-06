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
import app.ascend.mobile.ui.privacy.SensitivePhotoWindow
import android.view.WindowManager
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Synthetic-only visual/interaction evidence. No private face photo or screenshot. */
class ProfileAssistScreenTest {
    @Test fun overlappingSensitiveRoutesKeepProtectionUntilTheLastExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try { instrumentation.runOnMainSync {
            val window = activity.window
            fun secure() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val front = SensitivePhotoWindow.acquire(window)
            val profile = SensitivePhotoWindow.acquire(window)
            front(); assertTrue(secure())
            val returningFront = SensitivePhotoWindow.acquire(window)
            profile(); assertTrue(secure())
            returningFront(); assertFalse(secure())
            returningFront(); assertFalse(secure())
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            SensitivePhotoWindow.acquire(window)(); assertTrue(secure())
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun unavailableEditableProfileOffersReplacementAndCompletedHistoryDoesNot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val state = mutableStateOf<ProfileAssistState>(ProfileAssistState.Failed("existing-scan", "Unavailable", true))
        var replaced = false
        try {
            instrumentation.runOnMainSync { activity.setContent { AscendTheme {
                ProfileAssistScreen(state.value, {}, { _, _ -> }, {}, {}, {}, {}, { replaced = true }, {})
            } } }
            instrumentation.waitForIdleSync()
            fun node(current: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (current == null) return null
                if (current.text?.toString() == "Choose another profile photo") return current
                repeat(current.childCount) { node(current.getChild(it))?.let { value -> return value } }
                return null
            }
            var replacement: AccessibilityNodeInfo? = null
            repeat(30) { if (replacement == null) { replacement = node(instrumentation.uiAutomation.rootInActiveWindow); Thread.sleep(100) } }
            var clickable = requireNotNull(replacement)
            while (!clickable.isClickable) clickable = requireNotNull(clickable.parent)
            assertTrue(clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync(); assertTrue(replaced)
            instrumentation.runOnMainSync { state.value = ProfileAssistState.Failed("completed-scan", "Read only", false) }
            instrumentation.waitForIdleSync()
            assertNull(node(instrumentation.uiAutomation.rootInActiveWindow))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun explicitOrientationGuidesAndAccessibleConfirmationRenderWithoutScoreEditing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val image = Bitmap.createBitmap(180, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val state = mutableStateOf<ProfileAssistState>(ProfileAssistState.Ready("synthetic", "synthetic-image", image, null))
        try {
            instrumentation.runOnMainSync { activity.setContent { AscendTheme {
                ProfileAssistScreen(state.value, {}, { side, facing ->
                    val source = ProfileAssistSession.preview("synthetic-image", PixelResolution(180, 240), side, facing)
                    state.value = (state.value as ProfileAssistState.Ready).copy(session = source, display = source)
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
                if (current.text?.toString()?.contains(text) == true) return current
                repeat(current.childCount) { node(text, current.getChild(it))?.let { value -> return value } }
                return null
            }
            fun scroll(current: AccessibilityNodeInfo?, direction: Int): Boolean {
                if (current == null) return false
                if (current.isScrollable && current.performAction(direction)) return true
                repeat(current.childCount) { if (scroll(current.getChild(it), direction)) return true }
                return false
            }
            fun awaitText(text: String): AccessibilityNodeInfo {
                repeat(60) {
                    node(text, instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }
                    // New content may put a caption above the old scroll offset. Search both directions.
                    if (it % 5 == 0) scroll(instrumentation.uiAutomation.rootInActiveWindow,
                        if ((it / 15) % 2 == 0) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
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
            val selected = (state.value as ProfileAssistState.Ready).session!!
            assertEquals(ProfileSide.LEFT, selected.revision.input.side)
            assertEquals(ProfileFacing.RIGHT, selected.facing)
            awaitText("Left profile · nose points right")
            awaitText("Guided profile preview")
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(context.filesDir, "wp09-synthetic-profile.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            awaitText("Confirm point")
            click("Confirm point")
            assertEquals(1L, (state.value as ProfileAssistState.Ready).session!!.revisionToken)
            assertEquals(1, (state.value as ProfileAssistState.Ready).activeIndex)
            click("Previous")
            assertEquals(0, (state.value as ProfileAssistState.Ready).activeIndex)
            awaitText("Measurements are not available for this preview.")
        } finally { instrumentation.runOnMainSync { activity.finish() }; image.recycle() }
    }
}
