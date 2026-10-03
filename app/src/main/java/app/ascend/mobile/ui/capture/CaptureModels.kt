package app.ascend.mobile.ui.capture

import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.core.model.ReferenceModel

enum class CaptureRole {
    FRONT,
    PROFILE,
}

enum class CaptureSource {
    CAMERA,
    GALLERY,
}

enum class CaptureTimerOption(val seconds: Int) {
    OFF(0),
    THREE_SECONDS(3),
    FIVE_SECONDS(5),
}

sealed interface CaptureStep {
    data object SourceSelection : CaptureStep
    data class Tutorial(val role: CaptureRole) : CaptureStep
    data class Acquisition(val role: CaptureRole) : CaptureStep
    data class Camera(val role: CaptureRole) : CaptureStep
    data class Review(val role: CaptureRole) : CaptureStep
    data object Ready : CaptureStep
}

data class CropTransform(
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val viewportAspectRatio: Float = 3f / 4f,
) {
    init {
        require(scale in 1f..4f)
        require(offsetXFraction in -1f..1f)
        require(offsetYFraction in -1f..1f)
        require(viewportAspectRatio > 0f)
    }
}

data class CaptureMedia(
    val role: CaptureRole,
    val source: CaptureSource,
    val uri: String,
    val crop: CropTransform = CropTransform(),
    val confirmed: Boolean = false,
) {
    init {
        require(uri.isNotBlank())
    }
}

enum class CaptureGuidanceMessage(val displayText: String) {
    MOVE_FARTHER("Move farther away"),
    MOVE_CLOSER("Move closer"),
    CENTER_FACE("Center your face"),
    HOLD_HEAD_LEVEL("Hold head level"),
    FACE_FORWARD("Face forward"),
    TURN_TO_SIDE("Turn to the side"),
    IMPROVE_LIGHTING("Improve lighting"),
    HOLD_STILL("Hold still"),
}

data class CaptureReadyPayload(
    val front: CaptureMedia,
    val profile: CaptureMedia,
    val profileSide: ProfileSide,
    val referenceModel: ReferenceModel,
) {
    init {
        require(front.role == CaptureRole.FRONT && front.confirmed)
        require(profile.role == CaptureRole.PROFILE && profile.confirmed)
    }
}

data class CaptureUiState(
    val step: CaptureStep = CaptureStep.SourceSelection,
    val referenceModel: ReferenceModel? = null,
    val preferredSource: CaptureSource? = null,
    val profileSide: ProfileSide = ProfileSide.RIGHT,
    val front: CaptureMedia? = null,
    val profile: CaptureMedia? = null,
    val timerOption: CaptureTimerOption = CaptureTimerOption.OFF,
    val countdownSeconds: Int? = null,
    val guidance: CaptureGuidanceMessage? = null,
    val errorMessage: String? = null,
) {
    fun mediaFor(role: CaptureRole): CaptureMedia? =
        when (role) {
            CaptureRole.FRONT -> front
            CaptureRole.PROFILE -> profile
        }

    fun readyPayloadOrNull(): CaptureReadyPayload? {
        val selectedModel = referenceModel ?: return null
        val confirmedFront = front?.takeIf { it.confirmed } ?: return null
        val confirmedProfile = profile?.takeIf { it.confirmed } ?: return null
        return CaptureReadyPayload(
            front = confirmedFront,
            profile = confirmedProfile,
            profileSide = profileSide,
            referenceModel = selectedModel,
        )
    }
}

sealed interface CaptureAction {
    data class SelectReferenceModel(val model: ReferenceModel) : CaptureAction
    data class SelectPreferredSource(val source: CaptureSource) : CaptureAction
    data class ContinueTutorial(val role: CaptureRole) : CaptureAction
    data class OpenCamera(val role: CaptureRole) : CaptureAction

    data class MediaSelected(
        val role: CaptureRole,
        val source: CaptureSource,
        val uri: String,
    ) : CaptureAction

    data class GalleryCancelled(val role: CaptureRole) : CaptureAction
    data class ConfirmReview(val role: CaptureRole) : CaptureAction
    data class Retake(val role: CaptureRole) : CaptureAction

    data class UpdateCrop(
        val role: CaptureRole,
        val crop: CropTransform,
    ) : CaptureAction

    data class SelectProfileSide(val side: ProfileSide) : CaptureAction
    data class SelectTimer(val option: CaptureTimerOption) : CaptureAction
    data class SetGuidance(val guidance: CaptureGuidanceMessage?) : CaptureAction
    data class SetError(val message: String?) : CaptureAction

    data class CountdownStarted(
        val role: CaptureRole,
        val seconds: Int,
    ) : CaptureAction

    data class CountdownTick(
        val role: CaptureRole,
        val seconds: Int,
    ) : CaptureAction

    data class CountdownFinished(val role: CaptureRole) : CaptureAction
    data object CountdownCancelled : CaptureAction
    data object Back : CaptureAction
}

sealed interface CaptureEffect {
    data class TriggerShutter(val role: CaptureRole) : CaptureEffect
}


sealed interface CaptureSaveState {
    data object Idle : CaptureSaveState
    data object Saving : CaptureSaveState
    data class Saved(
        val scanId: String,
        val validationPending: Boolean,
    ) : CaptureSaveState
}
