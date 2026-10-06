package app.ascend.mobile.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ascend.mobile.core.data.*
import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.*
import app.ascend.mobile.core.profile.*
import app.ascend.mobile.storage.LocalScanStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal sealed interface ProfileAssistState {
    data object Loading : ProfileAssistState
    data class Failed(val scanId: String, val message: String, val canReplace: Boolean) : ProfileAssistState
    data class Ready(val scanId: String, val imageRevision: String, val bitmap: Bitmap,
        val session: ProfileAssistSession?, val display: ProfileAssistSession? = session,
        val readOnly: Boolean = false, val activeIndex: Int = 0, val busy: Boolean = false,
        val message: String? = null) : ProfileAssistState {
        val activePoint get() = display?.requiredPoints?.getOrNull(activeIndex)
        val previewing get() = display !== session
    }
}

@HiltViewModel
internal class ProfileAssistViewModel @Inject constructor(
    private val store: LocalScanStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProfileAssistState>(ProfileAssistState.Loading)
    val state = mutableState.asStateFlow()
    private var work: Job? = null

    fun load(scanId: String) {
        if (work?.isActive == true) return
        if ((mutableState.value as? ProfileAssistState.Ready)?.scanId == scanId) return
        mutableState.value = ProfileAssistState.Loading
        work = viewModelScope.launch {
            var bitmap: Bitmap? = null
            try {
                val photo = store.readGuestProfilePhoto(scanId)
                try {
                    withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size, bounds)
                        require(bounds.outWidth == photo.resolution.width && bounds.outHeight == photo.resolution.height)
                        require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outWidth.toLong() * bounds.outHeight <= 16_777_216)
                        bitmap = requireNotNull(BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }))
                    }
                } finally { photo.bytes.fill(0) }
                val session = store.readGuestProfileAssist(scanId)
                require(session == null || session.revision.input.imageRevision == photo.imageRevision)
                ensureActive()
                val index = savedState.get<Int>("profileActiveIndex") ?: session?.requiredPoints?.indexOfFirst {
                    session.revision.input.points[it]?.confirmed != true }?.coerceAtLeast(0) ?: 0
                mutableState.value = ProfileAssistState.Ready(scanId, photo.imageRevision, requireNotNull(bitmap), session,
                    readOnly = photo.readOnly, activeIndex = index.coerceIn(0, 11))
                bitmap = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val editable = try { store.getGuestScan(scanId)?.session?.state?.let { it != ScanState.COMPLETE } == true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
                mutableState.value = ProfileAssistState.Failed(scanId, "This profile photo is unavailable.", editable)
            }
            finally { bitmap?.recycle() }
        }
    }

    fun chooseSide(side: ProfileSide, facing: ProfileFacing) {
        val state = mutableState.value as? ProfileAssistState.Ready ?: return
        if (state.busy || state.readOnly) return
        mutate(state) {
            store.beginGuestProfileAssist(state.scanId, state.imageRevision, state.session?.revisionToken, side, facing)
        }
    }

    fun selectPoint(index: Int) {
        val state = mutableState.value as? ProfileAssistState.Ready ?: return
        val session = state.session ?: return
        if (state.busy || state.readOnly) return
        val next = index.coerceIn(0, session.requiredPoints.lastIndex)
        savedState["profileActiveIndex"] = next
        mutableState.value = state.copy(activeIndex = next, display = session, message = null)
    }

    fun preview(target: Point2) {
        val state = mutableState.value as? ProfileAssistState.Ready ?: return
        val original = state.session ?: return
        if (state.busy || state.readOnly) return
        val id = state.activePoint ?: return
        try {
            val next = original.confirm(id, target, original.revisionToken, System.currentTimeMillis())
            mutableState.value = state.copy(display = next, message = null)
        } catch (_: IllegalArgumentException) {
            mutableState.value = state.copy(message = "Keep the point inside the highlighted area and close to its original position.")
        }
    }

    fun cancelPreview() {
        val state = mutableState.value as? ProfileAssistState.Ready ?: return
        mutableState.value = state.copy(display = state.session, message = null)
    }

    fun confirmPoint() {
        val state = mutableState.value as? ProfileAssistState.Ready ?: return
        val source = state.session ?: return
        if (state.busy || state.readOnly) return
        if (state.message != null) { mutableState.value = state.copy(display = source, message = null); return }
        val id = state.activePoint ?: return
        val target = state.display?.revision?.input?.points?.get(id)?.point ?: source.policy.zones.getValue(id).missingPointAnchor
        mutate(state, advance = true) {
            store.confirmGuestProfilePoint(state.scanId, state.imageRevision, source.revisionToken, id, target)
        }
    }

    private fun mutate(state: ProfileAssistState.Ready, advance: Boolean = false, action: suspend () -> ProfileAssistSession) {
        mutableState.value = state.copy(busy = true, message = null)
        work = viewModelScope.launch {
            try {
                val next = action()
                ensureActive()
                val index = if (advance) (state.activeIndex + 1).coerceAtMost(next.requiredPoints.lastIndex) else 0
                savedState["profileActiveIndex"] = index
                mutableState.value = state.copy(session = next, display = next, activeIndex = index, busy = false, message = null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Re-read after any rejected/stale write; never keep an optimistic correction visible.
                val id = state.scanId
                release(); load(id)
            }
        }
    }

    fun replacePhoto(uri: String) {
        val state = mutableState.value
        val scanId = when (state) {
            is ProfileAssistState.Ready -> if (!state.busy && !state.readOnly) state.scanId else return
            is ProfileAssistState.Failed -> if (state.canReplace && work?.isActive != true) state.scanId else return
            ProfileAssistState.Loading -> return
        }
        mutableState.value = if (state is ProfileAssistState.Ready) state.copy(busy = true) else ProfileAssistState.Loading
        work = viewModelScope.launch {
            try {
                store.persistGuestCapture(scanId, CaptureView.PROFILE, uri, CaptureCrop(), CaptureOrigin.GALLERY,
                    (state as? ProfileAssistState.Ready)?.session?.revision?.input?.side ?: ProfileSide.LEFT)
                savedState["profileActiveIndex"] = 0
                release(); load(scanId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value = if (state is ProfileAssistState.Ready)
                    state.copy(busy = false, message = "This photo could not be used. Try another clear side profile.")
                else state
            }
        }
    }

    fun release() {
        work?.cancel(); work = null
        (mutableState.value as? ProfileAssistState.Ready)?.bitmap?.recycle()
        mutableState.value = ProfileAssistState.Loading
    }
    override fun onCleared() { release(); super.onCleared() }
}
