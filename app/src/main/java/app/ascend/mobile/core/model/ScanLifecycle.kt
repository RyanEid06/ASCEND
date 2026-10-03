package app.ascend.mobile.core.model

/** Pure lifecycle rules; callers persist the returned session atomically with asset changes. */
object ScanLifecycle {
    private val nextStates = mapOf(
        ScanState.FRONT_PENDING to ScanState.FRONT_CAPTURED,
        ScanState.FRONT_CAPTURED to ScanState.FRONT_VALID,
        ScanState.FRONT_VALID to ScanState.PROFILE_PENDING,
        ScanState.PROFILE_PENDING to ScanState.PROFILE_CAPTURED,
        ScanState.PROFILE_CAPTURED to ScanState.PROFILE_VALID,
        ScanState.PROFILE_VALID to ScanState.LANDMARKING,
        ScanState.LANDMARKING to ScanState.MEASURING,
        ScanState.MEASURING to ScanState.SCORING,
        ScanState.SCORING to ScanState.COMPLETE,
    )

    fun advance(session: ScanSession, atEpochMillis: Long): ScanSession {
        require(atEpochMillis >= session.updatedAtEpochMillis)
        val next = requireNotNull(nextStates[session.state]) { "Scan cannot advance from ${session.state}" }
        if (next == ScanState.PROFILE_VALID) require(session.profileSide != null)
        return session.copy(
            state = next,
            updatedAtEpochMillis = atEpochMillis,
            completedAtEpochMillis = if (next == ScanState.COMPLETE) atEpochMillis else null,
        )
    }

    /** A rejected front keeps the profile asset; a rejected profile keeps the front asset.
     * Repositories must invalidate derived results when applying either retake.
     */
    fun retake(session: ScanSession, view: CaptureView, atEpochMillis: Long): ScanSession {
        require(atEpochMillis >= session.updatedAtEpochMillis)
        require(session.state != ScanState.COMPLETE) { "Completed history is immutable; create a new scan" }
        if (view == CaptureView.PROFILE) require(session.state in setOf(
            ScanState.PROFILE_PENDING, ScanState.PROFILE_CAPTURED, ScanState.PROFILE_VALID,
            ScanState.LANDMARKING, ScanState.MEASURING, ScanState.SCORING,
        )) { "Profile retake requires a validated front" }
        return session.copy(
            state = if (view == CaptureView.FRONT) ScanState.FRONT_PENDING else ScanState.PROFILE_PENDING,
            updatedAtEpochMillis = atEpochMillis,
            completedAtEpochMillis = null,
            profileSide = if (view == CaptureView.PROFILE) null else session.profileSide,
        )
    }
}

enum class CaptureView { FRONT, PROFILE }

/** Stable reasons for capture guidance, without embedding face data in messages. */
enum class RetakeReason {
    TOO_BLURRY, TOO_DARK, TOO_BRIGHT, INSUFFICIENT_RESOLUTION,
    NO_FACE, MULTIPLE_FACES, OFF_CENTER, INVALID_POSE, OCCLUDED,
    INVALID_IMAGE, INVALID_CROP, ASSET_UNAVAILABLE,
}
