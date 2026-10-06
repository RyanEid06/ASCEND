@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.ascend.mobile.ui.vision

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ascend.mobile.core.vision.*
import kotlin.math.roundToInt

@Composable
fun FrontLandmarkRoute(scanId: String, onBack: () -> Unit, onProfile: () -> Unit = {}) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val viewModel: FrontLandmarkViewModel = viewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(scanId) { viewModel.load(scanId) }
    DisposableEffect(viewModel) { onDispose { viewModel.release() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Front landmarks") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onProfile) { Text("Confirm profile points") }
            when (val current = state) {
                FrontPreviewState.Loading -> { CircularProgressIndicator(); Text("Finding front landmarks on your device…") }
                is FrontPreviewState.Failed -> {
                    Text(failureMessage(current.reason))
                    Button(onClick = { viewModel.load(scanId) }) { Text("Try again") }
                }
                is FrontPreviewState.Ready -> {
                    FrontLandmarkOverlay(current, Modifier.fillMaxWidth().heightIn(max = 640.dp).aspectRatio(3f / 4f))
                    val problems = current.snapshot.qualityIssues - FrontQualityIssue.CONFIDENCE_UNAVAILABLE
                    Text(if (problems.isEmpty()) "Front landmarks detected." else "This photo needs attention before measurement.")
                    if (FrontQualityIssue.POSE_OUT_OF_RANGE in problems) Text("Retake with your head level and facing forward.")
                    if (FrontQualityIssue.OFF_CENTER in problems) Text("Center your face in the photo.")
                    if (FrontQualityIssue.INSUFFICIENT_RESOLUTION in problems) Text("Use a higher-resolution front photo.")
                    Text("Landmark reliability is still being validated. Measurements and scores are not available yet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Image and points share the inverse geometry transform, including aspect fit and letterboxing. */
@Composable
private fun FrontLandmarkOverlay(state: FrontPreviewState.Ready, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val image = remember(state.bitmap) { state.bitmap.asImageBitmap() }
    Canvas(modifier) {
        if (size.width <= 0 || size.height <= 0 || state.bitmap.isRecycled) return@Canvas
        val fit = FrontOverlayTransform.fit(state.snapshot, size.width.toDouble(), size.height.toDouble())
        drawImage(image, dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
            dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()))
        state.snapshot.points.indices.forEach { index ->
            val p = fit.project(state.snapshot.analysisPoint(index))
            drawCircle(color, radius = 1.5.dp.toPx(), center = Offset(p.x.toFloat(), p.y.toFloat()))
        }
    }
}

private fun failureMessage(reason: FrontFailure?) = when (reason) {
    FrontFailure.NO_FACE -> "No face was detected. Retake with one face clearly visible."
    FrontFailure.MULTIPLE_FACES -> "More than one face was detected. Use a photo with only one person."
    FrontFailure.INVALID_MESH, FrontFailure.POSE_UNAVAILABLE -> "This face could not be measured reliably. Retake facing forward in even lighting."
    FrontFailure.MODEL_UNAVAILABLE -> "The front landmark model is unavailable. Try reopening the app."
    FrontFailure.INFERENCE_FAILED -> "Front landmark detection could not finish. Try again."
    null -> "This local photo is unavailable or has changed. Return to your scans and try again."
}
