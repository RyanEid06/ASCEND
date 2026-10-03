package app.ascend.mobile.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ascend.mobile.core.data.CaptureCrop
import app.ascend.mobile.core.data.CaptureOrigin
import app.ascend.mobile.core.data.CaptureRejected
import app.ascend.mobile.core.data.ViewValidation
import app.ascend.mobile.core.model.CaptureView
import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.core.model.ReferenceModel
import app.ascend.mobile.core.model.RetakeReason
import app.ascend.mobile.storage.LocalScanStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val scanStore: LocalScanStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<CaptureSaveState>(CaptureSaveState.Idle)
    val saveState: StateFlow<CaptureSaveState> = _saveState.asStateFlow()

    private val _effects = MutableSharedFlow<CaptureEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<CaptureEffect> = _effects.asSharedFlow()

    private var countdownJob: Job? = null

    fun selectReferenceModel(model: ReferenceModel) {
        dispatch(CaptureAction.SelectReferenceModel(model))
    }

    fun selectPreferredSource(source: CaptureSource) {
        dispatch(CaptureAction.SelectPreferredSource(source))
    }

    fun continueTutorial(role: CaptureRole) {
        dispatch(CaptureAction.ContinueTutorial(role))
    }

    fun openCamera(role: CaptureRole) {
        dispatch(CaptureAction.OpenCamera(role))
    }

    fun mediaSelected(
        role: CaptureRole,
        source: CaptureSource,
        uri: String,
    ) {
        cancelCountdown()
        dispatch(CaptureAction.MediaSelected(role, source, uri))
    }

    fun galleryCancelled(role: CaptureRole) {
        dispatch(CaptureAction.GalleryCancelled(role))
    }

    fun confirmReview(role: CaptureRole) {
        dispatch(CaptureAction.ConfirmReview(role))
    }

    fun retake(role: CaptureRole) {
        cancelCountdown()
        dispatch(CaptureAction.Retake(role))
    }

    fun updateCrop(
        role: CaptureRole,
        crop: CropTransform,
    ) {
        dispatch(CaptureAction.UpdateCrop(role, crop))
    }

    fun selectProfileSide(side: ProfileSide) {
        dispatch(CaptureAction.SelectProfileSide(side))
    }

    fun selectTimer(option: CaptureTimerOption) {
        cancelCountdown()
        dispatch(CaptureAction.SelectTimer(option))
    }

    fun setGuidance(guidance: CaptureGuidanceMessage?) {
        dispatch(CaptureAction.SetGuidance(guidance))
    }

    fun setError(message: String?) {
        dispatch(CaptureAction.SetError(message))
    }

    fun saveReady(payload: CaptureReadyPayload) {
        if (_saveState.value == CaptureSaveState.Saving) return

        viewModelScope.launch {
            _saveState.value = CaptureSaveState.Saving
            var scanId: String? = null
            var activeRole = CaptureRole.FRONT
            try {
                val created = scanStore.createGuest(payload.referenceModel)
                scanId = created.session.id

                activeRole = CaptureRole.FRONT
                var stored = scanStore.persistGuestCapture(
                    scanId = scanId,
                    view = CaptureView.FRONT,
                    uri = payload.front.uri,
                    crop = payload.front.crop.toStorageCrop(),
                    origin = payload.front.source.toStorageOrigin(),
                    profileSide = null,
                )
                val front = stored.views.single { it.view == CaptureView.FRONT }
                if (front.validation == ViewValidation.REJECTED) {
                    rejectPersistedCapture(scanId, CaptureRole.FRONT, payload.front, front.reasons)
                    return@launch
                }

                activeRole = CaptureRole.PROFILE
                stored = scanStore.persistGuestCapture(
                    scanId = scanId,
                    view = CaptureView.PROFILE,
                    uri = payload.profile.uri,
                    crop = payload.profile.crop.toStorageCrop(),
                    origin = payload.profile.source.toStorageOrigin(),
                    profileSide = payload.profileSide,
                )
                val profile = stored.views.single { it.view == CaptureView.PROFILE }
                if (profile.validation == ViewValidation.REJECTED) {
                    rejectPersistedCapture(scanId, CaptureRole.PROFILE, payload.profile, profile.reasons)
                    return@launch
                }

                scanStore.deleteOwnedCameraSource(payload.front.uri, payload.front.source.toStorageOrigin())
                scanStore.deleteOwnedCameraSource(payload.profile.uri, payload.profile.source.toStorageOrigin())

                _saveState.value = CaptureSaveState.Saved(
                    scanId = scanId,
                    validationPending = stored.views.any { it.validation == ViewValidation.PENDING_HOOKS },
                )
            } catch (failure: CaptureRejected) {
                deleteFailedScan(scanId)
                val media = if (activeRole == CaptureRole.FRONT) payload.front else payload.profile
                cleanupRejectedCamera(media)
                dispatch(CaptureAction.Retake(activeRole))
                dispatch(CaptureAction.SetError(retakeMessage(failure.reasons)))
                _saveState.value = CaptureSaveState.Idle
            } catch (_: Throwable) {
                deleteFailedScan(scanId)
                dispatch(CaptureAction.SetError("Could not save this scan securely. Your selected photos are still available; try again."))
                _saveState.value = CaptureSaveState.Idle
            }
        }
    }

    private suspend fun rejectPersistedCapture(
        scanId: String,
        role: CaptureRole,
        media: CaptureMedia,
        reasons: Set<RetakeReason>,
    ) {
        deleteFailedScan(scanId)
        cleanupRejectedCamera(media)
        dispatch(CaptureAction.Retake(role))
        dispatch(CaptureAction.SetError(retakeMessage(reasons)))
        _saveState.value = CaptureSaveState.Idle
    }

    private suspend fun deleteFailedScan(scanId: String?) {
        if (scanId == null) return
        try {
            scanStore.deleteGuestScan(scanId)
        } catch (_: Throwable) {
            // Recovery cleanup will finish any interrupted deletion on the next repository open/recover.
        }
    }

    private suspend fun cleanupRejectedCamera(media: CaptureMedia) {
        try {
            scanStore.deleteOwnedCameraSource(media.uri, media.source.toStorageOrigin())
        } catch (_: Throwable) {
            // Cache cleanup is best effort here; Delete All/recovery also clears abandoned acquisition files.
        }
    }

    fun requestCapture(role: CaptureRole) {
        if (_uiState.value.step != CaptureStep.Camera(role)) return

        countdownJob?.cancel()
        val seconds = _uiState.value.timerOption.seconds
        if (seconds == 0) {
            _effects.tryEmit(CaptureEffect.TriggerShutter(role))
            return
        }

        countdownJob = viewModelScope.launch {
            dispatch(CaptureAction.CountdownStarted(role, seconds))
            var remaining = seconds
            while (remaining > 1) {
                delay(1_000)
                remaining -= 1
                dispatch(CaptureAction.CountdownTick(role, remaining))
            }
            delay(1_000)
            dispatch(CaptureAction.CountdownFinished(role))
            _effects.emit(CaptureEffect.TriggerShutter(role))
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        dispatch(CaptureAction.CountdownCancelled)
    }

    /**
     * Returns false only when the capture route itself should be popped.
     */
    fun onBack(): Boolean {
        if (_uiState.value.step == CaptureStep.SourceSelection) return false
        cancelCountdown()
        dispatch(CaptureAction.Back)
        return true
    }

    private fun dispatch(action: CaptureAction) {
        _uiState.update { current -> CaptureReducer.reduce(current, action) }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}

private fun CropTransform.toStorageCrop() = CaptureCrop(
    scale = scale,
    offsetXFraction = offsetXFraction,
    offsetYFraction = offsetYFraction,
    viewportAspectRatio = viewportAspectRatio,
)

private fun CaptureSource.toStorageOrigin() = when (this) {
    CaptureSource.CAMERA -> CaptureOrigin.CAMERA
    CaptureSource.GALLERY -> CaptureOrigin.GALLERY
}

private fun retakeMessage(reasons: Set<RetakeReason>): String {
    if (reasons.isEmpty()) return "This photo needs to be retaken."
    return when {
        RetakeReason.INVALID_IMAGE in reasons -> "This image could not be processed safely. Choose or capture another photo."
        RetakeReason.INVALID_CROP in reasons -> "The crop moved outside the image. Recenter the photo and try again."
        RetakeReason.INSUFFICIENT_RESOLUTION in reasons -> "This photo is too small. Use a higher-resolution photo."
        RetakeReason.TOO_BLURRY in reasons -> "This photo is too blurry. Hold still and retake it."
        RetakeReason.TOO_DARK in reasons || RetakeReason.TOO_BRIGHT in reasons -> "Lighting is not usable. Retake the photo in more even lighting."
        RetakeReason.NO_FACE in reasons -> "No face was detected. Retake with one face clearly visible."
        RetakeReason.MULTIPLE_FACES in reasons -> "More than one face was detected. Retake with only one person visible."
        RetakeReason.OFF_CENTER in reasons -> "Center your face and retake the photo."
        RetakeReason.INVALID_POSE in reasons -> "Your head position does not match this view. Retake the photo."
        else -> "This photo needs to be retaken before it can be saved."
    }
}
