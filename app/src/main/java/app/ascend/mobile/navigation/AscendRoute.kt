package app.ascend.mobile.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object FoundationHomeRoute : NavKey

@Serializable
data object FoundationInfoRoute : NavKey

@Serializable
data object DebugMenuRoute : NavKey

@Serializable
data object CaptureFlowRoute : NavKey
