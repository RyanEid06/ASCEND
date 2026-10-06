package app.ascend.mobile.storage

import android.content.Context
import android.graphics.BitmapFactory
import androidx.room3.Room
import app.ascend.mobile.core.data.*
import app.ascend.mobile.core.model.*
import app.ascend.mobile.core.vision.*
import app.ascend.mobile.core.front.*
import app.ascend.mobile.core.profile.*
import app.ascend.mobile.core.geometry.LandmarkId
import app.ascend.mobile.core.geometry.PixelResolution
import app.ascend.mobile.core.geometry.Point2
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget

/** One application-scoped instance. All calls are serialized, including recovery and deletion. */
class EncryptedScanRepository internal constructor(
    private val database: ScanDatabase,
    private val files: PhotoFiles,
    private val imagePolicy: ImageStoragePolicy,
    private val qualityPolicy: PhotoQualityPolicy,
    private val hook: FaceValidationHook,
    private val acquisitionCache: File,
) : LocalScanRepository, FrontMeasurementStorage {
    private val dao = database.scans()
    private val mutex = Mutex()
    @Volatile private var closed = false
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

    /** Additive WP07 seam; existing shared repository contract and schema are unchanged. */
    suspend fun saveFrontLandmarks(owner: ScanOwner, scanId: String, snapshot: FrontLandmarkSnapshot) = serialized {
        val row = requireNotNull(dao.get(owner.scope(), scanId)) { "Scan unavailable in active owner scope" }
        require(row.state != ScanState.COMPLETE.name) { "Completed history is immutable" }
        val photo = requireNotNull(dao.photos(scanId).firstOrNull { it.view == CaptureView.FRONT.name })
        require(photo.validation != ViewValidation.REJECTED.name)
        val bytes = files.read(photo.assetId)
        try {
            require(frontImageSha256(bytes) == snapshot.sourceImageSha256) { "Front image changed during extraction" }
            require(snapshot.resolution.width == photo.width && snapshot.resolution.height == photo.height)
            val encoded = FrontLandmarkCodec.encode(snapshot)
            try {
                FrontLandmarkCodec.decode(encoded)
                dao.savePayload(ScanPayloadRow(scanId, "front-landmarks-v1", encoded))
            } finally { encoded.fill(0) }
        } finally { bytes.fill(0) }
    }

    suspend fun readFrontLandmarks(owner: ScanOwner, scanId: String): FrontLandmarkSnapshot? = serialized {
        if (dao.get(owner.scope(), scanId) == null) return@serialized null
        val photo = dao.photos(scanId).firstOrNull { it.view == CaptureView.FRONT.name } ?: return@serialized null
        if (photo.validation == ViewValidation.REJECTED.name) return@serialized null
        val payload = dao.payload(scanId, "front-landmarks-v1") ?: return@serialized null
        try {
            val snapshot = FrontLandmarkCodec.decodeCached(payload.payload) ?: return@serialized null
            val bytes = files.read(photo.assetId)
            try {
                require(frontImageSha256(bytes) == snapshot.sourceImageSha256)
                require(snapshot.resolution.width == photo.width && snapshot.resolution.height == photo.height)
                snapshot
            } finally { bytes.fill(0) }
        } finally { payload.payload.fill(0) }
    }

    override suspend fun advance(owner: ScanOwner, scanId: String, atEpochMillis: Long): LocalScan = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        require(ScanState.valueOf(row.state) in setOf(ScanState.PROFILE_VALID, ScanState.LANDMARKING, ScanState.MEASURING)) {
            "Completion requires an analysis result with immutable provenance"
        }
        val photos = dao.photos(scanId)
        require(photos.size == 2 && photos.all { it.validation == ViewValidation.ACCEPTED.name })
        photos.forEach { files.read(it.assetId).fill(0) }
        val session = ScanLifecycle.advance(row.session(), atEpochMillis)
        val advanced = row.copy(state = session.state.name, updatedAt = session.updatedAtEpochMillis, completedAt = session.completedAtEpochMillis)
        dao.saveScan(advanced)
        snapshot(advanced)
    }

    override suspend fun complete(owner: ScanOwner, outcome: AnalysisOutcome.Complete, atEpochMillis: Long): LocalScan = serialized {
        require(dao.payload(outcome.scanId, "front-input-v1") == null) { "Front analyses require a matching correction revision" }
        require(readProfile(outcome.scanId) == null) { "Profile analyses require a reviewed revision-bound completion path" }
        persistComplete(owner, outcome, atEpochMillis)
    }

    private suspend fun persistComplete(owner: ScanOwner, outcome: AnalysisOutcome.Complete, atEpochMillis: Long,
        frontProvenance: FrontCompletedProvenance? = null): LocalScan {
        // A corrupt/stale cache is a miss, as in the read path; only a valid current session binds completion.
        require(readProfile(outcome.scanId) == null) { "Preview confirmations cannot complete a scored analysis" }
        val row = editable(owner, outcome.scanId, atEpochMillis)
        require(row.state == ScanState.SCORING.name && row.referenceModel == outcome.referenceModel.name)
        val photos = dao.photos(row.id)
        require(photos.size == 2 && photos.all { it.validation == ViewValidation.ACCEPTED.name })
        photos.forEach { files.read(it.assetId).fill(0) }
        val completed = ScanLifecycle.advance(row.session(), atEpochMillis)
        val bytes = AnalysisStorageCodec.encode(outcome)
        try {
            AnalysisStorageCodec.decode(bytes) // Validate the frozen snapshot even if caller collections were mutable.
            val stored = row.copy(state = completed.state.name, updatedAt = atEpochMillis, completedAt = atEpochMillis)
            val analysis = ScanPayloadRow(row.id, "analysis-v1", bytes)
            if (frontProvenance == null) dao.complete(stored, analysis)
            else {
                val evidence = FrontCodec.encode(frontProvenance)
                try { dao.completeFront(stored, analysis, ScanPayloadRow(row.id, "front-completion-v1", evidence)) }
                finally { evidence.fill(0) }
            }
            return snapshot(stored)
        } finally { bytes.fill(0) }
    }

    override suspend fun completeFrontAnalysis(owner: ScanOwner, outcome: AnalysisOutcome.Complete,
        expectedRevision: Long, policy: FrontPolicy, atEpochMillis: Long) = serialized {
        editable(owner, outcome.scanId, atEpochMillis)
        val front = requireNotNull(readFront(outcome.scanId))
        require(front.revision == expectedRevision) { "Analysis was computed before the latest correction" }
        require(front.original.modelVersion == outcome.versions.frontLandmarkModelVersion)
        checkFrontSource(outcome.scanId, front.original)
        val report = front.measure(policy)
        val supplied = outcome.metrics.filterIsInstance<MetricResult.Available>().filter { it.view == MeasurementView.FRONT }
        require(supplied.isNotEmpty())
        supplied.forEach { metric ->
            val measured = requireNotNull(report.metrics.singleOrNull { it.metricId == metric.metricId })
            require(measured.failure == null && measured.value == metric.measuredValue && measured.confidence == metric.confidence0To1) { "Front result does not match the current geometry and policy" }
        }
        persistComplete(owner, outcome, atEpochMillis, FrontCompletedProvenance(1, front.revision, report.geometryVersion, policy))
        Unit
    }

    override suspend fun readCompletedFrontProvenance(owner: ScanOwner, scanId: String): FrontCompletedProvenance? = serialized {
        val row = dao.get(owner.scope(), scanId) ?: return@serialized null
        if (row.state != ScanState.COMPLETE.name) return@serialized null
        val payload = dao.payload(scanId, "front-completion-v1") ?: return@serialized null
        try { FrontCodec.completed(payload.payload) } finally { payload.payload.fill(0) }
    }

    override suspend fun readAnalysis(owner: ScanOwner, scanId: String): AnalysisOutcome.Complete? = serialized {
        val row = dao.get(owner.scope(), scanId) ?: return@serialized null
        if (row.state != ScanState.COMPLETE.name) return@serialized null
        requireNotNull(dao.payload(scanId, "analysis-v1")) { "Completed analysis snapshot unavailable" }.let { payload ->
            try {
                AnalysisStorageCodec.decode(payload.payload).also {
                    require(it.scanId == scanId && it.referenceModel.name == row.referenceModel)
                }
            } finally { payload.payload.fill(0) }
        }
    }

    override suspend fun frontSourceRevision(owner: ScanOwner, scanId: String): String = serialized {
        requireNotNull(dao.get(owner.scope(), scanId))
        requireNotNull(dao.photos(scanId).singleOrNull { it.view == CaptureView.FRONT.name }).assetId
    }

    override suspend fun installFrontInput(owner: ScanOwner, scanId: String, input: FrontInput, atEpochMillis: Long) = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        require(row.state in setOf(ScanState.LANDMARKING.name, ScanState.MEASURING.name))
        require(dao.payload(scanId, "front-input-v1") == null) { "An extraction is immutable; retake creates a new source" }
        checkFrontSource(scanId, input)
        val bytes = FrontCodec.encode(FrontRevision(1, input, emptyList()))
        try {
            FrontCodec.revision(bytes)
            dao.saveFrontRevision(row.copy(state = ScanState.MEASURING.name, updatedAt = atEpochMillis), ScanPayloadRow(scanId, "front-input-v1", bytes))
        } finally { bytes.fill(0) }
    }

    override suspend fun readFrontRevision(owner: ScanOwner, scanId: String): FrontRevision? = serialized {
        if (dao.get(owner.scope(), scanId) == null) return@serialized null
        readFront(scanId)
    }

    override suspend fun correctFront(owner: ScanOwner, scanId: String, expectedRevision: Long, policy: CorrectionPolicy,
        landmarkId: String, x: Double, y: Double, atEpochMillis: Long): FrontRevision = serialized {
        val row = editable(owner, scanId, atEpochMillis) // COMPLETE is rejected before reading/mutating history.
        require(row.state in setOf(ScanState.MEASURING.name, ScanState.SCORING.name))
        val previous = requireNotNull(readFront(scanId))
        require(previous.revision == expectedRevision) { "Stale correction revision" }
        checkFrontSource(scanId, previous.original)
        val next = previous.correct(policy, landmarkId, x, y, atEpochMillis)
        val bytes = FrontCodec.encode(next)
        try {
            FrontCodec.revision(bytes)
            // Atomically remove measurement/scoring/coverage/extrema/advice caches, retaining source inputs.
            dao.saveFrontRevision(row.copy(state = ScanState.MEASURING.name, updatedAt = atEpochMillis), ScanPayloadRow(scanId, "front-input-v1", bytes))
        } finally { bytes.fill(0) }
        next
    }

    private suspend fun readFront(scanId: String): FrontRevision? {
        val payload = dao.payload(scanId, "front-input-v1") ?: return null
        return try { FrontCodec.revision(payload.payload) } finally { payload.payload.fill(0) }
    }

    internal suspend fun readProfilePhoto(owner: ScanOwner, scanId: String): ProfilePhoto = serialized {
        val row = requireNotNull(dao.get(owner.scope(), scanId))
        val photo = profilePhoto(scanId)
        ProfilePhoto(files.read(photo.assetId), photo.assetId, PixelResolution(photo.width, photo.height),
            photo.profileSide?.let(ProfileSide::valueOf), row.state == ScanState.COMPLETE.name)
    }

    internal suspend fun readProfileAssist(owner: ScanOwner, scanId: String): ProfileAssistSession? = serialized {
        if (dao.get(owner.scope(), scanId) == null) return@serialized null
        readProfile(scanId)
    }

    internal suspend fun beginProfileAssist(owner: ScanOwner, scanId: String, expectedImageRevision: String,
        expectedRevision: Long?, side: ProfileSide, facing: ProfileFacing, atEpochMillis: Long): ProfileAssistSession = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        val photo = profilePhoto(scanId)
        require(photo.assetId == expectedImageRevision) { "Profile image changed" }
        val previous = readProfile(scanId)
        require(previous?.revisionToken == expectedRevision) { "Stale profile correction revision" }
        val next = ProfileAssistSession.preview(photo.assetId, PixelResolution(photo.width, photo.height), side, facing)
            .copy(revisionToken = previous?.revisionToken?.plus(1) ?: 0)
        saveProfile(row, photo, next, atEpochMillis)
        next
    }

    /** Adapter seam for WP10 automatic proposals; no alternative point model or extractor implementation. */
    internal suspend fun installProfileAssist(owner: ScanOwner, scanId: String, session: ProfileAssistSession,
        atEpochMillis: Long) = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        val photo = profilePhoto(scanId)
        require(readProfile(scanId) == null) { "Profile source is immutable; retake to replace it" }
        require(session.revision.revision == 0L && session.revisionToken == 0L)
        val input = session.revision.input
        require(input.origin != ProfileOrigin.CONSENTED_LOCAL) { "No reviewed real-profile proposal policy" }
        require(input.imageRevision == photo.assetId && input.resolution == PixelResolution(photo.width, photo.height))
        saveProfile(row, photo, session, atEpochMillis)
    }

    internal suspend fun confirmProfilePoint(owner: ScanOwner, scanId: String, expectedImageRevision: String,
        expectedRevision: Long, pointId: LandmarkId, target: Point2, atEpochMillis: Long): ProfileAssistSession = serialized {
        val row = editable(owner, scanId, atEpochMillis)
        val photo = profilePhoto(scanId)
        require(photo.assetId == expectedImageRevision) { "Profile image changed" }
        val previous = requireNotNull(readProfile(scanId)) { "Profile confirmation unavailable" }
        require(previous.revisionToken == expectedRevision) { "Stale profile correction revision" }
        val next = previous.confirm(pointId, target, expectedRevision, atEpochMillis)
        saveProfile(row, photo, next, atEpochMillis)
        next
    }

    private suspend fun profilePhoto(scanId: String): PhotoRow = requireNotNull(
        dao.photos(scanId).singleOrNull { it.view == CaptureView.PROFILE.name && it.validation != ViewValidation.REJECTED.name }
    ) { "Profile photo unavailable; retake required" }

    private suspend fun readProfile(scanId: String): ProfileAssistSession? {
        val photo = dao.photos(scanId).singleOrNull { it.view == CaptureView.PROFILE.name } ?: return null
        if (photo.validation == ViewValidation.REJECTED.name) return null
        val payload = dao.payload(scanId, "profile-input-v1") ?: return null
        return try {
            ProfileAssistCodec.cached(payload.payload)?.takeIf {
                val input = it.revision.input
                input.imageRevision == photo.assetId && input.resolution == PixelResolution(photo.width, photo.height) &&
                    input.side?.name == photo.profileSide
            }
        } finally { payload.payload.fill(0) }
    }

    private suspend fun saveProfile(row: ScanRow, photo: PhotoRow, session: ProfileAssistSession, time: Long) {
        val bytes = ProfileAssistCodec.encode(session)
        try {
            ProfileAssistCodec.decode(bytes)
            val state = if (row.state in setOf(ScanState.SCORING.name, ScanState.LANDMARKING.name)) ScanState.MEASURING.name else row.state
            dao.saveProfileRevision(row.copy(profileSide = session.revision.input.side!!.name, updatedAt = time, state = state),
                photo.copy(profileSide = session.revision.input.side!!.name), ScanPayloadRow(row.id, "profile-input-v1", bytes))
        } finally { bytes.fill(0) }
    }

    private suspend fun checkFrontSource(scanId: String, input: FrontInput) {
        val photos = dao.photos(scanId)
        require(photos.size == 2 && photos.all { it.validation == ViewValidation.ACCEPTED.name })
        val front = photos.single { it.view == CaptureView.FRONT.name }
        require(front.assetId == input.imageRevision && front.width == input.width && front.height == input.height) { "Extraction does not match current standardized photo" }
        files.read(front.assetId).fill(0)
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
        clearAcquisitionCache(acquisitionCache)
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
            Logger.setTarget(NoopTarget())
            val root = File(context.noBackupFilesDir, "ascend-local")
            check(root.isDirectory || root.mkdirs())
            val databaseFile = File(root, "scans.db")
            val envelopeFile = File(root, "database-key.enc")
            val photoDirectory = File(root, "photos")
            val crypto = EncryptedFiles("${context.packageName}.local-storage.v1")
            val hasEnvelope = envelopeFile.exists() || File(root, "database-key.enc.bak").exists()
            if (!databaseFile.exists() && (photoDirectory.listFiles()?.isNotEmpty() == true ||
                    File(root, "scans.db-wal").exists() || File(root, "scans.db-shm").exists())) throw StorageDataUnavailable()
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
                val repository = EncryptedScanRepository(database, PhotoFiles(photoDirectory, crypto, 32_000_000), imagePolicy, qualityPolicy, hook,
                    File(context.cacheDir, "ascend_capture"))
                repository.cleanup() // Forces encrypted open/schema validation before returning.
                repository
            } catch (failure: Throwable) { database.close(); throw failure }
        }

        /** Destructive recovery only after explicit Delete All choice and all repository instances close.
         * This is available even when missing keys prevent opening the database. No remote/auth deletion.
         */
        suspend fun eraseUnrecoverableLocalData(context: Context) = withContext(Dispatchers.IO) {
            val parent = context.noBackupFilesDir.canonicalFile
            val root = File(parent, "ascend-local").canonicalFile
            require(root.parentFile == parent)
            fun erase(file: File) {
                val resolved = file.canonicalFile
                require(resolved == root || resolved.path.startsWith(root.path + File.separator))
                if (file.isDirectory) file.listFiles()?.forEach(::erase)
                check(file.delete() || !file.exists())
            }
            if (root.exists()) erase(root)
            clearAcquisitionCache(File(context.cacheDir, "ascend_capture"))
            EncryptedFiles("${context.packageName}.local-storage.v1").deleteAfterExplicitReset()
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

/** Call Delete All only after capture/timer writers have stopped. No gallery originals are touched. */
private fun clearAcquisitionCache(directory: File) {
    val root = directory.canonicalFile
    directory.listFiles()?.forEach { file ->
        require(file.canonicalFile.parentFile == root && file.isFile)
        check(file.delete() || !file.exists())
    }
}
