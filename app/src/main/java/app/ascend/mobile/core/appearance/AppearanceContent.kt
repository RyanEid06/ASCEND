package app.ascend.mobile.core.appearance

import app.ascend.mobile.core.model.Tier

/** Fixed product copy, independent of numerical scoring and extractor internals. */
internal object AppearanceContent {
    const val categoryLabel = "Appearance Details"

    fun title(kind: AppearanceFeatureKind): String = when (kind) {
        AppearanceFeatureKind.HAIRLINE_PATTERN -> "Hairline appearance"
        AppearanceFeatureKind.HAIR_DENSITY_PATTERN -> "Hair density appearance"
        AppearanceFeatureKind.BROW_DENSITY_PATTERN -> "Brow density appearance"
        AppearanceFeatureKind.BROW_SHAPE -> "Brow shape"
        AppearanceFeatureKind.BEARD_COVERAGE -> "Beard coverage"
        AppearanceFeatureKind.UNDER_EYE_PATTERN -> "Under-eye appearance"
        AppearanceFeatureKind.LIP_APPEARANCE -> "Lip appearance"
        AppearanceFeatureKind.FACIAL_LEANNESS_PROXY -> "Facial contour appearance"
        AppearanceFeatureKind.SKIN_TEXTURE_PATTERN -> "Skin texture appearance"
    }

    fun explanation(kind: AppearanceFeatureKind): String = when (kind) {
        AppearanceFeatureKind.HAIRLINE_PATTERN -> "Describes the visible hairline pattern in this photo."
        AppearanceFeatureKind.HAIR_DENSITY_PATTERN -> "Describes visible hair coverage under the capture conditions."
        AppearanceFeatureKind.BROW_DENSITY_PATTERN -> "Describes the visible brow coverage pattern."
        AppearanceFeatureKind.BROW_SHAPE -> "Describes the visible brow outline."
        AppearanceFeatureKind.BEARD_COVERAGE -> "Visible coverage varies with grooming and capture conditions."
        AppearanceFeatureKind.UNDER_EYE_PATTERN -> "Describes the visible under-eye pattern under the capture conditions."
        AppearanceFeatureKind.LIP_APPEARANCE -> "Describes the visible lip pattern in this photo."
        AppearanceFeatureKind.FACIAL_LEANNESS_PROXY -> "Describes the visible facial contour in this photo."
        AppearanceFeatureKind.SKIN_TEXTURE_PATTERN -> "Describes visible texture patterns. Lighting and image processing can change the result."
    }

    fun card(binding: AppearanceBinding, tier: Tier?, failure: AppearanceFailure?): AppearanceCard {
        val status = when {
            failure == AppearanceFailure.WAITING_FOR_OTHER_MEASUREMENTS -> "Waiting for reliable measurements"
            failure != null -> "Cannot assess reliably"
            else -> requireNotNull(tier).name
        }
        val action = when (failure) {
            AppearanceFailure.LIGHTING_UNRELIABLE -> "Try a photo with even lighting."
            AppearanceFailure.FOCUS_UNRELIABLE, AppearanceFailure.ROI_UNRELIABLE,
            AppearanceFailure.INSUFFICIENT_ROI_RESOLUTION -> "Try a clear photo with the feature visible."
            AppearanceFailure.STALE_SOURCE -> "Assess the current photo again."
            AppearanceFailure.LOW_CONFIDENCE, AppearanceFailure.MISSING_FEATURE,
            AppearanceFailure.VALUE_UNAVAILABLE -> "Try another clear photo."
            else -> null // Configuration/review failures cannot be repaired by repeatedly retaking a photo.
        }
        return AppearanceCard(binding.metricId, title(binding.kind), if (failure == null) tier else null,
            status, explanation(binding.kind), action)
    }
}
