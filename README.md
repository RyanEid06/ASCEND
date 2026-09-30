# ASCEND

ASCEND is an Android-first facial-analysis and looksmaxxing application built by Ryan and Eddy.

The product is based on standardized front + profile capture, deterministic facial measurements, T1-T5 per-metric tiers, four category scores, and an overall score. AI is an explanation/help layer, not the scoring authority.

## Product pillars

- Objective, reproducible geometry where a metric can actually be measured.
- Deterministic scoring relative to ASCEND's configured reference models.
- Harmony, Dimorphism, Angularity, Misc, and Overall outputs.
- One front image + one side-profile image per scan.
- Local-first face-photo privacy.
- Transparent metric overlays, ideal/reference visualization, and explanations.
- 13+ product; Hardmax/procedure guidance is restricted to 18+.
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

## Current stage

Architecture Freeze 2.0 is the current baseline. WP00 must be completed before permanent WP01 implementation begins. After WP00, both developers read the phase contracts, branch from the exact phase integration baseline, work in parallel, and stop at every mandatory sync/update gate before moving forward.

The detailed T1-T5 ranges, metric weights, rank thresholds, and final reference-model constants are intentionally not guessed in this repository. Ryan/Eddy will supply them and they will be inserted as versioned data, not hard-coded throughout the app.


## Implementation gate

The major product architecture, security boundary, data lifecycle, backend/auth model, signing/update flow and development environment are now documented before feature implementation. The deliberately unfinished inputs are the versioned facial-reference/statistical data (metric ranges, scoring anchors, weights, final rank thresholds and related provenance/content).
