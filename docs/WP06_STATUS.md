# WP06 — Local data and quality validation

Branch: `eddy/P02-WP06-local-data-quality`; target: `phase/P02-integration`.
Baseline: `b360be8`. Status: implemented for draft review; Phase 2 sync gate remains open.

## Implementation

- Application-scoped `LocalScanRepository` factory using the Phase 0 proven Room 3.0.3 / maintained SQLCipher 4.19.0 SQLiteDriver integration. The spike database is a separate instrumentation fixture, not a production schema to migrate.
- Production schema V2 stores owner-scoped scan sessions, independent FRONT/PROFILE asset/validation records and scan-scoped encrypted result/provenance payloads. An explicit V1 -> V2 migration adds interrupted-deletion state. No destructive migration fallback.
- Typed completion atomically stores AnalysisOutcome.Complete with all original versions, metric/tier/weight/coverage data, rank and extrema sets. Generic advancement cannot mark COMPLETE without this snapshot. Historical snapshots round-trip through a versioned codec that re-applies core invariants; owner-scoped reads and deletion preserve the same privacy boundary.
- ScanSession and ScanState remain unchanged. Each view's persisted evidence controls capture progress; retaking one removes only that asset/evidence and invalidates derived payloads. An accepted opposite view survives failures, restart and either retake. Completed history cannot be recaptured in place.
- Gallery/camera encoded bytes are bounded before decoding. MIME from decoded content must be JPEG/PNG/WebP; dimension/pixel limits apply before allocation. EXIF rotation/reflection is applied, the viewport-relative WP05 crop is normalized without stretching, transparent or out-of-image crops are rejected, and fresh PNG output strips EXIF/GPS.
- Blur is normalized-luma Laplacian variance on the policy's fixed sampling grid; brightness is mean normalized luma. Resolution uses the actual cropped output size, with no upscaling to satisfy thresholds. Face-count, yaw/pitch/roll and centering hooks report typed retake reasons. Unavailable/partial face observations remain pending and cannot advance to analysis.
- AES-256-GCM Keystore key protects a random 32-byte SQLCipher passphrase and each photo. Envelope format/key versions, nonce and ciphertext/tag are persisted; header plus asset purpose/ID are authenticated. Keystore generates a fresh nonce for each encryption. Photos and the database envelope use atomic writes in noBackupFilesDir.
- Immutable new asset write precedes its transactional DB reference. Startup removes unreferenced encrypted assets and resumes deletion markers. Missing/corrupt assets become per-view recovery failures; missing/invalid keys never silently replace history. Completed numerical history is retained if its photo is unavailable.
- Delete Scan is owner-scoped and idempotent. It first hides the scan, then removes assets and scan-scoped derived payloads, then the marker. Delete All marks every local owner scope and clears assets/orphans. There is no network queue or cloud connection in Phase 2; future WP18 remote deletion must be coordinated, not fabricated here.
- Existing allowBackup=false and all-domain backup/device-transfer exclusions are retained and statically checked in CI. No INTERNET or broad media permission was added. There is no media upload, telemetry or private-path logging.

## WP05 coordination and contract additions

Coordination was posted to [Ryan's WP05 PR](https://github.com/RyanEid06/ASCEND/pull/11#issuecomment-5967684447). Ryan confirmed compatibility at upstream commit `74b8bbb`.

Additive completion/readAnalysis operations accept existing typed AnalysisOutcome.Complete without changing core model types. These are for the later analysis producer; WP05 should stop at validated capture, never mark a score complete.

Additive storage contracts live in `core/data/CaptureStorageContract.kt`; existing WP05 payloads and core ScanSession/ScanState are unchanged. No shared schema/API was replaced. The local storage implementation remains owned by WP06.

Integration mapping:
1. Create/recover a scan in the current owner scope with the chosen reference model.
2. Use LocalCaptureImporter.persistFromUri for CaptureMedia.uri. It bounds encoded bytes before decoding, accepts picker content URIs or camera files only inside WP05's ascend_capture cache, and removes the owned camera source after encrypted persistence. It never stores the source URI/EXIF in Room or deletes gallery originals.
3. Map FRONT/PROFILE to CaptureView, CAMERA/GALLERY to CaptureOrigin, and copy scale, offsetXFraction, offsetYFraction, viewportAspectRatio to CaptureCrop. Only PROFILE has the CaptureReadyPayload.profileSide.
4. Call putCapture per confirmed role, or map the final payload to two calls. The per-role API supports persistence as soon as a view is confirmed; this is needed for recovery while capture is unfinished. The other view is not re-saved on an independent retake.
5. Map LocalView.reasons or CaptureRejected.reasons to WP05 SetGuidance/SetError and Retake(role). TOO_BLURRY -> HOLD_STILL; TOO_DARK/TOO_BRIGHT -> IMPROVE_LIGHTING; OFF_CENTER -> CENTER_FACE; INVALID_POSE -> FACE_FORWARD/TURN_TO_SIDE. Count/resolution/crop/image failures need concise error copy.
6. Treat PENDING_HOOKS as awaiting validation, never as an accepted scan. Revalidate after the relevant hook becomes available. Stop camera/timer work before leaving the capture route, remove WP05's narrowly scoped plaintext camera cache after handoff, and release encoded byte buffers after use. The importer handles its own buffer/source cleanup. Stop camera/timer writers before Delete All; it also clears abandoned ascend_capture files.
7. Use recover after process restart, offer resume/discard, and expose missing local images honestly. Do not log source URIs or stored private paths.

WP06 does not copy or merge WP05's UI implementation into its lane. Wiring these calls into Ryan's confirmation/navigation callbacks and human approval of the interface mapping remain the P2 integration checkpoint.

## Explicit storage/key behavior

Open exactly one repository instance per application process, on the IO dispatcher, using reviewed image/quality policies. Stop callers before close/logout; switch the active owner scope to deauthorize prior account data. Owner-scoped queries and mutations hide other accounts; full auth/account switching remains WP18. Close does not erase history or keys.

Format 1 / keyVersion 1 is the current format. Unknown versions fail closed. No automatic key rotation is introduced. A future reviewed rotation must rewrap the DB secret and migrate each asset before retiring the previous key; this PR neither destroys old aliases nor claims to implement rotation. Delete All removes scans/assets/payloads but retains the empty encrypted database, passphrase envelope and Keystore alias so it cannot interfere with future encrypted auth storage. Uninstall removes app-private data; a leftover alias without data is safe. Missing envelope with existing DB/assets, missing database with retained media, or missing Keystore key requires explicit recovery/deletion; no empty replacement database is created. eraseUnrecoverableLocalData is an explicit destructive recovery entry point only after the user chooses local Delete All and all repository instances close. It removes this storage namespace, the acquisition cache, and its storage-only Keystore alias.

Atomic DB/file boundaries leave only encrypted orphan files after a failed partial write. Startup removes them. There is no plaintext persistent standardized photo. WP05 camera acquisition cache stays its ownership until the integrated handoff cleanup is wired; no share cache exists in this lane.

## Validation and remaining decisions

Unit tests cover lifecycle and deterministic quality/face-hook boundaries. Instrumentation covers production encrypted reopen, owner isolation, both retakes, rejection/pending hooks, revalidation, corruption/orphan recovery, purpose/header authentication, missing-key preservation, hostile inputs, interrupted delete, Delete All and production V1 -> V2 migration. The CI restart fixture seeds both synthetic views and verifies them in a fresh instrumentation process after force-stop; CI verifies PAGE_SIZE=16384 and checks ELF/APK alignment.

Synthetic test policies/hooks are intentionally permissive fixtures, not production defaults. Exact standardized resolution/pixel limits, blur grid/thresholds, brightness/pose/centering limits and CV hook implementations require validated versioned policy at integration. No threshold is chosen to make real captures appear valid.

Local Gradle execution is unavailable because this workstation has no Android SDK. Local `git diff --check`, backup/permission policy and all 18 pinned-dependency checks pass. Initial [CI run](https://github.com/RyanEid06/ASCEND/actions/runs/37113521924) passed debug/release builds, all 60 unit tests, lint and native alignment checks. The full encrypted storage/migration suite passed, but its repeated-Gradle restart fixture failed because UTP removed seeded target-app data between invocations. The corrected restart fixture installs once and invokes adb instrumentation directly around force-stop. [Review-fix CI](https://github.com/RyanEid06/ASCEND/actions/runs/37115046682) passed 57 unit tests, build/lint/native checks, the full storage/migration suite and the corrected separate-process restart checks on the 16 KB emulator. The final typed-completion/provenance safeguard is awaiting its own verification; exact final results will be recorded on the draft PR. Human offline capture/restart QA, WP05 callback wiring, signed in-place update and physical-device verification are still mandatory before closing P2. This PR must remain draft and must not be merged automatically.
