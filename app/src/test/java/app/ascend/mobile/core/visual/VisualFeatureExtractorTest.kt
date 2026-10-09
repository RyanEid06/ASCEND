package app.ascend.mobile.core.visual

import app.ascend.mobile.core.front.FixtureOrigin
import app.ascend.mobile.core.geometry.Point2
import org.junit.Assert.*
import org.junit.Test

class VisualFeatureExtractorTest {
    private val policy = VisualPolicy("synthetic-policy-v1", "synthetic fixtures only", 16, 16, 0.8, 0.01)
    private val quality = VisualCaptureQuality(VISUAL_QUALITY_METHOD, VisualQuality.USABLE, VisualQuality.USABLE)
    private val feature = VisualFeature.BEARD_COVERAGE
    private fun gray(v: Int) = (255 shl 24) or (v shl 16) or (v shl 8) or v
    private fun image(size: Int = 65, revision: String = "image-1", origin: FixtureOrigin = FixtureOrigin.SYNTHETIC,
        pixel: (Int, Int) -> Int = { _, _ -> 120 }) = VisualImage.fromArgb(size, size, revision, origin,
        IntArray(size * size) { i -> gray(pixel(i % size, i / size)) })
    private fun proposal(image: VisualImage, kind: VisualFeature = feature) = VisualRoiProposal(kind,
        image.imageRevision, image.sha256, "synthetic-${kind.name}-v1", "synthetic-roi-v1",
        Point2(0.0, 0.0), Point2(1.0, 0.0), Point2(0.0, 1.0), VisualVisibility.VISIBLE, 0.95)
    private fun mask(image: VisualImage, kind: VisualFeature = feature, confidence: Double? = 0.9,
        select: (Int, Int) -> Boolean = { x, _ -> x < image.width / 2 }) = VisualMask(kind,
        image.imageRevision, image.sha256, image.width, image.height, "synthetic-mask-v1",
        "synthetic-${kind.name}-v1", confidence, BooleanArray(image.width * image.height) { i -> select(i % image.width, i / image.width) })
    private fun extract(image: VisualImage, kind: VisualFeature = feature, roi: VisualRoiProposal = proposal(image, kind),
        mask: VisualMask? = mask(image, kind), quality: VisualCaptureQuality = this.quality, limits: VisualPolicy? = policy) =
        VisualFeatureExtractor(limits).extract(image, quality, mapOf(kind to roi), mask?.let { mapOf(kind to it) }.orEmpty())
    private fun observation(report: VisualReport, kind: VisualFeature = feature) = report.observations.single { it.feature == kind }
    private fun failed(report: VisualReport, reason: VisualFailure, kind: VisualFeature = feature) {
        val result = observation(report, kind)
        assertEquals(reason, result.failure); assertNull(result.value); assertNull(result.confidence)
        assertEquals("UNVALIDATED", result.reliability)
    }

    @Test fun maskCoverageIsExactAndIndependentOfPigmentation() {
        for (kind in listOf(VisualFeature.HAIR_DENSITY_PATTERN, VisualFeature.BROW_DENSITY_PATTERN, feature)) {
            for (shade in listOf(30, 120, 220)) image(pixel = { _, _ -> shade }).use { img -> mask(img, kind).use { m ->
                val result = observation(extract(img, kind, mask = m), kind)
                assertEquals(0.5, result.value!!, 0.0); assertEquals(0.9, result.confidence!!, 0.0)
                assertEquals("SYNTHETIC_VERIFIED", result.reliability)
            } }
        }
    }
    @Test fun emptyCoverageMeansVisibleAbsenceRatherThanMissingMask() {
        image().use { img -> mask(img, select = { _, _ -> false }).use { m ->
            assertEquals(0.0, observation(extract(img, mask = m)).value!!, 0.0)
            failed(extract(img, mask = null), VisualFailure.MASK_REQUIRED)
        } }
    }
    @Test fun explicitHairlineBoundaryAndBrowArcMatchSyntheticGeometry() {
        image().use { img ->
            val hair = VisualFeature.HAIRLINE_PATTERN
            mask(img, hair, select = { _, y -> y < 32 }).use { m ->
                assertEquals(7.5 / 16, observation(extract(img, hair, mask = m), hair).value!!, 0.0)
            }
            val brow = VisualFeature.BROW_SHAPE
            mask(img, brow, select = { x, y -> y in (if (x in 16..48) 16..24 else 32..40) }).use { m ->
                assertEquals(0.25, observation(extract(img, brow, mask = m), brow).value!!, 0.0)
            }
        }
    }
    @Test fun missingBoundaryColumnsAndClippedHairlineFail() {
        image().use { img ->
            for (kind in listOf(VisualFeature.HAIRLINE_PATTERN, VisualFeature.BROW_SHAPE)) {
                mask(img, kind, select = { x, _ -> x < 30 }).use { m -> failed(extract(img, kind, mask = m), VisualFailure.EMPTY_FEATURE, kind) }
            }
            val kind = VisualFeature.HAIRLINE_PATTERN
            mask(img, kind, select = { _, _ -> true }).use { m -> failed(extract(img, kind, mask = m), VisualFailure.EMPTY_FEATURE, kind) }
        }
    }
    @Test fun realPhotosCannotBePromotedBySyntheticPolicyOrConfidence() {
        image(origin = FixtureOrigin.CONSENTED_LOCAL).use { img ->
            val report = extract(img)
            assertTrue(report.observations.all { it.failure == VisualFailure.UNVALIDATED_CAPTURE && it.value == null && it.confidence == null })
        }
    }
    @Test fun noPolicyAndUnknownQualityRemainUnavailable() {
        image().use { img ->
            failed(extract(img, limits = null), VisualFailure.POLICY_REQUIRED)
            failed(extract(img, quality = quality.copy(methodVersion = "wrong")), VisualFailure.QUALITY_METHOD_MISMATCH)
            for (state in listOf(VisualQuality.UNKNOWN, VisualQuality.UNSUITABLE)) {
                failed(extract(img, quality = quality.copy(lighting = state)), VisualFailure.LIGHTING_UNRELIABLE)
                failed(extract(img, quality = quality.copy(focus = state)), VisualFailure.FOCUS_UNRELIABLE)
            }
        }
    }
    @Test fun sourceHashAndRevisionBothBindRoisAndMasks() {
        image().use { img ->
            failed(extract(img, roi = proposal(img).copy(imageRevision = "old")), VisualFailure.STALE_SOURCE)
            failed(extract(img, roi = proposal(img).copy(imageSha256 = "a".repeat(64))), VisualFailure.STALE_SOURCE)
            image(revision = "old").use { old -> mask(old).use { stale -> failed(extract(img, mask = stale), VisualFailure.STALE_SOURCE) } }
            image(pixel = { _, _ -> 100 }).use { old -> mask(old).use { stale -> failed(extract(img, mask = stale), VisualFailure.STALE_SOURCE) } }
        }
    }
    @Test fun maskDefinitionDimensionsAndFeatureMustMatchRoi() {
        image().use { img ->
            mask(img).use { m -> failed(extract(img, roi = proposal(img).copy(definitionVersion = "new"), mask = m), VisualFailure.MASK_MISMATCH) }
            val bad = VisualMask(feature, img.imageRevision, img.sha256, 33, 33, "mask", proposal(img).definitionVersion, 1.0, BooleanArray(33 * 33))
            bad.use { failed(extract(img, mask = it), VisualFailure.MASK_MISMATCH) }
            try {
                VisualFeatureExtractor(policy).extract(img, quality, mapOf(VisualFeature.BROW_SHAPE to proposal(img)))
                fail("Mislabeled ROI accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun rejectsOcclusionClippingMirroringShearAndDegenerateAxes() {
        image().use { img ->
            for (visibility in listOf(VisualVisibility.OCCLUDED, VisualVisibility.UNKNOWN)) failed(extract(img,
                roi = proposal(img).copy(visibility = visibility)), VisualFailure.ROI_UNRELIABLE)
            failed(extract(img, roi = proposal(img).copy(origin = Point2(-0.01, 0.0))), VisualFailure.ROI_OUTSIDE_IMAGE)
            failed(extract(img, roi = proposal(img).copy(across = Point2(-1.0, 0.0))), VisualFailure.ROI_UNRELIABLE)
            failed(extract(img, roi = proposal(img).copy(across = Point2(0.0, 0.0))), VisualFailure.ROI_UNRELIABLE)
            failed(extract(img, roi = proposal(img).copy(down = Point2(0.1, 1.0))), VisualFailure.ROI_UNRELIABLE)
        }
    }
    @Test fun sourceResolutionCannotBeRepairedByUpsampling() {
        image(9).use { img -> failed(extract(img), VisualFailure.INSUFFICIENT_RESOLUTION) }
        image().use { img -> failed(extract(img, roi = proposal(img).copy(across = Point2(0.1, 0.0))), VisualFailure.INSUFFICIENT_RESOLUTION) }
    }
    @Test fun confidenceUsesMinimumOfExplicitRoiAndSegmentationEvidence() {
        image().use { img ->
            for (c in listOf(null, 0.79)) {
                mask(img, confidence = c).use { m -> failed(extract(img, mask = m), VisualFailure.LOW_CONFIDENCE) }
                failed(extract(img, roi = proposal(img).copy(confidence = c)), VisualFailure.LOW_CONFIDENCE)
            }
            mask(img, confidence = 0.8).use { m -> assertEquals(0.8, observation(extract(img, mask = m)).confidence!!, 0.0) }
        }
    }
    @Test fun hooksAndMissingRoisRemainExplicitlyUnavailable() {
        image().use { img ->
            val report = VisualFeatureExtractor(policy).extract(img, quality, emptyMap())
            for (kind in VisualFeature.entries) failed(report, if (kind in VisualFeatureCatalog.implemented)
                VisualFailure.ROI_MISSING else VisualFailure.UNSUPPORTED_DEFINITION, kind)
            assertEquals(VisualFeature.entries.toList(), report.observations.map { it.feature })
        }
    }
    @Test fun reportsAreDeterministicAndRetakesInvalidateCurrentSource() {
        image().use { img -> mask(img).use { m ->
            val first = extract(img, mask = m)
            assertEquals(first, extract(img, mask = m)); assertTrue(first.matchesCurrentSource(img))
            assertEquals(policy, first.policySnapshot)
            image(revision = "retake").use { assertFalse(first.matchesCurrentSource(it)) }
            image(pixel = { _, _ -> 121 }).use { assertFalse(first.matchesCurrentSource(it)) }
        } }
    }
    @Test fun roiRevisionChangesWithGeometryDefinitionProviderGridAndMask() {
        image().use { img -> mask(img).use { m ->
            val original = observation(extract(img, mask = m)).roiRevision
            assertNotNull(original)
            assertNotEquals(original, observation(extract(img, roi = proposal(img).copy(providerVersion = "next"), mask = m)).roiRevision)
            assertNotEquals(original, observation(extract(img, roi = proposal(img).copy(across = Point2(0.9, 0.0)), mask = m)).roiRevision)
            assertNotEquals(original, observation(extract(img, limits = policy.copy(gridEdge = 8), mask = m)).roiRevision)
            mask(img, select = { _, _ -> true }).use { changed -> assertNotEquals(original, observation(extract(img, mask = changed)).roiRevision) }
        } }
    }
    @Test fun uniformCoverageIsStableAcrossSyntheticDeviceResolutions() {
        for (size in listOf(33, 65, 129)) image(size).use { img -> mask(img).use { m ->
            assertEquals(0.5, observation(extract(img, mask = m)).value!!, 0.0)
        } }
    }
    @Test fun spatialTextureIsAffineLightingInvariantButFlatImagesFail() {
        val kind = VisualFeature.SKIN_TEXTURE_PATTERN
        val values = mutableListOf<Double>()
        for ((gain, offset) in listOf(1 to 0, 2 to 0, 1 to 40)) image(pixel = { x, y ->
            (40 + (x / 4 + y / 4) % 2 * 30) * gain + offset
        }).use { img -> mask(img, kind, select = { _, _ -> true }).use { m ->
            values += observation(extract(img, kind, mask = m), kind).value!!
        } }
        assertEquals(values[0], values[1], 1e-12); assertEquals(values[0], values[2], 1e-12)
        image().use { img -> mask(img, kind, select = { _, _ -> true }).use { m ->
            failed(extract(img, kind, mask = m), VisualFailure.INSUFFICIENT_CONTRAST, kind)
        } }
    }
    @Test fun nonSquareImagesUsePixelIsotropicAxesAndBilinearSamples() {
        val img = VisualImage.fromArgb(65, 33, "wide", FixtureOrigin.SYNTHETIC,
            IntArray(65 * 33) { i -> gray(i % 65 * 3) })
        img.use {
            val p = proposal(img).copy(across = Point2(0.5, 0.0))
            val roi = (VisualRoiStandardizer.extract(img, p, policy) as VisualRoiResult.Ready).roi
            roi.use { assertEquals(3.0 / 255.0, it.luminance[0], 1e-12); assertEquals(32, it.sourceShortEdgePixels) }
        }
    }
    @Test fun inputsCopyBuffersAndReleaseInvalidatesAccess() {
        val pixels = IntArray(65 * 65) { gray(100) }
        val img = VisualImage.fromArgb(65, 65, "copy", FixtureOrigin.SYNTHETIC, pixels)
        pixels.fill(gray(200)); assertEquals(100.0 / 255, img.luminance(0, 0), 1e-12)
        val bits = BooleanArray(65 * 65) { true }
        val m = VisualMask(feature, img.imageRevision, img.sha256, 65, 65, "mask", "def", 1.0, bits)
        bits.fill(false); assertTrue(m.contains(0, 0)); m.close(); img.close()
        try { img.luminance(0, 0); fail("Released pixels accessible") } catch (_: IllegalStateException) { }
        try { m.contains(0, 0); fail("Released mask accessible") } catch (_: IllegalStateException) { }
    }
    @Test fun rotatedRoiAxesPreserveSamplingAndRejectAxesOrthogonalOnlyInNormalizedSpace() {
        image(pixel = { x, _ -> x * 3 }).use { img ->
            val p = proposal(img).copy(origin = Point2(0.5, 0.1), across = Point2(0.4, 0.4), down = Point2(-0.4, 0.4))
            val roi = (VisualRoiStandardizer.extract(img, p, policy) as VisualRoiResult.Ready).roi
            roi.use {
                assertEquals(96.0 / 255, it.luminance[0], 1e-12)
                assertEquals(it.luminance[0], it.luminance[15 * 16 + 15], 1e-12)
                assertTrue(it.luminance[15] > it.luminance[0])
                assertEquals(36, it.sourceShortEdgePixels)
            }
        }
        VisualImage.fromArgb(65, 33, "wide", FixtureOrigin.SYNTHETIC, IntArray(65 * 33) { gray(100) }).use { img ->
            val p = proposal(img).copy(origin = Point2(0.5, 0.1), across = Point2(0.4, 0.4), down = Point2(-0.4, 0.4))
            assertEquals(VisualFailure.ROI_UNRELIABLE, (VisualRoiStandardizer.extract(img, p, policy) as VisualRoiResult.Failed).reason)
        }
    }
    @Test fun maskContentCopyAndRevisionAreStableAfterCallerMutation() {
        image().use { img ->
            val bits = BooleanArray(65 * 65) { true }
            VisualMask(feature, img.imageRevision, img.sha256, 65, 65, "mask", proposal(img).definitionVersion, 1.0, bits).use { m ->
                val first = extract(img, mask = m)
                bits.fill(false)
                assertEquals(first, extract(img, mask = m))
            }
        }
    }
    @Test fun hostilePixelAndPolicyInputsFailBeforeExtraction() {
        val invalid = listOf<() -> Unit>(
            { VisualImage.fromArgb(Int.MAX_VALUE, Int.MAX_VALUE, "bad", FixtureOrigin.SYNTHETIC, intArrayOf()) },
            { VisualImage.fromArgb(2, 2, "bad", FixtureOrigin.SYNTHETIC, IntArray(4)) },
            { VisualImage.fromArgb(2, 2, "bad", FixtureOrigin.SYNTHETIC, IntArray(3) { gray(0) }) },
            { policy.copy(gridEdge = 257) }, { policy.copy(minimumConfidence = Double.NaN) },
            { policy.copy(minimumRelativeContrast = 0.0) })
        invalid.forEach { action -> try { action(); fail("Invalid input accepted") } catch (_: IllegalArgumentException) { } }
    }
}
