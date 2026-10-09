# WP11 — Local visual-feature extraction groundwork

Ryan lane: `ryan/P05-WP11-visual-feature-extraction`, based on the shared green P5
baseline `8069a1e9bd6a88b0d65fc2a084f1f006041caf82`. Implemented on October 9, 2026
at the owner's request. This is extraction groundwork, not P5 acceptance or a
claim that appearance can already be assessed reliably on real photographs.

## Implemented behavior

`core/visual` is pure Kotlin. It accepts bounded opaque sRGB pixels from the
oriented, unmirrored, standardized final-crop image. `VisualBitmapAdapter` is the
Android ingress seam; it borrows the caller's bitmap without decoding or writing
files. Image and mask buffers are copied at ingress and explicitly released with
`use`/`close`; temporary sampled ROI buffers are cleared after extraction. There
is no network access, logging, persistence, schema migration or screen caller.

ROI proposals specify a visible rectangular region with orthogonal axes in
pixel-isotropic coordinates. Rotated regions are supported; shear, mirroring,
degeneracy and clipping are rejected. Bilinear pixel sampling and nearest-neighbor
semantic-mask sampling produce a versioned fixed grid. Source resolution is
checked before sampling; upsampling never makes a small source admissible. Each
axis is normalized within its supplied region; the signals below are dimensionless
spatial summaries, not calibrated physical dimensions or anatomical angles.

The image identity hashes dimensions, format and exact decoded pixels. An ROI
revision binds the image revision/hash, feature, geometry, ROI definition/provider,
policy version, grid size and mask content/provider. Both the image revision and
pixel hash must match on ROI/mask ingress. A report retains the policy snapshot,
ROI proposal, definition/provider versions and mask hash, without pixel/mask data.
`matchesCurrentSource` rejects reports after retake, crop, pixel or origin changes.
These digests describe local provenance; they do not replace owner-scoped storage
or defend against an arbitrary modified in-process caller.

## Candidate signals

None of these IDs is enabled in production scoring. No metric count is frozen.

| Feature | Current signal | Required input / limitation |
| --- | --- | --- |
| Hairline pattern | Mean lower hair-mask boundary / ROI height | Explicit hair mask; every column must expose a boundary; does not infer trichion, recession or a clinical condition |
| Hair density appearance | Foreground mask area / ROI area | Explicit semantic hair mask; coverage appearance, not follicle density or hair health |
| Brow density appearance | Foreground mask area / ROI area | Explicit semantic brow mask; region side/aggregation remains part of future ROI definition review |
| Brow shape | Maximum normalized centreline departure from its endpoint baseline | Visible mask trace in every column; bow descriptor, not a beauty ideal |
| Beard coverage | Foreground mask area / ROI area | Explicit semantic beard mask; visible absence can yield zero; absent mask is unavailable |
| Skin texture pattern | RMS adjacent-pixel difference / ROI luminance standard deviation | Explicit skin-region mask, usable lighting/focus and sufficient relative contrast; experimental spatial-frequency descriptor, not skin clarity, diagnosis or a score |
| Under-eye, lip, facial contour and skin evenness | Typed unavailable hooks | `UNSUPPORTED_DEFINITION` until representation, evidence and admissible extractors exist |

Masks must be supplied by an explicit semantic provider. No dark-pixel threshold,
skin-tone field, inferred sex, ethnicity or health classification exists here.
`VisualRoiProvider` is an extension seam; no automatic hair/skin/brow/beard
segmentation or anatomy provider is admitted. A frontal mesh is not treated as a
hairline or hair-density detector. The source photograph and mask must refer to
the same final crop and feature definition.

## Reliability and confidence

Extraction requires an explicit versioned `VisualPolicy`, visible ROIs, usable
lighting/focus with the exact quality method, sufficient source resolution and
explicit ROI/mask confidence. Confidence is the minimum of these two synthetic
input confidences. Its meaning is identified as
`wp11-synthetic-input-minimum-v1`; it is not an empirical probability of accurate
real-photo assessment.

Only `FixtureOrigin.SYNTHETIC` inputs can emit values, labeled
`SYNTHETIC_VERIFIED`. All `CONSENTED_LOCAL` inputs return `UNVALIDATED_CAPTURE`
with null value/confidence, even if a caller supplies a synthetic policy or high
confidence. Standalone ROI sampling can inspect bounded local pixels, but the
feature extractor has no path to publish real-photo measurements. Missing policy,
unknown quality, missing mask/ROI, low confidence, stale data, incompatible mask,
occlusion, clipping and insufficient resolution have explicit failure states.
There are no default production capture thresholds or attractiveness constants.

## WP12 integration proposal

WP12 remains independently owned in draft PR #25. No WP12 code was copied or
merged into this branch, and its internal proposal is not declared adopted.
The nine common feature names can be mapped to WP12's proposed vocabulary;
skin evenness is an additional unsupported hook requiring a representation decision.

At P5 sync, select reviewed signal-to-metric bindings explicitly; do not pass the
entire candidate catalogue as an enabled metric set. The caller must verify the
report against the current owned source, then map only successful observations,
their extractor ID/version, confidence method, ROI revision and native source
resolution. Carry the extraction policy/ROI/mask provenance alongside WP12's
policy/model/definition snapshots. Failed observations remain unavailable.
WP12's current `ScoringEngine` stays the sole numerical scoring authority.

The additional ROI provenance resolves the extraction-side identity ambiguity
identified in WP12's handoff. Storage retention, a combined immutable analysis
record, metric/extractor bindings, quality/confidence-method compatibility and
joint contract adoption remain integration work. WP12's example synthetic IDs and
curves must not be used as real appearance definitions. The skin texture signal
requires pigmentation/device/lighting fairness review before any scoring binding;
absence of a skin-color field does not establish fairness.

## Validation and remaining acceptance

Synthetic tests cover coverage/boundary/brow calculations, affine lighting changes,
pixel pigmentation changes with fixed masks, source-resolution changes, bilinear
sampling, aspect-correct rotated axes, explicit unsupported hooks, quality and
confidence failures, stale revisions/hashes, mask mismatch, ROI boundaries,
provenance, hostile dimensions, buffer ownership and release. These are engineering
checks, not empirical device or subgroup validation.

Before real-photo use: supply a reviewed ROI/semantic-mask provider and definition
for each enabled signal; validate mask/ROI confidence and capture quality; collect
consented private annotation and repeated-capture/device/lighting evidence; audit
pigmentation/subgroup fairness; freeze the runtime curves, membership, coverage and
weights with WP12; wire encrypted provenance/current-source invalidation; complete
physical-device signed-update QA. Production reference models remain drafts.
The app version, signing, permissions, database and QA packaging are unchanged.
