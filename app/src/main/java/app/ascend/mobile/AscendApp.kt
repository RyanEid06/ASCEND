package app.ascend.mobile

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.ascend.mobile.navigation.AscendNavHost
import app.ascend.mobile.ui.adaptive.AscendWindowWidthClass
import app.ascend.mobile.ui.theme.AscendTheme

@Composable
fun AscendApp() {
    AscendTheme {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val windowWidthClass = AscendWindowWidthClass.fromWidthDp(maxWidth.value)
            AscendNavHost(windowWidthClass = windowWidthClass)
        }
    }
}
