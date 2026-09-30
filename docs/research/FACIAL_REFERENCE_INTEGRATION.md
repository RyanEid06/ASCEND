# ASCEND Facial Reference Integration

Research dataset version: **2026.09.30-research-audit.2**  
Research schema version: **1.1.0**  
Runtime model schema: `reference-models/schema.json`

## Purpose

ASCEND has two deliberately separate data layers.

### 1. Research catalogue — `reference-data/v1/`

This is source-backed evidence inventory. It contains:
- `measurements.csv` — 147 measurement definitions only;
- `benchmarks.csv` — 200 source-specific benchmark/reference rows;
- `sources.csv` — 34 traced sources;
- `manifest.json` — dataset identity and evidence policy.

Research rows can describe competitor-public definitions, peer-reviewed population norms, aesthetic-preference studies, community conventions, and measurement-validation evidence.

**They are not runtime scoring configuration.**

Every benchmark row currently defaults to `runtime_scoring_eligible=false`.

### 2. Runtime scoring models — `reference-models/`

This is the only scoring authority the app may load.

Current files:
- `reference-models/schema.json` — strict runtime model contract;
- `reference-models/v1/male.draft.json`;
- `reference-models/v1/female.draft.json`.

The two v1 files are intentionally marked `DRAFT_NOT_RELEASE_SCORABLE`. They exist so WP04 can prove loading, schema validation, versioning, hashing, and fail-closed behavior without inventing ranges or thresholds.

WP04 creates validated production assets only after Ryan/Eddy explicitly freeze the real scoring model.

## Research data rules

1. Measurement calculation code and scoring evidence stay separate.
2. `measurements.csv` contains stable definitions/formulas/views/landmark requirements only. It does not contain T1–T7, ideal ranges, or scoring anchors.
3. Benchmark purpose is explicit:
   - `AESTHETIC_PREFERENCE`
   - `POPULATION_NORM`
   - `COMMUNITY_CONVENTION`
   - `COMPETITOR_REFERENCE`
   - `MEASUREMENT_VALIDATION`
   - `REFERENCE_ONLY` where needed later.
4. Population norms describe morphology. They are not automatically attractiveness ideals.
5. Community values can be preserved as community conventions but cannot become ASCEND scoring truth merely because they exist.
6. FaceIQ-public definitions/claims may be recorded, but missing proprietary curves/ranges must remain missing rather than guessed.
7. `definition_compatibility` must be exact enough for the ASCEND formula/landmark convention before evidence may inform a runtime constant.
8. Age applicability is explicit. The current catalogue is adult-derived; it does not provide a validated 13–17 normative model.
9. Do not use uncalibrated millimetres from ordinary photos. Metrics requiring physical calibration stay disabled unless ASCEND has a validated calibration path.
10. The production-enabled metric set is chosen from **source-supported + technically measurable + sufficiently repeatable** candidates, not by maximizing the count.

## Runtime scoring-model freeze

A runtime model may move from `DRAFT_NOT_RELEASE_SCORABLE` to `VALIDATED_RELEASE` only after review freezes:

- exact enabled metric IDs;
- formula/extractor feasibility for each enabled metric;
- benchmark IDs and benchmark-purpose rationale;
- T1–T5 boundaries;
- hidden 0–100 anchors;
- metric weights;
- category weights;
- category and overall coverage thresholds;
- blocking/required metrics;
- cross-metric score-scale comparability;
- measurement uncertainty / minimum meaningful score difference;
- community-rank thresholds;
- content/explanation IDs and recommendation tags where applicable.

No implementation may synthesize missing values to make this checklist pass.

## Runtime loading rules

- Strictly validate the model against `reference-models/schema.json`.
- Reject unknown/invalid fields in debug/CI loaders.
- Reject draft models for production scoring.
- Reject enabled IDs that do not exist in the metric-definition registry.
- Reject enabled metrics whose extractor/formula is not production-supported.
- Reject benchmark references that do not exist.
- Reject research evidence that is incompatible with the selected formula definition.
- Never fall back to `reference-data/` if the runtime model is missing or invalid.

## Historical result provenance

Persist enough information to make a completed scan stable and auditable:

- analysis/geometry engine version;
- landmark/profile/visual extractor versions;
- research dataset version used during the model freeze;
- runtime scoring-model version;
- reference-model version;
- exact enabled metric set or immutable model/config hash;
- score-scale/comparability version;
- coverage policy version and stored coverage;
- strongest/weakest tie-set IDs + extrema-selection version;
- benchmark IDs selected by the frozen runtime model.

Changing research files later must not reinterpret a historical scan.

## Tier semantics

Never collapse unlike evidence into one unlabeled concept:

- research population-distance bands = distance from a studied population, not attractiveness;
- aesthetic-preference experiments = study-specific preference evidence;
- community T1–T6/7 = community convention;
- competitor-private scoring curves = unavailable unless actually published;
- ASCEND T1–T5 = the explicit, versioned runtime model chosen by Ryan/Eddy.

That distinction must remain visible in provenance even if the user-facing UI stays simple.
