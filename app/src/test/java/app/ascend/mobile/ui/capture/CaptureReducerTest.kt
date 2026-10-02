package app.ascend.mobile.ui.capture

import app.ascend.mobile.core.model.ProfileSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureReducerTest {
    @Test
    fun sourceSelectionStartsFrontTutorialAndPreservesPreference() {
        val state = reduce(
            CaptureUiState(),
            CaptureAction.SelectPreferredSource(CaptureSource.GALLERY),
        )

        assertEquals(CaptureSource.GALLERY, state.preferredSource)
        assertEquals(CaptureStep.Tutorial(CaptureRole.FRONT), state.step)
    }

    @Test
    fun frontConfirmationProgressesToProfileThenReady() {
        var state = frontReviewState()
        state = reduce(state, CaptureAction.ConfirmReview(CaptureRole.FRONT))

        assertEquals(CaptureStep.Tutorial(CaptureRole.PROFILE), state.step)
        assertTrue(state.front?.confirmed == true)

        state = reduce(state, CaptureAction.ContinueTutorial(CaptureRole.PROFILE))
        state = reduce(
            state,
            CaptureAction.MediaSelected(
                CaptureRole.PROFILE,
                CaptureSource.GALLERY,
                "content://profile",
            ),
        )
        state = reduce(state, CaptureAction.ConfirmReview(CaptureRole.PROFILE))

        assertEquals(CaptureStep.Ready, state.step)
        assertNotNull(state.readyPayloadOrNull())
    }

    @Test
    fun frontRetakeDoesNotDeleteConfirmedProfile() {
        val ready = readyState()

        val retaking = reduce(ready, CaptureAction.Retake(CaptureRole.FRONT))

        assertEquals(CaptureStep.Acquisition(CaptureRole.FRONT), retaking.step)
        assertNull(retaking.front)
        assertTrue(retaking.profile?.confirmed == true)
    }

    @Test
    fun profileRetakeDoesNotDeleteConfirmedFront() {
        val ready = readyState()

        val retaking = reduce(ready, CaptureAction.Retake(CaptureRole.PROFILE))

        assertEquals(CaptureStep.Acquisition(CaptureRole.PROFILE), retaking.step)
        assertNull(retaking.profile)
        assertTrue(retaking.front?.confirmed == true)
    }

    @Test
    fun galleryCancellationLeavesAcquisitionStateUntouched() {
        val acquisition = CaptureUiState(
            step = CaptureStep.Acquisition(CaptureRole.FRONT),
            preferredSource = CaptureSource.GALLERY,
        )

        val result = reduce(
            acquisition,
            CaptureAction.GalleryCancelled(CaptureRole.FRONT),
        )

        assertSame(acquisition, result)
    }

    @Test
    fun timerStateOnlyAdvancesWhileMatchingCameraIsActive() {
        var state = CaptureUiState(step = CaptureStep.Camera(CaptureRole.FRONT))

        state = reduce(
            state,
            CaptureAction.CountdownStarted(CaptureRole.FRONT, 3),
        )
        assertEquals(3, state.countdownSeconds)

        state = reduce(
            state,
            CaptureAction.CountdownTick(CaptureRole.FRONT, 2),
        )
        assertEquals(2, state.countdownSeconds)

        val impossible = reduce(
            state,
            CaptureAction.CountdownTick(CaptureRole.PROFILE, 1),
        )
        assertEquals(2, impossible.countdownSeconds)

        state = reduce(
            state,
            CaptureAction.CountdownFinished(CaptureRole.FRONT),
        )
        assertNull(state.countdownSeconds)
    }

    @Test
    fun backNavigationWalksCaptureFlowWithoutDroppingOtherRole() {
        val ready = readyState()

        val profileReview = reduce(ready, CaptureAction.Back)
        assertEquals(CaptureStep.Review(CaptureRole.PROFILE), profileReview.step)
        assertTrue(profileReview.front?.confirmed == true)
        assertTrue(profileReview.profile?.confirmed == false)

        val profileAcquire = reduce(profileReview, CaptureAction.Back)
        assertEquals(CaptureStep.Acquisition(CaptureRole.PROFILE), profileAcquire.step)
        assertTrue(profileAcquire.front?.confirmed == true)
        assertNull(profileAcquire.profile)
    }

    @Test
    fun cropTransformIsKeptInReviewState() {
        val review = frontReviewState()
        val crop = CropTransform(
            scale = 2.25f,
            offsetXFraction = 0.2f,
            offsetYFraction = -0.15f,
        )

        val updated = reduce(
            review,
            CaptureAction.UpdateCrop(CaptureRole.FRONT, crop),
        )

        assertEquals(CaptureStep.Review(CaptureRole.FRONT), updated.step)
        assertEquals(crop, updated.front?.crop)
    }

    @Test
    fun impossibleProfileConfirmationBeforeFrontIsRejected() {
        val profileOnly = CaptureUiState(
            step = CaptureStep.Review(CaptureRole.PROFILE),
            profile = CaptureMedia(
                role = CaptureRole.PROFILE,
                source = CaptureSource.GALLERY,
                uri = "content://profile",
            ),
        )

        val result = reduce(
            profileOnly,
            CaptureAction.ConfirmReview(CaptureRole.PROFILE),
        )

        assertEquals(profileOnly, result)
    }

    @Test
    fun readyPayloadIncludesProfileSideAndBothConfirmedViews() {
        val ready = readyState(profileSide = ProfileSide.LEFT)

        val payload = ready.readyPayloadOrNull()

        assertNotNull(payload)
        assertEquals(ProfileSide.LEFT, payload?.profileSide)
        assertEquals(CaptureRole.FRONT, payload?.front?.role)
        assertEquals(CaptureRole.PROFILE, payload?.profile?.role)
    }

    private fun frontReviewState(): CaptureUiState =
        CaptureUiState(
            step = CaptureStep.Review(CaptureRole.FRONT),
            preferredSource = CaptureSource.CAMERA,
            front = CaptureMedia(
                role = CaptureRole.FRONT,
                source = CaptureSource.CAMERA,
                uri = "file://front.jpg",
            ),
        )

    private fun readyState(
        profileSide: ProfileSide = ProfileSide.RIGHT,
    ): CaptureUiState {
        var state = frontReviewState()
        state = reduce(state, CaptureAction.ConfirmReview(CaptureRole.FRONT))
        state = reduce(state, CaptureAction.ContinueTutorial(CaptureRole.PROFILE))
        state = reduce(
            state,
            CaptureAction.MediaSelected(
                CaptureRole.PROFILE,
                CaptureSource.CAMERA,
                "file://profile.jpg",
            ),
        )
        state = reduce(state, CaptureAction.ConfirmReview(CaptureRole.PROFILE))
        return state.copy(profileSide = profileSide)
    }

    private fun reduce(
        state: CaptureUiState,
        action: CaptureAction,
    ): CaptureUiState = CaptureReducer.reduce(state, action)
}
