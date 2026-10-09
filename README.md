# ASCEND

ASCEND is an Android-first facial-analysis and looksmaxxing application built by Ryan and Eddy.

The product is based on standardized front + profile capture, deterministic facial measurements, T1-T5 per-metric tiers, four category scores, and an overall score. AI is an explanation/help layer, not the scoring authority.

## Product pillars

- Objective, reproducible geometry where a metric can actually be measured.
- Deterministic scoring relative to ASCEND's configured reference models.
- Harmony, Dimorphism, Angularity, Misc, and Overall outputs.
- One front image + one side-profile image per scan.
- Portrait-first phone UX with adaptive/resizable Android layouts.
- Local-first face-photo privacy.
- Transparent metric overlays, ideal/reference visualization, and explanations.
- 13+ product; the full V1 feature set, including curated informational Hardmax/procedure content, uses the same simple self-declared 13+ gate.
- Free-first development and beta; architecture prepared for later premium entitlements.
- Android/Google Play first. No iOS work until Android is finished.

## Canonical planning docs

- [Master Plan](docs/MASTER_PLAN.md)
- [Frozen Product Decisions](docs/DECISIONS.md)
- [Scoring Contract](docs/SCORING_CONTRACT.md)
- [Ryan/Eddy Workflow](docs/WORKFLOW.md)
- [Security Architecture](docs/SECURITY.md)
- [Threat Model](docs/THREAT_MODEL.md)
- [Backend/Auth Contract](docs/BACKEND_CONTRACT.md)
- [Data Lifecycle](docs/DATA_LIFECYCLE.md)
- [Release & Signing](docs/RELEASE_SIGNING.md)
- [Development Environment](docs/DEVELOPMENT_ENVIRONMENT.md)
- [Facial Reference Data v1](reference-data/v1/README.md)
- [Runtime Reference Models](reference-models/README.md)
- [Facial Reference Integration](docs/research/FACIAL_REFERENCE_INTEGRATION.md)

## Current stage

P4 combines WP09's constrained profile confirmation UI and WP10's synthetic profile formula groundwork. The signed QA candidate is version 0.4.0 (versionCode 6); see the [P4 phone test checklist](docs/P04_QA_BUILD.md). Real-photo profile measurements and scoring remain unavailable until reviewed production policies and validation exist. The phase merge/build does not close that acceptance work. Shared phase sync and signed update checkpoints remain; ordinary implementation no longer requires a separate interface approval ceremony.

P5 lane groundwork now includes [WP11 local visual-feature extraction](docs/WP11_VISUAL_FEATURE_EXTRACTION.md): image-bound ROI sampling, explicit semantic-mask signals, confidence/failure outputs and synthetic capture-sensitivity checks. Real-photo feature admission remains unavailable until reviewed providers and empirical validation exist. WP12 scoring integration and P5 acceptance remain at the phase sync boundary.

Source-backed facial measurement/reference research is stored under `reference-data/v1/`, while production scoring authority is isolated under `reference-models/`. Research rows are never loaded directly as runtime scoring constants. The current Male/Female runtime files are intentionally non-scorable drafts until the explicit scoring-model freeze supplies validated enabled metrics, T1-T5 curves, hidden-score calibration, cross-metric comparability rules, measurement-uncertainty rules, coverage thresholds, weights and rank thresholds.


## Implementation gate

The major product architecture, security boundary, data lifecycle, backend/auth model, signing/update flow and development environment are now documented before feature implementation. The deliberately unfinished inputs are the versioned facial-reference/statistical data (metric ranges, scoring anchors, weights, final rank thresholds and related provenance/content).
