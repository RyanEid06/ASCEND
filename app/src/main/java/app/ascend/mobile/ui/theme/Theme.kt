package app.ascend.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AscendLightColorScheme = lightColorScheme(
    primary = AscendBlue,
    onPrimary = AscendSurface,
    primaryContainer = AscendSurfaceVariant,
    onPrimaryContainer = AscendBlueDark,
    secondary = AscendTeal,
    onSecondary = AscendSurface,
    background = AscendBackground,
    onBackground = AscendNavy,
    surface = AscendSurface,
    onSurface = AscendNavy,
    surfaceVariant = AscendSurfaceVariant,
    onSurfaceVariant = AscendSlate,
    outline = AscendOutline,
)

@Composable
fun AscendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AscendLightColorScheme,
        typography = Typography(),
        content = content,
    )
}
