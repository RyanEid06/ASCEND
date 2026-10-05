# WP09 — Profile capture and assisted confirmation

Status: **BLOCKED for the P4 sync gate — shared profile contract not frozen.**
This is a baseline audit and a proposal for joint review, not an implemented UI
or an adopted API. No automatic accuracy or production correction policy is claimed.

## Exact baseline and preflight

Audited on 2026-10-05. Ryan lane: `ryan/P04-WP09-profile-capture-assist`.
PR target: `phase/P04-integration`. WP10 and main are outside this lane.

All remote tips below were fetched and matched the same commit before this document:
`030f979e9af46d960c5ba80b16b81cbd495e7db2`.

| Ref | Preflight result |
| --- | --- |
| `main` | PR #21 merged at this exact SHA |
| `phase/P04-integration` | Same SHA; no old P4 feature history to merge/rebase |
| `ryan/P04-WP09-profile-capture-assist` | Same SHA |
| `eddy/P04-WP10-profile-extractors` | Same SHA; no WP10 commits beyond baseline |

[PR #21](https://github.com/RyanEid06/ASCEND/pull/21) is merged into main.
[Main CI 37329925825](https://github.com/RyanEid06/ASCEND/actions/runs/37329925825)
completed successfully for this SHA: build/unit/lint/dependency/privacy checks,
ARM64 phone QA and universal release packaging, native 16 KB checks, and the
16 KB emulator storage/migration/restart job. This is baseline evidence, not
WP09 feature validation. The clean local Ryan checkout was fast-forwarded to
its matching remote baseline; no other local branch was moved.

Read the canonical MASTER_PLAN, WORKFLOW, P04_HANDOFF, RELEASE_SIGNING,
WP03_GEOMETRY_ENGINE, WP05_CAPTURE_EXPERIENCE, WP06_STATUS,
WP07_FRONT_LANDMARK_PIPELINE, WP08_FRONT_MEASUREMENTS, SECURITY and DATA_LIFECYCLE
documents under `docs/`, and inspected the current capture, geometry, front
correction, encrypted repository, DAO and storage test implementation.

## Existing contracts and implementation gaps

- `core/model/ScanContract.kt` already defines `ProfileSide.LEFT/RIGHT` and
  nullable `ScanSession.profileSide`. Reuse them rather than add a second side enum.
- `core/data/CaptureStorageContract.kt` requires side metadata for PROFILE
  captures. Encrypted `PhotoRow` and `ScanRow` persist it. Owner-scoped photo
  access, independent photo retakes and completed-history protection exist.
- `CaptureUiState` currently defaults to RIGHT. It does not record whether a
  person explicitly confirmed that side. Existing metadata alone cannot prove
  orientation was confirmed; the new flow must ask when evidence is unavailable.
- WP03 provides semantic `LandmarkId`, aspect-correct geometry and representative
  profile formulas. It does not provide profile source/provenance, orientation,
  assisted point initialization, correction policy or a profile UI/engine DTO.
- `FrontInput`, `FrontRevision`, `FrontMeasurementStorage` and `CorrectionPolicy`
  are explicitly front-only. Front corrections require an existing original
  point with non-null confidence, and eligibility comes from `FrontCatalog`.
  Reusing this API unchanged cannot support zero/partial-point profile fallback.
- `profile-input-v1` is only a reserved payload name in measurement-dependent
  invalidation. There is no profile codec, install/read/correction repository
  seam or profile completion provenance safeguard on integration or WP10.
- Both DAO capture replacement and retake call `invalidateDerived(scanId)`.
  They preserve the opposite photo/evidence but remove every derived payload,
  including FRONT source/corrections. Preserving independent FRONT derived data
  on PROFILE retake needs a jointly reviewed invalidation change.
- `LocalScanStore` still uses pending CV admission hooks. Assisted point
  confirmation must not silently mark a photo valid or bypass that quality gate.

No frozen shared P4 profile contract was found in either audited branch.
The assignment and WORKFLOW shared-contract rule therefore stop dependent
implementation here. No Kotlin interface, schema, formula, policy, dependency,
navigation or packaging change is made by this handoff.

## Proposed shared seam — requires Ryan/Eddy P4 acknowledgement

The following specifies capabilities for one jointly owned contract. Final API
names, serialization version and ownership must be agreed in integration and
adopted by both lanes before implementation; this document does not freeze them.
Reuse existing side, owner, semantic point and measurement-mode types where they
fit. Keep WP10 anatomical definitions, formula implementations and confidence
semantics under Eddy's ownership.

| Capability | Proposed content and invariant |
| --- | --- |
| Source identity | Scan ID and owner-scoped storage operation; opaque current PROFILE asset revision and actual standardized width/height. If a provider uses an image hash, verify it against that same asset; hash and asset revision are distinct. |
| Side/orientation | Reuse LEFT/RIGHT; represent unknown orientation and explicit user confirmation separately from reliable detected-side evidence, with method/version provenance. No silent default or mirroring. |
| Point requirements | WP10 supplies supported metrics, required semantic IDs, assisted-required eligibility and visibility/unavailable reasons. WP09 cannot decide anatomy or add arbitrary points. |
| Point data | Image-normalized finite coordinates on the final oriented, unmirrored crop; AUTO or ASSISTED source; nullable confidence with its method/version. Missing points stay absent until permitted assisted placement is confirmed. |
| Provider provenance | Provider/extractor/model version and artifact identity when applicable; unavailable extraction must not require invented model metadata. |
| Assistance policy | Version/evidence and production versus explicitly non-production applicability; point-specific expected region, fixed initialization anchor, maximum isotropic displacement and anatomical constraints. An anchor for a missing point is policy guidance, not an extracted landmark. |
| Correction audit | Immutable original proposal or explicit absence, fixed guidance anchor, previous/current coordinates, confirmation versus movement action, method/time, image revision, policy version and monotonically checked correction revision. Confirmation without movement must be representable. |
| Metric output | Metric/formula ID, nullable raw value/unit/confidence, reliability, exact semantic source points, existing AUTOMATIC/ASSISTED mode and explicit unavailable reason. Mixed-input metrics carry ASSISTED when required points are assisted. |
| Storage operations | Owner-scoped source revision/read/install/confirm/correct operations; compare both image and correction revisions under the existing repository serialization boundary. Persist source and audit in encrypted storage. |
| Completion | Bind any eventual completed report to the exact source/correction/policy versions consumed by the engine. Preserve completed history; reject stale or revisionless writes. No scoring implementation in WP09. |

Proposed side convention: anatomical subject LEFT/RIGHT on the unmirrored
standardized photo. Screen-facing direction is a separate presentation detail;
the engine and overlay must share an explicit convention. Existing records with
side metadata but no confirmation evidence require confirmation on entry.

## Proposed WP09 interaction after the freeze

Open the existing encrypted PROFILE image by scan ID within its owner scope.
Show the side confirmation first when orientation is unknown. Then guide the
person through the agreed required points, showing the active anatomical region,
an automatic proposal when available, and concise confirmation/retake controls.

For zero, partial, low-confidence or unsupported automatic proposals, enter
constrained assistance for each eligible missing/untrusted point using the
agreed policy. Preserve nullable confidence: human confirmation is not a numeric
confidence upgrade. WP10 must define when assisted evidence supports a metric;
unknown confidence cannot become 1.0 merely to enter WP03 geometry.

Every move must remain within its original region and maximum displacement from
the fixed original anchor, including repeated drags and restored sessions. Freeze
reject-versus-clamp behavior and boundary feedback together; reject is proposed
so attempted movement cannot silently change the accepted geometry. Prevent
arbitrary creation, confidence editing and numerical metric editing.

There are no reviewed profile regions, missing-point anchors, displacement
bounds or production confidence rules at this baseline. Do not copy synthetic
front thresholds into production. Until such evidence exists, a clearly labeled
synthetic/demo policy can support test fixtures only; real captures must offer
retake/unavailable rather than fabricated geometry. This unresolved policy work
means the guaranteed real-profile fallback is not delivered by this document.

Use the same accepted revision for visible point/line/angle overlays and the
WP10 engine input. Apply one uniform photo-fit transform; invert the agreed
isotropic/roll normalization once, never stretch axes or silently mirror sides.
Keep controls usable in compact/medium/expanded windows. Restore active point,
side confirmation and accepted correction revision through recreation, then
revalidate against storage before accepting further interaction.

## Proposed persistence, invalidation and privacy

Reuse AES-GCM photos, SQLCipher/Keystore structured storage, backup exclusions
and owner scoping. Scope FLAG_SECURE to the sensitive surface using existing
behavior; release bitmap and encoded buffers on exit. No network permission,
upload, analytics, private face fixture or sensitive logging is needed.

Jointly specify view-aware invalidation for both retake and replacement:
invalidate PROFILE source/audit/results plus every combined downstream cache,
while preserving unaffected FRONT photo, validation, source and correction audit.
FRONT retake must invalidate its own sources and combined dependents analogously.
Deletion still removes all scan data. Completed scans remain immutable; an
updated analysis requires a new scan. No migration or DAO change is made here.

Treat corrupt derived profile caches as explicit recoverable misses, while
keeping strict completed-history decoding. Reject stale images/corrections,
deleted scans, absent images, unsupported sides and unconfirmed orientation.
Missing anatomy or a policy absent for a required point leads to retake/unavailable;
extraction failure alone leads to assistance when the frozen policy allows it.

## Required validation after implementation

No WP09 product code or new tests are implemented in this handoff. Required
future coverage includes LEFT/RIGHT and unknown-side confirmation; automatic
confirmation; zero/partial/low-confidence fallback; point eligibility and
confidence preservation; region/displacement rejection and cumulative-drag
escape; provenance replay; stale image/correction revisions; corrupt cache,
owner isolation, deletion, retake/replacement and unaffected FRONT retention;
completed-history immutability; rotation/recreation; aspect/roll/photo-fit
projection; exact overlay/engine point identity; inaccessible/unavailable states;
adaptive Compose and absence of unrestricted point/score editing.

Run the existing regression suite, clean debug/release/phoneQa builds, unit tests,
lint, relevant Android tests, dependency/privacy checks and packaged native
16 KB checks after implementation. Preserve separate ARM64/universal/dev
artifacts and the existing production signing/update chain.

This documentation-only handoff uses baseline main CI as existing runtime
evidence. Fresh local checks passed for all 20 pinned dependency versions,
storage backup/permissions/cleartext policy, all 11 referenced project document
paths and diff whitespace. The front-fixture checker could not execute because
the available bundled Python lacks `jsonschema`; no dependency was installed.
No fresh local Android build, unit/lint/instrumentation or native APK check was
run for this documentation-only change. The proposal PR's own CI status is
reported with delivery; neither baseline CI nor document checks prove WP09 behavior.

Real-device work remains: guided profile capture/confirmation on both sides,
reviewed bounds and abuse controls on actual images, accessibility and resizing,
FLAG_SECURE/recents handling, signed in-place update/data retention, and empirical
same-image/repeated-capture validation. Private media stays outside Git.

## P4 sync action and readiness

Ryan and Eddy must jointly freeze the source/point/output/storage seam, the
left/right and orientation conventions, assisted-required catalogue and missing
point initialization, confidence/reliability rules, correction policy applicability,
and view-specific invalidation/completion safeguards. Land the agreed contract
on integration, update both lanes to it, then resume WP09 UI work and WP10
engine work against that single seam. Reviewed production policies and photo
admission evidence are also needed for a real-profile completion claim.

WP09 status: **BLOCKED**, not READY. Existing P0–P3 behavior is unchanged by
this document. Profile confirmation, assisted fallback, overlays and the WP10
handoff remain unimplemented. No PR merge is authorized.
