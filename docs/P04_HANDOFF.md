# P3 merge and P4 preparation

## Current P4 sync / QA handoff — 2026-10-06

The owner authorized syncing and merging both P4 lanes, then preparing a signed
phone QA build. WP10 PR #23 and WP09 PR #22 are merged into P4 integration; the
candidate advances to versionCode 6 / versionName 0.4.0. The signed ARM64 phone
artifact must come from green main and the permanent key. The owner's subsequent
clarification selects ARM64-only phone backups/downloads, superseding the older
universal archive requirement below. Universal packaging is checked within CI but
is no longer published as a QA artifact; emulator ABI support remains.
The owner chose to perform the physical-phone test manually. Follow
[P4 QA checks](P04_QA_BUILD.md) before declaring the phone update accepted.

This is an integrated preview candidate: constrained profile confirmation and
synthetic formula coverage are implemented, while real-profile geometry, confidence,
production correction policies and scoring remain unavailable. Merge/build readiness
does not close those empirical acceptance items or authorize starting P5.

The remainder records the earlier P3/pre-P4 packaging handoff.

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

- `ASCEND-QA-arm64.apk` — signed `arm64-v8a` production-package build used for
  routine Ryan/Eddy physical-phone downloads after each phone's ABI is verified.
- `ASCEND-QA-universal.apk` — signed production-package build retaining the full
  supported ABI set for compatibility/archive testing.
- `ASCEND-dev.apk` — separate developer/debug package where needed.
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
shared interfaces, reuse the existing profile types and document necessary
additions for the P4 sync. On 2026-10-06 the owner removed the separate
contract-acknowledgement stop so the lanes can continue implementing and
reconcile their interfaces at that shared checkpoint.
Automatic extraction is an optimization; constrained assisted fallback must
remain usable. The original refreshed baseline contained no WP09/WP10 feature
implementation; later lane commits and their PRs record current progress.
