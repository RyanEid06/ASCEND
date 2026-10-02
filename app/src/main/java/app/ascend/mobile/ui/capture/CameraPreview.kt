package app.ascend.mobile.ui.capture

import android.content.Context
import android.net.Uri
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun CameraPreview(
    role: CaptureRole,
    effects: SharedFlow<CaptureEffect>,
    modifier: Modifier = Modifier,
    onImageCaptured: (String) -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        val listener = Runnable {
            if (disposed) return@Runnable

            try {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                if (disposed) return@Runnable

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
                imageCaptureState.value = imageCapture
            } catch (_: Exception) {
                imageCaptureState.value = null
                onCaptureError("Rear camera is unavailable. You can choose a gallery image instead.")
            }
        }

        providerFuture.addListener(listener, mainExecutor)

        onDispose {
            disposed = true
            imageCaptureState.value = null
            provider?.unbindAll()
        }
    }

    LaunchedEffect(effects, role) {
        effects.collect { effect ->
            if (effect is CaptureEffect.TriggerShutter && effect.role == role) {
                val imageCapture = imageCaptureState.value
                if (imageCapture == null) {
                    onCaptureError("Camera is still starting. Try again.")
                } else {
                    imageCapture.targetRotation =
                        previewView.display?.rotation ?: Surface.ROTATION_0
                    takeTemporaryPhoto(
                        context = context,
                        role = role,
                        imageCapture = imageCapture,
                        onImageCaptured = onImageCaptured,
                        onCaptureError = onCaptureError,
                    )
                }
            }
        }
    }
}

private fun takeTemporaryPhoto(
    context: Context,
    role: CaptureRole,
    imageCapture: ImageCapture,
    onImageCaptured: (String) -> Unit,
    onCaptureError: (String) -> Unit,
) {
    val directory = File(context.cacheDir, "ascend_capture").apply { mkdirs() }
    val outputFile = runCatching {
        File.createTempFile(
            "ascend_${role.name.lowercase()}_",
            ".jpg",
            directory,
        )
    }.getOrNull()

    if (outputFile == null) {
        onCaptureError("Could not create a private temporary capture.")
        return
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(outputFile).toString())
            }

            override fun onError(exception: ImageCaptureException) {
                outputFile.delete()
                onCaptureError("Photo capture failed. Try again.")
            }
        },
    )
}

fun deleteTemporaryCaptureIfOwned(
    context: Context,
    media: CaptureMedia?,
) {
    if (media?.source != CaptureSource.CAMERA) return

    val uri = runCatching { Uri.parse(media.uri) }.getOrNull() ?: return
    if (uri.scheme != "file") return

    val file = uri.path?.let(::File) ?: return
    val expectedParent = File(context.cacheDir, "ascend_capture")
    val owned = runCatching {
        file.canonicalFile.parentFile == expectedParent.canonicalFile
    }.getOrDefault(false)

    if (owned) {
        runCatching { file.delete() }
    }
}
