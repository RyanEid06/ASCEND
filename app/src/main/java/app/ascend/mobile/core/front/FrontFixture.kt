package app.ascend.mobile.core.front

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExpectedFrontMetric(val metricId: String, val value: Double?, val absoluteTolerance: Double?, val failure: FrontFailureName?) {
    init {
        require(metricId in FrontCatalog.mappings.map { it.metricId })
        require((value != null) == (failure == null))
        require(value == null || value.isFinite())
        require(if (value != null) absoluteTolerance != null && absoluteTolerance.isFinite() && absoluteTolerance >= 0 else absoluteTolerance == null)
    }
}

// Serialized names intentionally stable, independent of implementation enums.
@Serializable
enum class FrontFailureName { POLICY_REQUIRED, POLICY_NOT_VALIDATED_FOR_CAPTURE, FACE_COUNT_UNAVAILABLE, NOT_ONE_FACE, POSE_UNAVAILABLE, INSUFFICIENT_RESOLUTION, POSE_OUT_OF_RANGE, MISSING_LANDMARK, LOW_CONFIDENCE, CALIBRATION_REQUIRED, DEGENERATE_GEOMETRY, UNSUPPORTED_DEFINITION }

/** No media/path field. Real annotations and local consent references belong only in ignored private-fixtures. */
@Serializable
data class FrontFixture(
    val formatVersion: Int,
    val fixtureId: String,
    val annotationMethodVersion: String,
    val annotatorCode: String,
    val consentRecordLocalId: String?,
    val evidenceKind: String,
    val toleranceEvidence: String,
    val input: FrontInput,
    val policy: FrontPolicy,
    val expected: List<ExpectedFrontMetric>,
) {
    init {
        require(formatVersion == 1)
        require(listOf(fixtureId, annotationMethodVersion, annotatorCode, toleranceEvidence).all(String::isNotBlank))
        require(if (input.origin == FixtureOrigin.SYNTHETIC) consentRecordLocalId == null && evidenceKind == "SYNTHETIC_FORMULA" else
            !consentRecordLocalId.isNullOrBlank() && evidenceKind == "HUMAN_ANNOTATION")
        require(expected.isNotEmpty() && expected.map { it.metricId }.distinct().size == expected.size)
    }
    /** Useful to private QA tools; returns only metric IDs, never logs points or photos. */
    fun mismatches(): List<String> {
        val results = FrontMeasurements(policy).measure(input).metrics.associateBy { it.metricId }
        return expected.filter { expectation ->
            val result = results.getValue(expectation.metricId)
            if (expectation.failure != null) result.failure?.name != expectation.failure.name
            else result.value == null || kotlin.math.abs(result.value - requireNotNull(expectation.value)) > requireNotNull(expectation.absoluteTolerance)
        }.map { it.metricId }
    }
}

object FrontCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
    private const val MAX_BYTES = 1_000_000
    fun fixture(bytes: ByteArray): FrontFixture {
        require(bytes.size <= MAX_BYTES)
        return json.decodeFromString<FrontFixture>(bytes.toString(Charsets.UTF_8))
    }
    fun revision(bytes: ByteArray): FrontRevision {
        require(bytes.size <= MAX_BYTES)
        return json.decodeFromString<FrontRevision>(bytes.toString(Charsets.UTF_8))
    }
    fun encode(revision: FrontRevision): ByteArray = json.encodeToString(revision).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    fun encode(provenance: FrontCompletedProvenance): ByteArray = json.encodeToString(provenance).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    fun completed(bytes: ByteArray): FrontCompletedProvenance {
        require(bytes.size <= MAX_BYTES)
        return json.decodeFromString<FrontCompletedProvenance>(bytes.toString(Charsets.UTF_8))
    }
}

@Serializable
data class FrontCompletedProvenance(val formatVersion: Int, val correctionRevision: Long, val geometryVersion: String, val policy: FrontPolicy) {
    init { require(formatVersion == 1 && correctionRevision >= 0 && geometryVersion.isNotBlank()) }
}
