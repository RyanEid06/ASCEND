package app.ascend.mobile.foundation.flags

enum class FeatureFlag(
    val displayName: String,
    val description: String,
    val enabledByDefault: Boolean,
) {
    AdaptiveLayoutDiagnostics(
        displayName = "Adaptive layout diagnostics",
        description = "Shows extra window-class diagnostics while the foundation is being exercised.",
        enabledByDefault = false,
    ),
    FoundationStatusDetails(
        displayName = "Foundation status details",
        description = "Enables additional local-only foundation status copy.",
        enabledByDefault = false,
    ),
}
