package app.ascend.mobile.storage

import android.content.Context
import android.net.Uri
import app.ascend.mobile.core.data.*
import app.ascend.mobile.core.model.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded URI bridge for WP05; never persists source URI/EXIF or deletes gallery originals. */
class LocalCaptureImporter(
    private val context: Context,
    private val repository: LocalScanRepository,
    private val imagePolicy: ImageStoragePolicy,
) {
    suspend fun persistFromUri(
        owner: ScanOwner,
        scanId: String,
        view: CaptureView,
        uri: Uri,
        crop: CaptureCrop,
        origin: CaptureOrigin,
        profileSide: ProfileSide?,
        atEpochMillis: Long,
    ): LocalScan = withContext(Dispatchers.IO) {
        val cameraFile = if (origin == CaptureOrigin.CAMERA) {
            require(uri.scheme == "file")
            val file = File(requireNotNull(uri.path)).canonicalFile
            require(file.parentFile == File(context.cacheDir, "ascend_capture").canonicalFile)
            file
        } else {
            require(uri.scheme == "content")
            null
        }
        val source = cameraFile?.inputStream() ?: requireNotNull(context.contentResolver.openInputStream(uri))
        val encoded = source.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size().toLong() + count > imagePolicy.maxEncodedBytes) throw CaptureRejected(setOf(RetakeReason.INVALID_IMAGE))
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        try {
            val scan = repository.putCapture(owner, scanId, LocalCapture(view, encoded, crop, origin, profileSide), atEpochMillis)
            // A returned scan has committed an encrypted asset, including rejected/pending validation.
            if (cameraFile != null) check(cameraFile.delete() || !cameraFile.exists()) { "Temporary capture cleanup requires retry" }
            scan
        } finally { encoded.fill(0) }
    }
}
