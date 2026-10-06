# P4 signed QA candidate — 0.4.0 (6)

The owner authorized P4 sync/merge/build on 2026-10-06 and selected manual phone
testing. Both lanes are integrated through PR #23 (WP10) and PR #22 (WP09).
This is a QA preview, with real-photo measurement/reliability acceptance still open.

## Build and update identity

Main's Android CI supplies `ASCEND-QA-arm64.apk`, `ASCEND-QA-universal.apk` and
`ASCEND-QA-provenance.txt`. Use artifacts from the same exact successful main run.
The production package remains `app.ascend.mobile`, versionCode 6, versionName
0.4.0. Both APKs use certificate SHA-256
`b9565110d18e91bbdd6a0cbff0b238d39771c1357646bcb6c0cb7f11a73bb9d8`.
CI verifies signatures, identity, ABI packaging, packaged permissions and native
16 KB compatibility, and records commit, model hash, APK size and SHA-256.
The separate `ASCEND-dev.apk` is not the production update-chain test.

## Manual Honor phone checklist

1. Record phone model, Android version and supported ABI. Use ARM64 only if the
   phone supports `arm64-v8a`; otherwise use universal.
2. Before updating, note existing settings/history and which stored front/profile
   photos open successfully. Keep real photos and personal data off GitHub.
3. Install the signed QA APK over the existing ASCEND install. **Do not uninstall,
   clear data, or install the developer APK as a substitute.** Confirm version
   0.4.0, relaunch, and verify those same settings/history/encrypted photos survive.
4. Open an editable scan with a profile photo. Confirm anatomical LEFT and RIGHT
   separately from the direction the nose faces on screen. Exercise the guided
   confirmation sequence on both sides; only bounded movement is allowed. Repeated
   drags must not escape the original zone. Numerical values/confidence are not editable.
5. Check touch and accessible directional controls, scrolling, rotation/window
   resize, leaving/reopening the screen and app restart. Confirm the selected
   side/direction and saved corrections restore without an overlay/photo mismatch.
6. Replace/retake PROFILE within the scan: old profile corrections must reset,
   FRONT photo and corrections must remain. Retaking FRONT must preserve current
   profile corrections. Completed history must remain read-only.
7. Check screenshot blocking and recents privacy on both front and profile photo
   surfaces, including transitions between them. No face upload/network behavior
   should be required.
8. Expect explicit unvalidated-preview guidance and unavailable real-photo
   measurements/scoring. Completing preview points must not produce a scored
   analysis. Four synthetic angles have arithmetic coverage; eight candidate
   definitions remain unsupported. Synthetic success is not real-photo accuracy.

Record pass/fail per step, installed APK hash, phone/Android version and prior
installed version. A signed build and emulator CI are not physical-phone acceptance.
Production capture/correction policy evidence and same-image/repeated-capture
reliability remain separate work; this candidate does not close P4's real-profile
measurement gate or begin P5.
