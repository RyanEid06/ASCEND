package app.ascend.mobile.ui.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ascend.mobile.storage.LocalScanStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanStore: LocalScanStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshLocalScans()
    }

    fun refreshLocalScans() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(localStorageLoading = true, localStorageError = null)
            try {
                val scans = scanStore.recoverGuest()
                _uiState.value = _uiState.value.copy(
                    savedLocalScanCount = scans.size,
                    frontPreviewScanIds = scans.filter { scan -> scan.views.any {
                        it.view == app.ascend.mobile.core.model.CaptureView.FRONT &&
                            it.validation != app.ascend.mobile.core.data.ViewValidation.REJECTED
                    } }.map { it.session.id },
                    localStorageLoading = false,
                    profileScanIds = scans.filter { scan -> scan.views.any {
                        it.view == app.ascend.mobile.core.model.CaptureView.PROFILE
                    } }.map { it.session.id },
                    localStorageError = null,
                )
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    localStorageLoading = false,
                    localStorageError = "Local scan storage needs attention.",
                )
            }
        }
    }

    fun onToggleFoundationDetails() {
        _uiState.value = _uiState.value.copy(
            detailsExpanded = !_uiState.value.detailsExpanded,
        )
    }
}
