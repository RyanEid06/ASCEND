package app.ascend.mobile.core.front

import app.ascend.mobile.core.geometry.*
import org.junit.Assert.*
import org.junit.Test

class FrontMeasurementTest {
    private fun fixture() = javaClass.classLoader!!.getResourceAsStream("synthetic-front-v1.json")!!.use { FrontCodec.fixture(it.readBytes()) }
    private val input get() = fixture().input
    private val policy get() = fixture().policy
    private fun metric(input: FrontInput, name: String) = FrontMeasurements(policy).measure(input).metrics.single { it.metricId == "candidate.front.$name" }
    private fun bounds(source: FrontInput = input): CorrectionPolicy {
        val point = source.landmarks.getValue("lateral_canthus_left")
        return CorrectionPolicy("synthetic-correction-v1", "Synthetic boundary tests only", true,
            mapOf("lateral_canthus_left" to CorrectionZone(point.x - .04, point.x + .04, point.y - .04, point.y + .04, .02)))
    }
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun versionedGoldenFixturesVerifyAllSupportedFrontMappings() {
        val fixture = fixture()
        assertTrue(fixture.mismatches().isEmpty())
        assertEquals(11, fixture.expected.size)
        assertEquals(22, FrontCatalog.mappings.size)
        FrontCatalog.mappings.filter { it.formulaId == null }.forEach { mapping ->
            assertEquals(FrontFailure.UNSUPPORTED_DEFINITION, FrontMeasurements(policy).measure(input).metrics.single { it.metricId == mapping.metricId }.failure)
        }
        assertTrue(FrontCatalog.mappings.all { it.metricId.startsWith("candidate.front.") })
        assertTrue(CandidateFeasibilityMatrix.entries.none { it.reliabilityStatus == ReliabilityStatus.VALIDATED })
    }

    @Test fun identicalInputsAndMapOrderProduceExactlyIdenticalReports() {
        val engine = FrontMeasurements(policy)
        val first = engine.measure(input)
        repeat(10) { assertEquals(first, engine.measure(input.copy(landmarks = input.landmarks.entries.reversed().associate { it.key to it.value }))) }
    }

    @Test fun everyRequiredPointFailsForMissingUnknownAndLowConfidence() {
        FrontCatalog.mappings.filter { it.formulaId != null }.forEach { mapping ->
            mapping.required.forEach { id ->
                fun result(source: FrontInput) = FrontMeasurements(policy).measure(source).metrics.single { it.metricId == mapping.metricId }
                assertEquals(FrontFailure.MISSING_LANDMARK, result(input.copy(landmarks = input.landmarks - id.value)).failure)
                listOf(null, .79).forEach { confidence ->
                    val points = input.landmarks + (id.value to input.landmarks.getValue(id.value).copy(confidence = confidence))
                    assertEquals(FrontFailure.LOW_CONFIDENCE, result(input.copy(landmarks = points)).failure)
                }
            }
        }
    }

    @Test fun unknownGlobalConfidencePoseFaceCountAndResolutionFailExplicitly() {
        assertEquals(FrontFailure.LOW_CONFIDENCE, metric(input.copy(overallConfidence = null), "canthal_inclination").failure)
        assertEquals(FrontFailure.POSE_UNAVAILABLE, metric(input.copy(residualPose = null), "canthal_inclination").failure)
        val poseMissing = input.copy(residualPose = null)
        assertTrue(poseMissing.overlayImagePoints(metric(poseMissing, "canthal_inclination")).isEmpty())
        assertEquals(FrontFailure.FACE_COUNT_UNAVAILABLE, metric(input.copy(faceCount = null), "canthal_inclination").failure)
        listOf(0, 2).forEach { assertEquals(FrontFailure.NOT_ONE_FACE, metric(input.copy(faceCount = it), "canthal_inclination").failure) }
        listOf(null, 299).forEach { assertEquals(FrontFailure.INSUFFICIENT_RESOLUTION, metric(input.copy(faceShortEdgePixels = it), "canthal_inclination").failure) }
        assertEquals(FrontFailure.INSUFFICIENT_RESOLUTION, metric(input.copy(width = 719, faceShortEdgePixels = 300), "canthal_inclination").failure)
    }

    @Test fun confidenceBoundaryIsInclusiveAndMinimumConfidencePropagates() {
        val source = input.copy(overallConfidence = policy.minimumConfidence)
        FrontMeasurements(policy).measure(source).metrics.filter { it.formulaId != null }.forEach {
            assertNull(it.failure)
            assertEquals(policy.minimumConfidence, it.confidence!!, 0.0)
        }
    }

    @Test fun residualPoseLimitsRejectBothSignsAndAllAxes() {
        listOf(-1.0, 1.0).forEach { sign ->
            listOf(FrontPose(sign * 8.001, 0.0, 0.0), FrontPose(0.0, sign * 8.001, 0.0), FrontPose(0.0, 0.0, sign * 12.001)).forEach {
                assertEquals(FrontFailure.POSE_OUT_OF_RANGE, metric(input.copy(residualPose = it), "canthal_inclination").failure)
            }
        }
        assertNull(metric(input.copy(residualPose = FrontPose(8.0, -8.0, 0.0)), "canthal_inclination").failure)
    }

    private fun transformed(source: FrontInput, width: Int, height: Int, degrees: Double): FrontInput {
        val short = minOf(width, height).toDouble()
        val pivot = Point2(.5, .5)
        val points = source.landmarks.mapValues { (_, observation) ->
            val rolled = rotate(observation.point(), degrees, pivot)
            observation.copy(x = rolled.x * short / width, y = rolled.y * short / height)
        }
        return source.copy(width = width, height = height, landmarks = points,
            rollOrigin = FrontPoint(.5 * short / width, .5 * short / height, null), residualPose = FrontPose(0.0, 0.0, degrees))
    }

    @Test fun allFormulasAreAspectCorrectAndRollNormalizedWithExactOverlayInputs() {
        val golden = fixture()
        listOf(1200 to 2400, 2400 to 1200, 1200 to 1200).forEach { (width, height) ->
            listOf(-10.0, 0.0, 10.0).forEach { roll ->
                val source = transformed(input, width, height, roll)
                val results = FrontMeasurements(policy).measure(source).metrics.associateBy { it.metricId }
                golden.expected.forEach { expectation ->
                    val result = results.getValue(expectation.metricId)
                    assertNull(result.failure)
                    assertEquals(expectation.value!!, result.value!!, expectation.absoluteTolerance!!)
                    result.sourcePoints.forEach { (id, point) -> assertEquals(source.geometryFrame().normalizedPoint(LandmarkId(id)), point) }
                    source.overlayImagePoints(result).forEach { (id, point) ->
                        assertEquals(source.landmarks.getValue(id).x, point.x, 1e-12)
                        assertEquals(source.landmarks.getValue(id).y, point.y, 1e-12)
                    }
                }
            }
        }
    }

    @Test fun degenerateDenominatorsAndOneClosedEyeDoNotProduceGuessedNumbers() {
        val points = input.landmarks
        assertEquals(FrontFailure.DEGENERATE_GEOMETRY, metric(input.copy(landmarks = points + ("zygion_left" to points.getValue("zygion_right"))), "interocular_spacing").failure)
        assertEquals(FrontFailure.DEGENERATE_GEOMETRY, metric(input.copy(landmarks = points + ("upper_eyelid_left" to points.getValue("lower_eyelid_left"))), "eye_aperture_ratio").failure)
        assertEquals(FrontFailure.DEGENERATE_GEOMETRY, metric(input.copy(landmarks = points + ("lateral_canthus_left" to points.getValue("medial_canthus_left"))), "eye_aperture_ratio").failure)
        assertEquals(FrontFailure.DEGENERATE_GEOMETRY, metric(input.copy(landmarks = points + ("cupids_bow" to points.getValue("stomion"))), "upper_lower_vermilion_balance").failure)
        listOf(
            Triple("trichion", "menton", "facial_elongation"),
            Triple("brow_level", "subnasale", "midface_height_fraction"),
            Triple("subnasale", "stomion", "lower_face_subdivision"),
            Triple("gonion_left", "gonion_right", "jaw_to_cheek_width"),
            Triple("pupil_left", "pupil_right", "interocular_spacing"),
            Triple("cupids_bow", "labrale_inferius", "vermilion_fullness"),
            Triple("stomion", "labrale_inferius", "upper_lower_vermilion_balance"),
        ).forEach { (collapsed, reference, name) ->
            assertEquals(name, FrontFailure.DEGENERATE_GEOMETRY, metric(input.copy(landmarks = points + (collapsed to points.getValue(reference))), name).failure)
        }
    }

    @Test fun requiredCalibrationFailsClosedInTheExistingGeometryContract() {
        val base = RepresentativeFormulaRegistry.registry.formula(FormulaIds.CANTHAL_INCLINATION)!!
        val calibrated = object : GeometryFormula by base {
            override val calibrationRequired = true
            override fun evaluate(metricId: String, frame: GeometryFrame): GeometryMeasurementResult {
                val (_, failure) = prepare(metricId, frame)
                return failure ?: base.evaluate(metricId, frame)
            }
        }
        val result = calibrated.evaluate("synthetic-calibration-contract", input.geometryFrame()) as GeometryMeasurementResult.Unavailable
        assertEquals(GeometryFailureCode.CALIBRATION_REQUIRED, result.failureCode)
        assertNull(input.geometryFrame().millimetersPerNormalizedUnit)
    }

    @Test fun absentPolicyAndSyntheticPoliciesCannotAuthorizeRealCaptureMeasurements() {
        assertTrue(FrontMeasurements(null).measure(input).metrics.filter { it.formulaId != null }.all { it.failure == FrontFailure.POLICY_REQUIRED })
        assertTrue(FrontMeasurements(policy).measure(input.copy(origin = FixtureOrigin.CONSENTED_LOCAL)).metrics.filter { it.formulaId != null }.all { it.failure == FrontFailure.POLICY_NOT_VALIDATED_FOR_CAPTURE })
    }

    @Test fun correctionsAreAuditedAssistedAndRecomputeExactOverlayAndAffectedValues() {
        val original = FrontRevision(1, input, emptyList())
        val corrected = original.correct(bounds(), "lateral_canthus_left", .7, .37, 10)
        assertEquals(1L, corrected.revision)
        assertEquals(original.original, corrected.original)
        assertEquals(input.landmarks.getValue("lateral_canthus_left"), corrected.corrections.single().original)
        val prior = original.measure(policy).metrics.associateBy { it.metricId }
        val next = corrected.measure(policy).metrics.associateBy { it.metricId }
        assertNotEquals(prior.getValue("candidate.front.canthal_inclination").value, next.getValue("candidate.front.canthal_inclination").value)
        assertEquals(MeasurementMode.ASSISTED, next.getValue("candidate.front.canthal_inclination").mode)
        assertEquals(prior.getValue("candidate.front.interocular_spacing"), next.getValue("candidate.front.interocular_spacing"))
        assertEquals(.37, corrected.current().overlayImagePoints(next.getValue("candidate.front.canthal_inclination")).getValue("lateral_canthus_left").y, 1e-12)
        assertEquals(corrected, FrontCodec.revision(FrontCodec.encode(corrected)))
    }

    @Test fun cumulativeBoundsAreAnchoredToOriginalAndCannotRaiseConfidence() {
        val initial = FrontRevision(1, input, emptyList())
        assertEquals(1L, initial.correct(bounds(), "lateral_canthus_left", .7, .4, 1).revision)
        rejected { initial.correct(bounds(), "lateral_canthus_left", .7, .4001, 1) }
        val once = initial.correct(bounds(), "lateral_canthus_left", .7, .37, 1)
        rejected { once.correct(bounds(), "lateral_canthus_left", .7, .35, 2) }
        rejected { once.correct(bounds(), "lateral_canthus_left", .7, .375, 0) }
        rejected { initial.correct(bounds(), "zygion_left", .7, .4, 1) }
        rejected { FrontRevision(1, input.copy(landmarks = input.landmarks - "lateral_canthus_left"), emptyList()).correct(bounds(), "lateral_canthus_left", .7, .37, 1) }
        val low = input.copy(landmarks = input.landmarks + ("lateral_canthus_left" to input.landmarks.getValue("lateral_canthus_left").copy(confidence = .2)))
        val result = FrontRevision(1, low, emptyList()).correct(bounds(low), "lateral_canthus_left", .7, .37, 1).measure(policy).metrics.single { it.metricId.endsWith("canthal_inclination") }
        assertEquals(FrontFailure.LOW_CONFIDENCE, result.failure)
        rejected { initial.copy(corrections = listOf(once.corrections.single().copy(to = once.corrections.single().to.copy(confidence = 1.0)))) }
        rejected { once.corrections.single().copy(atEpochMillis = -1) }
    }

    @Test fun boundsUseIsotropicDistanceAndSyntheticBoundsRejectCapture() {
        val portrait = transformed(input, 1200, 2400, 0.0)
        val point = portrait.landmarks.getValue("lateral_canthus_left")
        rejected { FrontRevision(1, portrait, emptyList()).correct(bounds(portrait), "lateral_canthus_left", point.x, point.y - .015, 1) }
        rejected { FrontRevision(1, input.copy(origin = FixtureOrigin.CONSENTED_LOCAL), emptyList()).correct(bounds(), "lateral_canthus_left", .7, .37, 1) }
    }

    @Test fun fixtureCodecRejectsVersionUnknownFieldsInvalidPointsAndMissingConsent() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("synthetic-front-v1.json")!!.readBytes()
        val source = bytes.toString(Charsets.UTF_8)
        rejected { FrontCodec.fixture(source.replace("\"formatVersion\": 1", "\"formatVersion\": 2").toByteArray()) }
        rejected { FrontCodec.fixture(source.replace("\"x\": 0.5", "\"x\": 2.5").toByteArray()) }
        try { FrontCodec.fixture(source.replace("\"fixtureId\":", "\"extra\": true, \"fixtureId\":").toByteArray()); fail("Unknown field") } catch (_: kotlinx.serialization.SerializationException) { }
        rejected { fixture().copy(input = input.copy(origin = FixtureOrigin.CONSENTED_LOCAL)) }
        rejected { FrontCodec.fixture(ByteArray(1_000_001)) }
    }
}
