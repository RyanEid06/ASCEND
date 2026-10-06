package app.ascend.mobile.ui.privacy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/** Main-thread leases keep overlapping navigation entries protected until the last exits. */
internal object SensitivePhotoWindow {
    private data class Owners(var count: Int, val initiallySecure: Boolean)
    private val owners = WeakHashMap<Window, Owners>()

    fun acquire(window: Window): () -> Unit {
        val owner = owners.getOrPut(window) {
            Owners(0, window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        owner.count++
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        var released = false
        return {
            if (!released) {
                released = true
                owner.count--
                if (owner.count == 0) {
                    owners.remove(window)
                    if (!owner.initiallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}

@Composable
internal fun ProtectSensitivePhotoWindow() {
    val window = LocalContext.current.activity()?.window
    DisposableEffect(window) {
        val release = window?.let(SensitivePhotoWindow::acquire)
        onDispose { release?.invoke() }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
