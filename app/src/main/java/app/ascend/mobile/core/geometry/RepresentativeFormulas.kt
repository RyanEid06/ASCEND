package app.ascend.mobile.core.geometry

import app.ascend.mobile.core.model.MeasurementView

private val FRONT_LIMITS = PoseLimits(8.0, 8.0, 12.0)
private val PROFILE_LIMITS = PoseLimits(8.0, 8.0, 12.0)
private val DEFAULT_CONFIDENCE = ConfidenceRule(0.75)
private const val MIN_SHORT_EDGE = 720

private abstract class BaseFormula(
    final override val formulaId: String,
    final override val view: MeasurementView,
    final override val requiredLandmarks: Set<LandmarkId>,
    final override val mode: MeasurementMode,
    final override val calibrationRequired: Boolean = false,
    final override val minimumShortEdgePixels: Int = MIN_SHORT_EDGE,
    final override val poseLimits: PoseLimits,
    final override val confidenceRule: ConfidenceRule = DEFAULT_CONFIDENCE,
) : GeometryFormula {
    final override fun evaluate(metricId: String, frame: GeometryFrame): GeometryMeasurementResult {
        val (prepared, failure) = prepare(metricId, frame)
        if (failure != null) return failure
        return calculate(prepared!!)
    }

    protected abstract fun calculate(input: PreparedGeometry): GeometryMeasurementResult

    protected fun available(input: PreparedGeometry, value: Double, normalizedValue: Double? = null) =
        GeometryMeasurementResult.Available(
            metricId = input.metricId,
            formulaId = formulaId,
            rawValue = value,
            normalizedValue = normalizedValue,
            confidence0To1 = input.confidence0To1,
            sourceLandmarks = requiredLandmarks,
            measurementMode = mode,
        )

    protected fun degenerate(input: PreparedGeometry, detail: String) = GeometryMeasurementResult.Unavailable(
        metricId = input.metricId,
        formulaId = formulaId,
        failureCode = GeometryFailureCode.DEGENERATE_GEOMETRY,
        detail = detail,
        sourceLandmarks = requiredLandmarks,
    )
}

private class FacialElongationFormula : BaseFormula(
    formulaId = FormulaIds.FACIAL_ELONGATION,
    view = MeasurementView.FRONT,
    requiredLandmarks = setOf(Landmarks.TRICHION, Landmarks.MENTON, Landmarks.ZYGION_LEFT, Landmarks.ZYGION_RIGHT),
    mode = MeasurementMode.AUTOMATIC,
    poseLimits = FRONT_LIMITS,
) {
    override fun calculate(input: PreparedGeometry): GeometryMeasurementResult {
        val faceHeight = verticalDistance(input.points.getValue(Landmarks.TRICHION), input.points.getValue(Landmarks.MENTON))
        val faceWidth = horizontalDistance(input.points.getValue(Landmarks.ZYGION_LEFT), input.points.getValue(Landmarks.ZYGION_RIGHT))
        return safeRatio(faceHeight, faceWidth)?.let { available(input, it) }
            ?: degenerate(input, "Cheekbone width is zero")
    }
}

private class LowerFaceSubdivisionFormula : BaseFormula(
    formulaId = FormulaIds.LOWER_FACE_SUBDIVISION,
    view = MeasurementView.FRONT,
    requiredLandmarks = setOf(Landmarks.SUBNASALE, Landmarks.STOMION, Landmarks.MENTON),
    mode = MeasurementMode.AUTOMATIC,
    poseLimits = FRONT_LIMITS,
) {
    override fun calculate(input: PreparedGeometry): GeometryMeasurementResult {
        val upperLowerThird = verticalDistance(input.points.getValue(Landmarks.SUBNASALE), input.points.getValue(Landmarks.STOMION))
        val lowerThird = verticalDistance(input.points.getValue(Landmarks.SUBNASALE), input.points.getValue(Landmarks.MENTON))
        return safeRatio(upperLowerThird, lowerThird)?.let { available(input, it * 100.0) }
            ?: degenerate(input, "Lower-face height is zero")
    }
}

private class JawToCheekWidthFormula : BaseFormula(
    formulaId = FormulaIds.JAW_TO_CHEEK_WIDTH,
    view = MeasurementView.FRONT,
    requiredLandmarks = setOf(Landmarks.GONION_LEFT, Landmarks.GONION_RIGHT, Landmarks.ZYGION_LEFT, Landmarks.ZYGION_RIGHT),
    mode = MeasurementMode.AUTOMATIC,
    poseLimits = FRONT_LIMITS,
) {
    override fun calculate(input: PreparedGeometry): GeometryMeasurementResult {
        val jawWidth = horizontalDistance(input.points.getValue(Landmarks.GONION_LEFT), input.points.getValue(Landmarks.GONION_RIGHT))
        val cheekWidth = horizontalDistance(input.points.getValue(Landmarks.ZYGION_LEFT), input.points.getValue(Landmarks.ZYGION_RIGHT))
        return safeRatio(jawWidth, cheekWidth)?.let { available(input, it * 100.0) }
            ?: degenerate(input, "Cheekbone width is zero")
    }
}

private class CanthalInclinationFormula : BaseFormula(
    formulaId = FormulaIds.CANTHAL_INCLINATION,
    view = MeasurementView.FRONT,
    requiredLandmarks = setOf(
        Landmarks.MEDIAL_CANTHUS_LEFT,
        Landmarks.LATERAL_CANTHUS_LEFT,
        Landmarks.MEDIAL_CANTHUS_RIGHT,
        Landmarks.LATERAL_CANTHUS_RIGHT,
    ),
    mode = MeasurementMode.AUTOMATIC,
    poseLimits = FRONT_LIMITS,
) {
    override fun calculate(input: PreparedGeometry): GeometryMeasurementResult {
        val left = outwardInclinationDegrees(
            input.points.getValue(Landmarks.MEDIAL_CANTHUS_LEFT),
            input.points.getValue(Landmarks.LATERAL_CANTHUS_LEFT),
        )
        val right = outwardInclinationDegrees(
            input.points.getValue(Landmarks.MEDIAL_CANTHUS_RIGHT),
            input.points.getValue(Landmarks.LATERAL_CANTHUS_RIGHT),
        )
        if (left == null || right == null) return degenerate(input, "Canthal horizontal span is zero")
        return available(input, (left + right) / 2.0)
    }
}

private abstract class ProfileVertexAngleFormula(
    formulaId: String,
    private val first: LandmarkId,
    private val vertex: LandmarkId,
    private val third: LandmarkId,
) : BaseFormula(
    formulaId = formulaId,
    view = MeasurementView.PROFILE,
    requiredLandmarks = setOf(first, vertex, third),
    mode = MeasurementMode.ASSISTED,
    poseLimits = PROFILE_LIMITS,
) {
    override fun calculate(input: PreparedGeometry): GeometryMeasurementResult =
        angleDegrees(input.points.getValue(first), input.points.getValue(vertex), input.points.getValue(third))
            ?.let { available(input, it) }
            ?: degenerate(input, "Angle ray is degenerate")
}

private class NasofrontalAngleFormula : ProfileVertexAngleFormula(
    FormulaIds.NASOFRONTAL_ANGLE,
    Landmarks.GLABELLA,
    Landmarks.NASION,
    Landmarks.SUPRATIP,
)

private class NasolabialAngleFormula : ProfileVertexAngleFormula(
    FormulaIds.NASOLABIAL_ANGLE,
    Landmarks.COLUMELLA,
    Landmarks.SUBNASALE,
    Landmarks.LABRALE_SUPERIUS,
)

private class MentolabialAngleFormula : ProfileVertexAngleFormula(
    FormulaIds.MENTOLABIAL_ANGLE,
    Landmarks.LABRALE_INFERIUS,
    Landmarks.SUBLABIALE,
    Landmarks.POGONION,
)

private class SoftTissueJawAngleFormula : ProfileVertexAngleFormula(
    FormulaIds.SOFT_TISSUE_JAW_ANGLE,
    Landmarks.RAMUS_REFERENCE,
    Landmarks.GONION_LEFT,
    Landmarks.MANDIBULAR_BORDER_REFERENCE,
)

object FormulaIds {
    const val FACIAL_ELONGATION = "front.facial_elongation.v1"
    const val LOWER_FACE_SUBDIVISION = "front.lower_face_subdivision.v1"
    const val JAW_TO_CHEEK_WIDTH = "front.jaw_to_cheek_width.v1"
    const val CANTHAL_INCLINATION = "front.canthal_inclination.v1"
    const val NASOFRONTAL_ANGLE = "profile.nasofrontal_angle.v1"
    const val NASOLABIAL_ANGLE = "profile.nasolabial_angle.v1"
    const val MENTOLABIAL_ANGLE = "profile.mentolabial_angle.v1"
    const val SOFT_TISSUE_JAW_ANGLE = "profile.soft_tissue_jaw_angle.v1"
}

object RepresentativeFormulaRegistry {
    val formulas: List<GeometryFormula> = listOf(
        FacialElongationFormula(),
        LowerFaceSubdivisionFormula(),
        JawToCheekWidthFormula(),
        CanthalInclinationFormula(),
        NasofrontalAngleFormula(),
        NasolabialAngleFormula(),
        MentolabialAngleFormula(),
        SoftTissueJawAngleFormula(),
    )

    val registry: GeometryFormulaRegistry = GeometryFormulaRegistry(formulas)
}
