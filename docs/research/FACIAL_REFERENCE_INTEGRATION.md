# ASCEND Facial Reference Dataset v1

Dataset version: **2026.09.30-research-audit.1**  
Schema version: **1.0.0**

## What this is

A normalized, source-backed dataset for ASCEND's deterministic facial-analysis engine. It contains **147 measurement definitions**, **200 benchmark records**, and **34 sources**.

The key design rule is: **measurement calculation code and benchmark data are separate**. Kotlin computes the metric by stable `measurement_id`; JSON supplies definitions, applicability, source-backed reference values, tiers when actually available, evidence quality, and provenance.

## Recommended Android placement

Copy `reference-models/` into:

`app/src/main/assets/reference-models/`

Copy the Kotlin files into your reference/data layer and adjust the package name if needed. The loader expects:

`assets/reference-models/v1/dataset.json`

Use `kotlinx.serialization` for strict decoding. Keep `ignoreUnknownKeys = false` in CI/debug so accidental schema drift fails loudly.

## Files

- `reference-models/v1/dataset.json` — easiest single-file runtime asset.
- `measurements.json` — stable definitions/formulas/views/landmarks/photo feasibility.
- `benchmarks.json` — FaceIQ, research, community, and derived values with **field-level provenance**.
- `sources.json` — URLs/DOIs/quality notes.
- `runtime-index.json` — fast per-measurement index of available benchmark families and exact-research candidates; scoring remains disabled until a versioned model explicitly selects rules.
- `schema.json` — JSON Schema for CI validation.
- `scoring-policy.json` — safe selection policy; intentionally contains no invented universal weights.
- `profiles/*.json` — benchmark indexes for neutral/male/female filtering. These do **not** bypass population/age checks.
- `android/*.kt` — DTOs and asset loader.
- `database/supabase_reference_schema.sql` — optional server mirror.

## Runtime rules

1. Never parse `formula_display` into math at runtime. Map each stable measurement ID to deterministic Kotlin geometry code.
2. A benchmark's `published_or_reported.*.provenance` is authoritative for **that field**. A research row can contain assistant-derived tiers; those tier fields explicitly say so.
3. `source_family = published by BP/community system` is community convention only. It may power a separately labeled community mode, never scientific/FaceIQ claims.
4. FaceIQ records with empty numeric values are intentional: public formula/definition exists but the proprietary scoring curve/range was not publicly verified.
5. `definition_compatibility` must be checked before automatic scoring. Approximate or uncertain definitions should remain display/reference-only.
6. Do not use uncalibrated millimetres from ordinary photos. Prefer ratios/degrees unless scale calibration is available.
7. Persist `dataset_version`, scoring-model version, landmark-model version, and the chosen `benchmark_id` with every analysis result.
8. The 147 measurement definitions in this dataset are a reference catalogue, not the runtime scoring count. A versioned scoring model must explicitly select its enabled metric IDs; do not hard-code 33, 34, 147, or another count in the app.
9. Hidden 0–100 metric scores may be compared across different metrics only when the scoring model explicitly calibrates those metrics to the same cross-metric score semantics/version. Equal numeric ranges alone are not enough.
10. Completed results must retain the enabled metric-set/config identity, coverage policy/result, cross-metric score-scale version, and deterministic strongest/weakest selection provenance where that feature is available.

## Tier semantics

The engine may output T1–T5, but the **meaning** of those tiers must be attached to the benchmark/model:

- research `|z|` bands = distance from a population reference, **not attractiveness**;
- aesthetic experiment targets = study-specific preference result;
- community T1–T6/7 = published community convention;
- FaceIQ private curve = unavailable unless FaceIQ publishes it.

Do not collapse these into one unlabeled global tier table.

## Suggested result record

```json
{
  "measurement_id": "FQ_H_004",
  "raw_value": 1.98,
  "unit": "ratio",
  "benchmark_id": "COMM_FQ_H_004",
  "benchmark_family": "published by BP/community system",
  "tier": "T1",
  "dataset_version": "2026.09.30-research-audit.1",
  "scoring_model_version": "ascend-score-v1",
  "landmark_model_version": "mediapipe-face-landmarker:<pin-version-here>"
}
```

That record is auditable: changing reference data later does not rewrite what an old result meant.