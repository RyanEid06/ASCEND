package app.ascend.mobile.storage

import android.content.Context
import android.net.Uri
import app.ascend.mobile.core.data.CaptureCrop
import app.ascend.mobile.core.data.CaptureOrigin
import app.ascend.mobile.core.data.LocalScan
import app.ascend.mobile.core.model.CaptureView
import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.core.model.ReferenceModel
import app.ascend.mobile.core.model.ScanOwner
import app.ascend.mobile.core.vision.FrontLandmarkSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Phase-2 application-scoped gateway for encrypted guest scans.
 *
 * Capture admission remains pending until a reviewed CV quality/confidence policy is integrated.
 * WP07 previews the stored front mesh separately; model admission settings do not replace
 * validated biometric thresholds or the later, separate profile validation path.
 */
@Singleton
class LocalScanStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val openMutex = Mutex()
    @Volatile private var opened: EncryptedScanRepository? = null

    private suspend fun repository(): EncryptedScanRepository {
        opened?.let { return it }
        return openMutex.withLock {
            opened ?: EncryptedScanRepository.open(
                context = context,
                imagePolicy = IMAGE_POLICY,
                qualityPolicy = PRE_CV_QUALITY_POLICY,
            ).also { opened = it }
        }
    }

    suspend fun createGuest(
        referenceModel: ReferenceModel,
        atEpochMillis: Long = System.currentTimeMillis(),
    ): LocalScan = repository().create(ScanOwner.Guest, referenceModel, atEpochMillis)

    suspend fun persistGuestCapture(
        scanId: String,
        view: CaptureView,
        uri: String,
        crop: CaptureCrop,
        origin: CaptureOrigin,
        profileSide: ProfileSide?,
        atEpochMillis: Long = System.currentTimeMillis(),
    ): LocalScan {
        val repo = repository()
        return LocalCaptureImporter(context, repo, IMAGE_POLICY).persistFromUri(
            owner = ScanOwner.Guest,
            scanId = scanId,
            view = view,
            uri = Uri.parse(uri),
            crop = crop,
            origin = origin,
            profileSide = profileSide,
            atEpochMillis = atEpochMillis,
            cleanupCameraSource = false,
        )
    }

    suspend fun recoverGuest(
        atEpochMillis: Long = System.currentTimeMillis(),
    ): List<LocalScan> = repository().recover(ScanOwner.Guest, atEpochMillis)

    suspend fun readGuestFrontPhoto(scanId: String): ByteArray = repository().readPhoto(ScanOwner.Guest, scanId, CaptureView.FRONT)

    suspend fun readGuestFrontLandmarks(scanId: String): FrontLandmarkSnapshot? = repository().readFrontLandmarks(ScanOwner.Guest, scanId)

    suspend fun saveGuestFrontLandmarks(scanId: String, snapshot: FrontLandmarkSnapshot) {
        repository().saveFrontLandmarks(ScanOwner.Guest, scanId, snapshot)
    }

    internal suspend fun readGuestProfilePhoto(scanId: String) = repository().readProfilePhoto(ScanOwner.Guest, scanId)
    internal suspend fun getGuestScan(scanId: String) = repository().get(ScanOwner.Guest, scanId)
    internal suspend fun readGuestProfileAssist(scanId: String) = repository().readProfileAssist(ScanOwner.Guest, scanId)
    internal suspend fun beginGuestProfileAssist(scanId: String, imageRevision: String, expectedRevision: Long?,
        side: ProfileSide, facing: app.ascend.mobile.core.profile.ProfileFacing) =
        repository().beginProfileAssist(ScanOwner.Guest, scanId, imageRevision, expectedRevision, side, facing, System.currentTimeMillis())
    internal suspend fun confirmGuestProfilePoint(scanId: String, imageRevision: String, expectedRevision: Long,
        id: app.ascend.mobile.core.geometry.LandmarkId, point: app.ascend.mobile.core.geometry.Point2) =
        repository().confirmProfilePoint(ScanOwner.Guest, scanId, imageRevision, expectedRevision, id, point, System.currentTimeMillis())

    suspend fun deleteGuestScan(scanId: String) {
        repository().deleteScan(ScanOwner.Guest, scanId)
    }

    suspend fun deleteOwnedCameraSource(uriText: String, origin: CaptureOrigin) = withContext(Dispatchers.IO) {
        if (origin != CaptureOrigin.CAMERA) return@withContext
        val uri = Uri.parse(uriText)
        if (uri.scheme != "file") return@withContext
        val file = File(requireNotNull(uri.path)).canonicalFile
        val expectedParent = File(context.cacheDir, "ascend_capture").canonicalFile
        require(file.parentFile == expectedParent)
        check(file.delete() || !file.exists()) { "Temporary capture cleanup requires retry" }
    }

    companion object {
        val IMAGE_POLICY = ImageStoragePolicy(
            version = "p2-storage-v1",
            maxEncodedBytes = 24_000_000,
            maxSourceDimension = 12_000,
            maxSourcePixels = 80_000_000,
            maxDecodedPixels = 16_777_216,
            outputLongEdge = 2_048,
            qualityGridLongEdge = 256,
        )

        val PRE_CV_QUALITY_POLICY = app.ascend.mobile.core.data.PhotoQualityPolicy(
            version = "p2-pre-cv-v1",
            minWidth = 3,
            minHeight = 3,
            minMeanLuma = 0.0,
            maxMeanLuma = 1.0,
            minLaplacianVariance = 0.0,
            maxCenterOffset = 0.5,
            maxAbsPitch = 90.0,
            maxAbsRoll = 180.0,
            frontMaxAbsYaw = 90.0,
            profileMinAbsYaw = 0.0,
            profileMaxAbsYaw = 180.0,
        )
    }
}
