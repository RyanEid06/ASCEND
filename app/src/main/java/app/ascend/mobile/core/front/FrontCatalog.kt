package app.ascend.mobile.core.front

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.MeasurementView

data class FrontMapping(
    val metricId: String,
    val formulaId: String?,
    val sourceDefinitionIds: Set<String>,
    val unit: String?,
    val required: Set<LandmarkId>,
    val unresolved: String?,
)

/** Candidate mappings only. Membership in this catalogue never enables release scoring. */
object FrontCatalog {
    private fun ids(vararg id: LandmarkId) = id.toSet()
    private val cheek = ids(Landmarks.ZYGION_LEFT, Landmarks.ZYGION_RIGHT)
    private val canthi = ids(Landmarks.MEDIAL_CANTHUS_LEFT, Landmarks.LATERAL_CANTHUS_LEFT,
        Landmarks.MEDIAL_CANTHUS_RIGHT, Landmarks.LATERAL_CANTHUS_RIGHT)
    private val lids = ids(Landmarks.UPPER_EYELID_LEFT, Landmarks.LOWER_EYELID_LEFT,
        Landmarks.UPPER_EYELID_RIGHT, Landmarks.LOWER_EYELID_RIGHT)
    private val definitions = mapOf(
        "facial_elongation" to Triple(FormulaIds.FACIAL_ELONGATION, "ratio", ids(Landmarks.TRICHION, Landmarks.MENTON) + cheek),
        "midface_height_fraction" to Triple("front.midface_height_fraction.v1", "%", ids(Landmarks.BROW_LEVEL, Landmarks.SUBNASALE, Landmarks.TRICHION, Landmarks.MENTON)),
        "lower_face_subdivision" to Triple(FormulaIds.LOWER_FACE_SUBDIVISION, "%", ids(Landmarks.SUBNASALE, Landmarks.STOMION, Landmarks.MENTON)),
        "jaw_to_cheek_width" to Triple(FormulaIds.JAW_TO_CHEEK_WIDTH, "%", ids(Landmarks.GONION_LEFT, Landmarks.GONION_RIGHT) + cheek),
        "interocular_spacing" to Triple("front.interocular_spacing.v1", "%", ids(Landmarks.PUPIL_LEFT, Landmarks.PUPIL_RIGHT) + cheek),
        "eye_aperture_ratio" to Triple("front.eye_aperture_ratio.v1", "ratio", canthi + lids),
        "canthal_inclination" to Triple(FormulaIds.CANTHAL_INCLINATION, "degrees", canthi),
        "brow_eye_clearance" to Triple("front.brow_eye_clearance.v1", "ratio", ids(Landmarks.PUPIL_LEFT, Landmarks.PUPIL_RIGHT, Landmarks.INNER_BROW_LEFT, Landmarks.INNER_BROW_RIGHT) + lids),
        "brow_inclination" to Triple("front.brow_inclination.v1", "degrees", ids(Landmarks.INNER_BROW_LEFT, Landmarks.INNER_BROW_RIGHT, Landmarks.BROW_ARCH_LEFT, Landmarks.BROW_ARCH_RIGHT)),
        "vermilion_fullness" to Triple("front.vermilion_fullness.v1", "%", ids(Landmarks.CUPIDS_BOW, Landmarks.LABRALE_INFERIUS, Landmarks.SUBNASALE, Landmarks.MENTON)),
        "upper_lower_vermilion_balance" to Triple("front.upper_lower_vermilion_balance.v1", "ratio", ids(Landmarks.CUPIDS_BOW, Landmarks.STOMION, Landmarks.LABRALE_INFERIUS)),
    )
    val mappings: List<FrontMapping> = CandidateFeasibilityMatrix.entries.filter { it.view == MeasurementView.FRONT }.map { candidate ->
        val definition = definitions[candidate.metricId.removePrefix("candidate.front.")]
        FrontMapping(candidate.metricId, definition?.first, candidate.sourceMetricIds, definition?.second,
            definition?.third ?: emptySet(), if (definition == null) "Definition/landmark convention requires joint review; no guessed mapping" else null)
    }
    val semanticIds: Set<String> = mappings.flatMap { it.required }.map { it.value }.toSet()
    /** Explicit anatomical eligibility; production still requires reviewed, per-landmark zones and radii. */
    val correctionEligible: Set<String> = (canthi + ids(Landmarks.CUPIDS_BOW, Landmarks.STOMION, Landmarks.LABRALE_INFERIUS)).map { it.value }.toSet()
    fun mapping(metricId: String) = mappings.single { it.metricId == metricId }
}
