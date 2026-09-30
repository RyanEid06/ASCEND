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

## Current stage

Planning is frozen enough to begin implementation. Before coding a phase, both developers must read the phase contract, branch from the phase integration baseline, work in parallel, then stop at the mandatory sync gate before moving forward.

The detailed T1-T5 ranges, metric weights, rank thresholds, and final reference-model constants are intentionally not guessed in this repository. Ryan/Eddy will supply them and they will be inserted as versioned data, not hard-coded throughout the app.
