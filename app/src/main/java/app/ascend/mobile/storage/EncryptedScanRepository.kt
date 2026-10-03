package app.ascend.mobile.storage

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room3.Room
import app.ascend.mobile.core.data.*
import app.ascend.mobile.core.model.*
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

/** One application-scoped instance. All calls are serialized, including recovery and deletion. */
class EncryptedScanRepository internal constructor(
    private val database: ScanDatabase,
    private val files: PhotoFiles,
    private val imagePolicy: ImageStoragePolicy,
    private val qualityPolicy: PhotoQualityPolicy,
    private val hook: FaceValidationHook,
) : LocalScanRepository {
    private val dao = database.scans()
    private val mutex = Mutex()
    private var closed = false
    private val normalizer = PhotoNormalizer(imagePolicy)

    private suspend fun <T> serialized(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { check(!closed); block() }
    }

    override suspend fun create(owner: ScanOwner, model: ReferenceModel, atEpochMillis: Long): LocalScan = serialized {
        require(atEpochMillis >= 0)
        val row = ScanRow(UUID.randomUUID().toString(), owner.scope(), model.name, ScanState.FRONT_PENDING.name,
            atEpochMillis, atEpochMillis, null, null)
        dao.saveScan(row)
        snapshot(row)
    }

    override suspend fun get(owner: ScanOwner, scanId: String): LocalScan? = serialized {
        dao.get(owner.scope(), scanId)?.let { snapshot(it) }
    }

    override suspend fun list(owner: ScanOwner): List<LocalScan> = serialized {
        dao.list(owner.scope()).map { snapshot(it) }
    }

    override suspend fun putCapture(owner: ScanOwner, scanId: String, capture: LocalCapture, atEpochMillis: Long): LocalScan = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        val photo = try { normalizer.normalize(capture.encodedImage, capture.crop) }
        catch (failure: InvalidPhoto) { throw CaptureRejected(setOf(failure.reason)) }
        catch (_: IllegalArgumentException) { throw CaptureRejected(setOf(RetakeReason.INVALID_IMAGE)) }
        try {
            val reasons = PhotoQuality.evaluate(photo.bitmap.width, photo.bitmap.height, photo.statistics, qualityPolicy)
            val face = FaceQuality.evaluate(capture.view, hook.inspect(capture.view, photo.bitmap), qualityPolicy)
            val allReasons = reasons + face.reasons
            val validation = validation(allReasons, face.pending)
            val assetId = UUID.randomUUID().toString()
            val old = dao.photos(scanId).firstOrNull { it.view == capture.view.name }
            val newPhoto = PhotoRow(scanId, capture.view.name, assetId, photo.bitmap.width, photo.bitmap.height,
                capture.origin.name, listOf(capture.crop.scale, capture.crop.offsetXFraction, capture.crop.offsetYFraction,
                    capture.crop.viewportAspectRatio).joinToString(","), validation.name, allReasons.encode(),
                qualityPolicy.version, imagePolicy.version, photo.statistics.meanLuma, photo.statistics.laplacianVariance,
                capture.profileSide?.name)
            // New immutable asset precedes its DB pointer. Interrupted writes become removable orphans.
            files.write(assetId, photo.png)
            val photos = dao.photos(scanId).filterNot { it.view == newPhoto.view } + newPhoto
            dao.replaceCapture(row.captureState(photos, atEpochMillis), newPhoto)
            // If cleanup fails, startup orphan cleanup retries; never remove the newly committed asset.
            if (old != null) files.delete(old.assetId)
            snapshot(requireNotNull(dao.get(owner.scope(), scanId)))
        } finally { photo.bitmap.recycle(); photo.png.fill(0) }
    }

    override suspend fun revalidate(owner: ScanOwner, scanId: String, view: CaptureView, atEpochMillis: Long): LocalScan = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        val old = requireNotNull(dao.photos(scanId).firstOrNull { it.view == view.name })
        val bytes = files.read(old.assetId)
        try {
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                // Never reinterpret an old blur grid using a new threshold version.
                require(old.imagePolicyVersion == imagePolicy.version && old.qualityPolicyVersion == qualityPolicy.version)
                val reasons = PhotoQuality.evaluate(old.width, old.height, QualityStatistics(old.meanLuma, old.laplacianVariance), qualityPolicy)
                val face = FaceQuality.evaluate(view, hook.inspect(view, bitmap), qualityPolicy)
                val photo = old.copy(validation = validation(reasons + face.reasons, face.pending).name, reasons = (reasons + face.reasons).encode())
                val photos = dao.photos(scanId).filterNot { it.view == view.name } + photo
                dao.replaceCapture(row.captureState(photos, atEpochMillis), photo)
                snapshot(requireNotNull(dao.get(owner.scope(), scanId)))
            } finally { bitmap.recycle() }
        } finally { bytes.fill(0) }
    }

    override suspend fun retake(owner: ScanOwner, scanId: String, view: CaptureView, atEpochMillis: Long): LocalScan = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        val photos = dao.photos(scanId)
        val old = photos.firstOrNull { it.view == view.name }
        // Per-view records are the authority: no aggregate state can discard the opposite view.
        dao.retake(row.captureState(photos.filterNot { it.view == view.name }, atEpochMillis), view.name)
        if (old != null) files.delete(old.assetId)
        snapshot(requireNotNull(dao.get(owner.scope(), scanId)))
    }

    override suspend fun readPhoto(owner: ScanOwner, scanId: String, view: CaptureView): ByteArray = serialized {
        requireNotNull(dao.get(owner.scope(), scanId)) { "Scan unavailable in active owner scope" }
        val photo = requireNotNull(dao.photos(scanId).firstOrNull { it.view == view.name })
        files.read(photo.assetId)
    }

    override suspend fun advance(owner: ScanOwner, scanId: String, atEpochMillis: Long): LocalScan = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        require(ScanState.valueOf(row.state) in setOf(ScanState.PROFILE_VALID, ScanState.LANDMARKING, ScanState.MEASURING, ScanState.SCORING))
        val photos = dao.photos(scanId)
        require(photos.size == 2 && photos.all { it.validation == ViewValidation.ACCEPTED.name })
        photos.forEach { files.read(it.assetId).fill(0) }
        val session = ScanLifecycle.advance(row.session(), atEpochMillis)
        val advanced = row.copy(state = session.state.name, updatedAt = session.updatedAtEpochMillis, completedAt = session.completedAtEpochMillis)
        dao.saveScan(advanced)
        snapshot(advanced)
    }

    override suspend fun deleteScan(owner: ScanOwner, scanId: String) = serialized {
        // Idempotent, including retries after a partially completed delete.
        val row = dao.all().firstOrNull { it.id == scanId && it.owner == owner.scope() }
        if (row != null) {
            dao.saveScan(row.copy(deleting = true))
            finishDelete(row.id)
        }
    }

    override suspend fun deleteAll() = serialized {
        dao.markAllDeleting()
        dao.all().forEach { finishDelete(it.id) }
        files.cleanup(emptySet())
    }

    override suspend fun recover(owner: ScanOwner, atEpochMillis: Long): List<LocalScan> = serialized {
        cleanup()
        dao.list(owner.scope()).forEach { row ->
            require(atEpochMillis >= row.updatedAt)
            val photos = dao.photos(row.id)
            val damaged = photos.map { photo ->
                try { files.read(photo.assetId).fill(0); photo }
                catch (failure: StorageKeyUnavailable) { throw failure }
                catch (_: Exception) { photo.copy(validation = ViewValidation.REJECTED.name, reasons = setOf(RetakeReason.ASSET_UNAVAILABLE).encode()) }
            }
            if (photos != damaged) {
                // Completed history remains unchanged; missing local media is exposed in its view status.
                val updated = if (row.state == ScanState.COMPLETE.name) row else row.captureState(damaged, atEpochMillis)
                damaged.filter { it !in photos }.forEach {
                    if (row.state == ScanState.COMPLETE.name) dao.updatePhotoStatus(updated, it)
                    else dao.replaceCapture(updated, it)
                }
            }
        }
        dao.list(owner.scope()).map { snapshot(it) }
    }

    internal suspend fun cleanup() {
        dao.all().filter { it.deleting }.forEach { finishDelete(it.id) }
        files.cleanup(dao.liveAssetIds().toSet())
    }

    private suspend fun finishDelete(id: String) {
        dao.photos(id).forEach { files.delete(it.assetId) }
        dao.finishDelete(id)
    }

    private suspend fun editable(owner: ScanOwner, id: String, time: Long): ScanRow {
        val row = requireNotNull(dao.get(owner.scope(), id)) { "Scan unavailable in active owner scope" }
        require(row.state != ScanState.COMPLETE.name) { "Completed history is immutable" }
        require(time >= row.updatedAt)
        return row
    }

    private suspend fun snapshot(row: ScanRow) = LocalScan(row.session(), dao.photos(row.id).map {
        LocalView(CaptureView.valueOf(it.view), ViewValidation.valueOf(it.validation), it.reasons.decode(), it.width, it.height, it.qualityPolicyVersion)
    })

    /** Close only after callers have stopped their work; keys are never deleted by close/logout. */
    override fun close() { closed = true; database.close() }

    companion object {
        /** Call once per process from application DI with a reviewed policy and the active CV hooks. */
        suspend fun open(
            context: Context,
            imagePolicy: ImageStoragePolicy,
            qualityPolicy: PhotoQualityPolicy,
            hook: FaceValidationHook = FaceValidationHook { _, _ -> null },
        ): EncryptedScanRepository = withContext(Dispatchers.IO) {
            System.loadLibrary("sqlcipher")
            val root = File(context.noBackupFilesDir, "ascend-local")
            check(root.isDirectory || root.mkdirs())
            val databaseFile = File(root, "scans.db")
            val envelopeFile = File(root, "database-key.enc")
            val photoDirectory = File(root, "photos")
            val crypto = EncryptedFiles("${context.packageName}.local-storage.v1")
            val hasEnvelope = envelopeFile.exists() || File(root, "database-key.enc.bak").exists()
            val passphrase = if (hasEnvelope) {
                crypto.decrypt(EncryptedFiles.read(envelopeFile, 256), "database-passphrase")
            } else {
                // Any existing encrypted artifacts forbid silent replacement of the key envelope.
                if (root.listFiles()?.any { it.name != "photos" || (it.listFiles()?.isNotEmpty() == true) } == true) throw StorageKeyUnavailable()
                crypto.initializeForEmptyStore()
                ByteArray(32).also { secret ->
                    SecureRandom().nextBytes(secret)
                    EncryptedFiles.atomicWrite(envelopeFile, crypto.encrypt(secret, "database-passphrase"))
                }
            }
            require(passphrase.size == 32)
            val database = try {
                Room.databaseBuilder(context.applicationContext, ScanDatabase::class.java, databaseFile.absolutePath)
                    .setDriver(SQLCipherDriver(passphrase.copyOf(), null, null))
                    .addMigrations(ScanDatabase.MIGRATION_1_2).build()
            } finally { passphrase.fill(0) }
            try {
                val repository = EncryptedScanRepository(database, PhotoFiles(photoDirectory, crypto, 32_000_000), imagePolicy, qualityPolicy, hook)
                repository.cleanup() // Forces encrypted open/schema validation before returning.
                repository
            } catch (failure: Throwable) { database.close(); throw failure }
        }
    }
}

private fun ScanOwner.scope(): String = when (this) {
    ScanOwner.Guest -> "GUEST"
    is ScanOwner.Account -> "ACCOUNT:$userId"
}

private fun ScanRow.session() = ScanSession(id,
    if (owner == "GUEST") ScanOwner.Guest else ScanOwner.Account(owner.removePrefix("ACCOUNT:")),
    ReferenceModel.valueOf(referenceModel), ScanState.valueOf(state), createdAt, updatedAt, completedAt,
    profileSide?.let(ProfileSide::valueOf))

private fun ScanRow.captureState(photos: List<PhotoRow>, time: Long): ScanRow {
    val front = photos.firstOrNull { it.view == CaptureView.FRONT.name }
    val profile = photos.firstOrNull { it.view == CaptureView.PROFILE.name }
    val state = when {
        photos.any { it.validation == ViewValidation.REJECTED.name } -> ScanState.FAILED_RECOVERABLE
        front == null -> ScanState.FRONT_PENDING
        front.validation != ViewValidation.ACCEPTED.name -> ScanState.FRONT_CAPTURED
        profile == null -> ScanState.PROFILE_PENDING
        profile.validation != ViewValidation.ACCEPTED.name -> ScanState.PROFILE_CAPTURED
        else -> ScanState.PROFILE_VALID
    }
    return copy(state = state.name, updatedAt = time, completedAt = null, profileSide = profile?.profileSide)
}

private fun validation(reasons: Set<RetakeReason>, pending: Boolean) = when {
    reasons.isNotEmpty() -> ViewValidation.REJECTED
    pending -> ViewValidation.PENDING_HOOKS
    else -> ViewValidation.ACCEPTED
}

private fun Set<RetakeReason>.encode() = sortedBy { it.name }.joinToString(",") { it.name }
private fun String.decode(): Set<RetakeReason> = if (isEmpty()) emptySet() else split(',').map(RetakeReason::valueOf).toSet()
