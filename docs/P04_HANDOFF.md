# P3 merge and P4 preparation

On 2026-10-05 the project owner explicitly authorized merging all integrated P3
work to main, deleting obsolete merged branches, producing a fresh signed P3
mobile QA build, and creating the P4 branches. WP09 implementation is reserved
for a subsequent chat. This authorization defers the outstanding human acceptance
gates; it does not supply real-photo validation or close them.

## Mobile QA candidate

The new main candidate uses versionCode 5 / versionName 0.3.2, advancing from
P2 version 2 and the P3 version-3/version-4 candidates. The permanent production
package and signing certificate remain unchanged. Main CI publishes the signed
QA APK, separate developer APK and exact-commit/hash provenance, and runs the
16 KB emulator suite as well as build, unit tests, lint and packaged privacy checks.
The new main artifact supersedes the integration version-4 candidate for this
handoff. Install `ASCEND-P3-QA-signed.apk` over the existing production QA install
without uninstalling, then verify settings, history and encrypted assets remain
readable. The developer APK has a separate package and synthetic inspector.

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
