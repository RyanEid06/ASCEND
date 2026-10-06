package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProfileMeasurementsTest {
    private val fixture get() = javaClass.classLoader!!.getResourceAsStream("profile/synthetic-profile-v1.json")!!.use {
        Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject
    }
    private val input get(): ProfileInput {
        val data = fixture
        return ProfileInput(data.getValue("imageRevision").jsonPrimitive.content, PixelResolution(1000, 1000), 500,
            ProfileOrigin.SYNTHETIC, ProfileSide.RIGHT, true, "synthetic-side-v1", 1, PoseDeviation(), Point2(.5, .5),
            .95, "synthetic-confidence-v1", ProfileProvider("synthetic-extractor-v1", null, null),
            data.getValue("points").jsonObject.map { (id, coordinates) ->
                val xy = coordinates.jsonArray
                LandmarkId(id) to ProfilePoint(Point2(xy[0].jsonPrimitive.double, xy[1].jsonPrimitive.double), .9, ProfilePointSource.AUTOMATIC)
            }.toMap())
    }
    private val policy get() = ProfilePolicy("synthetic-policy-v1", "Synthetic arithmetic/gate fixtures only", .8, 720, 300,
        PoseLimits(8.0, 8.0, 12.0), emptySet())
    private fun supported(source: ProfileInput = input, limits: ProfilePolicy? = policy) =
        ProfileMeasurements(limits).measure(source).metrics.filter { it.formulaId != null }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private fun assistance(source: ProfileInput = input, ids: Set<LandmarkId> = ProfileCatalog.semanticIds): ProfileAssistancePolicy =
        ProfileAssistancePolicy("synthetic-guidance-v1", "Synthetic regions only", source.imageRevision, source.side!!,
            ids.associateWith { id ->
                val point = input.points.getValue(id).point
                ProfileZone((point.x - .05).coerceAtLeast(0.0), (point.x + .05).coerceAtMost(1.0),
                    (point.y - .05).coerceAtLeast(0.0), (point.y + .05).coerceAtMost(1.0), point, .025)
            })
    private fun confirm(revision: ProfileRevision, id: LandmarkId, point: Point2, bounds: ProfileAssistancePolicy = assistance()) =
        revision.confirm(id, point, bounds, revision.input.imageRevision, revision.revision, "synthetic-user-confirm-v1", revision.revision)

    @Test fun goldenFixtureAccountsForAllTwelveCandidatesWithoutScoringPromotion() {
        val data = fixture
        assertEquals(1, data.getValue("formatVersion").jsonPrimitive.int)
        assertEquals("SYNTHETIC_FORMULA_ONLY", data.getValue("evidence").jsonPrimitive.content)
        val report = ProfileMeasurements(policy).measure(input)
        assertEquals(12, report.metrics.size)
        assertEquals(12, report.metrics.map { it.metricId }.distinct().size)
        assertEquals(CandidateFeasibilityMatrix.entries.filter { it.metricId.startsWith("candidate.profile.") }.map { it.metricId }.toSet(),
            report.metrics.map { it.metricId }.toSet())
        data.getValue("expectedDegrees").jsonObject.forEach { (name, expected) ->
            val metric = report.metrics.single { it.metricId == "candidate.profile.$name" }
            assertNull(metric.failure)
            assertEquals(expected.jsonPrimitive.double, metric.value!!, data.getValue("absoluteToleranceDegrees").jsonPrimitive.double)
            assertEquals(MeasurementMode.AUTOMATIC, metric.mode)
            assertEquals(.9, metric.confidence!!, 0.0)
            assertEquals(ReliabilityStatus.SYNTHETIC_VERIFIED, metric.reliability)
        }
        assertEquals(8, report.metrics.count { it.failure == ProfileFailure.UNSUPPORTED_DEFINITION })
        assertTrue(report.metrics.single { it.metricId.endsWith("soft_tissue_jaw_angle") }.softTissueProxy)
        assertTrue(CandidateFeasibilityMatrix.entries.none { it.reliabilityStatus == ReliabilityStatus.VALIDATED })
    }

    @Test fun deterministicMapOrderingAndIndependentSideLabels() {
        val engine = ProfileMeasurements(policy)
        val first = engine.measure(input)
        assertEquals(first, engine.measure(input.copy(points = input.points.entries.reversed().associate { it.key to it.value })))
        val left = input.copy(side = ProfileSide.LEFT, points = input.points.mapValues { (_, p) -> p.copy(point = Point2(1 - p.point.x, p.point.y)) })
        supported(left).zip(supported()).forEach { (a, b) -> assertEquals(b.value!!, a.value!!, 1e-9) }
        assertEquals(ProfileSide.LEFT, engine.measure(left).side)
    }

    @Test fun everyRequiredPointRejectsMissingUnknownAndLowAutomaticConfidence() {
        ProfileCatalog.mappings.filter { it.anglePoints != null }.forEach { mapping ->
            fun metric(source: ProfileInput) = ProfileMeasurements(policy).measure(source).metrics.single { it.metricId == mapping.metricId }
            mapping.anglePoints!!.forEach { id ->
                assertEquals(ProfileFailure.MISSING_POINT, metric(input.copy(points = input.points - id)).failure)
                listOf(null, .799).forEach { confidence ->
                    assertEquals(ProfileFailure.LOW_CONFIDENCE, metric(input.copy(points = input.points +
                        (id to input.points.getValue(id).copy(confidence = confidence)))).failure)
                }
            }
        }
    }

    @Test fun globalConfidenceAndThresholdBoundaryDoNotGetInvented() {
        assertTrue(supported(input.copy(overallConfidence = null)).all { it.failure == ProfileFailure.LOW_CONFIDENCE })
        assertTrue(supported(input.copy(overallConfidence = .799)).all { it.failure == ProfileFailure.LOW_CONFIDENCE })
        assertTrue(supported(input.copy(overallConfidence = .8)).all { it.value != null && it.confidence == .8 })
    }

    @Test fun captureGatesRequirePolicyOrientationFaceCountPoseAndActualResolution() {
        fun all(source: ProfileInput, reason: ProfileFailure, limits: ProfilePolicy? = policy) =
            assertTrue(supported(source, limits).all { it.failure == reason })
        all(input, ProfileFailure.POLICY_REQUIRED, null)
        all(input.copy(origin = ProfileOrigin.CONSENTED_LOCAL), ProfileFailure.SYNTHETIC_ONLY)
        all(input.copy(orientationConfirmed = false), ProfileFailure.ORIENTATION_REQUIRED)
        all(input.copy(side = null, orientationConfirmed = false, orientationMethodVersion = null), ProfileFailure.ORIENTATION_REQUIRED)
        all(input.copy(faceCount = null), ProfileFailure.FACE_COUNT_UNAVAILABLE)
        listOf(0, 2).forEach { all(input.copy(faceCount = it), ProfileFailure.NOT_ONE_FACE) }
        all(input.copy(residualPose = null), ProfileFailure.POSE_UNAVAILABLE)
        all(input.copy(resolution = PixelResolution(719, 1000)), ProfileFailure.INSUFFICIENT_RESOLUTION)
        listOf(null, 299).forEach { all(input.copy(faceShortEdgePixels = it), ProfileFailure.INSUFFICIENT_RESOLUTION) }
        listOf(-1, 1).forEach { sign ->
            listOf(PoseDeviation(sign * 8.001, 0.0, 0.0), PoseDeviation(0.0, sign * 8.001, 0.0), PoseDeviation(0.0, 0.0, sign * 12.001)).forEach {
                all(input.copy(residualPose = it), ProfileFailure.POSE_OUT_OF_RANGE)
            }
        }
    }

    @Test fun isotropicBeforeRollAndExactOverlayInverse() {
        listOf(PixelResolution(800, 1600), PixelResolution(1600, 800)).forEach { resolution ->
            val short = resolution.shortEdge.toDouble()
            val pivot = Point2(.5, .5)
            val source = input.copy(resolution = resolution, residualPose = PoseDeviation(0.0, 0.0, 10.0),
                rollOrigin = Point2(pivot.x * short / resolution.width, pivot.y * short / resolution.height),
                points = input.points.mapValues { (_, p) ->
                    val rotated = rotate(p.point, 10.0, pivot)
                    p.copy(point = Point2(rotated.x * short / resolution.width, rotated.y * short / resolution.height))
                })
            supported(source).zip(supported()).forEach { (a, b) ->
                assertEquals(b.value!!, a.value!!, 1e-9)
                a.sourcePoints.forEach { (id, p) ->
                    val image = source.imagePoint(p)
                    assertEquals(source.points.getValue(id).point.x, image.x, 1e-12)
                    assertEquals(source.points.getValue(id).point.y, image.y, 1e-12)
                }
            }
        }
    }

    @Test fun collapsedAngleRaysFailCleanly() {
        ProfileCatalog.mappings.filter { it.anglePoints != null }.forEach { mapping ->
            val ids = mapping.anglePoints!!
            val source = input.copy(points = input.points + (ids[0] to input.points.getValue(ids[0]).copy(point = input.points.getValue(ids[1]).point)))
            assertEquals(ProfileFailure.DEGENERATE_GEOMETRY, ProfileMeasurements(policy).measure(source).metrics.single { it.metricId == mapping.metricId }.failure)
        }
    }

    @Test fun zeroProposalFallbackInitializesOnlyEligiblePointsAndKeepsUnknownConfidence() {
        val empty = input.copy(points = emptyMap(), provider = null, overallConfidence = null)
        val bounds = assistance(empty)
        var revision = ProfileRevision.start(empty)
        ProfileCatalog.semanticIds.forEach { id -> revision = confirm(revision, id, bounds.zones.getValue(id).missingPointAnchor, bounds) }
        assertTrue(revision.audit.all { it.originalProposal == null && it.kind == ProfileActionKind.INITIALIZE })
        assertTrue(revision.input.points.values.all { it.confidence == null && it.confirmed })
        supported(revision.input).forEach {
            assertNotNull(it.value)
            assertEquals(MeasurementMode.ASSISTED, it.mode)
            assertNull(it.confidence)
        }
        assertEquals(revision.audit, ProfileRevision.restore(empty, revision.audit, bounds).audit)
        assertEquals(revision.input, ProfileRevision.restore(empty, revision.audit, bounds).input)
    }

    @Test fun confirmationWithoutMovementIsRecordedAndRequiredByPolicy() {
        val id = Landmarks.NASION
        val limits = policy.copy(confirmationRequired = setOf(id))
        fun nasofrontal(source: ProfileInput) = supported(source, limits).single { it.metricId.endsWith("nasofrontal_angle") }
        assertEquals(ProfileFailure.CONFIRMATION_REQUIRED, nasofrontal(input).failure)
        val revision = confirm(ProfileRevision.start(input), id, input.points.getValue(id).point)
        assertEquals(ProfileActionKind.CONFIRM, revision.audit.single().kind)
        assertNull(nasofrontal(revision.input).failure)
        assertEquals(MeasurementMode.ASSISTED, nasofrontal(revision.input).mode)
        assertEquals(.9, revision.input.points.getValue(id).confidence!!, 0.0)
    }

    @Test fun partialFallbackOnlyMarksAffectedMetricsAssisted() {
        val id = Landmarks.COLUMELLA
        val partial = input.copy(points = input.points - id)
        val revision = confirm(ProfileRevision.start(partial), id, input.points.getValue(id).point, assistance(partial))
        val results = supported(revision.input)
        assertEquals(1, results.count { it.mode == MeasurementMode.ASSISTED })
        assertTrue(results.filterNot { it.metricId.endsWith("nasolabial_angle") }.all { it.mode == MeasurementMode.AUTOMATIC })
        assertNull(results.single { it.metricId.endsWith("nasolabial_angle") }.confidence)
    }

    @Test fun confirmationNeverRaisesKnownLowConfidence() {
        val id = Landmarks.NASION
        val low = input.copy(points = input.points + (id to input.points.getValue(id).copy(confidence = .2)))
        val revised = confirm(ProfileRevision.start(low), id, low.points.getValue(id).point, assistance(low))
        assertEquals(.2, revised.input.points.getValue(id).confidence!!, 0.0)
        assertEquals(ProfileFailure.LOW_CONFIDENCE, supported(revised.input).single { it.metricId.endsWith("nasofrontal_angle") }.failure)
    }

    @Test fun dragsCannotAccumulateEscapeAndGuidanceCannotChange() {
        val id = Landmarks.NASION
        val start = ProfileRevision.start(input)
        val anchor = input.points.getValue(id).point
        val first = confirm(start, id, Point2(anchor.x + .02, anchor.y))
        rejected { confirm(first, id, Point2(anchor.x + .04, anchor.y)) }
        rejected { confirm(first, id, Point2(anchor.x + .051, anchor.y)) }
        val accepted = confirm(first, id, Point2(anchor.x + .025, anchor.y))
        assertEquals(2L, accepted.revision)
        assertTrue(accepted.audit.all { it.anchor == anchor && it.originalProposal == input.points.getValue(id) })
        rejected { confirm(first, id, anchor, assistance().copy(version = "replacement")) }
        rejected { confirm(first, id, anchor, assistance().copy(zones = assistance().zones +
            (id to assistance().zones.getValue(id).copy(maximumShortEdgeDisplacement = .1)))) }
    }

    @Test fun staleImageSideRevisionAndCorruptAuditAreRejected() {
        val id = Landmarks.NASION
        val start = ProfileRevision.start(input)
        val point = input.points.getValue(id).point
        rejected { start.confirm(id, point, assistance(), "stale-image", 0, "test", 0) }
        rejected { start.confirm(id, point, assistance(), input.imageRevision, 1, "test", 0) }
        rejected { confirm(start, id, point, assistance().copy(side = ProfileSide.LEFT)) }
        rejected { confirm(start, id, point, assistance().copy(imageRevision = "another-image")) }
        rejected { confirm(start, Landmarks.TRICHION, point) }
        val first = confirm(start, id, point)
        rejected { ProfileRevision.restore(input, first.audit.map { it.copy(anchor = Point2(.1, .1)) }, assistance()) }
        rejected { ProfileRevision.restore(input, first.audit.map { it.copy(current = it.current.copy(confidence = 1.0)) }, assistance()) }
        rejected { confirm(ProfileRevision.start(input.copy(origin = ProfileOrigin.CONSENTED_LOCAL)), id, point) }
        rejected { confirm(ProfileRevision.start(input.copy(orientationConfirmed = false)), id, point) }
    }

    @Test fun revisionDefensivelySnapshotsCallerMaps() {
        val points = input.points.toMutableMap()
        val source = input.copy(points = points)
        val revision = ProfileRevision.start(source)
        points.clear()
        assertEquals(12, revision.input.points.size)
        val exported = revision.input.points as MutableMap
        exported.clear()
        assertEquals(12, revision.input.points.size)
    }

    @Test fun assistanceDistanceUsesImageAspectRatio() {
        val source = input.copy(resolution = PixelResolution(1600, 800))
        val id = Landmarks.NASION
        val anchor = source.points.getValue(id).point
        val start = ProfileRevision.start(source)
        val bounds = assistance(source)
        rejected { confirm(start, id, Point2(anchor.x + .02, anchor.y), bounds) }
        assertEquals(1L, confirm(start, id, Point2(anchor.x + .01, anchor.y), bounds).revision)
    }

    @Test fun malformedCoordinatesConfidenceAndProvenanceAreRejected() {
        rejected { ProfilePoint(Point2(-.01, .5), .9, ProfilePointSource.AUTOMATIC) }
        rejected { ProfilePoint(Point2(.5, .5), Double.NaN, ProfilePointSource.AUTOMATIC) }
        rejected { ProfilePoint(Point2(.5, .5), null, ProfilePointSource.ASSISTED, false) }
        rejected { input.copy(provider = null) }
        rejected { input.copy(points = mapOf(Landmarks.TRICHION to input.points.values.first())) }
        rejected { input.copy(side = null) }
        rejected { ProfileProvider("provider", "model", null) }
        rejected { ProfileProvider("provider", "model", "invalid-sha") }
    }
}
