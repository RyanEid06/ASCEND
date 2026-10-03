package app.ascend.mobile.storage

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.ascend.mobile.core.model.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** CI invokes each method in a separate instrumentation process with force-stop between them. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class StorageProcessDeathTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(target) {
        override fun getNoBackupFilesDir(): File = File(target.noBackupFilesDir, "wp06-process-test").apply { mkdirs() }
        override fun getCacheDir(): File = File(target.cacheDir, "wp06-process-test").apply { mkdirs() }
        override fun getPackageName(): String = "${target.packageName}.wp06process"
    }
    private val marker get() = File(context.noBackupFilesDir, "scan-id.txt")

    @Test fun a_seed() = runBlocking {
        val repository = LocalScanStorageTest.open(context)
        repository.deleteAll()
        val id = repository.create(ScanOwner.Guest, ReferenceModel.MALE, 0).session.id
        repository.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.FRONT), 1)
        repository.putCapture(ScanOwner.Guest, id, LocalScanStorageTest.capture(CaptureView.PROFILE), 2)
        repository.advance(ScanOwner.Guest, id, 3)
        repository.advance(ScanOwner.Guest, id, 4)
        repository.advance(ScanOwner.Guest, id, 5)
        repository.complete(ScanOwner.Guest, LocalScanStorageTest.analysisFixture(id), 6)
        marker.writeText(id) // Synthetic fixture ID only; no photo or key material.
        repository.close()
    }

    @Test fun b_verifyAfterRestart() = runBlocking {
        val repository = LocalScanStorageTest.open(context)
        try {
            val id = marker.readText()
            val scan = repository.recover(ScanOwner.Guest, 7).single()
            assertEquals(id, scan.session.id)
            assertEquals(ScanState.COMPLETE, scan.session.state)
            assertEquals(LocalScanStorageTest.analysisFixture(id), repository.readAnalysis(ScanOwner.Guest, id))
            assertTrue(repository.readPhoto(ScanOwner.Guest, id, CaptureView.FRONT).isNotEmpty())
            assertTrue(repository.readPhoto(ScanOwner.Guest, id, CaptureView.PROFILE).isNotEmpty())
        } finally { repository.deleteAll(); repository.close(); marker.delete() }
    }
}
