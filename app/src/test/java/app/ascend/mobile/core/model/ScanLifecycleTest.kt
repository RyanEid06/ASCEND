package app.ascend.mobile.core.model

import org.junit.Assert.assertEquals
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

    @Test(expected = IllegalArgumentException::class)
    fun cannotValidateProfileWithoutSide() {
        ScanLifecycle.advance(session(ScanState.PROFILE_CAPTURED).copy(profileSide = null), 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimeGoingBackwards() { ScanLifecycle.advance(session(), 0) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotAdvanceRecoverableFailure() { ScanLifecycle.advance(session(ScanState.FAILED_RECOVERABLE), 2) }

    @Test(expected = IllegalArgumentException::class)
    fun cannotAdvanceCompletedHistory() {
        ScanLifecycle.advance(session().copy(state = ScanState.COMPLETE, completedAtEpochMillis = 1), 2)
    }
}
