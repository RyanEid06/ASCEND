package app.ascend.mobile.core.geometry

import app.ascend.mobile.core.model.MeasurementView

data class MetricFeasibility(
    val metricId: String,
    val displayName: String,
    val formulaOrExtractorId: String?,
    val view: MeasurementView,
    val measurementMode: MeasurementMode,
    val requiredLandmarksOrFeatures: Set<String>,
    val minimumUsableResolution: PixelResolution,
    val poseLimits: PoseLimits,
    val lightingDependencies: Set<LightingDependency>,
    val calibrationRequired: Boolean,
    val confidenceRule: ConfidenceRule,
    val reliabilityStatus: ReliabilityStatus,
    /** Null until repeated-capture validation establishes a defensible tolerance. */
    val repeatedCaptureTolerance: Double?,
    /** Exact/near-exact research-definition IDs only; empty means mapping remains unresolved. */
    val sourceMetricIds: Set<String>,
) {
    init {
        require(metricId.isNotBlank() && displayName.isNotBlank())
        require(formulaOrExtractorId == null || formulaOrExtractorId.isNotBlank())
        require(requiredLandmarksOrFeatures.isNotEmpty())
        require(repeatedCaptureTolerance == null || (repeatedCaptureTolerance.isFinite() && repeatedCaptureTolerance >= 0.0))
        require(sourceMetricIds.all(String::isNotBlank))
        require((reliabilityStatus == ReliabilityStatus.VALIDATED) == (repeatedCaptureTolerance != null)) {
            "Only validated metrics may publish repeated-capture tolerances"
        }
    }
}

object CandidateFeasibilityMatrix {
    private val frontLimits = PoseLimits(8.0, 8.0, 12.0)
    private val profileLimits = PoseLimits(8.0, 8.0, 12.0)
    private val lighting = setOf(
        LightingDependency.EVEN_LIGHTING,
        LightingDependency.SUFFICIENT_CONTRAST,
        LightingDependency.NO_HARD_SHADOW_ON_REQUIRED_LANDMARKS,
    )
    private val confidence = ConfidenceRule(0.75)
    private val minimumResolution = PixelResolution(720, 720)

    private fun front(
        id: String,
        name: String,
        required: Set<String>,
        formulaId: String? = null,
        sourceMetricIds: Set<String> = emptySet(),
    ) = MetricFeasibility(
        metricId = id,
        displayName = name,
        formulaOrExtractorId = formulaId,
        view = MeasurementView.FRONT,
        measurementMode = MeasurementMode.AUTOMATIC,
        requiredLandmarksOrFeatures = required,
        minimumUsableResolution = minimumResolution,
        poseLimits = frontLimits,
        lightingDependencies = lighting,
        calibrationRequired = false,
        confidenceRule = confidence,
        reliabilityStatus = if (formulaId == null) ReliabilityStatus.UNVALIDATED else ReliabilityStatus.SYNTHETIC_VERIFIED,
        repeatedCaptureTolerance = null,
        sourceMetricIds = sourceMetricIds,
    )

    private fun profile(
        id: String,
        name: String,
        required: Set<String>,
        formulaId: String? = null,
        sourceMetricIds: Set<String> = emptySet(),
    ) = MetricFeasibility(
        metricId = id,
        displayName = name,
        formulaOrExtractorId = formulaId,
        view = MeasurementView.PROFILE,
        measurementMode = MeasurementMode.ASSISTED,
        requiredLandmarksOrFeatures = required,
        minimumUsableResolution = minimumResolution,
        poseLimits = profileLimits,
        lightingDependencies = lighting,
        calibrationRequired = false,
        confidenceRule = confidence,
        reliabilityStatus = if (formulaId == null) ReliabilityStatus.UNVALIDATED else ReliabilityStatus.SYNTHETIC_VERIFIED,
        repeatedCaptureTolerance = null,
        sourceMetricIds = sourceMetricIds,
    )

    val entries: List<MetricFeasibility> = listOf(
        front("candidate.front.facial_elongation", "Facial elongation", setOf("trichion", "menton", "bilateral zygion"), FormulaIds.FACIAL_ELONGATION, setOf("FQ_H_005")),
        front("candidate.front.midface_height_fraction", "Midface height fraction", setOf("brow-level reference", "subnasale", "trichion", "menton"), sourceMetricIds = setOf("FQ_H_002")),
        front("candidate.front.lower_face_subdivision", "Lower-face subdivision", setOf("subnasale", "stomion", "menton"), FormulaIds.LOWER_FACE_SUBDIVISION, setOf("FQ_H_033")),
        front("candidate.front.upper_middle_facial_proportion", "Upper/middle facial proportion", setOf("trichion", "brow-level reference", "subnasale")),
        front("candidate.front.jaw_to_cheek_width", "Jaw-to-cheek width", setOf("bilateral gonion", "bilateral zygion"), FormulaIds.JAW_TO_CHEEK_WIDTH, setOf("FQ_H_032")),
        front("candidate.front.chin_breadth", "Chin breadth", setOf("bilateral chin contour", "bilateral zygion")),
        front("candidate.front.intercanthal_spacing", "Intercanthal spacing", setOf("bilateral medial canthi", "bilateral zygion")),
        front("candidate.front.interocular_spacing", "Interocular spacing", setOf("pupil centers", "bilateral zygion"), sourceMetricIds = setOf("FQ_H_009")),
        front("candidate.front.eye_fissure_width", "Eye fissure width", setOf("bilateral medial/lateral canthi", "face-width reference")),
        front("candidate.front.eye_aperture_ratio", "Eye aperture ratio", setOf("medial/lateral canthi", "upper/lower eyelid margins"), sourceMetricIds = setOf("FQ_H_010")),
        front("candidate.front.canthal_inclination", "Canthal inclination", setOf("bilateral medial/lateral canthi"), FormulaIds.CANTHAL_INCLINATION, setOf("FQ_H_012")),
        front("candidate.front.brow_eye_clearance", "Brow-eye clearance", setOf("pupil centers", "inner brow", "eyelid margins"), sourceMetricIds = setOf("FQ_H_014")),
        front("candidate.front.brow_inclination", "Brow inclination", setOf("inner brow", "brow arch"), sourceMetricIds = setOf("FQ_H_013")),
        front("candidate.front.brow_peak_location", "Brow peak location", setOf("inner brow", "brow arch", "outer brow")),
        front("candidate.front.nasal_width", "Nasal width", setOf("bilateral alare", "face-width reference")),
        front("candidate.front.nasal_lateral_deviation", "Nasal lateral deviation", setOf("nasion", "pronasale", "subnasale", "facial midline")),
        front("candidate.front.mouth_width", "Mouth width", setOf("bilateral mouth corners", "face-width reference")),
        front("candidate.front.vermilion_fullness", "Vermilion fullness", setOf("labrale superius", "stomion", "labrale inferius", "lower-face reference"), sourceMetricIds = setOf("FQ_H_023")),
        front("candidate.front.upper_lower_vermilion_balance", "Upper/lower vermilion balance", setOf("Cupid's bow", "stomion", "labrale inferius"), sourceMetricIds = setOf("FQ_H_024")),
        front("candidate.front.philtrum_proportion", "Philtrum proportion", setOf("subnasale", "Cupid's bow", "lower-face reference")),
        front("candidate.front.mouth_corner_inclination", "Mouth-corner inclination", setOf("bilateral mouth corners", "stomion")),
        front("candidate.front.landmark_asymmetry", "Landmark asymmetry", setOf("paired facial landmarks", "facial midline")),
        profile("candidate.profile.facial_convexity", "Facial convexity", setOf("nasion", "subnasale", "pogonion"), sourceMetricIds = setOf("FQ_H_040")),
        profile("candidate.profile.convexity_including_nose", "Convexity including nose", setOf("glabella", "pronasale", "pogonion"), sourceMetricIds = setOf("FQ_H_042")),
        profile("candidate.profile.nasofrontal_angle", "Nasofrontal angle", setOf("glabella", "nasion", "supratip"), FormulaIds.NASOFRONTAL_ANGLE, setOf("FQ_H_038")),
        profile("candidate.profile.nasolabial_angle", "Nasolabial angle", setOf("columella", "subnasale", "labrale superius"), FormulaIds.NASOLABIAL_ANGLE, setOf("FQ_H_051")),
        profile("candidate.profile.mentolabial_angle", "Mentolabial angle", setOf("labrale inferius", "sublabiale", "pogonion"), FormulaIds.MENTOLABIAL_ANGLE, setOf("FQ_H_063")),
        profile("candidate.profile.nasal_projection", "Nasal projection", setOf("subalare", "pronasale", "dorsum reference"), sourceMetricIds = setOf("FQ_H_048")),
        profile("candidate.profile.nasal_bridge_curvature", "Nasal bridge curvature", setOf("nasion", "multiple dorsum points", "supratip")),
        profile("candidate.profile.upper_lip_projection", "Upper-lip projection", setOf("labrale superius", "profile reference line")),
        profile("candidate.profile.lower_lip_projection", "Lower-lip projection", setOf("labrale inferius", "profile reference line")),
        profile("candidate.profile.chin_projection", "Chin projection", setOf("pogonion", "profile reference line")),
        profile("candidate.profile.soft_tissue_jaw_angle", "Soft-tissue jaw-angle estimate", setOf("ramus reference", "gonion", "mandibular-border reference"), FormulaIds.SOFT_TISSUE_JAW_ANGLE, setOf("FQ_H_064")),
        profile("candidate.profile.chin_neck_contour_angle", "Chin-neck contour angle", setOf("menton", "cervical point", "neck point"), sourceMetricIds = setOf("FQ_H_069")),
    )

    init {
        require(entries.size == 34)
        require(entries.map { it.metricId }.distinct().size == entries.size)
        require(entries.mapNotNull { it.formulaOrExtractorId }.all(RepresentativeFormulaRegistry.registry.supportedFormulaIds::contains))
    }
}
