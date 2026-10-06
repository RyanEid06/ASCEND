# WP09 — Profile capture and assisted confirmation

Ryan lane: `ryan/P04-WP09-profile-capture-assist`; PR target: `phase/P04-integration`.
Baseline: `030f979e9af46d960c5ba80b16b81cbd495e7db2` (PR #21, green main CI).
WP10 dependency: `39b5c6ca7b0eed4fcef93d989addc8f686115936`, merged into this
Ryan development branch without merging either PR or changing integration/main.

## Owner instruction and design

On 2026-10-06 the owner removed the per-interface freeze and separate approval
stops, while retaining shared phase sync. WORKFLOW and P04_HANDOFF now record
that decision. Necessary additive UI/storage integration proceeds in this lane;
interface reconciliation and the integrated acceptance decision remain at P4 sync.

WP09 reuses Eddy's `ProfileInput`, `ProfilePoint`, `ProfileRevision`,
`ProfileAssistancePolicy` and metric catalogue. `ProfileAssistSession` is a small
UI/storage envelope around those existing objects, not another landmark model.
No detector, formula, scoring curve, anatomical definition or ML dependency was
added. WP10's real-photo measurement gate is unchanged.

## Implemented experience

- Home opens stored profile scans; the existing front preview also links to
  profile confirmation. Photo loading remains owner scoped and local.
- Explicit anatomical LEFT/RIGHT confirmation is independent of which direction
  the nose points in the unmirrored standardized image. Legacy default RIGHT
  metadata is never treated as proof of confirmed orientation.
- One active semantic point, anatomical guide, original displacement circle and
  exact current point are shown at a time. Dragging and accessible directional
  buttons use the same validation. Confirmation without moving is supported.
- Zero/partial proposals can initialize only catalogue-required eligible points.
  Original proposals, absent-proposal guidance and assisted points stay distinct.
  Confidence and numerical measurements/scores have no editing controls.
- Compact layouts stack photo and controls; wider windows use two columns.
  Navigation/ViewModel state survives rotation; the nonsensitive active index is
  saved separately, while side, direction, points and audit reopen from storage.
- The same canonical corrected `revision.input` feeds the overlay and WP10
  measurement adapter. Photo, points, lines and pointer inverse share one uniform
  image-fit transform; no axis stretching or implicit image mirroring.
- Photo replacement uses Photo Picker and the existing bounded EXIF/crop/encrypted
  import path. It invalidates profile-derived state and preserves the FRONT photo,
  validation, landmark source and correction audit. FRONT replacement/retake
  preserves the unaffected profile input while combined downstream caches expire.
- Missing/rejected photos offer replacement in the same editable scan, preserving
  its FRONT capture. Completed history cannot be replaced. Corrupt derived profile caches
  are recoverable misses. Historical completed analyses remain read only.

## Assistance policy and honest limits

The current image-specific policy is explicitly **unvalidated local preview**
(`wp09-local-preview-v1`, source origin `DEMO_LOCAL`). Its fixed illustrative
anchors/regions are not claimed to be empirically valid anatomy. The UI states
that these guides cannot generate measurements or scores. It does not relabel a
real photo as synthetic, invent face count/pose/confidence, or bypass photo quality.

For this preview, region half extents and maximum displacement are 0.09 isotropic
short-edge units. These are UI demonstration constraints, not production evidence.
Every movement is checked against the original proposal or original missing-point
anchor, not the previous drag. Repeated drags cannot escape. Bounds cannot be
edited; decoding verifies the exact versioned demo policy. Out-of-bounds movement
is rejected. Users can choose another photo when its anatomy does not fit a guide.

Automatic proposal installation is a typed storage seam using the same WP10
model; synthetic tests exercise automatic and mixed confirmation. No reliable
automatic real-profile extractor is supplied by WP09. Production `CONSENTED_LOCAL`
assistance remains unavailable without a reviewed policy. Eddy's engine continues
rejecting nonsynthetic inputs and unsupported definitions. The four synthetic
profile angles and eight unavailable candidate definitions are inherited unchanged.

The UI can complete the required point-confirmation sequence on a stored photo
under this labeled preview policy. This is not a claim that P4's real-profile
measurement/repeatability acceptance gate is closed.

## Persistence, invalidation and privacy

`ProfileAssistCodec` format 1 stores the original input, fixed policy, facing,
monotonic write token and immutable audit in SQLCipher payload `profile-input-v1`.
Restoration replays and compares every original/from/to/source/policy/time action.
Image and correction tokens are checked under the existing repository mutex.
Side/direction restart advances the write token instead of reusing revision zero.
Stale clients cannot write into a reset or replaced source.

The existing schema and encryption envelope stay unchanged. Shared DAO additions
provide view-aware invalidation and atomic profile side/payload updates. Pending
photo admission is preserved; UI confirmation never advances it to ACCEPTED.
A preview source cannot complete a scored analysis through generic/front completion.
Future profile scoring needs a reviewed completion path bound to source, correction
and policy revisions. Completed history is never rewritten.

No INTERNET permission, upload, analytics, sensitive logs, private face fixture or
new app dependency is introduced. Shared FLAG_SECURE leases protect overlapping
front/profile navigation entries until the last sensitive route exits;
bitmap/encoded buffers are released or zeroed on exit/cancellation. Backup exclusions,
SQLCipher/Keystore architecture and owner boundaries remain intact.

## Validation record

Focused local Kotlin harness: 48 tests passed, including 16 inherited WP10 tests
and 8 new WP09 tests. This harness uses Kotlin 2.0.21 and JSON runtime 1.7.3 in
ignored build output; it is separate from the pinned Android toolchain. It covers
LEFT/RIGHT, independent facing, all-zero fallback, original-anchor/cumulative
bounds, ineligible points, provenance codec/tamper/cache recovery, stale orientation
tokens, uniform aspect-fit inverse, automatic confidence preservation and refusal
to promote real/demo inputs into measurements.

Added Android storage tests cover reopen/owner isolation/stale corrections and
images, profile replacement/retake retaining FRONT corrections, FRONT retake
retaining profile corrections, corrupt-cache recovery and completed-history/scoring
safeguards. A synthetic-only accessibility smoke checks explicit orientation,
confirmation/navigation and captures a gray synthetic illustration for visual QA.
Final Android CI/build/lint/instrumentation/privacy/native results are recorded in
PR #22 and the delivery report, with exact-head evidence.

## P4 sync and real-device work

Reconcile these additive interfaces with Eddy at the P4 sync: DEMO_LOCAL preview
origin, the UI/storage codec/write token, automatic installation, view-specific
invalidation and revision-bound future completion. Both lanes still target the
same integration branch; neither PR is self-merged.

Production capture/assistance policy evidence, automatic extraction and real-profile
confidence remain WP10/integrated validation work. Required real-device checks are
both sides, touch/accessibility and resize/rotation behavior, screenshot/recents
protection, signed in-place update with retained data, and actual same-image/
repeated-capture reliability. Synthetic arithmetic and UI evidence cannot replace
those checks. WP09 can be reviewed at P4 sync with these limits explicit.
