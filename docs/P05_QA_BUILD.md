# P5 signed QA candidate — 0.5.0 (7)

The owner authorized WP11/WP12 synchronization, merge to main, removal of the
merged P5 branches, P6 branch preparation and a Desktop phone APK on 2026-10-09.
WP11 commit `970cc7007959b07913ede1e20305fac14548f56b` and WP12 commit
`d578f9c70472acdfeb9a79a1a8acb7a8bd573c9a` provide synthetic groundwork.

## Included scope and acceptance limits

WP11 includes bounded, image-bound ROI sampling, explicit semantic masks,
six candidate spatial signals and typed unavailable hooks. WP12 exercises the
canonical scorer, category weighting, tiers and neutral Appearance Details cards
using test-only models. Both reject real-photo inputs. Neither has a production
screen caller; no shared metric/extractor bindings or reviewed production policy
are adopted by this merge. The APK retains the existing capture/front/profile UI.

The full P5 product gate remains open: all four categories do not yet have an
admitted real-photo path. Remaining work includes source/ROI lifecycle binding,
immutable combined provenance, reviewed runtime definitions/configuration,
semantic providers, calibrated confidence and real-photo/device/fairness evidence.
No appearance score, community rank or completed Overall should be fabricated
to make P6 screens look complete. Unavailable and insufficient-data states are
part of the P6 implementation contract.

## Build identity and verification

Production package: `app.ascend.mobile`, versionName `0.5.0`, versionCode `7`.
Permanent certificate SHA-256:
`b9565110d18e91bbdd6a0cbff0b238d39771c1357646bcb6c0cb7f11a73bb9d8`.
Main publishes the universal APK with all four ABIs; the synchronized
`codex/arm64-qa` branch publishes the smaller `arm64-v8a` phone APK from the same
source commit. The separate `.dev` APK is not the signed update-chain candidate.

CI must pass build/unit tests, lint, pinned dependencies, source/packaged privacy,
synthetic fixtures, signing/package/ABI checks, native 16 KB checks and connected
storage/migration/process-restart tests on the 16 KB API 35 emulator. Exact commit,
CI runs, report counts, APK digest/size and Desktop-copy verification are recorded
in the local `build/p5-handoff/FINAL_HANDOFF.md` receipt after completion.

## Manual phone checklist

1. Install `ASCEND-P5-0.5.0-ARM64.apk` over the existing signed ASCEND app without
   uninstalling or clearing data. Confirm version 0.5.0.
2. Verify prior settings/history and encrypted front/profile photos survive;
   reopen them after force-stop/relaunch.
3. Exercise capture/import, front overlays and both anatomical profile sides,
   constrained edits, rotation and accessible controls using the P4 checklist.
4. Verify retakes invalidate only the replaced image's corrections. Check photo
   screenshot/recents privacy and offline operation.
5. Expect unavailable real-photo appearance/profile scores. No new P6 results
   dashboard or metric-detail UI is part of this candidate.

Record phone/Android version, previous app version, APK hash and each result.
CI/emulator success does not establish physical-phone retention or real-photo
measurement validity.

## P6 branch handoff

After green main, synchronize `codex/arm64-qa`, delete only the merged
`phase/P05-integration`, `ryan/P05-WP11-visual-feature-extraction` and
`eddy/P05-WP12-misc-scoring-ux` branches, then create from that exact main commit:

- `phase/P06-integration`
- `ryan/P06-WP13-results-dashboard`
- `eddy/P06-WP14-metric-detail-experience`

This handoff prepares branches only; it does not begin WP13/WP14 implementation.
