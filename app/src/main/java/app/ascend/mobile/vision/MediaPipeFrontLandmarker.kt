package app.ascend.mobile.vision

import android.content.Context
import android.graphics.Bitmap
import app.ascend.mobile.core.geometry.PixelResolution
import app.ascend.mobile.core.vision.*
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One IMAGE task per call; CPU inference and model resources never outlive that call. */
class MediaPipeFrontLandmarker @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun extract(bitmap: Bitmap, sourceImageSha256: String): FrontExtraction = withContext(Dispatchers.Default) {
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(2) // A one-face cap cannot detect multiple-face input.
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setOutputFacialTransformationMatrixes(true)
            .build()
        val task = try {
            val modelBytes = context.assets.open("face_landmarker.task").use { it.readBytes() }
            try { check(frontImageSha256(modelBytes) == FRONT_MODEL_SHA256) }
            finally { modelBytes.fill(0) }
            FaceLandmarker.createFromOptions(context, options)
        } catch (_: Exception) { return@withContext FrontExtraction.Failed(FrontFailure.MODEL_UNAVAILABLE) }
        catch (_: LinkageError) { return@withContext FrontExtraction.Failed(FrontFailure.MODEL_UNAVAILABLE) }
        try {
            // MPImage owns/recycles its bitmap when closed. Keep the caller's display image alive.
            val inferenceBitmap = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            val image = try { BitmapImageBuilder(inferenceBitmap).build() }
                catch (failure: Exception) { inferenceBitmap.recycle(); throw failure }
            try {
                val result = task.detect(image)
                val matrices = result.facialTransformationMatrixes().orElse(emptyList())
                val faces = result.faceLandmarks().mapIndexed { index, mesh -> RawFrontFace(
                    mesh.map { MeshPoint(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) },
                    matrices.getOrNull(index)?.map { it.toDouble() }?.toDoubleArray(),
                ) }
                FrontLandmarkProcessor.process(faces, PixelResolution(bitmap.width, bitmap.height), sourceImageSha256)
            } finally { image.close() }
        } catch (_: Exception) { FrontExtraction.Failed(FrontFailure.INFERENCE_FAILED) }
        finally { task.close() }
    }
}
