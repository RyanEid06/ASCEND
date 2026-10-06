@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.ascend.mobile.ui.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.core.profile.*

@Composable
internal fun ProfileAssistRoute(scanId: String, onBack: () -> Unit) {
    val activity = LocalContext.current.activity()
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val viewModel: ProfileAssistViewModel = viewModel()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.replacePhoto(uri.toString())
    }
    LaunchedEffect(scanId) { viewModel.load(scanId) }
    DisposableEffect(viewModel) { onDispose { viewModel.release() } }
    ProfileAssistScreen(state, onBack, viewModel::chooseSide, viewModel::selectPoint, viewModel::preview,
        viewModel::confirmPoint, viewModel::cancelPreview,
        onReplace = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRetry = { viewModel.load(scanId) })
}

@Composable
internal fun ProfileAssistScreen(state: ProfileAssistState, onBack: () -> Unit,
    onSide: (ProfileSide, ProfileFacing) -> Unit, onSelect: (Int) -> Unit, onPreview: (Point2) -> Unit,
    onConfirm: () -> Unit, onCancelPreview: () -> Unit, onReplace: () -> Unit, onRetry: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Profile confirmation") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (state) {
                ProfileAssistState.Loading -> { CircularProgressIndicator(); Text("Opening your local profile…") }
                is ProfileAssistState.Failed -> {
                    Text(state.message)
                    Button(onClick = onRetry) { Text("Try again") }
                    TextButton(onClick = onBack) { Text("Return to scans") }
                }
                is ProfileAssistState.Ready -> {
                    Text("Guided profile preview", style = MaterialTheme.typography.titleLarge)
                    Text("The highlighted guides are illustrative and have not been validated for measurements. Confirming points does not produce a score.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (state.readOnly) Text("Saved analysis — read only. Start a new scan to make changes.")
                    state.activePoint?.let { Text("${state.activeIndex + 1}/${state.display!!.requiredPoints.size} · ${pointName(it)}",
                        style = MaterialTheme.typography.titleMedium) }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        if (maxWidth < 560.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ProfileGuideOverlay(state, onPreview, onConfirm, onCancelPreview, Modifier.fillMaxWidth().height(360.dp))
                                ProfileControls(state, onSide, onSelect, onPreview, onConfirm, onReplace, onBack)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                ProfileGuideOverlay(state, onPreview, onConfirm, onCancelPreview, Modifier.weight(1f).height(500.dp))
                                Column(Modifier.weight(1f)) { ProfileControls(state, onSide, onSelect, onPreview, onConfirm, onReplace, onBack) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileControls(state: ProfileAssistState.Ready, onSide: (ProfileSide, ProfileFacing) -> Unit,
    onSelect: (Int) -> Unit, onPreview: (Point2) -> Unit, onConfirm: () -> Unit, onReplace: () -> Unit, onBack: () -> Unit) {
    var choosingSide by rememberSaveable(state.imageRevision) { mutableStateOf(false) }
    var sideName by rememberSaveable(state.imageRevision) { mutableStateOf<String?>(null) }
    var facingName by rememberSaveable(state.imageRevision) { mutableStateOf<String?>(null) }
    val session = state.display
    val enabled = !state.busy && !state.readOnly
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session == null || choosingSide) {
            Text("Which side of your face is visible?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileSide.entries.forEach { side -> FilterChip(selected = sideName == side.name,
                    onClick = { sideName = side.name }, label = { Text("${side.name.lowercase().replaceFirstChar(Char::uppercase)} side") }, enabled = enabled) }
            }
            Text("Which way does your nose point in this photo?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileFacing.entries.forEach { facing -> FilterChip(selected = facingName == facing.name,
                    onClick = { facingName = facing.name }, label = { Text("Nose points ${facing.name.lowercase()}") }, enabled = enabled) }
            }
            Button(onClick = {
                onSide(ProfileSide.valueOf(requireNotNull(sideName)), ProfileFacing.valueOf(requireNotNull(facingName)))
                choosingSide = false
            }, enabled = enabled && sideName != null && facingName != null) { Text("Confirm side and direction") }
        } else {
            Text("${session.revision.input.side!!.name.lowercase().replaceFirstChar(Char::uppercase)} profile · nose points ${session.facing.name.lowercase()}",
                style = MaterialTheme.typography.titleMedium)
            val id = state.activePoint!!
            Text("Point ${state.activeIndex + 1} of ${session.requiredPoints.size}")
            Text(pointName(id), style = MaterialTheme.typography.titleLarge)
            Text(if (session.revision.original.points[id]?.source == ProfilePointSource.AUTOMATIC)
                "A point was proposed. Check its location, then confirm." else "Move the highlighted point within its guide, then confirm.")
            Text("If the guide does not match your photo, choose another clear profile. These preview bounds cannot be enlarged.",
                style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            fun move(x: Double, y: Double) {
                val point = session.imagePoints()[id] ?: session.policy.zones.getValue(id).missingPointAnchor
                val resolution = session.revision.input.resolution
                onPreview(Point2(point.x + x * resolution.shortEdge / resolution.width, point.y + y * resolution.shortEdge / resolution.height))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { move(0.0, -.01) }, enabled = enabled) { Text("Move up") }
                OutlinedButton(onClick = { move(0.0, .01) }, enabled = enabled) { Text("Move down") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { move(-.01, 0.0) }, enabled = enabled) { Text("Move left") }
                OutlinedButton(onClick = { move(.01, 0.0) }, enabled = enabled) { Text("Move right") }
            }
            Button(onClick = onConfirm, enabled = enabled) { Text("Confirm point") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSelect(state.activeIndex - 1) }, enabled = enabled && state.activeIndex > 0) { Text("Previous") }
                TextButton(onClick = { onSelect(state.activeIndex + 1) }, enabled = enabled && state.activeIndex < session.requiredPoints.lastIndex) { Text("Next") }
            }
            if (session.complete && !state.previewing) {
                Text("All profile points confirmed. Measurements are awaiting profile validation.", color = MaterialTheme.colorScheme.primary)
                Button(onClick = onBack, enabled = !state.busy) { Text("Done") }
            }
            // The engine consumes exactly the displayed revision. Null policy keeps real-photo results unavailable.
            if (session.measure(null).metrics.none { it.value != null }) Text("Measurements are not available for this preview.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { choosingSide = true; sideName = null; facingName = null }, enabled = enabled) { Text("Change side or direction") }
        }
        TextButton(onClick = onReplace, enabled = enabled) { Text("Choose another profile photo") }
        Text("Your photo and confirmed points stay encrypted on this device.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileGuideOverlay(state: ProfileAssistState.Ready, onPreview: (Point2) -> Unit,
    onConfirm: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val image = remember(state.bitmap) { state.bitmap.asImageBitmap() }
    val session = state.display
    val active = state.activePoint
    val color = MaterialTheme.colorScheme.primary
    val region = MaterialTheme.colorScheme.tertiary
    val gesture = if (active == null || session == null || state.readOnly || state.busy) Modifier else Modifier.pointerInput(
        state.imageRevision, active, state.session?.revisionToken) {
        var dragging = false
        var target = Offset.Zero
        fun fit() = ProfileImageFit.fit(session.revision.input.resolution, size.width.toDouble(), size.height.toDouble())
        detectDragGestures(onDragStart = { pointer ->
            val point = state.session!!.imagePoints()[active] ?: session.policy.zones.getValue(active).missingPointAnchor
            val screen = fit().project(point)
            target = Offset(screen.x.toFloat(), screen.y.toFloat())
            dragging = (pointer - target).getDistance() <= 40.dp.toPx()
        }, onDragCancel = { dragging = false; onCancel() }, onDragEnd = {
            if (dragging) onConfirm()
            dragging = false
        }) { change, delta ->
            if (dragging) {
                change.consume(); target += delta
                onPreview(fit().unproject(Point2(target.x.toDouble(), target.y.toDouble())))
            }
        }
    }
    Canvas(modifier.then(gesture).semantics { contentDescription = "Profile photo with bounded confirmation guides" }) {
        if (state.bitmap.isRecycled || size.width <= 0 || size.height <= 0) return@Canvas
        val resolution = PixelResolution(state.bitmap.width, state.bitmap.height)
        val fit = ProfileImageFit.fit(resolution, size.width.toDouble(), size.height.toDouble())
        fun offset(point: Point2): Offset { val p = fit.project(point); return Offset(p.x.toFloat(), p.y.toFloat()) }
        val imageScale = (fit.width / resolution.width).toFloat()
        withTransform({ translate(fit.left.toFloat(), fit.top.toFloat()); scale(imageScale, imageScale, Offset.Zero) }) { drawImage(image) }
        if (session != null) {
            val points = session.imagePoints() // Same revision.input is supplied to WP10; never the old proposals.
            ProfileCatalog.mappings.forEach { mapping -> mapping.anglePoints?.zipWithNext()?.forEach { (a, b) ->
                if (a in points && b in points) drawLine(color.copy(alpha = .65f), offset(points.getValue(a)), offset(points.getValue(b)), 2.dp.toPx())
            } }
            points.forEach { (id, point) -> drawCircle(color, if (id == active) 7.dp.toPx() else 3.dp.toPx(), offset(point)) }
            active?.let { id ->
                session.policy.zones[id]?.let { zone ->
                    val topLeft = offset(Point2(zone.minimumX, zone.minimumY))
                    val bottomRight = offset(Point2(zone.maximumX, zone.maximumY))
                    val regionSize = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
                    drawRect(region.copy(alpha = .12f), topLeft, regionSize)
                    drawRect(region, topLeft, regionSize, style = Stroke(2.dp.toPx()))
                    if (id !in points) drawCircle(region, 7.dp.toPx(), offset(zone.missingPointAnchor), style = Stroke(2.dp.toPx()))
                    val anchor = offset(session.revision.original.points[id]?.point ?: zone.missingPointAnchor)
                    drawCircle(region.copy(alpha = .35f), (zone.maximumShortEdgeDisplacement * resolution.shortEdge * imageScale).toFloat(),
                        anchor, style = Stroke(1.dp.toPx()))
                }
            }
        }
    }
}

internal fun pointName(id: LandmarkId) = when (id) {
    Landmarks.GLABELLA -> "Forehead contour"
    Landmarks.NASION -> "Dip at the nose bridge"
    Landmarks.SUPRATIP -> "Nose just above the tip"
    Landmarks.COLUMELLA -> "Underside of the nose"
    Landmarks.SUBNASALE -> "Where the nose meets the upper lip"
    Landmarks.LABRALE_SUPERIUS -> "Upper lip contour"
    Landmarks.LABRALE_INFERIUS -> "Lower lip contour"
    Landmarks.SUBLABIALE -> "Dip below the lower lip"
    Landmarks.POGONION -> "Front of the chin"
    Landmarks.RAMUS_REFERENCE -> "Visible back edge of the jaw"
    ProfileCatalog.visibleGonion -> "Visible jaw corner"
    Landmarks.MANDIBULAR_BORDER_REFERENCE -> "Lower edge of the jaw"
    else -> "Profile point"
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
