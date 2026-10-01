package app.ascend.mobile.core.model

enum class Category { HARMONY, DIMORPHISM, ANGULARITY, MISC }
enum class Tier { T1, T2, T3, T4, T5 }
enum class MeasurementView { FRONT, PROFILE, VISUAL }

/** Immutable provenance stored with a completed result, including its scoring snapshot identity. */
data class AnalysisVersions(
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
) {
    init {
        require(listOf(
            appVersion, analysisEngineVersion, frontLandmarkModelVersion,
            profileExtractorVersion, referenceModelVersion, scoringModelVersion,
            metricConfigHash, enabledMetricSetVersion, coveragePolicyVersion,
            scoreScaleVersion, extremaSelectionVersion, recommendationVersion,
        ).all(String::isNotBlank))
    }
}

sealed interface MetricResult {
    val metricId: String

    data class Available(
        override val metricId: String,
        val category: Category,
        val view: MeasurementView,
        val measuredValue: Double,
        val tier: Tier,
        val hiddenScore0To100: Double,
        val confidence0To1: Double,
        val weightUsed: Double,
        val scoreScaleVersion: String?,
    ) : MetricResult {
        init {
            require(metricId.isNotBlank())
            require(measuredValue.isFinite())
            require(hiddenScore0To100.isFinite() && hiddenScore0To100 in 0.0..100.0)
            require(confidence0To1.isFinite() && confidence0To1 in 0.0..1.0)
            require(weightUsed.isFinite() && weightUsed > 0.0)
            require(scoreScaleVersion == null || scoreScaleVersion.isNotBlank())
        }
    }

    data class Unavailable(
        override val metricId: String,
        val reason: String,
    ) : MetricResult {
        init {
            require(metricId.isNotBlank())
            require(reason.isNotBlank())
        }
    }
}

data class CategoryResult(
    val category: Category,
    val score0To10: Double,
    /** Valid enabled/applicable weight divided by total enabled/applicable weight. */
    val weightedCoverage0To1: Double,
    val coveragePolicyVersion: String,
) {
    init {
        require(score0To10.isFinite() && score0To10 in 0.0..10.0)
        require(weightedCoverage0To1.isFinite() && weightedCoverage0To1 in 0.0..1.0)
        require(coveragePolicyVersion.isNotBlank())
    }
}

sealed interface AnalysisOutcome {
    val scanId: String

    /** No overall score is present when required measurements or categories are missing. */
    data class InsufficientReliableMeasurements(
        override val scanId: String,
        val unavailableMetricIds: Set<String>,
        val reason: String,
    ) : AnalysisOutcome {
        init {
            require(scanId.isNotBlank())
            require(reason.isNotBlank())
        }
    }

    data class Complete(
        override val scanId: String,
        val referenceModel: ReferenceModel,
        val versions: AnalysisVersions,
        val enabledMetricIds: Set<String>,
        val metrics: List<MetricResult>,
        val categories: Map<Category, CategoryResult>,
        val overallScore0To10: Double,
        /** Null until a reviewed rank configuration is available. */
        val rankLabel: String?,
        /** Empty sets mean extrema cannot be stated reliably/comparably. */
        val strongestMetricIds: Set<String>,
        val weakestMetricIds: Set<String>,
    ) : AnalysisOutcome {
        init {
            require(scanId.isNotBlank())
            require(enabledMetricIds.isNotEmpty() && enabledMetricIds.all(String::isNotBlank))
            require(metrics.map(MetricResult::metricId).toSet().size == metrics.size)
            require(metrics.map(MetricResult::metricId).toSet() == enabledMetricIds)
            require(categories.keys == Category.entries.toSet())
            require(categories.all { (key, value) -> key == value.category })
            require(categories.values.all { it.coveragePolicyVersion == versions.coveragePolicyVersion })
            require(overallScore0To10.isFinite() && overallScore0To10 in 0.0..10.0)
            require(rankLabel == null || rankLabel.isNotBlank())
            val comparable = metrics.filterIsInstance<MetricResult.Available>()
                .filter { it.scoreScaleVersion == versions.scoreScaleVersion }
                .mapTo(mutableSetOf()) { it.metricId }
            require(strongestMetricIds.all(comparable::contains))
            require(weakestMetricIds.all(comparable::contains))
            require(strongestMetricIds.isEmpty() == weakestMetricIds.isEmpty())
        }
    }
}
