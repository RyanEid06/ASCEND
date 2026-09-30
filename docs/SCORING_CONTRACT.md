# ASCEND Scoring Contract

Version: Architecture Freeze 2.1

This file defines the engineering contract for scoring. It does not supply the final beauty-reference constants; those are configuration data to be supplied later.

## 1. Separation of concerns

The scoring pipeline is:

Photo -> quality validation -> landmark/visual-feature extraction -> normalized measurements -> metric evaluator -> hidden 0-100 metric scores -> T1-T5 labels -> weighted category scores -> overall /10 -> overall community rank.

AI is outside this pipeline.

## 2. Required metric definition

Every metric configuration must contain enough information to evaluate it without hard-coded UI logic.

Recommended fields:

- id
- displayName
- category: HARMONY | DIMORPHISM | ANGULARITY | MISC
- referenceModel: MALE | FEMALE | BOTH
- view: FRONT | PROFILE | VISUAL
- valueType: RATIO | DEGREE | NORMALIZED_DISTANCE | CATEGORICAL | CONTINUOUS_VISUAL
- unitLabel
- formulaId or extractorId
- required landmarks/features
- ideal/T1 definition
- tier boundaries T1-T5
- hidden score curve/anchors for 0-100
- metricWeight
- requiresManualConfirmation
- minimumConfidence
- unavailableBehavior
- explanationId
- recommendationTags
- modelVersion
- provenance/notes
- enabled flag / membership through the versioned enabled metric set
- requiredForCompletion
- extremaEligible
- scoreScaleId / cross-metric comparability version
- measurementUncertainty or minimumMeaningfulScoreDelta when validated

## 3. Enabled metric set

The production/runtime metric set is configuration, not a source-code constant.

Requirements:
- A scoring-model version declares the exact enabled metric IDs for each reference model/version.
- Code must never assume that the active set contains 33, 34, 147, or any other fixed count.
- The current 34 geometry measurements are a candidate engineering catalogue. The research dataset contains a larger definition catalogue. Neither automatically defines the V1 enabled scoring set.
- Disabled/reference-only metrics may exist in the dataset without participating in category, overall, coverage, or strongest/weakest calculations.
- Every completed analysis persists the exact enabled metric IDs or an immutable configuration snapshot/hash that resolves unambiguously to that set.

## 4. Why hidden 0-100 exists

The user sees tiers, not an individual /10 or /100 rating.

Example:

- Value A = T1
- Value B = T1

Both are “ideal” at the tier level, but the backend may score them 97 and 99 because one is closer to the configured optimum.

This allows smooth category calculations without cluttering the UI.

## 5. Do not assume one universal curve

Some metrics may have:
- a center optimum
- an ideal interval/plateau
- one-sided preference
- asymmetric penalties
- categorical or visual states

Therefore the engine should use configurable piecewise score anchors rather than one hard-coded formula for every metric.

Example concept:

value -> interpolated hiddenScore
anchor 1 -> 100
anchor 2 -> 97
anchor 3 -> 90
...
anchor n -> 0

Tier boundaries are evaluated from the same reference config.

## 6. Category score

For a category C:

categoryPercent = sum(metricHiddenScore_i * weight_i) / sum(weight_i for available metrics)

categoryScore10 = categoryPercent / 10

Rules:
- Only metrics that are enabled, applicable to the selected reference model, and valid for the current scan are considered.
- Missing metrics may not silently receive average/ideal values.
- If a required/high-value metric is unavailable because capture is bad, prefer retake.
- Coverage is computed against the enabled/applicable metric set, not against a hard-coded metric count.
- Each category has a versioned minimum publishable coverage rule. The full scan may also define an overall minimum coverage rule and blocking required metrics.
- Coverage should be weight-aware where metric weights differ: valid enabled/applicable weight divided by total enabled/applicable weight.
- If any required completion rule fails, return **INSUFFICIENT_RELIABLE_MEASUREMENTS** / **Not enough reliable measurements** instead of presenting the scan as complete.
- Coverage values and the coverage-policy version are persisted with the completed result.

## 7. Overall score

Initial V1 category weights:

- Harmony: 25%
- Dimorphism: 25%
- Angularity: 25%
- Misc: 25%

These are initial product weights, not a scientific claim that the categories are equally predictive or equally important. The values remain versioned configuration and must pass scoring-model validation/sensitivity review before production freeze.

overall10 =
  harmony * 0.25 +
  dimorphism * 0.25 +
  angularity * 0.25 +
  misc * 0.25

Store full precision. UI can display up to two decimals.

If a full category is unavailable, do not pretend the scan is complete. Require remediation/retake unless a later product rule explicitly defines a partial result.

## 8. Rank mapping

Final community rank is mapped only from overall score and selected reference model.

Male labels:
Sub 5 -> LTN -> MTN -> HTN -> Chadlite -> Chad -> True Adam

Female labels:
Sub 5 -> LTB -> MTB -> HTB -> Stacylite -> Stacy -> True Eve

Exact score thresholds remain TBD and belong in versioned configuration. Rank vocabulary is community terminology and must not be represented as a scientific diagnosis, biological identity, or population percentile.

## 9. Versioning

Every result stores:
- analysisEngineVersion
- landmarkModelVersion
- referenceModelVersion
- metricConfigHash
- scoringModelVersion
- enabledMetricIds or an immutable config snapshot reference that resolves to the exact set
- scoreScaleVersion / crossMetricComparabilityVersion
- coveragePolicyVersion
- extremaSelectionVersion
- strongestMetricIds
- weakestMetricIds
- recommendationVersion

Even if “ideal values” are expected to stay stable, implementation bugs, detectors, formulas, or weights can change. Historical scans must remain reproducible.

## 10. Geometry rules

- Normalize image roll before measurements.
- Track yaw/pitch/roll quality thresholds.
- Use dimensionless ratios and angles where practical.
- Do not report millimetres unless scale calibration exists.
- Do not silently estimate anatomy that is not visible.
- Profile measurements that depend on uncertain soft-tissue landmarks must support constrained user confirmation.
- Front and profile extractors are separate components.

## 11. Suggested initial geometry catalogue

The existing planning catalogue currently contains 34 candidate measurements: 22 frontal and 12 profile. It is a candidate engineering set, not a declaration that all 34 are universal beauty laws and not a hard-coded production metric count.

Front candidates:
1. facial elongation
2. midface height fraction
3. lower-face subdivision
4. upper/middle facial proportion
5. jaw-to-cheek width
6. chin breadth
7. intercanthal spacing
8. interocular spacing
9. eye fissure width
10. eye aperture ratio
11. canthal inclination
12. brow-eye clearance
13. brow inclination
14. brow peak location
15. nasal width
16. nasal lateral deviation
17. mouth width
18. vermilion fullness
19. upper/lower vermilion balance
20. philtrum proportion
21. mouth-corner inclination
22. landmark asymmetry

Profile candidates:
23. facial convexity
24. convexity including nose
25. nasofrontal angle
26. nasolabial angle
27. mentolabial angle
28. nasal projection
29. nasal bridge curvature
30. upper-lip projection
31. lower-lip projection
32. chin projection
33. soft-tissue jaw-angle estimate
34. chin-neck contour angle

Final definitions, formulas, ranges, enabled membership, and category membership must be frozen in a dedicated metric-config WP before release scoring is considered valid.

## 12. Misc scoring

Misc uses the same hidden 0-100 -> category pipeline but may receive values from separate deterministic visual-feature extractors instead of geometry.

Examples:
- hairline/density appearance
- eyebrow density/shape
- eyelash visibility
- skin clarity/evenness
- beard coverage
- under-eye appearance
- lip appearance
- facial leanness

Do not turn color/ethnicity into a quality score. Do not infer disease or hormone status.

## 13. Cross-metric comparability and Strongest/Weakest

A hidden 0-100 score is not automatically comparable with every other hidden 0-100 score merely because both use the same numeric range.

Before a metric may participate in Strongest/Weakest selection, its scoring configuration must declare that it is calibrated to the active common score semantics (`scoreScaleId` or equivalent). That common scale must define what equal numeric values mean across unlike metrics.

Eligible candidate set:
- metric ID is in the active enabled metric set
- metric applies to the selected reference model
- measurement is available
- confidence meets its configured minimum
- hidden score is present and finite
- metric is marked `extremaEligible`
- metric uses the active comparable score scale/version
- result passed the applicable coverage/completion policy

Selection:
1. Compute the maximum and minimum hidden scores over the eligible candidate set.
2. Build strongest and weakest tie sets using the configured measurement-uncertainty / minimum-meaningful-difference policy.
3. If score differences are smaller than validated measurement error, treat the metrics as effectively tied.
4. Store tie sets in deterministic metric-ID order.
5. If no adequate comparable candidate set exists, publish no Strongest/Weakest claim.

UI:
- single strongest: **Strongest measured feature**
- single weakest: **Largest improvement opportunity**
- tie: use plural/equivalent wording and do not imply a clear ordering inside the tied set
- show metric name, T1-T5 tier, measured value where appropriate, and a short explanation

AI may explain these deterministic selections. AI cannot select, reorder, replace, or invent them.

Measurement uncertainty must come from versioned validation/configuration. Do not invent one universal epsilon merely to make ties convenient.

## 14. Determinism requirements

Given:
- same input image bytes
- same crop
- same manually confirmed landmarks
- same model versions/config

ASCEND must produce the same numeric result.

AI output must never be used to modify the deterministic score.

## 15. Scoring tests

Before any scoring WP is mergeable:
- exact formula unit tests
- boundary tests for every T1-T5 transition
- interpolation tests
- symmetry/asymmetry test fixtures
- enabled/disabled metric-set tests with no hard-coded count assumptions
- missing metric behavior
- low-confidence exclusion
- required/blocking metric behavior
- category and scan coverage thresholds
- explicit insufficient-reliable-measurements state
- metric-weight normalization
- category weight tests
- cross-metric score-scale eligibility
- strongest/weakest single-winner cases
- uncertainty/tie cases
- deterministic tie ordering
- model/config/version changes preserving old extrema selections
- repeat scans under identical inputs/config returning identical selections
- repeated-capture reliability fixtures once empirical uncertainty bounds exist
- rank threshold tests once thresholds exist
- snapshot/golden tests for versioned example scans
