# WP02 — Core contracts and CI

Owner: Eddy lane. Base: `phase/P00-integration` at Architecture Freeze 2.2.

## Delivered in this lane

- Pure Kotlin scan, ownership, reference-model, age-confirmation, intent, metric, category, overall-outcome and provenance types under `app/.../core/model/`.
- Constructors reject out-of-range scores/confidence, missing IDs/versions, mismatched enabled metric sets, and extrema IDs that do not use the completed result's comparable score scale.
- An explicit `InsufficientReliableMeasurements` outcome without an overall score.
- Unit tests for contract invariants, missing results, comparable extrema and tie sets.
- Android CI for build, unit tests and lint. Until WP01 enters the phase branch, CI composes WP01 and WP02 in a disposable checkout; it does not merge either lane to `main`.
- Static pinned-dependency policy and native-library inventory/16 KB alignment gate.
- An instrumentation-only Room 3 + maintained SQLCipher proof that exercises encrypted create/reopen, wrong passphrase, explicit migration, and a versioned Keystore-wrapped passphrase/missing-key path. The SQLCipher/Room dependencies are scoped to the test APK; WP06 owns the production database.

## Configuration and secrets boundary

The current Phase 0 app has no backend connection. Build and unit-test jobs use no service credentials or signing secrets. Future public identifiers such as a Supabase URL/publishable key may be configured per build environment, but provider secrets, service-role keys and signing passwords must only exist in protected server/build secret stores. `local.properties`, `.env*` (except a no-secret sample), keystores and private fixtures stay ignored. A missing environment setting must fail the feature explicitly rather than silently target production or localhost.

## Room / SQLCipher compatibility spike — P0 gate, pending execution

Run the committed instrumentation tests on the real WP01 Android skeleton before P0 closes; do not claim this spike passed from a documentation review, a JVM unit test, or a compiled test APK. The candidate pairing to validate is Room 3.0.3 with the maintained `net.zetetic:sqlcipher-android` 4.19.0 using its `SQLiteDriver` integration. These are candidates, **not frozen versions**, until the Android build and device tests pass. Do not use deprecated `android-database-sqlcipher`.

Required device/instrumentation evidence:

1. Generate a random database passphrase, wrap it with an Android Keystore key, and record the versioned envelope metadata without logging key material.
2. Create an encrypted Room database and write representative sensitive records.
3. Close/reopen, kill/restart the process, and verify the records remain readable only with the correct key.
4. Verify wrong-key and missing-key paths fail safely without deleting or silently replacing user history.
5. Perform an explicit schema migration and verify the previous records survive.
6. Inspect the raw DB bytes to confirm they are encrypted, and verify Android backup/device transfer excludes the DB, key envelope and sensitive assets.
7. Inspect every packaged native `.so` and the APK/AAB for 16 KB ELF and ZIP alignment; test installation/open on a 16 KB emulator/device when available.

The local machine used for this change has no Android SDK or emulator, so device compatibility is **not yet verified**. CI builds the instrumentation test APK and checks native libraries, but cannot replace running the tests on an emulator/device. Process-death recovery and 16 KB device installation also remain explicit follow-up checks. The phase integration gate remains open until they pass.

## Dependency verification follow-up

The current CI rejects dynamic versions in the version catalogue. Once the combined Android project resolves successfully, generate and commit Gradle dependency lockfiles and verification metadata from a trusted workstation/CI run, then switch CI to strict verification. Do not create guessed lockfile hashes.

References: [ASCEND security contract](SECURITY.md), [Phase workflow](WORKFLOW.md), [Room 3 release notes](https://developer.android.com/jetpack/androidx/releases/room3), [SQLCipher Android integration](https://github.com/sqlcipher/sqlcipher-android), [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes).
