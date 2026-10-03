package app.ascend.mobile.storage

import app.ascend.mobile.core.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned encrypted-DB snapshot; decoding re-applies the existing core invariants. */
internal object AnalysisStorageCodec {
    private val json = Json { encodeDefaults = true }

    fun encode(result: AnalysisOutcome.Complete): ByteArray = json.encodeToString(StoredAnalysis(
        1, result.scanId, result.referenceModel.name, result.versions.toStored(), result.enabledMetricIds,
        result.metrics.map { metric -> when (metric) {
            is MetricResult.Unavailable -> StoredMetric(metric.metricId, false, reason = metric.reason)
            is MetricResult.Available -> StoredMetric(metric.metricId, true, metric.category.name, metric.view.name,
                metric.measuredValue, metric.tier.name, metric.hiddenScore0To100, metric.confidence0To1,
                metric.weightUsed, metric.scoreScaleVersion)
        } }, result.categories.values.map { StoredCategory(it.category.name, it.score0To10, it.weightedCoverage0To1, it.coveragePolicyVersion) },
        result.overallScore0To10, result.rankLabel, result.strongestMetricIds, result.weakestMetricIds,
    )).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): AnalysisOutcome.Complete {
        val stored = json.decodeFromString<StoredAnalysis>(bytes.toString(Charsets.UTF_8))
        require(stored.formatVersion == 1)
        require(stored.categories.map { it.category }.distinct().size == stored.categories.size)
        return AnalysisOutcome.Complete(stored.scanId, ReferenceModel.valueOf(stored.referenceModel), stored.versions.toCore(),
            stored.enabledMetricIds, stored.metrics.map { metric ->
                if (!metric.available) MetricResult.Unavailable(metric.id, requireNotNull(metric.reason))
                else MetricResult.Available(metric.id, Category.valueOf(requireNotNull(metric.category)),
                    MeasurementView.valueOf(requireNotNull(metric.view)), requireNotNull(metric.measuredValue),
                    Tier.valueOf(requireNotNull(metric.tier)), requireNotNull(metric.hiddenScore), requireNotNull(metric.confidence),
                    requireNotNull(metric.weight), metric.scoreScaleVersion)
            }, stored.categories.associate { category ->
                val key = Category.valueOf(category.category)
                key to CategoryResult(key, category.score, category.coverage, category.policyVersion)
            }, stored.overall, stored.rank, stored.strongest, stored.weakest)
    }
}

@Serializable
private data class StoredAnalysis(
    val formatVersion: Int,
    val scanId: String,
    val referenceModel: String,
    val versions: StoredVersions,
    val enabledMetricIds: Set<String>,
    val metrics: List<StoredMetric>,
    val categories: List<StoredCategory>,
    val overall: Double,
    val rank: String?,
    val strongest: Set<String>,
    val weakest: Set<String>,
)

@Serializable
private data class StoredMetric(
    val id: String,
    val available: Boolean,
    val category: String? = null,
    val view: String? = null,
    val measuredValue: Double? = null,
    val tier: String? = null,
    val hiddenScore: Double? = null,
    val confidence: Double? = null,
    val weight: Double? = null,
    val scoreScaleVersion: String? = null,
    val reason: String? = null,
)

@Serializable
private data class StoredCategory(val category: String, val score: Double, val coverage: Double, val policyVersion: String)

@Serializable
private data class StoredVersions(
    val appVersion: String,
    val analysisEngineVersion: String,
    val frontLandmarkModelVersion: String,
    val profileExtractorVersion: String,
    val referenceModelVersion: String,
    val scoringModelVersion: String,
    val metricConfigHash: String,
    val enabledMetricSetVersion: String,
    val coveragePolicyVersion: String,
    val scoreScaleVersion: String,
    val extremaSelectionVersion: String,
    val recommendationVersion: String,
)

private fun AnalysisVersions.toStored() = StoredVersions(appVersion, analysisEngineVersion, frontLandmarkModelVersion,
    profileExtractorVersion, referenceModelVersion, scoringModelVersion, metricConfigHash, enabledMetricSetVersion,
    coveragePolicyVersion, scoreScaleVersion, extremaSelectionVersion, recommendationVersion)
private fun StoredVersions.toCore() = AnalysisVersions(appVersion, analysisEngineVersion, frontLandmarkModelVersion,
    profileExtractorVersion, referenceModelVersion, scoringModelVersion, metricConfigHash, enabledMetricSetVersion,
    coveragePolicyVersion, scoreScaleVersion, extremaSelectionVersion, recommendationVersion)
