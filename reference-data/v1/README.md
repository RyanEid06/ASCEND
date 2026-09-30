# ASCEND Facial Reference Data v1

Version: 2026.09.30-research-audit.2

This directory is the canonical source-backed facial measurement/reference dataset for ASCEND.

## Files
- `measurements.csv` — 147 normalized measurement definitions only; no T1–T7/reference/scoring constants.
- `benchmarks.csv` — 200 source-specific benchmark/reference records with explicit benchmark purpose, age applicability, definition compatibility and `runtime_scoring_eligible`.
- `sources.csv` — 34 traced source records.
- `manifest.json` — dataset identity, counts, and provenance policy.

## Data rules
1. Measurement definitions and benchmark families stay separate.
2. FaceIQ-public, peer-reviewed research, community/BP, and ASCEND-derived values must never be silently merged.
3. Missing FaceIQ proprietary ranges/curves remain unavailable rather than guessed.
4. Community tier tables are implementation references, not scientific consensus.
5. Population/sex-specific norms must retain their source population and method.
6. Runtime scoring constants must be versioned and must not be hard-coded throughout UI code.
7. Historical analyses retain the reference-model version used at calculation time.
8. Every research benchmark currently defaults to `runtime_scoring_eligible=false`; production scoring requires a separately reviewed model under `reference-models/`.
9. Population norms are morphology evidence, not automatic attractiveness ideals.
10. Age applicability is explicit; this v1 research catalogue currently contains adult-derived evidence, not a validated 13–17 norm set.

See `docs/research/FACIAL_REFERENCE_INTEGRATION.md` and `docs/SCORING_CONTRACT.md`.
