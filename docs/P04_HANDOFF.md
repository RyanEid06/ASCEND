# P3 merge and P4 preparation

On 2026-10-05 the project owner explicitly authorized merging all integrated P3
work to main, deleting obsolete merged branches, producing a fresh signed P3
mobile QA build, and creating the P4 branches. WP09 implementation is reserved
for a subsequent chat. This authorization defers the outstanding human acceptance
gates; it does not supply real-photo validation or close them.

## Mobile QA candidate

The new main candidate uses versionCode 5 / versionName 0.3.2, advancing from
P2 version 2 and the P3 version-3/version-4 candidates. The permanent production
package and signing certificate remain unchanged. Main CI currently publishes the
signed universal QA APK, separate developer APK and exact-commit/hash provenance,
and runs the 16 KB emulator suite as well as build, unit tests, lint and packaged
privacy checks. The new main artifact supersedes the integration version-4 candidate
for this handoff. Install `ASCEND-P3-QA-signed.apk` over the existing production QA
install without uninstalling, then verify settings, history and encrypted assets remain
readable. The developer APK has a separate package and synthetic inspector.

The observed signed P3 universal APK is approximately 61 MB. This is not evidence
that ASCEND's own app code is unusually large: the dominant payload is native
MediaPipe/SQLCipher code plus the bundled face-landmark model, and the universal APK
carries native code for multiple ABIs. Release minification/resource shrinking is already
enabled.

## Acceptance still pending

- Both developers' human offline capture/restart checkpoint and physical-device
  signed in-place update/data-retention acceptance.
- Reviewed anatomical mappings, calibrated confidence, capture policy and
  correction bounds for real frontal photos.
- Real-photo landmarks with same-point metric overlays and deterministic
  measurements, low-confidence failures and repeatability evidence.

Synthetic tests demonstrate arithmetic and storage behavior, not real-capture
reliability. Draft scoring models remain non-scorable; no production constants,
confidence thresholds or VALIDATED statuses are introduced by this handoff.

## Pre-P4 QA packaging decision

This capability was **not** present in the existing plan/build contract, so it is now
a required shared packaging task before WP09 or WP10 implementation starts.

Required steady-state artifacts at phase gates:

- `ASCEND-PX-QA-arm64.apk` — signed `arm64-v8a` production-package build used for
  routine Ryan/Eddy physical-phone downloads after each phone's ABI is verified.
- `ASCEND-PX-QA-universal.apk` — signed production-package build retaining the full
  supported ABI set for compatibility/archive testing.
- `ASCEND-PX-dev.apk` — separate developer/debug package where needed.
- provenance/report output containing the exact Git commit, version, certificate,
  model hashes and SHA-256 hashes for both QA APK variants.

Both QA APKs must keep the same `app.ascend.mobile` identity, permanent signing
certificate, versionCode/versionName and in-place update chain. The only intended
difference is native ABI packaging. x86/x86_64 support stays in the project for
CI/emulator testing; the project itself is **not** becoming ARM64-only.

Ryan and Eddy currently use Honor Android phones, but the exact models/ABIs are not
recorded here. Before the first ARM64-only install, verify that each phone reports
`arm64-v8a`; otherwise that device uses the universal QA APK.

Google Play distribution remains AAB-based later, allowing Play to deliver the
device-appropriate native code instead of making end users install the universal APK.

Because the P4 branches were created before this decision and no WP09/WP10
implementation has started, merge this packaging baseline to green `main` first,
then refresh/recreate all P4 branches from that new main commit before feature work.

## Fresh P4 branches

Create all three from the same green main merge commit:

- `phase/P04-integration`
- `ryan/P04-WP09-profile-capture-assist`
- `eddy/P04-WP10-profile-extractors`

Ryan owns orientation/side metadata, guided key-point confirmation, constrained
draggable zones, assisted fallback and profile overlay/angle UI. Eddy owns
profile adapters/formulas, required-point confidence, manual-confirmation rules,
regression fixtures and soft-tissue proxy labels. Before either lane changes
shared interfaces, freeze and jointly acknowledge the P4 profile contract.
Automatic extraction is an optimization; constrained assisted fallback must
remain usable. These branches contain no WP09/WP10 implementation yet.
