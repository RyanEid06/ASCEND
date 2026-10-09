# WP12 — Appearance Details groundwork

Status: lane-private synthetic proposal, started on October 6, 2026 at the owner's request. This is preparatory P5 work, not a P5 acceptance or production-scoring claim.

Base: `8069a1e9bd6a88b0d65fc2a084f1f006041caf82`, shared by `main` and `phase/P05-integration` at start. Its Android CI run [37461784358](https://github.com/RyanEid06/ASCEND/actions/runs/37461784358) passed. Work is on `eddy/P05-WP12-misc-scoring-ux`, targeting the P5 integration branch. The WP11 branch had no implementation beyond that baseline when this work began.

## Scope and behavior

`core/appearance` proposes internal input, binding, reliability and presentation types for MISC, labeled **Appearance Details**. It has no app caller, persistence change or screen integration. It does not change the shared scorer, production assets, application version, permissions, signing or branch-specific APK profiles.

The adapter delegates hidden 0–100 scores, T1–T5 classification, within-category weighting, category weights and completion/coverage rules to the existing `ScoringEngine`. Binding membership must exactly match the enabled MISC metrics in the admitted model. Other categories must come from the caller's actual measurements; missing categories remain insufficient. The adapter does not fabricate ideal measurements to complete an appearance-only scan. Optional missing metrics can contribute a partial category only when the canonical model permits that coverage.

Cards expose the tier, neutral description and a useful retry instruction where capture can resolve the failure. They do not expose individual numeric scores. Explanation content is separate from numerical scoring. Unavailable features say **Cannot assess reliably**. Accepted features waiting for other required measurements show **Waiting for reliable measurements**, with no tier or category score.

## Admission and provenance

There are no production confidence, resolution or quality defaults. An explicit versioned policy is required. Lighting and focus must be usable under the policy's quality method. Each feature must identify the current image revision, an ROI revision, extractor ID/version, confidence method version, reliability state, ROI quality/resolution, finite value and finite confidence. Unknown, stale, incompatible, low-confidence or unavailable observations fail closed. The confidence floor is the greater of the policy and model minimum. Domain and canonical tier failures do not produce invented scores.

The report records the proposal version, input snapshot, policy version, model hash, definition versions, fairness evidence references and per-feature failures. Policy and binding versions are references to configurations that a future integration must retain immutably. ROI revision is currently an opaque reference, not proof of ROI ownership or freshness; resolving it against the actual source/ROI lifecycle is a WP11 integration prerequisite.

Every `CONSENTED_LOCAL` input is rejected. Only explicitly synthetic inputs and `SYNTHETIC_VERIFIED` observations are admitted. These markers support tests; they are not real-photo reliability evidence or a security boundary against arbitrary in-process callers.

The existing model trust gate runs first: draft/research paths, unreviewed benchmarks, invalid model definitions and version/hash drift cannot be promoted by the adapter. The parser fixture under `app/src/test/resources/appearance` supplies fake review labels, benchmark eligibility and simple numerical curves solely to exercise that gate and weighting behavior. It is not a production-approved model and is not an Android runtime asset.

## Fairness and content limits

Bindings reject skin color, ethnicity and health inference as scoring bases. The proposed feature taxonomy has no skin-tone input. Fixed descriptions avoid diagnosis, inferred hormone state or color ranking. Skin texture refers to spatial appearance patterns; future extractor review must verify that pigmentation, lighting and camera processing do not become scoring proxies.

These are structural checks, not evidence of empirical fairness. A nonblank evidence reference is not a completed review. No real-photo invariance, subgroup calibration, capture robustness or medical claim is established here.

## WP11 agreement required before integration

This proposal is internal and is **not a frozen shared interface**. WP11 and WP12 must jointly agree on:

- metric IDs and enabled membership, continuous/categorical representation, value domains, extractor definitions and versions;
- source/ROI identity and stale-data rejection, visibility/occlusion rules and ROI ownership;
- calibrated confidence meaning and method version, lighting/focus method, device/resolution sensitivity and evidence-backed thresholds;
- reviewed curves, T1–T5 rules, coverage/weights, benchmark eligibility and runtime-model approvals;
- evidence that appearance signals do not rank pigmentation or infer ethnicity/health, including real-photo subgroup and device evaluation;
- unavailable-state presentation and the storage/provenance seam before any app wiring.

The feature enum is a proposed vocabulary, not a promise that every listed feature is measurable or enabled. Categorical features are explicitly unsupported until their encoding and scoring definitions are reviewed. Production reference models remain drafts and unavailable for scoring.

## Validation

Fourteen synthetic unit tests exercise canonical score/tier/weight calculations, configurable category weights, deterministic input ordering, quality/confidence/domain failures, extractor and confidence-method drift, optional coverage, missing other categories, prohibited bases and invalid model/binding/version admission.

Local validation passed: all 133 Android JVM tests, debug APK assembly, Android lint, pinned-dependency policy, source privacy contract and whitespace checks. Inspection confirmed that the synthetic scoring fixture is absent from the debug APK. Release packaging and emulator checks are left to PR CI; this local run did not perform signed QA or device testing.

Remaining acceptance work includes WP11 extraction, the joint interface agreement, reviewed runtime configuration and empirical reliability/fairness evidence. Existing real-photo and physical-device signed-update gates remain open; no physical-device validation was performed for this groundwork.
