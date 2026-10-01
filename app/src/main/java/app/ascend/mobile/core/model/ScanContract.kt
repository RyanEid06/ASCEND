package app.ascend.mobile.core.model

/** The user chooses a comparison model; this is never inferred from a face. */
enum class ReferenceModel { MALE, FEMALE }

enum class IntentMode { NOT_SELECTED, SOFTMAX, HARDMAX, BOTH }

data class AgeConfirmation(
    val confirmedThirteenOrOver: Boolean,
    val policyVersion: String,
    val recordedAtEpochMillis: Long,
) {
    init {
        require(policyVersion.isNotBlank())
        require(recordedAtEpochMillis >= 0)
    }
}

sealed interface ScanOwner {
    data object Guest : ScanOwner

    data class Account(val userId: String) : ScanOwner {
        init { require(userId.isNotBlank()) }
    }
}

enum class ProfileSide { LEFT, RIGHT }

/** Persisted states allow an interrupted scan to resume or be discarded safely. */
enum class ScanState {
    FRONT_PENDING,
    FRONT_CAPTURED,
    FRONT_VALID,
    PROFILE_PENDING,
    PROFILE_CAPTURED,
    PROFILE_VALID,
    LANDMARKING,
    MEASURING,
    SCORING,
    COMPLETE,
    FAILED_RECOVERABLE,
}

data class ScanSession(
    val id: String,
    val owner: ScanOwner,
    val referenceModel: ReferenceModel,
    val state: ScanState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val profileSide: ProfileSide? = null,
) {
    init {
        require(id.isNotBlank())
        require(createdAtEpochMillis >= 0)
        require(updatedAtEpochMillis >= createdAtEpochMillis)
        require(completedAtEpochMillis == null || completedAtEpochMillis in createdAtEpochMillis..updatedAtEpochMillis)
        require((state == ScanState.COMPLETE) == (completedAtEpochMillis != null))
    }
}
