# WP09 implementation plan

Goal: finish guided profile confirmation, bounded correction and encrypted local
reopening using Eddy's existing profile types; preserve the shared phase sync gate.

The owner's 2026-10-06 instruction removes per-interface approval stops. Adopt
WP10 commit `39b5c6c` on Ryan's branch, document interface additions in the PR and
reconcile them at P4 sync. Do not merge PRs or change main/integration.

1. Add focused tests for demo-local assistance, LEFT/RIGHT, original-anchor
   bounds, provenance codec replay and uniform overlay inverse. Reuse
   `ProfileInput`, `ProfileRevision` and `ProfileAssistancePolicy`.
2. Add a versioned session codec and non-production local preview guidance.
   Preserve unknown pose/confidence. Eddy's real-photo formula gate stays intact.
3. Persist the session in SQLCipher under the existing profile payload kind;
   compare source and correction revisions atomically. Preserve unaffected FRONT
   inputs on profile retake/replacement. Test owner isolation, corruption,
   reopen, stale writes, both retakes and completed-history rejection.
4. Build the sensitive Compose route: explicit anatomical side and photo-facing
   direction, one active point/zone, drag + confirmation, retake/unavailable,
   saved state, compact/wide layouts and corrected-point line overlays. Connect
   stored profiles from Home and the existing front preview.
5. Relax workflow approval ceremony while retaining phase sync, privacy,
   ownership, review and final validation. Run focused tests, full regression,
   build/lint/native/privacy and emulator/UI checks; review, commit/push Ryan's
   branch and update PR #22.

Real photos may use explicitly labeled unvalidated preview guidance, never
synthetic scoring or fabricated pose/confidence. Production profile measurements
remain unavailable until WP10 supplies a reviewed real-input path and policy.
This distinction must be visible in the UI and final delivery.
