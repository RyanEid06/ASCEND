package app.ascend.mobile.core.appearance

import app.ascend.mobile.core.model.*
import app.ascend.mobile.core.scoring.*
import kotlinx.serialization.json.*

/** Lane-private proposal. Reuses the canonical scorer; it does not introduce another score curve. */
internal class AppearanceScoring private constructor(
    private val engine: ScoringEngine,
    private val bindings: Map<String, AppearanceBinding>,
    private val configuredMinimumConfidence: Map<String, Double>,
    private val otherMetricIds: Set<String>,
    private val policy: AppearancePolicy?,
) {
    companion object {
        fun load(
            path: String, model: ByteArray, formulas: FormulaRegistry, benchmarks: BenchmarkRegistry,
            bindings: List<AppearanceBinding>, policy: AppearancePolicy?,
        ): AppearanceScoring {
            // Draft/research/unreviewed models fail in the existing trust gate before any numeric evaluation.
            val snapshot = model.copyOf()
            val engine = ScoringEngine.loadRuntimeModel(path, snapshot, formulas, benchmarks)
            val definitions = Json.parseToJsonElement(snapshot.decodeToString()).jsonObject.getValue("metrics").jsonArray.map { it.jsonObject }
            val misc = definitions.filter { it.getValue("category").jsonPrimitive.content == Category.MISC.name }
            fun fail(message: String): Nothing = throw InvalidScoringConfig(message)
            if (bindings.isEmpty() || bindings.map { it.metricId }.distinct().size != bindings.size ||
                bindings.map { it.metricId }.toSet() != misc.map { it.getValue("id").jsonPrimitive.content }.toSet()) fail("Appearance binding set must match enabled MISC metrics")
            val byId = bindings.associateBy { it.metricId }
            misc.forEach { metric ->
                val binding = byId.getValue(metric.getValue("id").jsonPrimitive.content)
                if (metric.getValue("valueType").jsonPrimitive.content != "CONTINUOUS_VISUAL" ||
                    metric.getValue("measurementMode").jsonPrimitive.content != "AUTOMATIC" ||
                    metric.getValue("calibrationRequired").jsonPrimitive.boolean ||
                    metric.getValue("formulaOrExtractorId").jsonPrimitive.content != binding.extractorId) fail("Appearance definition/extractor mismatch")
            }
            return AppearanceScoring(engine, byId.toSortedMap(), misc.associate {
                it.getValue("id").jsonPrimitive.content to it.getValue("minimumConfidence").jsonPrimitive.double
            }, definitions.filter { it.getValue("category").jsonPrimitive.content != Category.MISC.name }
                .map { it.getValue("id").jsonPrimitive.content }.toSet(), policy)
        }
    }

    fun score(scanId: String, source: AppearanceInput, otherMeasurements: Map<String, Measurement>, versions: AnalysisVersions): AppearanceReport {
        require(source.observations.keys.all { it in bindings }) { "Unknown appearance feature" }
        require(otherMeasurements.keys.all { it in otherMetricIds }) { "Other measurements cannot inject MISC or unknown metrics" }
        val input = source.copy(observations = source.observations.toSortedMap())
        val failures = mutableMapOf<String, AppearanceFailure>()
        val accepted = mutableMapOf<String, Measurement>()
        bindings.forEach { (id, binding) ->
            val failure = gate(input, binding)
            if (failure != null) failures[id] = failure else {
                val observation = input.observations.getValue(id)
                accepted[id] = Measurement(requireNotNull(observation.value), requireNotNull(observation.confidence), MeasurementView.VISUAL)
            }
        }
        // Other categories are supplied by their actual callers; no placeholder/ideal values are fabricated.
        val outcome = engine.score(scanId, otherMeasurements + accepted, versions)
        val complete = outcome as? AnalysisOutcome.Complete
        val available = complete?.metrics?.filterIsInstance<MetricResult.Available>()?.associateBy { it.metricId }.orEmpty()
        val unavailableIds = when (outcome) {
            is AnalysisOutcome.InsufficientReliableMeasurements -> outcome.unavailableMetricIds
            is AnalysisOutcome.Complete -> outcome.metrics.filterIsInstance<MetricResult.Unavailable>().map { it.metricId }.toSet()
        }
        bindings.keys.filter { it !in failures && it in unavailableIds }.forEach { failures[it] = AppearanceFailure.SCORE_UNAVAILABLE }
        if (complete == null) bindings.keys.filter { it !in failures }.forEach { failures[it] = AppearanceFailure.WAITING_FOR_OTHER_MEASUREMENTS }
        return AppearanceReport(APPEARANCE_CONTRACT_PROPOSAL, Category.MISC, AppearanceContent.categoryLabel, input,
            policy?.version, versions.metricConfigHash, bindings.mapValues { it.value.definitionVersion },
            bindings.mapValues { it.value.fairnessEvidence }, failures.toSortedMap(),
            bindings.map { (id, binding) -> AppearanceContent.card(binding, available[id]?.tier, failures[id]) },
            complete?.categories?.get(Category.MISC), outcome)
    }

    private fun gate(input: AppearanceInput, binding: AppearanceBinding): AppearanceFailure? {
        val limits = policy ?: return AppearanceFailure.POLICY_REQUIRED
        if (input.origin != AppearanceOrigin.SYNTHETIC) return AppearanceFailure.SYNTHETIC_ONLY
        if (input.qualityMethodVersion != limits.qualityMethodVersion) return AppearanceFailure.QUALITY_METHOD_MISMATCH
        if (input.lighting != AppearanceQuality.USABLE) return AppearanceFailure.LIGHTING_UNRELIABLE
        if (input.focus != AppearanceQuality.USABLE) return AppearanceFailure.FOCUS_UNRELIABLE
        val observation = input.observations[binding.metricId] ?: return AppearanceFailure.MISSING_FEATURE
        if (observation.imageRevision != input.imageRevision) return AppearanceFailure.STALE_SOURCE
        if (observation.kind != binding.kind || observation.extractorId != binding.extractorId ||
            observation.extractorVersion != binding.extractorVersion) return AppearanceFailure.EXTRACTOR_MISMATCH
        if (observation.confidenceMethodVersion != binding.confidenceMethodVersion) return AppearanceFailure.CONFIDENCE_METHOD_MISMATCH
        if (observation.reliability != "SYNTHETIC_VERIFIED") return AppearanceFailure.UNVALIDATED_FEATURE
        if (observation.roiQuality != AppearanceQuality.USABLE) return AppearanceFailure.ROI_UNRELIABLE
        if (observation.roiShortEdgePixels == null || observation.roiShortEdgePixels < limits.minimumRoiShortEdgePixels) return AppearanceFailure.INSUFFICIENT_ROI_RESOLUTION
        if (observation.confidence == null || observation.confidence < maxOf(limits.minimumConfidence, configuredMinimumConfidence.getValue(binding.metricId))) return AppearanceFailure.LOW_CONFIDENCE
        val value = observation.value ?: return AppearanceFailure.VALUE_UNAVAILABLE
        if (value !in binding.minimumValue..binding.maximumValue) return AppearanceFailure.OUTSIDE_DEFINITION_DOMAIN
        return null
    }
}
