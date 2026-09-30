# ASCEND Runtime Reference Models

This directory is deliberately separate from `reference-data/`.

- `reference-data/` is the research catalogue. It may contain competitor descriptions, community conventions, population norms, preference studies, and measurement-validation evidence.
- `reference-models/` is the only location from which production scoring configuration may be built/loaded.

## Safety rule

Research rows are **not runtime scoring constants**. Every row in `reference-data/v1/benchmarks.csv` is currently marked `runtime_scoring_eligible=false`.

A production model is created only after Ryan/Eddy explicitly review and freeze:
- enabled metric IDs;
- formula/extractor feasibility;
- benchmark purpose and definition compatibility;
- T1–T5 boundaries;
- hidden 0–100 anchors;
- weights;
- category/overall coverage thresholds;
- score-scale comparability;
- measurement uncertainty / meaningful-difference rules;
- rank thresholds.

## Files

- `schema.json` — strict contract for runtime scoring models.
- `v1/male.draft.json` and `v1/female.draft.json` — intentionally non-scorable placeholders used to prove parsing/versioning without inventing constants.

The app must never silently fall back from a missing validated runtime model to research/community data. A missing/invalid production model is a fail-closed configuration error.

Draft files must not be renamed to production assets until the scoring-model freeze has passed its validation gate.
