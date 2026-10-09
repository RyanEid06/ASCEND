package app.ascend.mobile.core.visual

import app.ascend.mobile.core.front.FixtureOrigin
import kotlin.math.*

/** Candidate signal IDs, not enabled scoring metrics or attractiveness definitions. */
internal object VisualFeatureCatalog {
    fun extractorId(feature: VisualFeature) = "candidate.visual.${feature.name.lowercase(java.util.Locale.ROOT)}"
    val implemented = setOf(VisualFeature.HAIRLINE_PATTERN, VisualFeature.HAIR_DENSITY_PATTERN,
        VisualFeature.BROW_DENSITY_PATTERN, VisualFeature.BROW_SHAPE, VisualFeature.BEARD_COVERAGE,
        VisualFeature.SKIN_TEXTURE_PATTERN)
}

/** Runs offline on fixed pixels/masks. Real-photo admission is deliberately unavailable. */
internal class VisualFeatureExtractor(private val policy: VisualPolicy?) {
    fun extract(
        image: VisualImage,
        quality: VisualCaptureQuality,
        proposals: Map<VisualFeature, VisualRoiProposal>,
        masks: Map<VisualFeature, VisualMask> = emptyMap(),
    ): VisualReport {
        image.requireOpen()
        require(proposals.all { (feature, proposal) -> feature == proposal.feature })
        require(masks.all { (feature, mask) -> feature == mask.feature })
        val observations = VisualFeature.entries.map { feature ->
            val proposal = proposals[feature]
            fun failed(reason: VisualFailure) = observation(image, feature, proposal, masks[feature], null, null, null, null, reason)
            when {
                policy == null -> failed(VisualFailure.POLICY_REQUIRED)
                image.origin != FixtureOrigin.SYNTHETIC -> failed(VisualFailure.UNVALIDATED_CAPTURE)
                quality.methodVersion != VISUAL_QUALITY_METHOD -> failed(VisualFailure.QUALITY_METHOD_MISMATCH)
                quality.lighting != VisualQuality.USABLE -> failed(VisualFailure.LIGHTING_UNRELIABLE)
                quality.focus != VisualQuality.USABLE -> failed(VisualFailure.FOCUS_UNRELIABLE)
                feature !in VisualFeatureCatalog.implemented -> failed(VisualFailure.UNSUPPORTED_DEFINITION)
                proposal == null -> failed(VisualFailure.ROI_MISSING)
                else -> extractFeature(image, feature, proposal, masks[feature], policy)
            }
        }
        return VisualReport(image.origin, image.imageRevision, image.sha256, policy?.version, quality, observations, policy)
    }

    private fun extractFeature(image: VisualImage, feature: VisualFeature, proposal: VisualRoiProposal,
        mask: VisualMask?, limits: VisualPolicy): VisualObservation {
        fun failed(reason: VisualFailure) = observation(image, feature, proposal, mask, null, null, null, null, reason)
        if (mask == null) return failed(VisualFailure.MASK_REQUIRED)
        return when (val result = VisualRoiStandardizer.extract(image, proposal, limits, mask)) {
            is VisualRoiResult.Failed -> failed(result.reason)
            is VisualRoiResult.Ready -> result.roi.use { roi ->
                val confidence = proposal.confidence?.let { a -> mask.confidence?.let { b -> min(a, b) } }
                if (confidence == null || confidence < limits.minimumConfidence) {
                    return@use observation(image, feature, proposal, mask, roi.revision, roi.sourceShortEdgePixels,
                        null, null, VisualFailure.LOW_CONFIDENCE)
                }
                val signal = signal(feature, roi, limits)
                observation(image, feature, proposal, mask, roi.revision, roi.sourceShortEdgePixels,
                    signal.first, if (signal.second == null) confidence else null, signal.second)
            }
        }
    }

    private fun signal(feature: VisualFeature, roi: VisualRoi, limits: VisualPolicy): Pair<Double?, VisualFailure?> {
        val mask = requireNotNull(roi.mask)
        val n = roi.edge
        return when (feature) {
            VisualFeature.HAIR_DENSITY_PATTERN, VisualFeature.BROW_DENSITY_PATTERN, VisualFeature.BEARD_COVERAGE ->
                mask.count { it }.toDouble() / mask.size to null
            VisualFeature.HAIRLINE_PATTERN -> {
                // Mean lower boundary of an explicit hair mask; every column must have a visible transition.
                val boundary = (0 until n).map { x -> (0 until n).lastOrNull { y -> mask[y * n + x] } }
                if (boundary.any { it == null || it == n - 1 }) null to VisualFailure.EMPTY_FEATURE
                else boundary.sumOf { requireNotNull(it) + 0.5 } / (n * n) to null
            }
            VisualFeature.BROW_SHAPE -> {
                // Normalized centreline bow relative to its endpoints, not an inferred anatomical angle.
                val trace = (0 until n).map { x ->
                    val ys = (0 until n).filter { y -> mask[y * n + x] }
                    if (ys.isEmpty()) null else ys.average()
                }
                if (trace.any { it == null }) null to VisualFailure.EMPTY_FEATURE else {
                    val first = requireNotNull(trace.first()); val last = requireNotNull(trace.last())
                    trace.mapIndexed { x, y -> abs(requireNotNull(y) - (first + (last - first) * x / (n - 1))) }.max() / n to null
                }
            }
            VisualFeature.SKIN_TEXTURE_PATTERN -> {
                val selected = roi.luminance.filterIndexed { index, _ -> mask[index] }
                if (selected.size < 2) return null to VisualFailure.EMPTY_FEATURE
                val mean = selected.average()
                val variance = selected.sumOf { (it - mean).pow(2) } / selected.size
                if (mean <= 0.0 || sqrt(variance) / mean < limits.minimumRelativeContrast) {
                    return null to VisualFailure.INSUFFICIENT_CONTRAST
                }
                var energy = 0.0; var pairs = 0
                for (y in 0 until n) for (x in 0 until n) {
                    val i = y * n + x
                    if (!mask[i]) continue
                    if (x + 1 < n && mask[i + 1]) { energy += (roi.luminance[i + 1] - roi.luminance[i]).pow(2); pairs++ }
                    if (y + 1 < n && mask[i + n]) { energy += (roi.luminance[i + n] - roi.luminance[i]).pow(2); pairs++ }
                }
                if (pairs == 0) null to VisualFailure.EMPTY_FEATURE else sqrt(energy / pairs / variance) to null
            }
            else -> null to VisualFailure.UNSUPPORTED_DEFINITION
        }
    }

    private fun observation(image: VisualImage, feature: VisualFeature, proposal: VisualRoiProposal?, mask: VisualMask?,
        roiRevision: String?, shortEdge: Int?, value: Double?, confidence: Double?, failure: VisualFailure?) =
        VisualObservation(feature, image.imageRevision, roiRevision, VisualFeatureCatalog.extractorId(feature),
            value, confidence, shortEdge, failure, proposal?.definitionVersion, proposal?.providerVersion,
            mask?.providerVersion, proposal, mask?.sha256,
            if (failure == null) "SYNTHETIC_VERIFIED" else "UNVALIDATED")
}
