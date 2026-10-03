package app.ascend.mobile.core.data

import app.ascend.mobile.core.model.CaptureView
import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.core.model.ReferenceModel
import app.ascend.mobile.core.model.RetakeReason
import app.ascend.mobile.core.model.ScanOwner
import app.ascend.mobile.core.model.ScanSession
import app.ascend.mobile.core.model.AnalysisOutcome

/** Same viewport-relative pan/zoom semantics as WP05 CropTransform. */
data class CaptureCrop(
    val scale: Float = 1f,
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val viewportAspectRatio: Float = 3f / 4f,
) {
    init {
        require(scale in 1f..4f)
        require(offsetXFraction in -1f..1f && offsetYFraction in -1f..1f)
        require(viewportAspectRatio.isFinite() && viewportAspectRatio in 0.25f..4f)
    }
}

enum class CaptureOrigin { CAMERA, GALLERY }

data class LocalCapture(
    val view: CaptureView,
    val encodedImage: ByteArray,
    val crop: CaptureCrop = CaptureCrop(),
    val origin: CaptureOrigin,
    val profileSide: ProfileSide? = null,
) {
    init { require((view == CaptureView.PROFILE) == (profileSide != null)) }
}

enum class ViewValidation { PENDING_HOOKS, ACCEPTED, REJECTED }

class CaptureRejected(val reasons: Set<RetakeReason>) : IllegalArgumentException("Capture requires retake")

data class LocalView(
    val view: CaptureView,
    val validation: ViewValidation,
    val reasons: Set<RetakeReason>,
    val width: Int,
    val height: Int,
    val policyVersion: String,
)

data class LocalScan(val session: ScanSession, val views: List<LocalView>)

interface LocalScanRepository : AutoCloseable {
    suspend fun create(owner: ScanOwner, model: ReferenceModel, atEpochMillis: Long): LocalScan
    suspend fun get(owner: ScanOwner, scanId: String): LocalScan?
    suspend fun list(owner: ScanOwner): List<LocalScan>
    suspend fun putCapture(owner: ScanOwner, scanId: String, capture: LocalCapture, atEpochMillis: Long): LocalScan
    suspend fun revalidate(owner: ScanOwner, scanId: String, view: CaptureView, atEpochMillis: Long): LocalScan
    suspend fun retake(owner: ScanOwner, scanId: String, view: CaptureView, atEpochMillis: Long): LocalScan
    suspend fun readPhoto(owner: ScanOwner, scanId: String, view: CaptureView): ByteArray
    suspend fun advance(owner: ScanOwner, scanId: String, atEpochMillis: Long): LocalScan
    suspend fun complete(owner: ScanOwner, outcome: AnalysisOutcome.Complete, atEpochMillis: Long): LocalScan
    suspend fun readAnalysis(owner: ScanOwner, scanId: String): AnalysisOutcome.Complete?
    suspend fun deleteScan(owner: ScanOwner, scanId: String)
    /** Explicit local Delete All; does not delete remote/account data or auth state. */
    suspend fun deleteAll()
    suspend fun recover(owner: ScanOwner, atEpochMillis: Long): List<LocalScan>
}
