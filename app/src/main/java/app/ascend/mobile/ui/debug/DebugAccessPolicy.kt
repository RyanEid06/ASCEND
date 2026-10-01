package app.ascend.mobile.ui.debug

object DebugAccessPolicy {
    fun isAvailable(isDebugBuild: Boolean): Boolean = isDebugBuild
}
