# ASCEND Release, Signing and Update Contract

Version: 2.2 Architecture Freeze
Status: Must be completed before the first implementation APK becomes the long-lived test install

## 1. Permanent application identity

Choose the final production applicationId before WP01 implementation work is treated as permanent.

Use a separate debug/development applicationId suffix so development and release/QA installs can coexist where useful.

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
3. build signed QA APK
4. verify signature
5. install over previous QA APK without uninstalling
6. launch and run migration/update smoke tests
7. verify settings/history/encrypted assets survive
8. only then begin the next phase

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
- debug APK for fast local iteration

Phase QA:
- signed QA APK installable as an update over prior QA build

Play:
- signed Android App Bundle (AAB) as the store artifact
- APK may still be generated for controlled QA where appropriate

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
