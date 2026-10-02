@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.ascend.mobile.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ascend.mobile.core.model.ProfileSide
import app.ascend.mobile.ui.adaptive.AscendWindowWidthClass
import kotlin.math.max

@Composable
fun CaptureRoute(
    windowWidthClass: AscendWindowWidthClass,
    onExit: () -> Unit,
    onCaptureReady: (CaptureReadyPayload) -> Unit,
) {
    val viewModel: CaptureViewModel = viewModel()
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    var galleryRole by remember { mutableStateOf<CaptureRole?>(null) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val role = galleryRole
        galleryRole = null
        if (role != null) {
            if (uri == null) {
                viewModel.galleryCancelled(role)
            } else {
                viewModel.mediaSelected(
                    role = role,
                    source = CaptureSource.GALLERY,
                    uri = uri.toString(),
                )
            }
        }
    }

    fun pickGallery(role: CaptureRole) {
        viewModel.cancelCountdown()
        galleryRole = role
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun handleBack() {
        val reviewRole = (state.step as? CaptureStep.Review)?.role
        if (reviewRole != null) {
            deleteTemporaryCaptureIfOwned(context, state.mediaFor(reviewRole))
        }
        if (!viewModel.onBack()) {
            onExit()
        }
    }

    BackHandler(onBack = ::handleBack)

    CaptureScreen(
        windowWidthClass = windowWidthClass,
        state = state,
        effects = viewModel.effects,
        onBack = ::handleBack,
        onSelectSource = viewModel::selectPreferredSource,
        onContinueTutorial = viewModel::continueTutorial,
        onSelectProfileSide = viewModel::selectProfileSide,
        onOpenCamera = viewModel::openCamera,
        onPickGallery = ::pickGallery,
        onMediaSelected = { role, source, uri ->
            viewModel.mediaSelected(role, source, uri)
        },
        onCaptureRequested = viewModel::requestCapture,
        onSelectTimer = viewModel::selectTimer,
        onCameraError = viewModel::setError,
        onUpdateCrop = viewModel::updateCrop,
        onRetake = { role ->
            deleteTemporaryCaptureIfOwned(context, state.mediaFor(role))
            viewModel.retake(role)
        },
        onConfirmReview = viewModel::confirmReview,
        onCaptureReady = onCaptureReady,
    )
}

@Composable
private fun CaptureScreen(
    windowWidthClass: AscendWindowWidthClass,
    state: CaptureUiState,
    effects: kotlinx.coroutines.flow.SharedFlow<CaptureEffect>,
    onBack: () -> Unit,
    onSelectSource: (CaptureSource) -> Unit,
    onContinueTutorial: (CaptureRole) -> Unit,
    onSelectProfileSide: (ProfileSide) -> Unit,
    onOpenCamera: (CaptureRole) -> Unit,
    onPickGallery: (CaptureRole) -> Unit,
    onMediaSelected: (CaptureRole, CaptureSource, String) -> Unit,
    onCaptureRequested: (CaptureRole) -> Unit,
    onSelectTimer: (CaptureTimerOption) -> Unit,
    onCameraError: (String?) -> Unit,
    onUpdateCrop: (CaptureRole, CropTransform) -> Unit,
    onRetake: (CaptureRole) -> Unit,
    onConfirmReview: (CaptureRole) -> Unit,
    onCaptureReady: (CaptureReadyPayload) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleForStep(state.step),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val maxWidth = when (windowWidthClass) {
                AscendWindowWidthClass.Compact -> 560.dp
                AscendWindowWidthClass.Medium -> 680.dp
                AscendWindowWidthClass.Expanded -> 760.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxWidth),
            ) {
                when (val step = state.step) {
                    CaptureStep.SourceSelection -> SourceSelectionScreen(
                        onSelectSource = onSelectSource,
                    )

                    is CaptureStep.Tutorial -> TutorialScreen(
                        role = step.role,
                        profileSide = state.profileSide,
                        onSelectProfileSide = onSelectProfileSide,
                        onContinue = { onContinueTutorial(step.role) },
                    )

                    is CaptureStep.Acquisition -> AcquisitionScreen(
                        role = step.role,
                        preferredSource = state.preferredSource,
                        profileSide = state.profileSide,
                        onOpenCamera = { onOpenCamera(step.role) },
                        onPickGallery = { onPickGallery(step.role) },
                    )

                    is CaptureStep.Camera -> CameraScreen(
                        role = step.role,
                        state = state,
                        effects = effects,
                        onMediaSelected = { uri ->
                            onMediaSelected(step.role, CaptureSource.CAMERA, uri)
                        },
                        onCaptureRequested = { onCaptureRequested(step.role) },
                        onPickGallery = { onPickGallery(step.role) },
                        onSelectTimer = onSelectTimer,
                        onCameraError = onCameraError,
                    )

                    is CaptureStep.Review -> {
                        val media = state.mediaFor(step.role)
                        if (media == null) {
                            MissingMediaScreen(onRetake = { onRetake(step.role) })
                        } else {
                            ReviewScreen(
                                media = media,
                                onCropChange = { crop -> onUpdateCrop(step.role, crop) },
                                onRetake = { onRetake(step.role) },
                                onChooseAnother = { onPickGallery(step.role) },
                                onConfirm = { onConfirmReview(step.role) },
                            )
                        }
                    }

                    CaptureStep.Ready -> {
                        val payload = state.readyPayloadOrNull()
                        if (payload == null) {
                            MissingMediaScreen(onRetake = { onRetake(CaptureRole.FRONT) })
                        } else {
                            ReadyScreen(
                                payload = payload,
                                onRetake = onRetake,
                                onContinue = { onCaptureReady(payload) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSelectionScreen(
    onSelectSource: (CaptureSource) -> Unit,
) {
    ScrollStage {
        Text(
            text = "Create a local scan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "ASCEND needs one straight front photo and one true side profile. Both stay local in this capture flow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SourceCard(
            title = "Use rear camera",
            body = "Recommended for standardized capture. Camera permission is requested only when you enter the camera.",
            button = "Camera",
            onClick = { onSelectSource(CaptureSource.CAMERA) },
        )
        SourceCard(
            title = "Choose from gallery",
            body = "Uses Android Photo Picker. ASCEND does not request broad photo-library or storage permission.",
            button = "Gallery",
            onClick = { onSelectSource(CaptureSource.GALLERY) },
        )
    }
}

@Composable
private fun SourceCard(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClick,
            ) {
                Text(button)
            }
        }
    }
}

@Composable
private fun TutorialScreen(
    role: CaptureRole,
    profileSide: ProfileSide,
    onSelectProfileSide: (ProfileSide) -> Unit,
    onContinue: () -> Unit,
) {
    ScrollStage {
        TutorialIllustration(role = role)
        Text(
            text = if (role == CaptureRole.FRONT) {
                "Set up the front photo"
            } else {
                "Set up the side profile"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (role == CaptureRole.FRONT) {
                "Keep the camera near eye level, head level, expression neutral, face straight forward, and lighting even."
            } else {
                "Keep the camera near eye level and turn to one true side profile without tilting or twisting your head."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        GuidanceCard()
        if (role == CaptureRole.PROFILE) {
            Text(
                text = "Profile direction",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileSideButton(
                    modifier = Modifier.weight(1f),
                    text = "Left side",
                    selected = profileSide == ProfileSide.LEFT,
                    onClick = { onSelectProfileSide(ProfileSide.LEFT) },
                )
                ProfileSideButton(
                    modifier = Modifier.weight(1f),
                    text = "Right side",
                    selected = profileSide == ProfileSide.RIGHT,
                    onClick = { onSelectProfileSide(ProfileSide.RIGHT) },
                )
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun GuidanceCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Standardized capture",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text("• Rear camera preferred")
            Text("• Roughly 2 m away — guidance only, not a measured distance")
            Text("• Remove major facial occlusions where practical")
            Text("• Hold still and use even lighting")
        }
    }
}

@Composable
private fun ProfileSideButton(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) {
            Text(text)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) {
            Text(text)
        }
    }
}

@Composable
private fun AcquisitionScreen(
    role: CaptureRole,
    preferredSource: CaptureSource?,
    profileSide: ProfileSide,
    onOpenCamera: () -> Unit,
    onPickGallery: () -> Unit,
) {
    ScrollStage {
        Text(
            text = if (role == CaptureRole.FRONT) "Front photo" else "Side profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (role == CaptureRole.FRONT) {
                "Capture or import the straight front view."
            } else {
                "Capture or import the ${profileSide.name.lowercase()} profile."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preferredSource == CaptureSource.GALLERY) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickGallery,
            ) { Text("Choose from gallery") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenCamera,
            ) { Text("Use rear camera instead") }
        } else {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenCamera,
            ) { Text("Open rear camera") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickGallery,
            ) { Text("Choose from gallery instead") }
        }
        Text(
            text = "You can replace this view later without restarting the other one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CameraScreen(
    role: CaptureRole,
    state: CaptureUiState,
    effects: kotlinx.coroutines.flow.SharedFlow<CaptureEffect>,
    onMediaSelected: (String) -> Unit,
    onCaptureRequested: () -> Unit,
    onPickGallery: () -> Unit,
    onSelectTimer: (CaptureTimerOption) -> Unit,
    onCameraError: (String?) -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            onCameraError("Camera access is off. Grant access or choose a gallery image.")
        } else {
            onCameraError(null)
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!permissionGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Camera access needed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.errorMessage
                            ?: "ASCEND only needs camera access while you use the capture screen.",
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    ) {
                        Text("Grant camera access")
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onPickGallery,
                    ) {
                        Text("Choose from gallery")
                    }
                }
            }
            return@Column
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.onBackground),
        ) {
            CameraPreview(
                role = role,
                effects = effects,
                modifier = Modifier.fillMaxSize(),
                onImageCaptured = onMediaSelected,
                onCaptureError = onCameraError,
            )
            CaptureGuideOverlay(role = role)

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp),
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    text = (state.guidance ?: defaultGuidance(role)).displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }

            state.countdownSeconds?.let { remaining ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(96.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = remaining.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        state.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        TimerSelector(
            selected = state.timerOption,
            onSelected = onSelectTimer,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onPickGallery,
            ) {
                Text("Gallery")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = state.countdownSeconds == null,
                onClick = onCaptureRequested,
            ) {
                Text(if (state.countdownSeconds == null) "Take photo" else "Counting…")
            }
        }
    }
}

@Composable
private fun TimerSelector(
    selected: CaptureTimerOption,
    onSelected: (CaptureTimerOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaptureTimerOption.entries.forEach { option ->
            val label = if (option.seconds == 0) "Timer off" else "${option.seconds}s"
            if (selected == option) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(label) }
            } else {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onSelected(option) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun CaptureGuideOverlay(role: CaptureRole) {
    val lineColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
    ) {
        val ovalWidth = size.width * 0.58f
        val ovalHeight = size.height * 0.62f
        val left = (size.width - ovalWidth) / 2f
        val top = (size.height - ovalHeight) / 2f
        drawOval(
            color = lineColor.copy(alpha = 0.78f),
            topLeft = Offset(left, top),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 4f),
        )
        drawLine(
            color = lineColor.copy(alpha = 0.45f),
            start = Offset(size.width * 0.18f, size.height * 0.43f),
            end = Offset(size.width * 0.82f, size.height * 0.43f),
            strokeWidth = 2f,
        )
        if (role == CaptureRole.PROFILE) {
            drawLine(
                color = lineColor.copy(alpha = 0.78f),
                start = Offset(size.width * 0.57f, size.height * 0.42f),
                end = Offset(size.width * 0.66f, size.height * 0.49f),
                strokeWidth = 4f,
            )
        }
    }
}

@Composable
private fun ReviewScreen(
    media: CaptureMedia,
    onCropChange: (CropTransform) -> Unit,
    onRetake: () -> Unit,
    onChooseAnother: () -> Unit,
    onConfirm: () -> Unit,
) {
    val decoded by rememberDecodedImage(media.uri)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Center the ${media.role.displayName()}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Pinch to zoom and drag to center. The image keeps its aspect ratio; this step records crop metadata rather than distorting the source.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when (val result = decoded) {
                null -> CircularProgressIndicator()
                is PreviewDecodeResult.Failure -> {
                    Card {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Image unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(result.message)
                        }
                    }
                }
                is PreviewDecodeResult.Success -> CroppableImage(
                    image = result.image,
                    crop = media.crop,
                    onCropChange = onCropChange,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onRetake,
            ) { Text("Retake") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onChooseAnother,
            ) { Text("Choose another") }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = decoded is PreviewDecodeResult.Success,
            onClick = onConfirm,
        ) {
            Text(
                if (media.role == CaptureRole.FRONT) {
                    "Confirm front"
                } else {
                    "Confirm profile"
                },
            )
        }
    }
}

@Composable
private fun CroppableImage(
    image: ImageBitmap,
    crop: CropTransform,
    onCropChange: (CropTransform) -> Unit,
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val width = max(1, viewportSize.width)
        val height = max(1, viewportSize.height)
        onCropChange(
            crop.copy(
                scale = (crop.scale * zoomChange).coerceIn(1f, 4f),
                offsetXFraction = (
                    crop.offsetXFraction + panChange.x / width.toFloat()
                    ).coerceIn(-1f, 1f),
                offsetYFraction = (
                    crop.offsetYFraction + panChange.y / height.toFloat()
                    ).coerceIn(-1f, 1f),
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .aspectRatio(crop.viewportAspectRatio)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportSize = it }
                .graphicsLayer {
                    scaleX = crop.scale
                    scaleY = crop.scale
                    translationX = crop.offsetXFraction * viewportSize.width
                    translationY = crop.offsetYFraction * viewportSize.height
                }
                .transformable(transformState),
        )
    }
}

@Composable
private fun ReadyScreen(
    payload: CaptureReadyPayload,
    onRetake: (CaptureRole) -> Unit,
    onContinue: () -> Unit,
) {
    ScrollStage {
        Text(
            text = "Capture complete",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Both views are ready for the local quality/persistence boundary. Replacing either view keeps the other one intact.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReadyMediaCard(
            media = payload.front,
            subtitle = "Straight front",
            onRetake = { onRetake(CaptureRole.FRONT) },
        )
        ReadyMediaCard(
            media = payload.profile,
            subtitle = "${payload.profileSide.name.lowercase()} side profile",
            onRetake = { onRetake(CaptureRole.PROFILE) },
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ReadyMediaCard(
    media: CaptureMedia,
    subtitle: String,
    onRetake: () -> Unit,
) {
    val decoded by rememberDecodedImage(media.uri)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = media.role.displayName().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val result = decoded) {
                is PreviewDecodeResult.Success -> {
                    Image(
                        bitmap = result.image,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                is PreviewDecodeResult.Failure -> Text(result.message)
                null -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRetake,
            ) {
                Text("Retake only this view")
            }
        }
    }
}

@Composable
private fun MissingMediaScreen(
    onRetake: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Capture state needs recovery",
                style = MaterialTheme.typography.titleLarge,
            )
            Button(onClick = onRetake) {
                Text("Return to capture")
            }
        }
    }
}

@Composable
private fun TutorialIllustration(role: CaptureRole) {
    val transition = rememberInfiniteTransition(label = "capture tutorial")
    val phoneOffset by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "eye level guidance",
    )
    val lineColor = MaterialTheme.colorScheme.primary
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val headCenter = Offset(size.width * 0.45f, size.height * 0.47f)
        drawOval(
            color = neutralColor,
            topLeft = Offset(headCenter.x - 48f, headCenter.y - 68f),
            size = Size(96f, 136f),
            style = Stroke(width = 5f),
        )
        if (role == CaptureRole.PROFILE) {
            drawLine(
                color = neutralColor,
                start = Offset(headCenter.x + 35f, headCenter.y - 8f),
                end = Offset(headCenter.x + 58f, headCenter.y + 4f),
                strokeWidth = 5f,
            )
        }
        drawLine(
            color = lineColor.copy(alpha = 0.45f),
            start = Offset(size.width * 0.12f, headCenter.y - 18f),
            end = Offset(size.width * 0.88f, headCenter.y - 18f),
            strokeWidth = 3f,
        )

        val phoneTop = headCenter.y - 64f + phoneOffset
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(size.width * 0.76f, phoneTop),
            size = Size(44f, 82f),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 5f),
        )
    }
}

@Composable
private fun ScrollStage(
    content: @Composable Column.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun rememberDecodedImage(
    uri: String,
): androidx.compose.runtime.State<PreviewDecodeResult?> {
    val context = LocalContext.current.applicationContext
    return produceState<PreviewDecodeResult?>(
        initialValue = null,
        key1 = uri,
        key2 = context,
    ) {
        value = SafeImageDecoder.decodeForPreview(context, uri)
    }
}

private fun titleForStep(step: CaptureStep): String =
    when (step) {
        CaptureStep.SourceSelection -> "New scan"
        is CaptureStep.Tutorial -> "${step.role.displayName()} guide"
        is CaptureStep.Acquisition -> "${step.role.displayName()} photo"
        is CaptureStep.Camera -> "${step.role.displayName()} camera"
        is CaptureStep.Review -> "Review ${step.role.displayName()}"
        CaptureStep.Ready -> "Ready"
    }

private fun CaptureRole.displayName(): String =
    when (this) {
        CaptureRole.FRONT -> "front"
        CaptureRole.PROFILE -> "profile"
    }

private fun defaultGuidance(role: CaptureRole): CaptureGuidanceMessage =
    when (role) {
        CaptureRole.FRONT -> CaptureGuidanceMessage.CENTER_FACE
        CaptureRole.PROFILE -> CaptureGuidanceMessage.TURN_TO_SIDE
    }
