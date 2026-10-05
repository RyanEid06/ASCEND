# P3 integration QA APK

User-authorized integration and APK preparation on 2026-10-05. WP08 PR #16 was
merged after green lane CI. WP07 PR #17 was updated with the exact WP08 integration;
the only merge conflict was the repository imports, and both interfaces/routes were
preserved. The merged implementation is tested together before QA delivery.

The final build uses `versionCode` 4 and `versionName` 0.3.1. It supersedes the
version-3 candidate after APK inspection found dependency-added INTERNET and
ACCESS_NETWORK_STATE permissions. Both are explicitly removed for the local-only
P3 phase; CI now checks permissions in the packaged debug/release APKs rather than
only the app source manifest. The final code advances monotonically from either
P2 version 2 or the earlier candidate version 3. The QA
application remains `app.ascend.mobile`; the developer inspector build remains
`app.ascend.mobile.dev`. P03 integration pushes use the existing GitHub signing
secrets and verify the public certificate SHA-256 from the prior P2 QA artifact:
`b9565110d18e91bbdd6a0cbff0b238d39771c1357646bcb6c0cb7f11a73bb9d8`.
No signing key is created, exposed or copied into this repository.

CI retains `ASCEND-P3-QA-signed.apk`, `ASCEND-P3-dev.apk` and a provenance record
containing the exact Git commit, app version, signing certificate, model hash and
APK hashes. Native 16 KB checks cover the packaged debug/release/test APKs; the
existing 16 KB emulator tests cover both lanes, synthetic debug UI and encrypted
process restart. Final run identities/results are recorded in the packaging PR.

The signed QA APK is the candidate for updating an existing P2 QA installation
without uninstalling. The dev APK is a separate developer install; its debug-only
inspector uses synthetic measurements. Verify the physical-device in-place update
and retained encrypted assets/history before declaring update acceptance.

**Phase 3 sync gate remains OPEN.** Combining the lanes does not supply a reviewed
anatomical mesh mapping, calibrated global/per-point confidence, capture policy,
correction bounds or real-photo reliability evidence. WP07 previews the front mesh;
WP08's measurement inspector remains synthetic, with a separate integration seam.
Unreviewed confidence and unsupported definitions fail unavailable. No scoring
constants or reliability status are promoted. The outstanding P2 human checkpoint,
joint seam review, real-photo same-point overlays and repeatability checks remain
required. This integration QA build does not merge P3 into main or close its gate.
