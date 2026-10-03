package app.ascend.mobile.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.security.keystore.KeyProperties
import androidx.room3.*
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.core.data.*
import app.ascend.mobile.core.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import org.junit.Assert.*
import org.junit.Test

/** Synthetic media only. Each test uses an isolated no-backup storage namespace. */
class LocalScanStorageTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    private fun isolated(): Context = object : android.content.ContextWrapper(base) {
        private val directory = File(base.noBackupFilesDir, "wp06-${UUID.randomUUID()}")
        override fun getNoBackupFilesDir(): File = directory.apply { mkdirs() }
        override fun getPackageName(): String = "${base.packageName}.${directory.name}"
    }

    @Test fun offlineReopenAndIndependentRetakes() = runBlocking {
        val context = isolated()
        var repository = open(context)
        try {
            val scan = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0)
            repository.putCapture(ScanOwner.Guest, scan.session.id, capture(CaptureView.FRONT), 1)
            val valid = repository.putCapture(ScanOwner.Guest, scan.session.id, capture(CaptureView.PROFILE), 2)
            assertEquals(ScanState.PROFILE_VALID, valid.session.state)
            val profileBytes = repository.readPhoto(ScanOwner.Guest, scan.session.id, CaptureView.PROFILE)
            repository.close()
            repository = open(context)
            assertEquals(valid, repository.get(ScanOwner.Guest, scan.session.id))
            val retaken = repository.retake(ScanOwner.Guest, scan.session.id, CaptureView.FRONT, 3)
            assertEquals(ScanState.FRONT_PENDING, retaken.session.state)
            assertEquals(ProfileSide.LEFT, retaken.session.profileSide)
            assertArrayEquals(profileBytes, repository.readPhoto(ScanOwner.Guest, scan.session.id, CaptureView.PROFILE))
            repository.putCapture(ScanOwner.Guest, scan.session.id, capture(CaptureView.FRONT), 4)
            val frontBytes = repository.readPhoto(ScanOwner.Guest, scan.session.id, CaptureView.FRONT)
            val profileRetaken = repository.retake(ScanOwner.Guest, scan.session.id, CaptureView.PROFILE, 5)
            assertEquals(ScanState.PROFILE_PENDING, profileRetaken.session.state)
            assertNull(profileRetaken.session.profileSide)
            assertArrayEquals(frontBytes, repository.readPhoto(ScanOwner.Guest, scan.session.id, CaptureView.FRONT))
            assertNull(repository.get(ScanOwner.Account("other"), scan.session.id))
            repository.deleteScan(ScanOwner.Account("other"), scan.session.id)
            assertNotNull(repository.get(ScanOwner.Guest, scan.session.id))
            repository.deleteScan(ScanOwner.Guest, scan.session.id)
            repository.deleteScan(ScanOwner.Guest, scan.session.id)
            assertNull(repository.get(ScanOwner.Guest, scan.session.id))
            assertTrue(photoDirectory(context).listFiles().orEmpty().isEmpty())
        } finally { repository.deleteAll(); repository.close() }
    }

    @Test fun rejectedProfileKeepsFrontAndUnavailableHooksRemainPending() = runBlocking {
        val context = isolated()
        var repository = open(context)
        try {
            val id = repository.create(ScanOwner.Guest, ReferenceModel.FEMALE, 0).session.id
            repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.FRONT), 1)
            val front = repository.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT)
            repository.close()
            repository = open(context, FaceValidationHook { _, _ -> FaceObservation(2) })
            val failed = repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.PROFILE), 2)
            assertEquals(ScanState.FAILED_RECOVERABLE, failed.session.state)
            assertEquals(setOf(RetakeReason.MULTIPLE_FACES), failed.views.single { it.view == CaptureView.PROFILE }.reasons)
            assertArrayEquals(front, repository.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT))
            repository.close()
            repository = open(context, FaceValidationHook { _, _ -> null })
            val pending = repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.PROFILE), 3)
            assertEquals(ViewValidation.PENDING_HOOKS, pending.views.single { it.view == CaptureView.PROFILE }.validation)
            try { repository.advance(ScanOwner.Guest, id, 4); fail("Pending hooks cannot advance") } catch (_: IllegalArgumentException) { }
            repository.close()
            repository = open(context)
            assertEquals(ScanState.PROFILE_VALID, repository.revalidate(ScanOwner.Guest, id, CaptureView.PROFILE, 5).session.state)
        } finally { repository.deleteAll(); repository.close() }
    }

    @Test fun encryptedFilesTamperMissingKeyAndOrphanRecovery() = runBlocking {
        val context = isolated()
        var repository = open(context)
        val id = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
        repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.FRONT), 1)
        val db = File(context.noBackupFilesDir, "ascend-local/scans.db")
        assertFalse(db.readBytes().take(16).toByteArray().contentEquals("SQLite format 3\u0000".toByteArray()))
        val asset = photoDirectory(context).listFiles()!!.single()
        val envelope = asset.readBytes()
        assertFalse(envelope.take(8).toByteArray().contentEquals(png().take(8).toByteArray()))
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 1).toByte()
        asset.writeBytes(envelope)
        File(photoDirectory(context), "orphan.enc").writeBytes(byteArrayOf(1))
        repository.close()
        repository = open(context)
        assertFalse(File(photoDirectory(context), "orphan.enc").exists())
        assertEquals(setOf(RetakeReason.ASSET_UNAVAILABLE), repository.recover(ScanOwner.Guest, 2).single().views.single().reasons)
        repository.close()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        store.deleteEntry("${context.packageName}.local-storage.v1")
        val before = db.readBytes()
        try { open(context); fail("Missing key must fail closed") } catch (_: StorageKeyUnavailable) { }
        assertArrayEquals(before, db.readBytes())
        EncryptedScanRepository.eraseUnrecoverableLocalData(context)
        val empty = open(context)
        try { assertTrue(empty.list(ScanOwner.Guest).isEmpty()) } finally { empty.close() }
    }

    @Test fun deletionResumesAfterInterruptionAndDeleteAllRemovesOtherOwners() = runBlocking {
        val context = isolated()
        val repository = open(context)
        val id = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
        repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.FRONT), 1)
        repository.create(ScanOwner.Account("test-owner"), ReferenceModel.FEMALE, 0)
        repository.close()
        val root = File(context.noBackupFilesDir, "ascend-local")
        val crypto = EncryptedFiles("${context.packageName}.local-storage.v1")
        val secret = crypto.decrypt(File(root, "database-key.enc").readBytes(), "database-passphrase")
        val database = Room.databaseBuilder(context, ScanDatabase::class.java, File(root, "scans.db").absolutePath)
            .setDriver(SQLCipherDriver(secret, null, null)).addMigrations(ScanDatabase.MIGRATION_1_2).build()
        val row = database.scans().get("GUEST", id)!!
        database.scans().saveScan(row.copy(deleting = true))
        database.close()
        val reopened = open(context)
        try {
            assertNull(reopened.get(ScanOwner.Guest, id))
            assertTrue(photoDirectory(context).listFiles().orEmpty().isEmpty())
            assertEquals(1, reopened.list(ScanOwner.Account("test-owner")).size)
            val abandonedDirectory = File(context.cacheDir, "ascend_capture").apply { mkdirs() }
            val abandoned = File.createTempFile("wp06-abandoned-", ".jpg", abandonedDirectory).apply { writeBytes(png()) }
            reopened.deleteAll()
            assertFalse(abandoned.exists())
            reopened.deleteAll()
            assertTrue(reopened.list(ScanOwner.Account("test-owner")).isEmpty())
        } finally { reopened.close() }
    }

    @Test fun versionedEnvelopeAuthenticatesPurposeAndHeader() {
        val crypto = EncryptedFiles("${base.packageName}.test-envelope-${UUID.randomUUID()}")
        crypto.initializeForEmptyStore()
        val envelope = crypto.encrypt(byteArrayOf(1, 2, 3), "first")
        assertArrayEquals(byteArrayOf(1, 2, 3), crypto.decrypt(envelope, "first"))
        try { crypto.decrypt(envelope, "second"); fail("Purpose must authenticate") } catch (_: javax.crypto.AEADBadTagException) { }
        envelope[7] = 2
        try { crypto.decrypt(envelope, "first"); fail("Unknown format must fail") } catch (_: IllegalArgumentException) { }
    }

    @Test fun maliciousAndOutOfBoundsCropCannotReplaceAcceptedImage() = runBlocking {
        val context = isolated()
        val repository = open(context)
        try {
            val id = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.FRONT), 1)
            val before = repository.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT)
            for (bad in listOf(capture(CaptureView.FRONT).copy(encodedImage = byteArrayOf(1, 2, 3)),
                capture(CaptureView.FRONT).copy(crop = CaptureCrop(offsetXFraction = 1f)))) {
                try { repository.putCapture(ScanOwner.Guest, id, bad, 2); fail("Invalid input must be rejected") }
                catch (_: CaptureRejected) { }
                assertArrayEquals(before, repository.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT))
            }
        } finally { repository.deleteAll(); repository.close() }
    }

    @Test fun boundedCameraImportDeletesOnlyOwnedTemporarySource() = runBlocking {
        val context = isolated()
        val repository = open(context)
        val directory = File(context.cacheDir, "ascend_capture").apply { mkdirs() }
        val source = File.createTempFile("wp06-test-", ".png", directory).apply { writeBytes(png()) }
        val outside = File.createTempFile("wp06-outside-", ".png", context.cacheDir).apply { writeBytes(png()) }
        try {
            val id = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            val importer = LocalCaptureImporter(context, repository, imagePolicy)
            importer.persistFromUri(ScanOwner.Guest, id, CaptureView.FRONT, android.net.Uri.fromFile(source),
                CaptureCrop(viewportAspectRatio = 1f), CaptureOrigin.CAMERA, null, 1)
            assertFalse(source.exists())
            assertTrue(repository.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT).isNotEmpty())
            try {
                importer.persistFromUri(ScanOwner.Guest, id, CaptureView.FRONT, android.net.Uri.fromFile(outside),
                    CaptureCrop(), CaptureOrigin.CAMERA, null, 2)
                fail("Only WP05 private acquisition cache may be consumed as a camera file")
            } catch (_: IllegalArgumentException) { }
            assertTrue(outside.exists())
            val oversize = File.createTempFile("wp06-oversize-", ".png", directory).apply { writeBytes(ByteArray(imagePolicy.maxEncodedBytes + 1)) }
            try {
                importer.persistFromUri(ScanOwner.Guest, id, CaptureView.FRONT, android.net.Uri.fromFile(oversize),
                    CaptureCrop(), CaptureOrigin.CAMERA, null, 2)
                fail("Oversized source must be rejected before decode")
            } catch (_: CaptureRejected) { } finally { oversize.delete() }
        } finally { source.delete(); outside.delete(); repository.deleteAll(); repository.close() }
    }

    @Test fun missingEnvelopeAndMissingDatabaseNeverEraseHistory() = runBlocking {
        val context = isolated()
        val repository = open(context)
        val id = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
        repository.putCapture(ScanOwner.Guest, id, capture(CaptureView.FRONT), 1)
        repository.close()
        val root = File(context.noBackupFilesDir, "ascend-local")
        val envelope = File(root, "database-key.enc")
        val original = envelope.readBytes()
        envelope.delete()
        try { open(context); fail("Missing envelope cannot replace data") } catch (_: StorageKeyUnavailable) { }
        assertFalse(envelope.exists())
        envelope.writeBytes(original)
        File(root, "scans.db").delete()
        try { open(context); fail("Missing database cannot erase retained photos") } catch (_: StorageDataUnavailable) { }
        assertEquals(1, photoDirectory(context).listFiles()!!.size)
        EncryptedScanRepository.eraseUnrecoverableLocalData(context)
    }

    @Test fun normalizerAppliesExifAndStripsMetadataAndRejectsTransparency() {
        val temporary = File.createTempFile("wp06-exif-", ".jpg", base.cacheDir)
        val bitmap = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        try {
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            androidx.exifinterface.media.ExifInterface(temporary).apply {
                setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, "6")
                setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, "12/1,0/1,0/1")
                setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                saveAttributes()
            }
            val normalized = PhotoNormalizer(imagePolicy).normalize(temporary.readBytes(), CaptureCrop())
            try {
                assertEquals(24, normalized.bitmap.width)
                assertEquals(32, normalized.bitmap.height)
                val stripped = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(normalized.png))
                assertNull(stripped.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE))
                // ExifInterface synthesizes UNDEFINED when the encoded image has no orientation tag.
                assertEquals(androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED,
                    stripped.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, -1))
            } finally { normalized.bitmap.recycle(); normalized.png.fill(0) }
            bitmap.eraseColor(Color.TRANSPARENT)
            val encoded = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            try { PhotoNormalizer(imagePolicy).normalize(encoded, CaptureCrop()); fail("Transparent input must not invent a background") }
            catch (failure: InvalidPhoto) { assertEquals(RetakeReason.INVALID_IMAGE, failure.reason) }
        } finally { bitmap.recycle(); temporary.delete() }
    }

    @Test fun encryptedProductionSchemaMigrationKeepsSessionAndMedia() = runBlocking {
        System.loadLibrary("sqlcipher")
        val context = isolated()
        val file = File(context.noBackupFilesDir, "migration.db")
        val secret = ByteArray(32) { (it + 1).toByte() }
        val old = Room.databaseBuilder(context, LocalScanV1::class.java, file.absolutePath)
            .setDriver(SQLCipherDriver(secret.copyOf(), null, null)).build()
        old.records().insert(ScanRowV1("old", "GUEST", "MALE", "FRONT_CAPTURED", 10, 11, null, null))
        old.records().insertPhoto(PhotoRow("old", "FRONT", UUID.randomUUID().toString(), 32, 32, "CAMERA", "1,0,0,1",
            "PENDING_HOOKS", "", "test", "test", 0.5, 0.0, null))
        old.close()
        val migrated = Room.databaseBuilder(context, ScanDatabase::class.java, file.absolutePath)
            .setDriver(SQLCipherDriver(secret.copyOf(), null, null)).addMigrations(ScanDatabase.MIGRATION_1_2).build()
        try {
            val row = migrated.scans().get("GUEST", "old")!!
            assertEquals(10L, row.createdAt)
            assertFalse(row.deleting)
            assertEquals("FRONT_CAPTURED", row.state)
            assertEquals(1, migrated.scans().photos("old").size)
        } finally { migrated.close() }
    }

    companion object {
        internal val imagePolicy = ImageStoragePolicy("synthetic-test", 1_000_000, 1024, 1_048_576, 1_048_576, 128, 32)
        internal val qualityPolicy = PhotoQualityPolicy("synthetic-test", 3, 3, 0.0, 1.0, 0.0, 0.5, 90.0, 180.0, 90.0, 0.0, 180.0)
        internal suspend fun open(context: Context, hook: FaceValidationHook = FaceValidationHook { view, _ ->
            FaceObservation(1, 0.5, 0.5, if (view == CaptureView.PROFILE) 90.0 else 0.0, 0.0, 0.0)
        }) = EncryptedScanRepository.open(context, imagePolicy, qualityPolicy, hook)
        internal fun capture(view: CaptureView) = LocalCapture(view, png(), CaptureCrop(viewportAspectRatio = 1f), CaptureOrigin.CAMERA,
            if (view == CaptureView.PROFILE) ProfileSide.LEFT else null)
        internal fun png(): ByteArray {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.GRAY)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            return output.toByteArray()
        }
        private fun photoDirectory(context: Context) = File(context.noBackupFilesDir, "ascend-local/photos")
    }
}

@Entity(tableName = "local_scans")
internal data class ScanRowV1(@PrimaryKey val id: String, val owner: String, val referenceModel: String,
    val state: String, val createdAt: Long, val updatedAt: Long, val completedAt: Long?, val profileSide: String?)

@Dao
internal interface LocalScanV1Dao {
    @Insert suspend fun insert(row: ScanRowV1)
    @Insert suspend fun insertPhoto(row: PhotoRow)
}

@Database(entities = [ScanRowV1::class, PhotoRow::class, ScanPayloadRow::class], version = 1, exportSchema = false)
internal abstract class LocalScanV1 : RoomDatabase() { abstract fun records(): LocalScanV1Dao }
