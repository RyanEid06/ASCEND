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

P5 synchronizes [WP11 local visual-feature extraction](docs/WP11_VISUAL_FEATURE_EXTRACTION.md) and [WP12 Appearance Details scoring groundwork](docs/WP12_APPEARANCE_DETAILS.md). The signed QA candidate is version 0.5.0 (versionCode 7); see the [P5 phone checklist and P6 handoff](docs/P05_QA_BUILD.md). Image-bound ROI sampling and synthetic appearance scoring are included, with explicit unavailable states. The existing capture/profile phone experience remains the testable UI; these new foundations have no production screen caller or adopted extraction-to-scoring binding.

Real-photo appearance/profile scoring, reviewed runtime models, empirical reliability/fairness and physical-device update acceptance remain open. The owner authorized merging this groundwork and preparing P6 branches; branch preparation does not certify the full P5 product gate or implement WP13/WP14. Shared phase sync and signed update checkpoints remain; ordinary implementation no longer requires a separate interface approval ceremony.

Source-backed facial measurement/reference research is stored under `reference-data/v1/`, while production scoring authority is isolated under `reference-models/`. Research rows are never loaded directly as runtime scoring constants. The current Male/Female runtime files are intentionally non-scorable drafts until the explicit scoring-model freeze supplies validated enabled metrics, T1-T5 curves, hidden-score calibration, cross-metric comparability rules, measurement-uncertainty rules, coverage thresholds, weights and rank thresholds.


## Implementation gate

The major product architecture, security boundary, data lifecycle, backend/auth model, signing/update flow and development environment are now documented before feature implementation. The deliberately unfinished inputs are the versioned facial-reference/statistical data (metric ranges, scoring anchors, weights, final rank thresholds and related provenance/content).
