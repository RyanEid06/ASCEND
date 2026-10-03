package app.ascend.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanLifecycleTest {
    private fun session(state: ScanState = ScanState.FRONT_PENDING) = ScanSession(
        "scan", ScanOwner.Guest, ReferenceModel.MALE, state, 0, 1,
        profileSide = ProfileSide.LEFT,
    )

    @Test fun completeScanRecordsCompletionTime() {
        var scan = session()
        repeat(9) { scan = ScanLifecycle.advance(scan, scan.updatedAtEpochMillis + 1) }
        assertEquals(ScanState.COMPLETE, scan.state)
        assertEquals(10L, scan.completedAtEpochMillis)
    }

    @Test fun frontRetakePreservesProfileSide() {
        val scan = ScanLifecycle.retake(session(ScanState.PROFILE_VALID), CaptureView.FRONT, 2)
        assertEquals(ScanState.FRONT_PENDING, scan.state)
        assertEquals(ProfileSide.LEFT, scan.profileSide)
    }

    @Test fun profileRetakeClearsOldSide() {
        val scan = ScanLifecycle.retake(session(ScanState.PROFILE_VALID), CaptureView.PROFILE, 2)
        assertEquals(ScanState.PROFILE_PENDING, scan.state)
        assertNull(scan.profileSide)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotValidateProfileWithoutSide() {
        ScanLifecycle.advance(session(ScanState.PROFILE_CAPTURED).copy(profileSide = null), 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimeGoingBackwards() { ScanLifecycle.advance(session(), 0) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotAdvanceRecoverableFailure() { ScanLifecycle.advance(session(ScanState.FAILED_RECOVERABLE), 2) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotSkipFrontUsingProfileRetake() {
        ScanLifecycle.retake(session(), CaptureView.PROFILE, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotRetakeCompletedHistory() {
        ScanLifecycle.retake(session().copy(state = ScanState.COMPLETE, completedAtEpochMillis = 1), CaptureView.FRONT, 2)
    }
}
