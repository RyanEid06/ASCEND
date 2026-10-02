# WP05 Capture Experience

Status: Ryan Phase 2 lane implementation.

## Scope

WP05 owns the local capture UX only:

- standardized FRONT and PROFILE tutorials
- CameraX rear-camera preview and still capture
- Android Photo Picker import without broad media permission
- safe, bounded preview decoding with EXIF orientation handling
- crop/center metadata
- timer and retake flow
- independent FRONT/PROFILE replacement
- non-obstructive guidance presentation
- a typed `CaptureReadyPayload` handoff boundary for WP06

WP05 deliberately does not implement Room, encryption, permanent media storage, scan persistence, quality validation, MediaPipe, landmark extraction, scoring, upload, account sync, or AI.

## State model

`CaptureViewModel` owns a `StateFlow<CaptureUiState>`. The journey is represented by the sealed `CaptureStep` model rather than unrelated booleans:

`SourceSelection -> Tutorial(role) -> Acquisition(role) -> Camera/Review(role) -> Ready`

The FRONT and PROFILE media slots are independent. Retaking one role clears only that role. A confirmed opposite role remains intact.

The Navigation 3 entry already uses a ViewModel-store decorator, so ordinary recomposition, rotation, and window resizing do not recreate the capture ViewModel while the route remains on the back stack.

## Camera and gallery boundary

Camera capture is lifecycle-bound through `ProcessCameraProvider` and `PreviewView`. The rear camera is the default. Camera resources are unbound when the camera composable leaves composition.

Camera output is written only to an app-private cache directory for WP05 handoff. No capture is published to the public gallery.

Gallery import uses `ActivityResultContracts.PickVisualMedia`; WP05 does not request storage or broad photo-library access.

Both sources enter the same review/crop path.

## Image safety and crop metadata

`SafeImageDecoder`:

- rejects non-image MIME types when one is available
- validates image dimensions before allocating a preview
- rejects extreme source dimensions/pixel counts
- downsamples large images for UI preview
- applies EXIF orientation for presentation
- does not log paths, bytes, EXIF, or face content

The crop UI preserves aspect ratio. It records normalized pan/zoom metadata in `CropTransform`; it does not silently stretch the image or bake landmark/scoring assumptions into the UI.

Final encrypted image normalization, metadata stripping, persistence, and hostile-media policy remain WP06 ownership.

## Guidance boundary

`CaptureGuidanceMessage` is a presentation contract only. WP05 supplies neutral defaults such as Center your face or Turn to the side. WP06 can later map validation outcomes into this UI contract without WP05 implementing a competing blur/brightness/pose engine.

Distance copy is explicitly heuristic: roughly two metres is guidance, not a measured phone-to-face distance.

## Timer

The countdown coroutine is owned by `CaptureViewModel`, not by recomposition. Leaving camera state or changing timer settings cancels the active countdown.

## Tests

`CaptureReducerTest` covers:

- source selection
- FRONT -> PROFILE progression
- independent FRONT retake
- independent PROFILE retake
- gallery cancellation
- replacement from review
- timer state
- back navigation
- crop-state preservation
- impossible PROFILE confirmation before FRONT
- final handoff payload and profile side

Android CI remains the authority for clean build, unit tests, lint, release shrink, dependency policy, existing geometry/scoring tests, and the existing 16 KB emulator gate.

## Phase 2 integration note

The current navigation callback consumes `CaptureReadyPayload` and returns to the foundation screen because WP06 is intentionally absent from this lane. During the Phase 2 sync, that callback is the narrow seam where WP06 should validate/persist the two selected media references plus crop metadata.

Do not replace this with a second persistence implementation in WP05.
