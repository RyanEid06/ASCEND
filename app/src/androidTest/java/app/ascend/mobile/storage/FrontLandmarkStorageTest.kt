package app.ascend.mobile.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.core.data.*
import app.ascend.mobile.core.model.*
import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.vision.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FrontLandmarkStorageTest {
    private fun isolated(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "wp07-${UUID.randomUUID()}"
        return object : android.content.ContextWrapper(base) {
            override fun getNoBackupFilesDir() = File(base.noBackupFilesDir, name).apply { mkdirs() }
            override fun getCacheDir() = File(base.cacheDir, name).apply { mkdirs() }
            override fun getPackageName() = "${base.packageName}.$name"
        }
    }
    private suspend fun open(context: Context) = EncryptedScanRepository.open(context,
        LocalScanStore.IMAGE_POLICY, LocalScanStore.PRE_CV_QUALITY_POLICY)
    private fun capture(color: Int): LocalCapture {
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream()
        try { bitmap.eraseColor(color); bitmap.compress(Bitmap.CompressFormat.PNG, 100, png) }
        finally { bitmap.recycle() }
        return LocalCapture(CaptureView.FRONT, png.toByteArray(), origin = CaptureOrigin.GALLERY)
    }
    private fun snapshot(hash: String) = FrontLandmarkSnapshot(PixelResolution(300, 400),
        List(478) { index -> MeshPoint(if (index == 468) 0.4 else if (index == 473) 0.6 else 0.5, 0.5, 0.0) }, PoseDeviation(), hash)

    @Test fun persistsAcrossReopenAndHidesOtherOwners() = runBlocking {
        val context = isolated()
        var repo = open(context)
        try {
            val id = repo.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repo.putCapture(ScanOwner.Guest, id, capture(Color.BLUE), 1)
            val image = repo.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT)
            val expected = snapshot(frontImageSha256(image)); image.fill(0)
            repo.saveFrontLandmarks(ScanOwner.Guest, id, expected)
            repo.close(); repo = open(context)
            assertEquals(expected, repo.readFrontLandmarks(ScanOwner.Guest, id))
            assertNull(repo.readFrontLandmarks(ScanOwner.Account("other"), id))
            try { repo.saveFrontLandmarks(ScanOwner.Account("other"), id, expected); fail("Cross-owner write accepted") }
            catch (_: IllegalArgumentException) { }
            repo.deleteScan(ScanOwner.Guest, id)
            assertNull(repo.readFrontLandmarks(ScanOwner.Guest, id))
        } finally { repo.deleteAll(); repo.close() }
    }
    @Test fun retakeInvalidatesSnapshotAndRejectsStaleInference() = runBlocking {
        val repo = open(isolated())
        try {
            val id = repo.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
            repo.putCapture(ScanOwner.Guest, id, capture(Color.BLUE), 1)
            val bytes = repo.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT)
            val old = snapshot(frontImageSha256(bytes)); bytes.fill(0)
            repo.saveFrontLandmarks(ScanOwner.Guest, id, old)
            repo.retake(ScanOwner.Guest, id, CaptureView.FRONT, 2)
            assertNull(repo.readFrontLandmarks(ScanOwner.Guest, id))
            repo.putCapture(ScanOwner.Guest, id, capture(Color.RED), 3)
            try { repo.saveFrontLandmarks(ScanOwner.Guest, id, old); fail("Stale snapshot accepted") }
            catch (_: IllegalArgumentException) { }
            assertNull(repo.readFrontLandmarks(ScanOwner.Guest, id))
            repo.deleteAll()
            assertNull(repo.readFrontLandmarks(ScanOwner.Guest, id))
        } finally { repo.deleteAll(); repo.close() }
    }
}
