package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide
import org.junit.Assert.*
import org.junit.Test

class ProfileAssistSessionTest {
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }
    private fun session(side: ProfileSide = ProfileSide.LEFT, facing: ProfileFacing = ProfileFacing.RIGHT) =
        ProfileAssistSession.preview("image-1", PixelResolution(900, 1200), side, facing)

    @Test fun demoLocalFallbackCompletesAllRequiredPointsWithoutInventingConfidence() {
        var value = session()
        assertEquals(ProfileOrigin.DEMO_LOCAL, value.revision.input.origin)
        assertTrue(value.revision.input.points.isEmpty())
        assertNull(value.revision.input.residualPose)
        assertFalse(value.complete)
        value.requiredPoints.forEach { id -> value = value.confirm(id, value.policy.zones.getValue(id).missingPointAnchor, value.revision.revision) }
        assertTrue(value.complete)
        assertTrue(value.revision.input.points.values.all { it.source == ProfilePointSource.ASSISTED && it.confidence == null })
        assertTrue(ProfileMeasurements(null).measure(value.revision.input).metrics.all { it.value == null })
    }

    @Test fun leftRightAndFacingAreIndependentAndNeverSilentlyMirrored() {
        val left = session(ProfileSide.LEFT)
        val right = session(ProfileSide.RIGHT)
        assertEquals(left.policy.zones, right.policy.zones)
        assertEquals(ProfileSide.LEFT, left.revision.input.side)
        assertEquals(ProfileSide.RIGHT, right.revision.input.side)
        val mirrored = session(facing = ProfileFacing.LEFT)
        left.policy.zones.forEach { (id, zone) ->
            assertEquals(1 - zone.missingPointAnchor.x, mirrored.policy.zones.getValue(id).missingPointAnchor.x, 1e-12)
        }
    }

    @Test fun repeatedDragsCannotEscapeOriginalRegionOrDisplacement() {
        var value = session()
        val id = value.requiredPoints.first()
        val zone = value.policy.zones.getValue(id)
        val anchor = zone.missingPointAnchor
        value = value.confirm(id, anchor, 0)
        value = value.confirm(id, Point2(anchor.x + .02, anchor.y), 1)
        val current = value
        rejected { current.confirm(id, Point2(anchor.x + .20, anchor.y), 2) }
        rejected { current.confirm(id, anchor, 1) }
        rejected { current.confirm(LandmarkId("arbitrary-point"), anchor, 2) }
        assertEquals(anchor, current.revision.audit.last().anchor)
    }

    @Test fun codecRoundTripsProvenanceAndRejectsTamperedAuditOrVersion() {
        var value = session()
        val id = value.requiredPoints.first()
        value = value.confirm(id, value.policy.zones.getValue(id).missingPointAnchor, 0)
        val bytes = ProfileAssistCodec.encode(value)
        val restored = ProfileAssistCodec.decode(bytes)
        assertEquals(value.revision.input, restored.revision.input)
        assertEquals(value.revision.audit, restored.revision.audit)
        assertEquals(value.facing, restored.facing)
        val json = bytes.toString(Charsets.UTF_8)
        rejected { ProfileAssistCodec.decode(json.replace("\"formatVersion\":1", "\"formatVersion\":99").toByteArray()) }
        rejected { ProfileAssistCodec.decode(json.replace("\"revision\":1", "\"revision\":2").toByteArray()) }
        assertNull(ProfileAssistCodec.cached(byteArrayOf(0, 1)))
    }

    @Test fun overlayFitUsesOneScaleAndExactInverseOnEveryAspect() {
        listOf(PixelResolution(900, 1200), PixelResolution(1200, 900), PixelResolution(1000, 1000)).forEach { resolution ->
            val fit = ProfileImageFit.fit(resolution, 780.0, 400.0)
            assertEquals(fit.width / resolution.width, fit.height / resolution.height, 1e-12)
            val original = Point2(.2, .7)
            val restored = fit.unproject(fit.project(original))
            assertEquals(original.x, restored.x, 1e-12)
            assertEquals(original.y, restored.y, 1e-12)
        }
    }

    @Test fun automaticProposalConfirmationUsesTheSameCorrectedInputAsTheOverlay() {
        val preview = session()
        val id = preview.requiredPoints.first()
        val proposal = preview.policy.zones.getValue(id).missingPointAnchor
        val input = preview.revision.input.copy(origin = ProfileOrigin.SYNTHETIC,
            provider = ProfileProvider("fixture-v1", null, null),
            points = mapOf(id to ProfilePoint(proposal, .9, ProfilePointSource.AUTOMATIC)))
        val value = ProfileAssistSession(ProfileRevision.start(input), preview.policy, preview.facing)
            .confirm(id, Point2(proposal.x + .01, proposal.y), 0)
        assertEquals(.9, value.revision.input.points.getValue(id).confidence!!, 0.0)
        assertEquals(value.revision.input.points.getValue(id).point, value.imagePoints().getValue(id))
        assertEquals(proposal, value.revision.audit.single().originalProposal!!.point)
    }

    @Test fun orientationRestartTokensDoNotLetStaleClientsWriteIntoTheNewSession() {
        val initial = session()
        val restarted = session(ProfileSide.RIGHT).copy(revisionToken = initial.revisionToken + 1)
        val id = restarted.requiredPoints.first()
        rejected { restarted.confirm(id, restarted.policy.zones.getValue(id).missingPointAnchor, initial.revisionToken) }
        val restored = ProfileAssistCodec.decode(ProfileAssistCodec.encode(restarted))
        assertEquals(1L, restored.revisionToken)
        assertEquals(0L, restored.revision.revision)
    }

    @Test fun productionOriginsCannotBorrowDemoGuidanceAndConfidenceIsNeverEditable() {
        val initial = session()
        val source = initial.revision.input.copy(origin = ProfileOrigin.CONSENTED_LOCAL)
        val id = initial.requiredPoints.first()
        rejected { ProfileAssistSession(ProfileRevision.start(source), initial.policy, initial.facing)
            .confirm(id, initial.policy.zones.getValue(id).missingPointAnchor, 0) }
        var completed = initial
        completed.requiredPoints.forEach { point -> completed = completed.confirm(point, completed.policy.zones.getValue(point).missingPointAnchor, completed.revisionToken) }
        val policy = ProfilePolicy("test", "test", .8, 1, 1, PoseLimits(8.0, 8.0, 12.0), emptySet())
        assertTrue(ProfileMeasurements(policy).measure(completed.revision.input).metrics.filter { it.formulaId != null }
            .all { it.failure == ProfileFailure.SYNTHETIC_ONLY && it.confidence == null })
    }
}
