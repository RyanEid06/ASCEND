package app.ascend.mobile.core.appearance

import app.ascend.mobile.core.model.*

internal const val APPEARANCE_CONTRACT_PROPOSAL = "wp12-appearance-synthetic-v1"
internal enum class AppearanceOrigin { SYNTHETIC, CONSENTED_LOCAL }
internal enum class AppearanceQuality { USABLE, UNSUITABLE, UNKNOWN }
internal enum class AppearanceFeatureKind {
    HAIRLINE_PATTERN, HAIR_DENSITY_PATTERN, BROW_DENSITY_PATTERN, BROW_SHAPE,
    BEARD_COVERAGE, UNDER_EYE_PATTERN, LIP_APPEARANCE, FACIAL_LEANNESS_PROXY, SKIN_TEXTURE_PATTERN,
}
internal enum class AppearanceScoreBasis { SPATIAL_APPEARANCE_PATTERN, SKIN_COLOR, ETHNICITY, HEALTH_INFERENCE }

/** No pixel data, skin-tone field, ethnicity, diagnosis or inferred hormone state crosses this seam. */
internal data class AppearanceObservation(
    val kind: AppearanceFeatureKind,
    val imageRevision: String,
    val roiRevision: String,
    val extractorId: String,
    val extractorVersion: String,
    val value: Double?,
    val confidence: Double?,
    val roiShortEdgePixels: Int?,
    val roiQuality: AppearanceQuality,
    val reliability: String,
    val confidenceMethodVersion: String,
) {
    init {
        require(listOf(imageRevision, roiRevision, extractorId, extractorVersion, reliability, confidenceMethodVersion).all(String::isNotBlank))
        require(value == null || value.isFinite())
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
        require(roiShortEdgePixels == null || roiShortEdgePixels > 0)
    }
}

internal data class AppearanceInput(
    val origin: AppearanceOrigin,
    val imageRevision: String,
    val qualityMethodVersion: String,
    val lighting: AppearanceQuality,
    val focus: AppearanceQuality,
    val observations: Map<String, AppearanceObservation>,
) {
    init {
        require(imageRevision.isNotBlank() && qualityMethodVersion.isNotBlank())
        require(observations.keys.all(String::isNotBlank))
    }
}

/** Proposed metric-to-extractor bindings; no production calibration or fairness evidence is supplied. */
internal data class AppearanceBinding(
    val metricId: String,
    val kind: AppearanceFeatureKind,
    val extractorId: String,
    val extractorVersion: String,
    val definitionVersion: String,
    val fairnessEvidence: String,
    val scoreBasis: AppearanceScoreBasis,
    val minimumValue: Double,
    val maximumValue: Double,
    val confidenceMethodVersion: String,
) {
    init {
        require(listOf(metricId, extractorId, extractorVersion, definitionVersion, fairnessEvidence, confidenceMethodVersion).all(String::isNotBlank))
        require(scoreBasis == AppearanceScoreBasis.SPATIAL_APPEARANCE_PATTERN) { "Color, ethnicity and health cannot be scoring bases" }
        require(minimumValue.isFinite() && maximumValue.isFinite() && minimumValue < maximumValue)
    }
}

internal data class AppearancePolicy(
    val version: String,
    val evidence: String,
    val qualityMethodVersion: String,
    val minimumConfidence: Double,
    val minimumRoiShortEdgePixels: Int,
) {
    init {
        require(listOf(version, evidence, qualityMethodVersion).all(String::isNotBlank))
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0)
        require(minimumRoiShortEdgePixels > 0)
    }
}

internal enum class AppearanceFailure {
    POLICY_REQUIRED, SYNTHETIC_ONLY, QUALITY_METHOD_MISMATCH, LIGHTING_UNRELIABLE, FOCUS_UNRELIABLE,
    MISSING_FEATURE, STALE_SOURCE, EXTRACTOR_MISMATCH, CONFIDENCE_METHOD_MISMATCH, UNVALIDATED_FEATURE, ROI_UNRELIABLE,
    INSUFFICIENT_ROI_RESOLUTION, LOW_CONFIDENCE, VALUE_UNAVAILABLE, OUTSIDE_DEFINITION_DOMAIN,
    SCORE_UNAVAILABLE, WAITING_FOR_OTHER_MEASUREMENTS,
}

/** Intentionally contains no individual /100 or /10 score. */
internal data class AppearanceCard(
    val metricId: String,
    val title: String,
    val tier: Tier?,
    val status: String,
    val explanation: String,
    val nextAction: String?,
)

internal data class AppearanceReport(
    val contractVersion: String,
    val category: Category,
    val categoryLabel: String,
    val input: AppearanceInput,
    val policyVersion: String?,
    val modelHash: String,
    val definitionVersions: Map<String, String>,
    val fairnessEvidence: Map<String, String>,
    val failures: Map<String, AppearanceFailure>,
    val cards: List<AppearanceCard>,
    val categoryResult: CategoryResult?,
    val analysis: AnalysisOutcome,
)
