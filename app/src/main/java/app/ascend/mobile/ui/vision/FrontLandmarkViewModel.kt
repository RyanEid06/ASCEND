package app.ascend.mobile.ui.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ascend.mobile.core.vision.*
import app.ascend.mobile.storage.LocalScanStore
import app.ascend.mobile.vision.MediaPipeFrontLandmarker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface FrontPreviewState {
    data object Loading : FrontPreviewState
    data class Ready(val bitmap: Bitmap, val snapshot: FrontLandmarkSnapshot) : FrontPreviewState
    data class Failed(val reason: FrontFailure?) : FrontPreviewState
}

@HiltViewModel
class FrontLandmarkViewModel @Inject constructor(
    private val store: LocalScanStore,
    private val provider: MediaPipeFrontLandmarker,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FrontPreviewState>(FrontPreviewState.Loading)
    val state: StateFlow<FrontPreviewState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun load(scanId: String) {
        if (job?.isActive == true) return
        releaseBitmap()
        mutableState.value = FrontPreviewState.Loading
        job = viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                val bytes = store.readGuestFrontPhoto(scanId)
                val hash: String
                try {
                    hash = frontImageSha256(bytes)
                    withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outWidth.toLong() * bounds.outHeight <= 16_777_216)
                        bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }))
                    }
                } finally { bytes.fill(0) }
                val image = requireNotNull(bitmap)
                val cached = store.readGuestFrontLandmarks(scanId)?.takeIf {
                    it.sourceImageSha256 == hash && it.modelVersion == FRONT_MODEL_VERSION &&
                        it.providerVersion == FRONT_PROVIDER_VERSION && it.pipelineVersion == FRONT_PIPELINE_VERSION &&
                        it.policyVersion == FRONT_POLICY_VERSION
                }
                val result = cached?.let { FrontExtraction.Detected(it) } ?: provider.extract(image, hash)
                ensureActive()
                when (result) {
                    is FrontExtraction.Failed -> mutableState.value = FrontPreviewState.Failed(result.reason)
                    is FrontExtraction.Detected -> {
                        // Atomic current-photo check rejects replacement/deletion while inference ran.
                        if (cached == null) store.saveGuestFrontLandmarks(scanId, result.snapshot)
                        ensureActive()
                        mutableState.value = FrontPreviewState.Ready(image, result.snapshot)
                        bitmap = null // Screen owns the displayed bitmap until release().
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = FrontPreviewState.Failed(null) }
            finally { bitmap?.recycle() }
        }
    }

    private fun releaseBitmap() {
        (mutableState.value as? FrontPreviewState.Ready)?.bitmap?.recycle()
        mutableState.value = FrontPreviewState.Loading
    }
    fun release() { job?.cancel(); releaseBitmap() }
    override fun onCleared() { release(); super.onCleared() }
}
