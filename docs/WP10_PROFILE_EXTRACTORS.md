# WP10 — Profile formula groundwork and contract proposal

Started at the user's request on 2026-10-06 in
`eddy/P04-WP10-profile-extractors`; PR target `phase/P04-integration`.
Baseline: `030f979e9af46d960c5ba80b16b81cbd495e7db2`, the green main ARM64 +
universal QA packaging commit. Main, integration and WP10 matched on preflight.
Ryan's WP09 tip was `b6537185ea76597d96429ebae7daa9767cda083d`, containing
`docs/WP09_PROFILE_CAPTURE_ASSIST.md` and no profile implementation.

Current sync status (2026-10-06): the owner authorized merging both P4 lanes and
preparing signed QA version 0.4.0. PR #23 and WP09 PR #22 are now integrated,
including the common profile types, bounded preview assistance, encrypted audit
persistence and view-specific invalidation. The standalone acknowledgement stop
below is historical and superseded by the owner's relaxed workflow. **Real-photo
P4 acceptance remains OPEN:** no production confidence/correction policy or
validated real-profile measurements are supplied by this synthetic groundwork.

## Scope completed

`core/profile` contains internal-only types and a pure Kotlin experiment:

- Semantic profile angle evaluation with explicit unavailable results, per-required
  point/global confidence gates, confirmation requirements and mixed-input modes.
- A catalogue covering the 12 existing profile candidate IDs. Four angles have
  deterministic synthetic fixtures; eight remain unsupported.
- Zero/partial-proposal constrained assistance for eligible points. Missing-point
  guidance is distinguished from extracted anatomy. Confirmation without moving a
  point is represented explicitly; no numerical metric/score editing exists.
- Fixed original anchors, region and isotropic displacement rejection; repeated
  drags cannot accumulate displacement. Image/side/revision checks, immutable audit,
  monotonic timestamps and replay validation reject stale or corrupted evidence.
- Aspect correction before roll removal and the exact inverse for later photo
  overlays. No silent mirroring or reuse of the original detector mesh after edits.
- Public synthetic unit-test data only, with arithmetic tolerances explicitly
  separated from empirical capture repeatability.

No existing model, geometry registry, repository, DAO, schema, capture policy,
navigation route, signing/version or ABI packaging contract changes. No dependency
is added. Internal declarations cannot be adopted as a public WP09 seam without
the joint contract decision. No current app flow calls this experiment.

### WP09 adoption update (2026-10-06)

The preceding status records the WP10 groundwork commit. The owner subsequently
removed per-interface approval stops and authorized WP09 to reuse these internal
types. The Ryan branch now includes the assisted profile UI/storage adapter and
an explicitly unvalidated DEMO_LOCAL preview origin; the synthetic-only formula
gate remains unchanged. Shared interface reconciliation and production profile
acceptance remain at P4 sync. See `WP09_PROFILE_CAPTURE_ASSIST.md`.

## Definitions and unsupported candidates

The three existing WP03 formula definitions are retained as unsigned interior
angles in [0,180] degrees:

| Candidate suffix | Ordered first / vertex / third | Definition |
| --- | --- | --- |
| nasofrontal_angle | glabella / nasion / supratip | FQ_H_038 |
| nasolabial_angle | columella / subnasale / labrale superius | FQ_H_051 |
| mentolabial_angle | labrale inferius / sublabiale / soft-tissue pogonion | FQ_H_063 |
| soft_tissue_jaw_angle | ramus reference / visible gonion / mandibular-border reference | FQ_H_064; soft-tissue proxy |

The jaw experiment uses the explicit semantic ID `gonion_visible` and a distinct
experimental formula ID; it does not alias a right-profile point to WP03's
`gonion_left`. This is a proposal for joint anatomy/adapter review. It is a
surface-contour estimate, not a skeletal gonial measurement or diagnosis.

`reference-data/v1/measurements.csv` specifies **oriented** convexity angles.
`facial_convexity` and `convexity_including_nose` therefore stay unavailable rather
than silently substitute unsigned angles. `nasal_projection`,
`nasal_bridge_curvature`, `upper_lip_projection`, `lower_lip_projection`,
`chin_projection` and `chin_neck_contour_angle` also remain unavailable pending
their denominator/reference-line/contour conventions. Research rows are definition
context, not production benchmarks; no score ranges, weights or constants are loaded.

## Proposed response to WP09's P4 contract

This responds to Ryan's proposal without treating it as adopted. Required joint
decisions before shared interface or UI/storage work:

1. Reuse existing `ProfileSide.LEFT/RIGHT` as **anatomical subject side**. Keep
   orientation unknown/confirmed and method provenance separate from legacy side
   metadata. Screen-facing direction is separate; source coordinates are on the
   final oriented, unmirrored standardized crop.
2. Bind source and correction revisions to the current owner-scoped PROFILE asset.
   Preserve actual width/height, usable face resolution, face count, residual pose,
   fixed roll origin and provider/confidence method versions. Unknown pose and
   confidence remain null. Unavailable extraction need not invent a model/hash.
3. Freeze ordered anatomical point definitions and the eligible assistance subset,
   especially visible gonion/ramus/border references. No guessed frontal-mesh
   indexes or implied 90-degree anatomy. Automatic proposals are optional.
4. Version actual image/side-specific regions, fixed missing-point guidance,
   displacement bounds and anatomy constraints. Reject movement outside them;
   never silently clamp. The experiment uses synthetic bounds only.
5. Freeze the confidence distinction between automatic observations and assisted
   confirmation. The experiment preserves known confidence, including low values;
   confirmation never raises it. Missing-point assistance has null confidence and
   cannot fabricate detector certainty. Synthetic geometry can be available with
   null confidence in ASSISTED mode; this is not release scoring admission. A known
   low confidence still fails; confirm/move alone is not an evidence upgrade.
6. Agree the serialized source/audit/report names and formats, owner-scoped
   repository operations, view-aware retake/replacement invalidation, and completion
   checks binding image + correction + policy revisions to exact measured values.
   This work does not implement or change those storage contracts.

Land the agreed shared seam on integration and update both lanes before wiring
WP09/WP10. This PR does not acknowledge on Ryan's behalf, modify his branch or
claim the P4 freeze. Production regions and confidence/capture admission require
reviewed evidence separately from software API acknowledgement.

## Experiment limitations and validation

All calculations and assistance actions reject `CONSENTED_LOCAL` input. Only
explicitly synthetic input with a supplied test policy can yield values. This is
a deliberate boundary for preparatory work while shared/production decisions are
open. The fixture's 1e-9-degree tolerance tests floating-point arithmetic, not
camera reproducibility. Reliability never becomes VALIDATED; all existing
feasibility/repeated-capture and draft scoring status remain unchanged.

Tests cover fixture values, all candidate IDs, missing/unknown/low required points,
inclusive confidence boundaries, policy/orientation/count/pose/resolution failures,
left/right reflection, aspect/roll invariance and overlay inverse, degenerate rays,
zero/partial assistance, mixed modes, confirmation without movement, confidence
preservation, cumulative-drag escape, changed guidance, stale image/side/revision,
provenance tampering and defensive snapshots.

Local verification passed 79 pure Kotlin tests (including 16 new profile tests)
and all 111 Android debug unit tests with zero failures, errors or skips. Pinned
dependency, source storage/privacy and diff-whitespace checks passed. The PR
records full Android build/lint and CI results when finished. No UI was changed,
so no screenshot is claimed. No migration or instrumentation behavior is
introduced. CI remains responsible for the existing emulator regression suite.

## Remaining P4 acceptance

- Joint shared-contract acknowledgement and reviewed real-profile capture,
  orientation, anatomical, confidence and assistance policies.
- Owner-scoped encrypted profile source/audit persistence, view-specific
  invalidation and stale-completion safeguards, adopted by both lanes.
- WP09 integrated guided fallback/overlays, then actual same-point real-photo
  measurement and low-confidence behavior on both sides.
- Both developers' human capture/restart checkpoint, signed physical-phone
  in-place update with retained settings/history/encrypted assets, and empirical
  same-image/repeated-capture evidence.

Neither this groundwork nor green synthetic CI closes P3/P4 human acceptance.
No main/integration merge or production reliability promotion is authorized here.
