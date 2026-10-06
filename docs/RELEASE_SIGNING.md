# ASCEND Release, Signing and Update Contract

Version: 2.2 Architecture Freeze
Status: Must be completed before the first implementation APK becomes the long-lived test install

## 1. Permanent application identity

Frozen identities:
- production: `app.ascend.mobile`
- development/debug: `app.ascend.mobile.dev`

Do not change the production applicationId after WP01 begins. Development and release/QA installs can coexist because they use separate identities.

Changing the production applicationId later creates a different Android application. Therefore it is an architecture decision, not a cosmetic rename.

## 2. Signing key

Generate a brand-new ASCEND signing key. Do not reuse keys from SpendWise or other apps.

Rules:
- strong key/passwords
- never commit the keystore
- never paste signing secrets into issues/chats/prompts
- keep two encrypted/offline backups in separate trusted locations
- document alias/certificate fingerprints without documenting passwords
- test backup recovery before relying on it

## 3. QA update chain

Once the permanent key is created, all long-lived physical-device QA APKs use that signing identity.

At every phase gate:
1. merge the phase to green main
2. increment versionCode
3. fast-forward `codex/arm64-qa` to that same main commit; main CI publishes universal (all four ABIs), ARM64 branch CI publishes only the signed `arm64-v8a` phone APK
4. verify signatures, matching production identity/version and record hashes/provenance
5. install it over the previous production QA install without uninstalling when the phone reports `arm64-v8a`; request a compatible build for other devices
6. launch and run migration/update smoke tests
7. verify settings/history/encrypted assets survive
8. only then begin the next phase

The ARM64 QA APK keeps the production applicationId, monotonic versionCode/versionName and permanent signing certificate. It updates the existing QA installation even if that older install came from a universal APK.

Debug developer builds may continue using normal debug signing, but they do not replace the signed update-chain test.

## 4. Google Play App Signing

Before store distribution:
- enroll in Play App Signing
- use a separate upload key after enrollment where supported/recommended
- keep local/offline recovery documentation
- record Play certificate fingerprints for OAuth/API registration

Do not casually rotate keys or create parallel signed identities.

## 5. Versioning

Maintain:
- monotonically increasing Android versionCode
- human-readable versionName
- app version stored on completed analysis for provenance

Suggested pre-release style:
- 0.x.y versionName
- versionCode generated/managed from a single source of truth

No build script should silently reset/decrease versionCode.

## 6. Release build hardening

Release/QA candidate must verify:
- debuggable=false for production
- minification/obfuscation choice documented and tested
- cleartext disabled
- correct backend environment
- no localhost/test endpoints
- no test credentials
- no server secrets
- correct signing certificate
- every packaged native library is inventoried and passes the project's current 16 KB page-size compatibility/alignment gate
- backup policy correct
- logs appropriately stripped/redacted

## 7. Build outputs

Development:
- debug APK for fast local iteration; emulator/CI support must retain the ABIs required by the test environment, including x86/x86_64 where used

Phase QA:
- `ASCEND-QA-arm64.apk` — signed `arm64-v8a` production-package APK; this is the normal Ryan/Eddy phone download after each developer phone has been verified as ARM64
- `main` publishes `ASCEND-QA-universal.apk` containing arm64-v8a, armeabi-v7a, x86 and x86_64
- synchronized `codex/arm64-qa` publishes only `ASCEND-QA-arm64.apk`; Ryan/Eddy phone downloads/backups use this smaller APK
- the phone APK must preserve the production package, version/update chain and permanent signing certificate
- `ASCEND-dev.apk` may remain a separate debug/developer package where needed
- provenance must record the exact commit plus phone APK hash, size and signing identity

Keep the same feature source on both permanent branches; CPU differences are build packaging, not separate implementations. Fast-forward the ARM64 branch after each phase merges to green main. Do not select feature commits based on CPU architecture. Keep x86/x86_64 support for developer/emulator testing.

If a phone does not support `arm64-v8a`, request a compatible build rather than forcing this ARM64 APK.

Play:
- signed Android App Bundle (AAB) is the store artifact
- Google Play performs device-specific delivery from the AAB so end users do not need the universal QA APK
- APKs may still be generated for controlled QA where appropriate

## 8. Migration discipline

Room/schema changes require:
- explicit migration
- migration unit/instrumentation tests
- in-place old APK -> new APK device test

Never rely on destructive migration for user data in release builds unless a deliberately disposable cache table is separately identified.

## 9. Automated upgrade test target

CI should eventually:
1. install a previous known-good APK on emulator
2. seed representative local data/assets
3. install new APK with update semantics
4. launch
5. assert DB migration
6. assert user settings/history
7. assert encrypted asset readability
8. assert current engine can display the historical result

## 10. Release provenance

For each candidate retain:
- Git commit SHA
- versionCode/versionName
- signing certificate fingerprint
- dependency lock/report
- native-library inventory / 16 KB compatibility result
- reference config hash
- CV model hashes
- CI run/artifact identity

A user-visible result must be traceable to the software/configuration that generated it.
