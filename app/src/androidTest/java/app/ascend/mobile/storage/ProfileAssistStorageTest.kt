package app.ascend.mobile.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.core.model.*
import app.ascend.mobile.core.profile.*
import app.ascend.mobile.core.front.*
import androidx.room3.Room
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProfileAssistStorageTest {
    private fun isolated(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "wp09-${UUID.randomUUID()}"
        return object : android.content.ContextWrapper(base) {
            override fun getNoBackupFilesDir() = File(base.noBackupFilesDir, name).apply { mkdirs() }
            override fun getCacheDir() = File(base.cacheDir, name).apply { mkdirs() }
            override fun getPackageName() = "${base.packageName}.$name"
        }
    }

    @Test fun profileConfirmationReopensAndRejectsStaleRevisionsAndOtherOwners() = runBlocking {
        val context = isolated()
        var repo = LocalScanStorageTest.open(context)
        try {
            val id = repo.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.FRONT), 1)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 2)
            val photo = repo.readProfilePhoto(ScanOwner.Guest, id)
            photo.bytes.fill(0)
            val source = repo.beginProfileAssist(ScanOwner.Guest, id, photo.imageRevision, null, ProfileSide.RIGHT, ProfileFacing.LEFT, 3)
            val point = source.requiredPoints.first()
            val corrected = repo.confirmProfilePoint(ScanOwner.Guest, id, photo.imageRevision, 0,
                point, source.policy.zones.getValue(point).missingPointAnchor, 4)
            assertEquals(1L, corrected.revision.revision)
            assertEquals(ProfileSide.RIGHT, repo.get(ScanOwner.Guest, id)!!.session.profileSide)
            try { repo.confirmProfilePoint(ScanOwner.Guest, id, photo.imageRevision, 0, point, corrected.imagePoints().getValue(point), 5); fail("Stale revision") }
            catch (_: IllegalArgumentException) { }
            assertNull(repo.readProfileAssist(ScanOwner.Account("other"), id))
            repo.close(); repo = LocalScanStorageTest.open(context)
            assertEquals(corrected.revision.input, repo.readProfileAssist(ScanOwner.Guest, id)!!.revision.input)
            assertEquals(corrected.revision.audit, repo.readProfileAssist(ScanOwner.Guest, id)!!.revision.audit)
            repo.retake(ScanOwner.Guest, id, CaptureView.PROFILE, 6)
            assertNull(repo.readProfileAssist(ScanOwner.Guest, id))
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 7)
            try { repo.beginProfileAssist(ScanOwner.Guest, id, photo.imageRevision, null, ProfileSide.LEFT, ProfileFacing.LEFT, 8); fail("Stale image") }
            catch (_: IllegalArgumentException) { }
        } finally { repo.deleteAll(); repo.close() }
    }

    @Test fun frontRetakePreservesIndependentProfilePointsAndProfileRetakePreservesFrontPhoto() = runBlocking {
        val repo = LocalScanStorageTest.open(isolated())
        try {
            val id = repo.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.FRONT), 1)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 2)
            val photo = repo.readProfilePhoto(ScanOwner.Guest, id); photo.bytes.fill(0)
            val source = repo.beginProfileAssist(ScanOwner.Guest, id, photo.imageRevision, null, ProfileSide.LEFT, ProfileFacing.RIGHT, 3)
            val idPoint = source.requiredPoints.first()
            val next = repo.confirmProfilePoint(ScanOwner.Guest, id, photo.imageRevision, 0, idPoint, source.policy.zones.getValue(idPoint).missingPointAnchor, 4)
            repo.retake(ScanOwner.Guest, id, CaptureView.FRONT, 5)
            assertEquals(next.revision.input, repo.readProfileAssist(ScanOwner.Guest, id)!!.revision.input)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.FRONT), 6)
            assertEquals(next.revision.input, repo.readProfileAssist(ScanOwner.Guest, id)!!.revision.input)
            val front = repo.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT)
            repo.retake(ScanOwner.Guest, id, CaptureView.PROFILE, 7)
            assertArrayEquals(front, repo.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT))
        } finally { repo.deleteAll(); repo.close() }
    }

    @Test fun profileReplacementAndRetakePreserveFrontSourceAndCorrectionState() = runBlocking {
        val repo = LocalScanStorageTest.open(isolated())
        try {
            val id = repo.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.FRONT), 1)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 2)
            repo.advance(ScanOwner.Guest, id, 3)
            val input = FrontInput(FRONT_CONTRACT_VERSION, repo.frontSourceRevision(ScanOwner.Guest, id), 32, 32, 24,
                FixtureOrigin.SYNTHETIC, "test-model", "0".repeat(64), "test-extractor", "test-confidence", 1, .9,
                FrontPose(0.0, 0.0, 0.0), mapOf("stomion" to FrontPoint(.5, .65, .95)), FrontPoint(.5, .5, null))
            repo.installFrontInput(ScanOwner.Guest, id, input, 4)
            val bounds = CorrectionPolicy("test", "Synthetic only", true, mapOf("stomion" to CorrectionZone(.4, .6, .5, .8, .05)))
            val front = repo.correctFront(ScanOwner.Guest, id, 0, bounds, "stomion", .51, .65, 5)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 6)
            assertEquals(front, repo.readFrontRevision(ScanOwner.Guest, id))
            repo.retake(ScanOwner.Guest, id, CaptureView.PROFILE, 7)
            assertEquals(front, repo.readFrontRevision(ScanOwner.Guest, id))
        } finally { repo.deleteAll(); repo.close() }
    }

    @Test fun corruptedProfileCacheRecoversWithoutRewritingCompletedHistory() = runBlocking {
        val context = isolated()
        var repo = LocalScanStorageTest.open(context)
        try {
            val id = repo.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.FRONT), 1)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 2)
            val photo = repo.readProfilePhoto(ScanOwner.Guest, id); photo.bytes.fill(0)
            repo.close()
            val root = File(context.noBackupFilesDir, "ascend-local")
            val crypto = EncryptedFiles("${context.packageName}.local-storage.v1")
            val secret = crypto.decrypt(EncryptedFiles.read(File(root, "database-key.enc"), 256), "database-passphrase")
            val database = try { Room.databaseBuilder(context, ScanDatabase::class.java, File(root, "scans.db").absolutePath)
                .setDriver(SQLCipherDriver(secret.copyOf(), null, null)).addMigrations(ScanDatabase.MIGRATION_1_2).build()
            } finally { secret.fill(0) }
            try { database.scans().savePayload(ScanPayloadRow(id, "profile-input-v1", byteArrayOf(0, 1))) } finally { database.close() }
            repo = LocalScanStorageTest.open(context)
            assertNull(repo.readProfileAssist(ScanOwner.Guest, id))
            repo.beginProfileAssist(ScanOwner.Guest, id, photo.imageRevision, null, ProfileSide.LEFT, ProfileFacing.RIGHT, 3)
            repo.advance(ScanOwner.Guest, id, 4); repo.advance(ScanOwner.Guest, id, 5); repo.advance(ScanOwner.Guest, id, 6)
            try { repo.complete(ScanOwner.Guest, LocalScanStorageTest.analysisFixture(id), 7); fail("Preview cannot complete scoring") }
            catch (_: IllegalArgumentException) { }
            repo.retake(ScanOwner.Guest, id, CaptureView.PROFILE, 8)
            repo.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 9)
            repo.advance(ScanOwner.Guest, id, 10); repo.advance(ScanOwner.Guest, id, 11); repo.advance(ScanOwner.Guest, id, 12)
            val outcome = LocalScanStorageTest.analysisFixture(id)
            repo.complete(ScanOwner.Guest, outcome, 13)
            assertTrue(repo.readProfilePhoto(ScanOwner.Guest, id).also { it.bytes.fill(0) }.readOnly)
            try { repo.beginProfileAssist(ScanOwner.Guest, id, photo.imageRevision, null, ProfileSide.LEFT, ProfileFacing.RIGHT, 14); fail("Completed history") }
            catch (_: IllegalArgumentException) { }
            assertEquals(outcome, repo.readAnalysis(ScanOwner.Guest, id))
        } finally { repo.deleteAll(); repo.close() }
    }
}
