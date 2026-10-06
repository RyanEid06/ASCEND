package app.ascend.mobile.core.appearance

import app.ascend.mobile.core.model.*
import app.ascend.mobile.core.scoring.*
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AppearanceScoringTest {
    private val model get() = javaClass.classLoader!!.getResourceAsStream("appearance/synthetic-model-v1.json")!!.use { it.readBytes() }
    private val formulas = object : FormulaRegistry {
        override fun supports(id: String, mode: String, calibrationRequired: Boolean) =
            id in setOf("fixture-geometry", "fixture-brow", "fixture-texture") && mode == "AUTOMATIC" && !calibrationRequired
    }
    private val benchmarks = object : BenchmarkRegistry {
        override fun review(id: String) = if (id == "synthetic-only") BenchmarkReview("AESTHETIC_PREFERENCE", "exact", "ADULT_EVIDENCE", true) else null
    }
    private val bindings get() = listOf(
        AppearanceBinding("appearance.brow", AppearanceFeatureKind.BROW_DENSITY_PATTERN, "fixture-brow", "extractor-v1",
            "synthetic-brow-definition-v1", "Synthetic fairness contract check only", AppearanceScoreBasis.SPATIAL_APPEARANCE_PATTERN, 0.0, 1.0, "synthetic-confidence-v1"),
        AppearanceBinding("appearance.texture", AppearanceFeatureKind.SKIN_TEXTURE_PATTERN, "fixture-texture", "extractor-v1",
            "synthetic-texture-definition-v1", "Synthetic fairness contract check only", AppearanceScoreBasis.SPATIAL_APPEARANCE_PATTERN, 0.0, 1.0, "synthetic-confidence-v1"),
    )
    private val policy get() = AppearancePolicy("synthetic-policy-v1", "Synthetic quality gates only", "synthetic-quality-v1", .8, 100)
    private fun observation(binding: AppearanceBinding, value: Double) = AppearanceObservation(binding.kind, "image-v1", "roi-${binding.metricId}-v1",
        binding.extractorId, binding.extractorVersion, value, .9, 120, AppearanceQuality.USABLE, "SYNTHETIC_VERIFIED", binding.confidenceMethodVersion)
    private val input get() = AppearanceInput(AppearanceOrigin.SYNTHETIC, "image-v1", "synthetic-quality-v1", AppearanceQuality.USABLE,
        AppearanceQuality.USABLE, bindings.associate { it.metricId to observation(it, if (it.metricId.endsWith("brow")) .9 else .5) })
    private val others get() = mapOf("h" to Measurement(.8, 1.0), "d" to Measurement(.8, 1.0), "a" to Measurement(.8, 1.0))
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun versions(bytes: ByteArray = model) = AnalysisVersions("fixture-app", "fixture-engine", "fixture-front", "fixture-profile",
        "synthetic-appearance-v1", "fixture-scoring", hash(bytes), "fixture-set", "fixture-coverage-v1", "fixture-scale-v1",
        "fixture-extrema-v1", "fixture-recommendations")
    private fun load(bytes: ByteArray = model, selected: List<AppearanceBinding> = bindings, limits: AppearancePolicy? = policy) =
        AppearanceScoring.load("reference-models/v1/synthetic-appearance-test.json", bytes, formulas, benchmarks, selected, limits)
    private fun score(source: AppearanceInput = input, context: Map<String, Measurement> = others, limits: AppearancePolicy? = policy) =
        load(limits = limits).score("synthetic-scan", source, context, versions())
    private fun change(source: AppearanceInput = input, id: String = "appearance.brow", update: (AppearanceObservation) -> AppearanceObservation) =
        source.copy(observations = source.observations + (id to update(source.observations.getValue(id))))
    private fun rejected(block: () -> Unit) = assertThrows(IllegalArgumentException::class.java) { block() }
    private fun mutate(bytes: ByteArray = model, update: (JsonObject) -> JsonObject) = update(Json.parseToJsonElement(bytes.decodeToString()).jsonObject).toString().toByteArray()

    @Test fun canonicalHiddenScoresBecomeTiersAndWeightedMiscWithoutIndividualScoresInCards() {
        val result = score()
        assertEquals(Category.MISC, result.category)
        assertEquals("Appearance Details", result.categoryLabel)
        assertTrue(result.failures.isEmpty())
        val completed = result.analysis as AnalysisOutcome.Complete
        val metrics = completed.metrics.filterIsInstance<MetricResult.Available>().associateBy { it.metricId }
        assertEquals(90.0, metrics.getValue("appearance.brow").hiddenScore0To100, 1e-12)
        assertEquals(50.0, metrics.getValue("appearance.texture").hiddenScore0To100, 1e-12)
        assertEquals(MeasurementView.VISUAL, metrics.getValue("appearance.brow").view)
        assertEquals(6.0, result.categoryResult!!.score0To10, 1e-12) // (90*1 + 50*3)/4/10
        assertEquals(1.0, result.categoryResult.weightedCoverage0To1, 0.0)
        assertEquals(7.5, completed.overallScore0To10, 1e-12) // (8+8+8+6)/4
        assertEquals(Tier.T1, result.cards.single { it.metricId.endsWith("brow") }.tier)
        assertEquals(Tier.T3, result.cards.single { it.metricId.endsWith("texture") }.tier)
        assertNull(completed.rankLabel)
        assertTrue(completed.strongestMetricIds.isEmpty())
        assertEquals(hash(model), result.modelHash)
    }

    @Test fun modelCategoryWeightsAreUsedWithoutHardCodedQuarterWeights() {
        val bytes = mutate { root -> JsonObject(root + ("categories" to JsonObject(Category.entries.associate { category ->
            category.name to buildJsonObject { put("weight", if (category == Category.MISC) .4 else .2); put("minimumCoverage", 1.0) }
        }))) }
        val result = load(bytes).score("synthetic", input, others, versions(bytes))
        assertEquals(7.2, (result.analysis as AnalysisOutcome.Complete).overallScore0To10, 1e-12)
    }

    @Test fun repeatInputsAndMapOrderRemainIdentical() {
        val first = score()
        assertEquals(first, score(input.copy(observations = input.observations.entries.reversed().associate { it.key to it.value }), others.toSortedMap()))
    }

    @Test fun lightingFocusAndMethodFailuresOfferCorrectRemediationWithoutScores() {
        listOf(AppearanceQuality.UNKNOWN, AppearanceQuality.UNSUITABLE).forEach { quality ->
            val lighting = score(input.copy(lighting = quality))
            assertTrue(lighting.failures.values.all { it == AppearanceFailure.LIGHTING_UNRELIABLE })
            assertTrue(lighting.cards.all { it.tier == null && it.nextAction == "Try a photo with even lighting." })
            assertNull(lighting.categoryResult)
            assertTrue(score(input.copy(focus = quality)).failures.values.all { it == AppearanceFailure.FOCUS_UNRELIABLE })
        }
        assertTrue(score(input.copy(qualityMethodVersion = "unknown")).failures.values.all { it == AppearanceFailure.QUALITY_METHOD_MISMATCH })
    }

    @Test fun unknownAndLowConfidenceExcludeValuesAndInclusiveBoundaryPasses() {
        listOf(null, .799).forEach { confidence ->
            val result = score(change { it.copy(confidence = confidence) })
            assertEquals(AppearanceFailure.LOW_CONFIDENCE, result.failures.getValue("appearance.brow"))
            assertNull(result.categoryResult)
            assertTrue(result.cards.all { it.tier == null })
            assertTrue(result.analysis is AnalysisOutcome.InsufficientReliableMeasurements)
        }
        assertTrue(score(change { it.copy(confidence = .8) }).failures.isEmpty())
        // A weaker capture policy cannot lower the model's configured confidence floor.
        assertEquals(AppearanceFailure.LOW_CONFIDENCE, score(change { it.copy(confidence = .69) }, limits = policy.copy(minimumConfidence = .1))
            .failures.getValue("appearance.brow"))
    }

    @Test fun freshnessExtractorKindReliabilityRoiAndValueAreCheckedPerFeature() {
        val cases: List<Pair<AppearanceFailure, (AppearanceObservation) -> AppearanceObservation>> = listOf(
            AppearanceFailure.STALE_SOURCE to { it.copy(imageRevision = "old") },
            AppearanceFailure.EXTRACTOR_MISMATCH to { it.copy(extractorId = "other") },
            AppearanceFailure.EXTRACTOR_MISMATCH to { it.copy(extractorVersion = "old") },
            AppearanceFailure.EXTRACTOR_MISMATCH to { it.copy(kind = AppearanceFeatureKind.LIP_APPEARANCE) },
            AppearanceFailure.CONFIDENCE_METHOD_MISMATCH to { it.copy(confidenceMethodVersion = "unknown") },
            AppearanceFailure.UNVALIDATED_FEATURE to { it.copy(reliability = "UNVALIDATED") },
            AppearanceFailure.ROI_UNRELIABLE to { it.copy(roiQuality = AppearanceQuality.UNKNOWN) },
            AppearanceFailure.INSUFFICIENT_ROI_RESOLUTION to { it.copy(roiShortEdgePixels = null) },
            AppearanceFailure.INSUFFICIENT_ROI_RESOLUTION to { it.copy(roiShortEdgePixels = 99) },
            AppearanceFailure.VALUE_UNAVAILABLE to { it.copy(value = null) },
            AppearanceFailure.OUTSIDE_DEFINITION_DOMAIN to { it.copy(value = 1.01) },
        )
        cases.forEach { (reason, update) ->
            assertEquals(reason, score(change(update = update)).failures.getValue("appearance.brow"))
        }
        assertEquals(AppearanceFailure.MISSING_FEATURE, score(input.copy(observations = input.observations - "appearance.brow")).failures.getValue("appearance.brow"))
    }

    @Test fun noProductionDefaultsOrRealPhotoAdmission() {
        assertTrue(score(limits = null).failures.values.all { it == AppearanceFailure.POLICY_REQUIRED })
        val real = score(input.copy(origin = AppearanceOrigin.CONSENTED_LOCAL))
        assertTrue(real.failures.values.all { it == AppearanceFailure.SYNTHETIC_ONLY })
        assertTrue(real.cards.all { it.tier == null && it.nextAction == null })
        assertNull(real.categoryResult)
    }

    @Test fun missingOtherCategoriesNeverBecomeIdealPlaceholdersOrCompleteAppearanceOnlyScans() {
        val result = score(context = emptyMap())
        assertTrue(result.analysis is AnalysisOutcome.InsufficientReliableMeasurements)
        assertTrue(result.failures.values.all { it == AppearanceFailure.WAITING_FOR_OTHER_MEASUREMENTS })
        assertTrue(result.cards.all { it.tier == null && it.nextAction == null })
        assertNull(result.categoryResult)
    }

    @Test fun optionalFailuresRespectModelCoverageAndEnabledMembership() {
        val bytes = mutate { root ->
            val categories = root.getValue("categories").jsonObject
            val misc = categories.getValue("MISC").jsonObject
            val metrics = root.getValue("metrics").jsonArray.map { item ->
                val metric = item.jsonObject
                if (metric.getValue("id").jsonPrimitive.content == "appearance.brow") JsonObject(metric + ("requiredForCompletion" to JsonPrimitive(false))) else metric
            }
            JsonObject(root + mapOf("categories" to JsonObject(categories + ("MISC" to JsonObject(misc + ("minimumCoverage" to JsonPrimitive(.75))))), "metrics" to JsonArray(metrics)))
        }
        val partial = input.copy(observations = input.observations - "appearance.brow")
        val result = load(bytes).score("synthetic", partial, others, versions(bytes))
        assertTrue(result.analysis is AnalysisOutcome.Complete)
        assertEquals(.75, result.categoryResult!!.weightedCoverage0To1, 0.0)
        assertEquals(5.0, result.categoryResult.score0To10, 1e-12)
        assertNull(result.cards.single { it.metricId.endsWith("brow") }.tier)
        assertEquals(Tier.T3, result.cards.single { it.metricId.endsWith("texture") }.tier)
    }

    @Test fun skinColorEthnicityAndHealthCannotBeScoringBasesOrPresentationLabels() {
        listOf(AppearanceScoreBasis.SKIN_COLOR, AppearanceScoreBasis.ETHNICITY, AppearanceScoreBasis.HEALTH_INFERENCE).forEach { basis ->
            rejected { bindings.first().copy(scoreBasis = basis) }
        }
        rejected { AppearanceFeatureKind.valueOf("SKIN_TONE") }
        val diagnosticOrColorRanking = Regex("(?i)healthy|disease|hormone|diagnos|lighter skin|darker skin|ethnic|acne|iron deficiency")
        AppearanceFeatureKind.entries.forEach { kind ->
            assertFalse(diagnosticOrColorRanking.containsMatchIn(AppearanceContent.title(kind) + AppearanceContent.explanation(kind)))
        }
        assertEquals("Appearance Details", AppearanceContent.categoryLabel)
    }

    @Test fun draftUnreviewedAndResearchModelsCannotBePromotedByAdapter() {
        rejected { load(mutate { JsonObject(it + ("status" to JsonPrimitive("DRAFT_NOT_RELEASE_SCORABLE"))) }) }
        rejected { AppearanceScoring.load("reference-data/v1/benchmarks.csv", model, formulas, benchmarks, bindings, policy) }
        val deniedBenchmarks = object : BenchmarkRegistry { override fun review(id: String) = BenchmarkReview("POPULATION_NORM", "exact", "ADULT_EVIDENCE", false) }
        rejected { AppearanceScoring.load("reference-models/v1/test.json", model, formulas, deniedBenchmarks, bindings, policy) }
    }

    @Test fun bindingSetAndExtractorMismatchCategoricalEncodingAndVersionDriftAreRejected() {
        rejected { load(selected = bindings.take(1)) }
        rejected { load(selected = bindings + bindings.first()) }
        rejected { load(selected = bindings.map { it.copy(extractorId = "unrecognized") }) }
        val categorical = mutate { root -> JsonObject(root + ("metrics" to JsonArray(root.getValue("metrics").jsonArray.map {
            val metric = it.jsonObject
            if (metric.getValue("category").jsonPrimitive.content == "MISC") JsonObject(metric + ("valueType" to JsonPrimitive("CATEGORICAL"))) else metric
        }))) }
        rejected { load(categorical) }
        rejected { load().score("synthetic", input, others, versions().copy(metricConfigHash = "wrong")) }
    }

    @Test fun unknownFeaturesOrContextCannotBypassAdmissionAndMalformedValuesFail() {
        rejected { score(input.copy(observations = input.observations + ("skin-tone" to input.observations.values.first()))) }
        rejected { score(context = others + ("appearance.brow" to Measurement(1.0, 1.0))) }
        rejected { change { it.copy(confidence = Double.NaN) } }
        rejected { change { it.copy(value = Double.POSITIVE_INFINITY) } }
        rejected { bindings.first().copy(fairnessEvidence = "") }
        rejected { bindings.first().copy(minimumValue = 2.0) }
    }

    @Test fun modelTierDomainFailureDoesNotBecomeWaitingOrAnInventedTier() {
        // The fixture intentionally has tiny gaps between inclusive tier intervals.
        val gap = .1999999995
        val result = score(change { it.copy(value = gap) })
        assertEquals(AppearanceFailure.SCORE_UNAVAILABLE, result.failures.getValue("appearance.brow"))
        assertNull(result.cards.single { it.metricId.endsWith("brow") }.tier)
    }
}
