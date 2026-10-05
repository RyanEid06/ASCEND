package app.ascend.mobile.ui.foundation

data class HomeUiState(
    val detailsExpanded: Boolean = false,
    val savedLocalScanCount: Int = 0,
    val frontPreviewScanIds: List<String> = emptyList(),
    val localStorageLoading: Boolean = true,
    val localStorageError: String? = null,
)
