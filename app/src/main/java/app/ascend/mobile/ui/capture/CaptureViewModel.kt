package app.ascend.mobile.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ascend.mobile.core.model.ProfileSide
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

class CaptureViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CaptureEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<CaptureEffect> = _effects.asSharedFlow()

    private var countdownJob: Job? = null

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
