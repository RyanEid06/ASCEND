package app.ascend.mobile.core.front

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.MeasurementView

/** Additive registry. WP03's registry, shared models and profile formulas are unchanged. */
class FrontMeasurements(private val policy: FrontPolicy?) {
    fun measure(input: FrontInput, correctedIds: Set<String> = emptySet()): FrontReport {
        val stable = input.copy(landmarks = input.landmarks.toSortedMap())
        val registry = policy?.let { GeometryFormulaRegistry(
            RepresentativeFormulaRegistry.formulas.filter { it.view == MeasurementView.FRONT } +
                FrontCatalog.mappings.filter { it.formulaId != null && it.formulaId !in RepresentativeFormulaRegistry.registry.supportedFormulaIds }
                    .map { FrontMappedFormula(it, itPolicy = itPolicy()) }
        ) }
        val results = FrontCatalog.mappings.map { mapping ->
            val gateFailure = gate(stable, mapping)
            val frame = if (stable.residualPose != null) stable.geometryFrame() else null
            val points = mapping.required.mapNotNull { id -> frame?.normalizedPoint(id)?.let { id.value to it } }.toMap()
            if (gateFailure != null) FrontMetricResult(mapping.metricId, mapping.formulaId, mapping.unit,
                null, null, gateFailure, MeasurementMode.UNSUPPORTED, points)
            else {
                val result = requireNotNull(registry).measure(mapping.metricId, requireNotNull(mapping.formulaId), requireNotNull(frame))
                when (result) {
                    is GeometryMeasurementResult.Available -> FrontMetricResult(mapping.metricId, mapping.formulaId,
                        mapping.unit, result.rawValue, result.confidence0To1, null,
                        if (mapping.required.any { it.value in correctedIds }) MeasurementMode.ASSISTED else result.measurementMode, points)
                    is GeometryMeasurementResult.Unavailable -> FrontMetricResult(mapping.metricId, mapping.formulaId,
                        mapping.unit, null, null, when (result.failureCode) {
                            GeometryFailureCode.CALIBRATION_REQUIRED -> FrontFailure.CALIBRATION_REQUIRED
                            GeometryFailureCode.INSUFFICIENT_RESOLUTION -> FrontFailure.INSUFFICIENT_RESOLUTION
                            GeometryFailureCode.POSE_OUT_OF_RANGE -> FrontFailure.POSE_OUT_OF_RANGE
                            GeometryFailureCode.MISSING_LANDMARK -> FrontFailure.MISSING_LANDMARK
                            GeometryFailureCode.LOW_CONFIDENCE -> FrontFailure.LOW_CONFIDENCE
                            GeometryFailureCode.DEGENERATE_GEOMETRY -> FrontFailure.DEGENERATE_GEOMETRY
                            else -> FrontFailure.UNSUPPORTED_DEFINITION
                        }, MeasurementMode.UNSUPPORTED, points)
                }
            }
        }
        return FrontReport(FRONT_CONTRACT_VERSION, stable, policy?.version, "wp08-front-geometry-v1+$GEOMETRY_ENGINE_VERSION", results)
    }

    private fun itPolicy() = requireNotNull(policy)

    private fun gate(input: FrontInput, mapping: FrontMapping): FrontFailure? {
        if (mapping.formulaId == null) return FrontFailure.UNSUPPORTED_DEFINITION
        val limits = policy ?: return FrontFailure.POLICY_REQUIRED
        if (limits.syntheticOnly && input.origin != FixtureOrigin.SYNTHETIC) return FrontFailure.POLICY_NOT_VALIDATED_FOR_CAPTURE
        if (input.faceCount == null) return FrontFailure.FACE_COUNT_UNAVAILABLE
        if (input.faceCount != 1) return FrontFailure.NOT_ONE_FACE
        if (input.residualPose == null) return FrontFailure.POSE_UNAVAILABLE
        if (minOf(input.width, input.height) < limits.minimumImageShortEdgePixels || input.faceShortEdgePixels == null ||
            input.faceShortEdgePixels < limits.minimumFaceShortEdgePixels) return FrontFailure.INSUFFICIENT_RESOLUTION
        if (!limits.poseLimits().accepts(input.residualPose.geometry())) return FrontFailure.POSE_OUT_OF_RANGE
        if (mapping.required.any { it.value !in input.landmarks }) return FrontFailure.MISSING_LANDMARK
        if (input.overallConfidence == null || input.overallConfidence < limits.minimumConfidence ||
            mapping.required.any { id -> input.landmarks.getValue(id.value).confidence.let { it == null || it < limits.minimumConfidence } }) return FrontFailure.LOW_CONFIDENCE
        return null
    }
}

private class FrontMappedFormula(private val mapping: FrontMapping, itPolicy: FrontPolicy) : GeometryFormula {
    override val formulaId = requireNotNull(mapping.formulaId)
    override val view = MeasurementView.FRONT
    override val requiredLandmarks = mapping.required
    override val mode = MeasurementMode.AUTOMATIC
    override val calibrationRequired = false
    override val minimumShortEdgePixels = itPolicy.minimumImageShortEdgePixels
    override val poseLimits = itPolicy.poseLimits()
    override val confidenceRule = ConfidenceRule(itPolicy.minimumConfidence)

    override fun evaluate(metricId: String, frame: GeometryFrame): GeometryMeasurementResult {
        val (prepared, failure) = prepare(metricId, frame)
        if (failure != null) return failure
        val points = requireNotNull(prepared).points
        fun p(id: LandmarkId) = points.getValue(id)
        fun h(a: LandmarkId, b: LandmarkId) = verticalDistance(p(a), p(b))
        fun w(a: LandmarkId, b: LandmarkId) = horizontalDistance(p(a), p(b))
        fun eyeWidth() = (w(Landmarks.MEDIAL_CANTHUS_LEFT, Landmarks.LATERAL_CANTHUS_LEFT) + w(Landmarks.MEDIAL_CANTHUS_RIGHT, Landmarks.LATERAL_CANTHUS_RIGHT)) / 2
        fun eyeHeight() = (h(Landmarks.UPPER_EYELID_LEFT, Landmarks.LOWER_EYELID_LEFT) + h(Landmarks.UPPER_EYELID_RIGHT, Landmarks.LOWER_EYELID_RIGHT)) / 2
        val value: Double? = when (mapping.metricId.removePrefix("candidate.front.")) {
            "midface_height_fraction" -> safeRatio(h(Landmarks.BROW_LEVEL, Landmarks.SUBNASALE), h(Landmarks.TRICHION, Landmarks.MENTON))?.times(100)
            "interocular_spacing" -> safeRatio(w(Landmarks.PUPIL_LEFT, Landmarks.PUPIL_RIGHT), w(Landmarks.ZYGION_LEFT, Landmarks.ZYGION_RIGHT))?.times(100)
            "eye_aperture_ratio" -> if (h(Landmarks.UPPER_EYELID_LEFT, Landmarks.LOWER_EYELID_LEFT) <= 1e-9 || h(Landmarks.UPPER_EYELID_RIGHT, Landmarks.LOWER_EYELID_RIGHT) <= 1e-9 || eyeWidth() <= 1e-9) null else safeRatio(eyeWidth(), eyeHeight())
            "brow_eye_clearance" -> if (h(Landmarks.UPPER_EYELID_LEFT, Landmarks.LOWER_EYELID_LEFT) <= 1e-9 || h(Landmarks.UPPER_EYELID_RIGHT, Landmarks.LOWER_EYELID_RIGHT) <= 1e-9) null else safeRatio((h(Landmarks.PUPIL_LEFT, Landmarks.INNER_BROW_LEFT) + h(Landmarks.PUPIL_RIGHT, Landmarks.INNER_BROW_RIGHT)) / 2, eyeHeight())
            "brow_inclination" -> {
                val left = outwardInclinationDegrees(p(Landmarks.INNER_BROW_LEFT), p(Landmarks.BROW_ARCH_LEFT))
                val right = outwardInclinationDegrees(p(Landmarks.INNER_BROW_RIGHT), p(Landmarks.BROW_ARCH_RIGHT))
                if (left == null || right == null) null else (left + right) / 2
            }
            "vermilion_fullness" -> safeRatio(h(Landmarks.CUPIDS_BOW, Landmarks.LABRALE_INFERIUS), h(Landmarks.SUBNASALE, Landmarks.MENTON))?.times(100)
            "upper_lower_vermilion_balance" -> safeRatio(h(Landmarks.STOMION, Landmarks.LABRALE_INFERIUS), h(Landmarks.CUPIDS_BOW, Landmarks.STOMION))
            else -> null
        }
        return if (value == null || !value.isFinite()) GeometryMeasurementResult.Unavailable(metricId, formulaId,
            GeometryFailureCode.DEGENERATE_GEOMETRY, "Required span or denominator is degenerate", requiredLandmarks)
        else GeometryMeasurementResult.Available(metricId, formulaId, value, confidence0To1 = prepared.confidence0To1,
            sourceLandmarks = requiredLandmarks, measurementMode = mode, geometryEngineVersion = "wp08-front-geometry-v1")
    }
}
