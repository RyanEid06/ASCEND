package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*

internal data class ProfileMapping(
    val metricId: String,
    val formulaId: String?,
    /** Ordered first/vertex/third; null until the angle/reference convention is reviewed. */
    val anglePoints: List<LandmarkId>?,
    val researchDefinitionId: String?,
    val softTissueProxy: Boolean = false,
)

internal object ProfileCatalog {
    // Visible soft-tissue gonion has no arbitrary LEFT/RIGHT alias.
    val visibleGonion = LandmarkId("gonion_visible")
    private fun angle(name: String, formula: String, source: String, a: LandmarkId, v: LandmarkId, b: LandmarkId, proxy: Boolean = false) =
        ProfileMapping("candidate.profile.$name", formula, listOf(a, v, b), source, proxy)
    private fun unsupported(name: String) = ProfileMapping("candidate.profile.$name", null, null, null)

    val mappings = listOf(
        unsupported("facial_convexity"), // Research specifies an oriented angle, not an unsigned substitute.
        unsupported("convexity_including_nose"),
        angle("nasofrontal_angle", FormulaIds.NASOFRONTAL_ANGLE, "FQ_H_038", Landmarks.GLABELLA, Landmarks.NASION, Landmarks.SUPRATIP),
        angle("nasolabial_angle", FormulaIds.NASOLABIAL_ANGLE, "FQ_H_051", Landmarks.COLUMELLA, Landmarks.SUBNASALE, Landmarks.LABRALE_SUPERIUS),
        angle("mentolabial_angle", FormulaIds.MENTOLABIAL_ANGLE, "FQ_H_063", Landmarks.LABRALE_INFERIUS, Landmarks.SUBLABIALE, Landmarks.POGONION),
        unsupported("nasal_projection"),
        unsupported("nasal_bridge_curvature"),
        unsupported("upper_lip_projection"),
        unsupported("lower_lip_projection"),
        unsupported("chin_projection"),
        // Distinct ID: WP03's original formula names GONION_LEFT, which is ambiguous on a right profile.
        angle("soft_tissue_jaw_angle", "profile.visible_soft_tissue_jaw_angle.experimental.v1", "FQ_H_064",
            Landmarks.RAMUS_REFERENCE, visibleGonion, Landmarks.MANDIBULAR_BORDER_REFERENCE, proxy = true),
        unsupported("chin_neck_contour_angle"),
    )
    val semanticIds = mappings.flatMap { it.anglePoints.orEmpty() }.toSet()
}

/** Pure lane-private formula experiment; no detector indexes, storage writes or scoring admission. */
internal class ProfileMeasurements(private val policy: ProfilePolicy?) {
    fun measure(source: ProfileInput): ProfileReport {
        val input = source.copy(points = source.points.toSortedMap(compareBy { it.value }))
        val results = ProfileCatalog.mappings.map { mapping ->
            val failure = gate(input, mapping)
            val required = mapping.anglePoints.orEmpty()
            val points = if (input.residualPose == null) emptyMap() else required.mapNotNull { id ->
                input.points[id]?.let { id to input.normalized(it.point) }
            }.toMap()
            fun unavailable(reason: ProfileFailure) = ProfileMetricResult(
                mapping.metricId, mapping.formulaId, if (mapping.formulaId == null) null else "degrees",
                null, null, MeasurementMode.UNSUPPORTED, reason, ReliabilityStatus.UNVALIDATED, points, mapping.softTissueProxy,
            )
            if (failure != null) unavailable(failure) else {
                val value = angleDegrees(points.getValue(required[0]), points.getValue(required[1]), points.getValue(required[2]))
                if (value == null) unavailable(ProfileFailure.DEGENERATE_GEOMETRY) else {
                    val observations = required.map { input.points.getValue(it) }
                    // Assisted confirmation may support synthetic geometry, but never fabricates certainty.
                    val confidences = listOf(input.overallConfidence) + observations.map { it.confidence }
                    val confidence = if (confidences.any { it == null }) null else confidences.filterNotNull().minOrNull()
                    ProfileMetricResult(mapping.metricId, mapping.formulaId, "degrees", value, confidence,
                        if (observations.any { it.source == ProfilePointSource.ASSISTED || it.confirmed }) MeasurementMode.ASSISTED
                        else MeasurementMode.AUTOMATIC,
                        null, ReliabilityStatus.SYNTHETIC_VERIFIED, points, mapping.softTissueProxy)
                }
            }
        }
        return ProfileReport(PROFILE_EXPERIMENT_VERSION, input.imageRevision, policy?.version, input.side, results)
    }

    private fun gate(input: ProfileInput, mapping: ProfileMapping): ProfileFailure? {
        val required = mapping.anglePoints ?: return ProfileFailure.UNSUPPORTED_DEFINITION
        val limits = policy ?: return ProfileFailure.POLICY_REQUIRED
        if (input.origin != ProfileOrigin.SYNTHETIC) return ProfileFailure.SYNTHETIC_ONLY
        if (!input.orientationConfirmed || input.side == null) return ProfileFailure.ORIENTATION_REQUIRED
        if (input.faceCount == null) return ProfileFailure.FACE_COUNT_UNAVAILABLE
        if (input.faceCount != 1) return ProfileFailure.NOT_ONE_FACE
        val pose = input.residualPose ?: return ProfileFailure.POSE_UNAVAILABLE
        if (!limits.poseLimits.accepts(pose)) return ProfileFailure.POSE_OUT_OF_RANGE
        if (input.resolution.shortEdge < limits.minimumImageShortEdgePixels || input.faceShortEdgePixels == null ||
            input.faceShortEdgePixels < limits.minimumFaceShortEdgePixels) return ProfileFailure.INSUFFICIENT_RESOLUTION
        if (required.any { it !in input.points }) return ProfileFailure.MISSING_POINT
        if (required.any { it in limits.confirmationRequired && !input.points.getValue(it).confirmed }) return ProfileFailure.CONFIRMATION_REQUIRED
        val automatic = required.map { input.points.getValue(it) }.filter { it.source == ProfilePointSource.AUTOMATIC }
        if (automatic.isNotEmpty() && (input.overallConfidence == null || input.overallConfidence < limits.minimumAutomaticConfidence ||
            automatic.any { it.confidence == null || it.confidence < limits.minimumAutomaticConfidence })) return ProfileFailure.LOW_CONFIDENCE
        // A known low confidence stays low even after assisted confirmation or a move.
        if ((input.overallConfidence != null && input.overallConfidence < limits.minimumAutomaticConfidence) ||
            required.any { input.points.getValue(it).confidence?.let { c -> c < limits.minimumAutomaticConfidence } == true }) return ProfileFailure.LOW_CONFIDENCE
        return null
    }
}
