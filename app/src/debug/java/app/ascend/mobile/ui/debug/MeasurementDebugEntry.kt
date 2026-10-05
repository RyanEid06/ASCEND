package app.ascend.mobile.ui.debug

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ascend.mobile.core.front.*
import app.ascend.mobile.core.geometry.Point2

@Composable
fun MeasurementDebugEntry(onBack: () -> Unit) {
    val context = LocalContext.current
    val fixture = remember { context.assets.open("synthetic-front-v1.json").use { FrontCodec.fixture(it.readBytes()) } }
    MeasurementDebugScreen(fixture.input, fixture.policy, onBack)
}

/** WP07 may supply a local FrontInput here after the shared seam is agreed. No logging/export or media upload. */
@Composable
fun MeasurementDebugScreen(input: FrontInput, policy: FrontPolicy?, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.hostActivity()
    if (input.origin == FixtureOrigin.CONSENTED_LOCAL && activity == null) {
        Text("Secure local measurement surface unavailable")
        return
    }
    DisposableEffect(context, input.origin) {
        val window = activity?.window
        val alreadySecure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        val sensitive = input.origin == FixtureOrigin.CONSENTED_LOCAL
        if (sensitive) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (sensitive && !alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val state: MeasurementDebugViewModel = viewModel()
    LaunchedEffect(input) { state.useInput(input) }
    val revision = state.revision ?: return
    var lowConfidence by rememberSaveable(input.imageRevision) { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf("candidate.front.canthal_inclination") }
    val current = revision.current().let { if (lowConfidence) it.copy(overallConfidence = null) else it }
    val report = FrontMeasurements(policy).measure(current, revision.corrections.map { it.landmarkId }.toSet())
    val metric = report.metrics.single { it.metricId == selected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Front measurement inspector", style = MaterialTheme.typography.headlineSmall)
        Text(if (input.origin == FixtureOrigin.SYNTHETIC) "Synthetic formula verification only. Real-capture reliability is unproven." else "Local consented capture. Reliability requires separate repeated-capture evidence.")
        Text("Model: ${input.modelVersion}\nExtractor: ${input.extractorVersion}\nPolicy: ${policy?.version ?: "missing"}\nCorrection revision: ${revision.revision}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { lowConfidence = !lowConfidence }) { Text(if (lowConfidence) "Restore confidence" else "Remove confidence") }
            TextButton(onClick = { state.reset(input); lowConfidence = false }) { Text("Reset fixture") }
        }
        // Bounds here are an explicitly synthetic demonstration, never available for real captures.
        if (input.origin == FixtureOrigin.SYNTHETIC) {
            TextButton(onClick = {
                val id = "lateral_canthus_left"
                val original = input.landmarks.getValue(id)
                val targetY = original.y - 0.01
                if (revision.current().landmarks.getValue(id).y != targetY) state.update(revision.correct(
                    CorrectionPolicy("synthetic-debug-v1", "Synthetic movement demonstration only", true,
                        mapOf(id to CorrectionZone(original.x - 0.02, original.x + 0.02, original.y - 0.02, original.y + 0.02, 0.02))),
                    id, original.x, targetY, revision.revision + 1))
            }) { Text("Apply bounded synthetic correction") }
        }
        Text("${metric.metricId}\n${metric.value?.let { "$it ${metric.unit}" } ?: "Unavailable: ${metric.failure}"}\nMode: ${metric.mode}")
        MeasurementPointDiagram(metric.sourcePoints)
        Text("Roll-normalized isotropic points used by this formula. Select a metric below.")
        metric.sourcePoints.forEach { (id, point) ->
            Text("$id: (${point.x}, ${point.y}), confidence ${current.landmarks[id]?.confidence ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Geometry: ${report.geometryVersion}\nImage: ${input.width} × ${input.height}\nResidual pose: ${input.residualPose ?: "unknown"}\nConfidence method: ${input.confidenceMethodVersion}", style = MaterialTheme.typography.bodySmall)
        report.metrics.forEach { item ->
            TextButton(onClick = { selected = item.metricId }) {
                Text("${item.metricId.removePrefix("candidate.front.")}: ${item.value?.toString() ?: item.failure?.name}")
            }
        }
    }
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.hostActivity() else null
    else -> null
}

/** Points/audit stay in memory across rotation, never in an unencrypted saved-instance bundle. */
class MeasurementDebugViewModel : ViewModel() {
    var revision by mutableStateOf<FrontRevision?>(null)
        private set
    fun useInput(input: FrontInput) { if (revision?.original != input) reset(input) }
    fun reset(input: FrontInput) { revision = FrontRevision(1, input.copy(landmarks = input.landmarks.toMap()), emptyList()) }
    fun update(value: FrontRevision) { revision = value }
}

@Composable
private fun MeasurementPointDiagram(points: Map<String, Point2>) {
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        if (points.isEmpty()) return@Canvas
        val minX = points.values.minOf { it.x }; val maxX = points.values.maxOf { it.x }
        val minY = points.values.minOf { it.y }; val maxY = points.values.maxOf { it.y }
        val span = maxOf(maxX - minX, maxY - minY, 1e-9)
        val scale = minOf(size.width, size.height) * 0.8 / span
        // The same scale for both axes preserves angles and aspect ratio.
        points.values.forEach { point ->
            drawCircle(Color(0xFF007D98), 5.dp.toPx(), Offset(
                (size.width / 2 + (point.x - (minX + maxX) / 2) * scale).toFloat(),
                (size.height / 2 + (point.y - (minY + maxY) / 2) * scale).toFloat()))
        }
    }
}
