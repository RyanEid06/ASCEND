# WP07 — Front landmark pipeline

Ryan lane: `ryan/P03-WP07-front-landmark-pipeline`, baseline `f466c4e`.
Authority: WORKFLOW Phase 3 and MASTER_PLAN frontal path. Only WP07 is authorized;
WP08 anatomical mapping, annotations and confidence calibration remain Eddy's lane.

## Design and execution record

Process the already oriented, cropped, unmirrored FRONT PNG in encrypted local storage.
Use the bundled official MediaPipe model in IMAGE/CPU mode with capacity for two
faces so multiple-face images cannot silently become single-face inputs. Preserve
model hash, provider/pipeline/policy versions, source-image hash, raw mesh and pose.
Reject missing/multiple faces, malformed meshes or unavailable pose. Apply an
aspect-correct short-edge transform and roll correction; invert that exact transform
for an image-fit overlay. Do not synthesize hidden anatomy or rectify yaw/pitch.

MediaPipe detector thresholds are admission settings, not calibrated per-point
confidence. The mesh preview can be shown while confidence is unavailable; geometry
requires an explicit reviewed mapping and calibrated confidence supplied by WP08.
No score or completed analysis is produced by this lane. The existing capture hooks
remain pending until a reviewed quality/confidence policy is integrated; broad
PRE_CV limits are not promoted into production biometric thresholds. Profile
validation also requires the separate profile path.

Store snapshots inside the existing encrypted scan_payloads table, owner scoped,
bound to the current FRONT photo hash. Reuse existing retake/deletion invalidation;
check the image hash again under the repository mutex before saving to reject races.
No Room schema, frozen core model, scoring API or LocalScanRepository interface change.
No plaintext face cache, media upload, logging or network permission.

Tasks:
1. Write synthetic tests for pose, malformed/count/quality gates, normalization,
   inverse overlay, geometry confidence and snapshot codec; implement pure Kotlin.
2. Pin provider/model, implement the Android adapter and encrypted snapshot seam;
   add storage instrumentation coverage for owner isolation, retake and stale writes.
3. Wire saved scans to a front preview route and release sensitive bitmap buffers
   when leaving. Keep UI resizable and allow retry after provider failure.
4. Run local pure tests and policy checks, attempt Android build, review, commit
   and push only Ryan's existing branch. Build quota failure is recorded without retry.

Review focus: source replacement during inference; multiple-face truncation;
non-square/rotated overlays; missing confidence; bitmap cleanup and cancellation.

Validation results and exact limitations are recorded below before delivery.

## Lane validation — 2026-10-04

- Local JVM compile plus **46/46 JUnit tests pass**, comprising the existing pure
  model/geometry/data suites and 10 new front-vision tests. Executed with the
  installed Gradle distribution's Kotlin 2.0.21 compiler and JUnit 4.13.2 via
  `scripts/test_front_vision.ps1 -KotlinLibDirectory <Gradle-lib-directory>`.
  This is separate from the project's pinned Android/Kotlin build toolchain.
- Wrong-model geometry admission reproduced a failing assertion before the gate
  was added; corrupt derived-cache decoding reproduced failure before safe cache
  invalidation. Both regressions pass in the final suite.
- All **20 pinned dependency versions** pass policy checks; local-storage backup,
  permission and cleartext checks pass; `git diff --check` passes.
- Official model hash verified against the bundled artifact. Downloaded MediaPipe
  1.0.0 AAR API signatures inspected. All four tasks-core JNI libraries (arm64-v8a,
  armeabi-v7a, x86, x86_64) have ELF load-segment alignment **16,384 bytes**. Final
  packaged APK alignment and device loading still require Android CI.
- Fresh final review found a MediaPipe bitmap-ownership defect; fixed by a separate
  inference copy. Added a synthetic real-provider instrumentation regression.
  Corrupt-cache retry handling was also fixed. No unresolved review finding remains.
- Android `assembleDebug assembleRelease assembleDebugAndroidTest test lint` was
  attempted; wrapper bootstrap download was stopped because this workstation has
  no configured Android SDK. No Android compile, lint, APK, instrumentation or GUI
  pass is claimed. No SDK was installed and no CI quota workaround is introduced.
- Added two encrypted storage instrumentation tests for reopen/owner isolation,
  deletion, retake invalidation and stale inference, plus one actual-provider
  blank-image/bitmap-ownership test; these remain unrun pending Android capacity.

WP07 source implementation is ready for lane review. P3 remains open: WP08 mappings,
annotation comparisons, calibrated quality/confidence, real-photo repeatability,
device/emulator preview checks, integration and signed update acceptance are required
before the phase closes. Runtime scoring models remain draft/non-scorable.

## Implementation seams for WP08

- `MediaPipeFrontLandmarker.extract(bitmap, sourceImageSha256)` returns `FrontExtraction`.
- `FrontLandmarkSnapshot` preserves all 478 raw model points (x/y image normalized;
  z model-relative only), resolution, pose, provenance and typed quality issues.
- `analysisPoint(index)` returns roll-corrected isotropic short-edge coordinates;
  `imagePoint(point)` reverses that exact transform. Yaw/pitch are gated, not corrected.
- `FrontGeometryAdapter.frame(snapshot, mapping, calibratedConfidence)` requires a
  `FrontGeometryMapping(version, indices, modelVersion)` and explicit per-index
  confidence. Missing confidence, another model or unusable preview quality returns
  null. WP08 owns anatomical mappings/validation; no mesh index is silently called
  trichion, gonion, zygion or another unsupported anatomical point.
- `EncryptedScanRepository.saveFrontLandmarks/readFrontLandmarks` are additive
  concrete methods. SQLCipher payload kind `front-landmarks-v1`, codec format 1;
  no schema migration. Decode of a corrupted derived cache returns a cache miss
  so inference can replace it. Completed numerical-history decoding stays strict.
- A successful capture opens the front preview by scan ID. Home can reopen the
  most recent usable front capture. This is a preview, not WP08's debug measurement
  screen or WP13's result UI. Sensitive bitmap state is released on route exit and
  the preview uses screen-scoped FLAG_SECURE.
