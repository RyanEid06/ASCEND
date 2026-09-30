# ASCEND Development Environment

Version: 2.2 Architecture Freeze
Target host: Windows 11 developer machines

## 1. Required machine tooling

Install/verify once:
- Android Studio stable
- Android SDK Platform 36 or current Play-required target if newer at implementation time
- Android Build Tools
- Android Platform Tools (adb)
- Android emulator + representative API 36 image
- JDK 17
- Git
- Docker Desktop or compatible container runtime
- Node.js 20+ for Supabase CLI/npm workflow

Project-owned tools/dependencies:
- Gradle wrapper
- Kotlin/Compose dependencies
- CameraX
- MediaPipe
- Room
- supported `sqlcipher-android` integration only after the WP00 compatibility spike; do not use deprecated `android-database-sqlcipher`
- WorkManager
- Navigation 3 current stable release
- Hilt
- Supabase Android libraries
- test libraries

Do not install global Gradle/Kotlin/Postgres merely because the project needs those capabilities.

## 2. Local Supabase

Use the repository-pinned Supabase CLI/dependency strategy so both developers run the same expected tooling.

Local stack is used for:
- migrations
- seed data
- RLS tests
- Edge Functions
- destructive experiments

Hosted staging is not a substitute for local reproducibility.

## 3. Local configuration

Expected local-only files:
- local.properties
- .env.local or equivalent
- keystore.properties/local signing config where appropriate

Committed examples:
- .env.example
- documented variable names only
- no real credentials

.gitignore must exclude:
- keystores
- local secret files
- private face fixtures
- generated local DB dumps
- IDE/local machine state not meant for source control

## 4. Android baseline

Initial architecture:
- Kotlin
- Jetpack Compose / Material 3
- minSdk 26
- targetSdk 36 initially, re-checked against current Play requirements before public release
- portrait-first phone UX with adaptive/resizable compact/medium/expanded layouts; no correctness dependency on portrait locking
- Navigation 3 stable
- UDF + ViewModel/StateFlow screen state
- CameraX
- Android Photo Picker
- Coroutines/Flow
- Hilt
- Room + supported SQLCipher encrypted DB strategy proven before WP06
- DataStore for non-secret settings
- Android Keystore for encryption/session keys
- WorkManager/CoroutineWorker for durable retryable sync/delete jobs

## 5. Device testing

Maintain at minimum:
- one primary physical Android device used throughout development
- emulator for clean installs/process/API tests
- later Samsung-class device
- Pixel/reference-class device
- another OEM/low-mid device if available

Phase APK QA is done as an in-place update, not only fresh installs.

## 6. Private fixture strategy

Public repository fixtures:
- synthetic
- generated
- explicitly licensed for redistribution

Private face QA fixtures:
- outside public source tree or in a private ignored directory
- never committed accidentally
- no production-user exports

Expected-result/golden data can be committed if it contains no private biometric source material.

## 7. Developer debug tools

Allowed only in debug/dev builds:
- scan state inspector
- landmark/overlay inspector
- formula debug values
- scoring config inspector
- feature flag override
- DEV_UNLOCK
- local backend selector

Release build must not expose unrestricted debug controls.

## 8. Environment verification checklist

Before WP01 starts:
- Android Studio stable is installed and launches correctly
- JDK 17 selected
- SDK/platform tools available
- adb sees physical device
- emulator boots
- Git auth works
- Docker runs
- Node/npm works
- Supabase local stack can start
- no secret files tracked by Git
- production applicationId is `app.ascend.mobile`
- development/debug applicationId is `app.ascend.mobile.dev`
- signing/update strategy is frozen; permanent key creation/backups must complete before P0 closes and before the first long-lived signed QA APK
- Room + supported SQLCipher compatibility-spike acceptance criteria are frozen; the executable proof runs on the Phase 0 project before P0 closes
- key-envelope format/version/rotation/deletion semantics frozen
- 16 KB page-size test strategy frozen for every native dependency
- GitHub main-branch governance actually enabled, not merely documented
