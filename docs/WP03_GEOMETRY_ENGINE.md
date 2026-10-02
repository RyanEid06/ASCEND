# WP03 Geometry Engine

Status: Phase 1 lane implementation. Pure Kotlin; no camera/model dependency.

## Scope

WP03 provides the deterministic geometry side of the Phase 1 contract:

- normalized point/vector/angle/ratio primitives
- roll-normalized coordinates and residual pose gates
- explicit missing/low-confidence/pose/resolution/calibration/degenerate failure states
- minimum-required-landmark confidence propagation
- formula registry with a narrow integration seam for WP04
- feasibility records for all 34 candidate geometry measurements from `SCORING_CONTRACT.md`
- eight representative formulas (four front, four profile)
- synthetic unit fixtures

It does **not** provide scoring constants, beauty ranges, rank thresholds, camera extraction, or production landmark mapping.

## Coordinate and pose contract

Input points are normalized image coordinates. Before any Euclidean geometry, x/y are converted into isotropic short-edge units using the real image resolution so portrait/landscape aspect ratio cannot distort ratios, angles, or roll correction. `PoseDeviation` is the residual deviation from the requested capture target (FRONT or PROFILE), not raw anatomical head yaw. Formula evaluation removes roll only after that aspect correction and before any vertical/horizontal projection. Upstream landmark adapters are responsible for mapping model-specific indexes to semantic `LandmarkId`s and for matching the pose sign convention.

## Fail-closed behavior

A formula returns `Unavailable` rather than guessing when:

- the capture view is wrong
- the short image edge is below the formula minimum
- residual yaw/pitch/roll exceeds limits
- a required landmark is missing
- required landmark confidence is too low
- calibration is required but absent
- a denominator/ray is degenerate
- the formula ID is not registered

No millimetre result is produced without validated scale calibration.

## Reliability

`SYNTHETIC_VERIFIED` means only that deterministic formula behavior is covered by synthetic unit fixtures. It is **not** repeated-capture validation. All repeated-capture tolerances remain null in WP03 and no candidate is marked `VALIDATED` yet.

This is intentional: WP04's release scorer should continue to fail closed until a later validation/configuration step explicitly promotes eligible measurements.

## Representative formulas

Front:

- `front.facial_elongation.v1` — source definition `FQ_H_005`
- `front.lower_face_subdivision.v1` — source definition `FQ_H_033`
- `front.jaw_to_cheek_width.v1` — source definition `FQ_H_032`
- `front.canthal_inclination.v1` — source definition `FQ_H_012`

Profile (assisted because profile landmarks require constrained confirmation until extraction is validated):

- `profile.nasofrontal_angle.v1` — `FQ_H_038`
- `profile.nasolabial_angle.v1` — `FQ_H_051`
- `profile.mentolabial_angle.v1` — `FQ_H_063`
- `profile.soft_tissue_jaw_angle.v1` — `FQ_H_064`

## WP04 integration seam

`GeometryFormulaRegistry.supports(formulaOrExtractorId, mode, calibrationRequired)` intentionally mirrors the WP04 validation query without importing the scoring package. At the Phase 1 sync gate, integration code can adapt this registry to WP04's `FormulaRegistry` interface without coupling the two parallel lanes during implementation.
