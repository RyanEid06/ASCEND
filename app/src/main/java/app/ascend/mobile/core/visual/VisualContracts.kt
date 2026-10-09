package app.ascend.mobile.core.visual

import app.ascend.mobile.core.front.FixtureOrigin
import app.ascend.mobile.core.geometry.Point2

internal const val VISUAL_CONTRACT_VERSION = "wp11-visual-synthetic-v1"
internal const val VISUAL_EXTRACTOR_VERSION = "wp11-spatial-v1"
internal const val VISUAL_CONFIDENCE_METHOD = "wp11-synthetic-input-minimum-v1"
internal const val VISUAL_QUALITY_METHOD = "wp11-explicit-capture-quality-v1"

internal enum class VisualFeature {
    HAIRLINE_PATTERN, HAIR_DENSITY_PATTERN, BROW_DENSITY_PATTERN, BROW_SHAPE,
    BEARD_COVERAGE, UNDER_EYE_PATTERN, LIP_APPEARANCE, FACIAL_LEANNESS_PROXY,
    SKIN_TEXTURE_PATTERN, SKIN_EVENNESS_PATTERN,
}
internal enum class VisualQuality { USABLE, UNSUITABLE, UNKNOWN }
internal enum class VisualVisibility { VISIBLE, OCCLUDED, UNKNOWN }
internal enum class VisualFailure {
    POLICY_REQUIRED, UNVALIDATED_CAPTURE, QUALITY_METHOD_MISMATCH, LIGHTING_UNRELIABLE,
    FOCUS_UNRELIABLE, ROI_MISSING, STALE_SOURCE, ROI_UNRELIABLE, ROI_OUTSIDE_IMAGE,
    INSUFFICIENT_RESOLUTION, MASK_REQUIRED, MASK_MISMATCH, LOW_CONFIDENCE,
    EMPTY_FEATURE, UNSUPPORTED_DEFINITION, INSUFFICIENT_CONTRAST,
}

/** Capture evidence is explicit; there are no production quality/confidence defaults. */
internal data class VisualCaptureQuality(
    val methodVersion: String,
    val lighting: VisualQuality,
    val focus: VisualQuality,
) { init { require(methodVersion.isNotBlank()) } }

/** A rectangle in final-crop, oriented, unmirrored image coordinates. Axes are pixel-isotropic. */
internal data class VisualRoiProposal(
    val feature: VisualFeature,
    val imageRevision: String,
    val imageSha256: String,
    val definitionVersion: String,
    val providerVersion: String,
    val origin: Point2,
    val across: Point2,
    val down: Point2,
    val visibility: VisualVisibility,
    val confidence: Double?,
) {
    init {
        require(listOf(imageRevision, definitionVersion, providerVersion).all(String::isNotBlank))
        require(imageSha256.matches(Regex("[a-f0-9]{64}")))
        require(listOf(origin.x, origin.y, across.x, across.y, down.x, down.y).all(Double::isFinite))
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
    }
    fun imagePoint(u: Double, v: Double) = Point2(origin.x + u * across.x + v * down.x,
        origin.y + u * across.y + v * down.y)
}

/** Hard allocation ceilings are resource protection, not validated measurement thresholds. */
internal data class VisualPolicy(
    val version: String,
    val evidence: String,
    val gridEdge: Int,
    val minimumSourceShortEdgePixels: Int,
    val minimumConfidence: Double,
    val minimumRelativeContrast: Double,
) {
    init {
        require(version.isNotBlank() && evidence.isNotBlank())
        require(gridEdge in 8..256 && minimumSourceShortEdgePixels >= gridEdge)
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0)
        require(minimumRelativeContrast.isFinite() && minimumRelativeContrast > 0.0)
    }
}

/** Only numbers and versioned provenance leave extraction; no images or masks are in reports. */
internal data class VisualObservation(
    val feature: VisualFeature,
    val imageRevision: String,
    val roiRevision: String?,
    val extractorId: String,
    val value: Double?,
    val confidence: Double?,
    val roiShortEdgePixels: Int?,
    val failure: VisualFailure?,
    val definitionVersion: String?,
    val roiProviderVersion: String?,
    val maskProviderVersion: String?,
    val roiProposal: VisualRoiProposal?,
    val maskSha256: String?,
    val reliability: String,
    val extractorVersion: String = VISUAL_EXTRACTOR_VERSION,
    val confidenceMethodVersion: String = VISUAL_CONFIDENCE_METHOD,
) {
    init {
        require((failure == null) == (value != null))
        require(value == null || value.isFinite())
        require((failure == null) == (confidence != null))
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
    }
}

internal data class VisualReport(
    val origin: FixtureOrigin,
    val imageRevision: String,
    val imageSha256: String,
    val policyVersion: String?,
    val quality: VisualCaptureQuality,
    val observations: List<VisualObservation>,
    val policySnapshot: VisualPolicy?,
    val contractVersion: String = VISUAL_CONTRACT_VERSION,
) {
    fun matchesCurrentSource(image: VisualImage): Boolean {
        image.requireOpen()
        return image.imageRevision == imageRevision && image.sha256 == imageSha256 && image.origin == origin
    }
}

/** No automatic anatomy/segmentation provider is admitted yet. Missing proposals remain absent. */
internal fun interface VisualRoiProvider {
    fun propose(image: VisualImage): Map<VisualFeature, VisualRoiProposal>
}
