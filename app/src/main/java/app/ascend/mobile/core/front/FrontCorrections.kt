package app.ascend.mobile.core.front

import app.ascend.mobile.core.geometry.Point2
import app.ascend.mobile.core.geometry.distance
import kotlinx.serialization.Serializable

@Serializable
data class CorrectionZone(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double, val maximumShortEdgeDisplacement: Double) {
    init {
        require(listOf(minX, maxX, minY, maxY, maximumShortEdgeDisplacement).all(Double::isFinite))
        require(minX in 0.0..1.0 && maxX in minX..1.0 && minY in 0.0..1.0 && maxY in minY..1.0)
        require(maximumShortEdgeDisplacement > 0.0)
    }
    fun contains(point: FrontPoint) = point.x in minX..maxX && point.y in minY..maxY
}

@Serializable
data class CorrectionPolicy(val version: String, val evidence: String, val syntheticOnly: Boolean, val zones: Map<String, CorrectionZone>) {
    init {
        require(version.isNotBlank() && evidence.isNotBlank())
        require(zones.isNotEmpty() && zones.keys.all { it in FrontCatalog.correctionEligible })
    }
}

@Serializable
data class CorrectionRecord(
    val landmarkId: String,
    val original: FrontPoint,
    val from: FrontPoint,
    val to: FrontPoint,
    val atEpochMillis: Long,
    val policy: CorrectionPolicy,
    val inputImageRevision: String,
    val modelVersion: String,
    val modelArtifactSha256: String,
    val extractorVersion: String,
    val revision: Long,
    val method: String = "bounded-human-confirmation-v1",
) {
    init { require(atEpochMillis >= 0 && revision > 0) }
}

/** Source plus an audit chain, never an editable cached score. Completed history is read-only in storage. */
@Serializable
data class FrontRevision(val formatVersion: Int, val original: FrontInput, val corrections: List<CorrectionRecord>) {
    init {
        require(formatVersion == 1)
        var points = original.landmarks.toMap()
        var time = -1L
        corrections.forEachIndexed { index, record ->
            require(record.revision == index.toLong() + 1 && record.atEpochMillis >= time)
            require(record.method == "bounded-human-confirmation-v1")
            require(record.inputImageRevision == original.imageRevision && record.modelVersion == original.modelVersion &&
                record.modelArtifactSha256 == original.modelArtifactSha256 && record.extractorVersion == original.extractorVersion)
            require(record.original == original.landmarks[record.landmarkId] && record.from == points[record.landmarkId])
            validateCorrection(original, record.policy, record.landmarkId, record.to)
            points = points + (record.landmarkId to record.to)
            time = record.atEpochMillis
        }
    }
    val revision: Long get() = corrections.size.toLong()
    fun current(): FrontInput = original.copy(landmarks = original.landmarks.toMap() + corrections.associate { it.landmarkId to it.to })
    fun measure(policy: FrontPolicy?) = FrontMeasurements(policy).measure(current(), corrections.map { it.landmarkId }.toSet())

    fun correct(policy: CorrectionPolicy, id: String, x: Double, y: Double, atEpochMillis: Long): FrontRevision {
        require(atEpochMillis >= (corrections.lastOrNull()?.atEpochMillis ?: 0))
        val from = requireNotNull(current().landmarks[id]) { "Missing points require re-extraction" }
        val to = FrontPoint(x, y, from.confidence) // Confirmation never upgrades extractor confidence.
        require(to != from) { "Correction must move the point" }
        validateCorrection(original, policy, id, to)
        val record = CorrectionRecord(id, original.landmarks.getValue(id), from, to, atEpochMillis,
            policy.copy(zones = policy.zones.toMap()), original.imageRevision, original.modelVersion,
            original.modelArtifactSha256, original.extractorVersion, revision + 1)
        return FrontRevision(1, original.copy(landmarks = original.landmarks.toMap()), corrections.toList() + record)
    }
}

private fun validateCorrection(original: FrontInput, policy: CorrectionPolicy, id: String, to: FrontPoint) {
    require(!policy.syntheticOnly || original.origin == FixtureOrigin.SYNTHETIC) { "Synthetic bounds cannot authorize capture correction" }
    require(id in FrontCatalog.correctionEligible)
    val zone = requireNotNull(policy.zones[id]) { "No reviewed bounds for this landmark" }
    val anchor = requireNotNull(original.landmarks[id]) { "Missing points cannot be invented" }
    require(anchor.confidence != null && to.confidence == anchor.confidence)
    require(zone.contains(anchor) && zone.contains(to)) { "Outside anatomical correction zone" }
    val shortEdge = minOf(original.width, original.height).toDouble()
    fun isotropic(point: FrontPoint) = Point2(point.x * original.width / shortEdge, point.y * original.height / shortEdge)
    val a = isotropic(anchor)
    val b = isotropic(to)
    // Machine-rounding allowance only, not an empirical capture or correction tolerance.
    val rounding = 8 * Math.ulp(maxOf(1.0, kotlin.math.abs(a.x), kotlin.math.abs(a.y), kotlin.math.abs(b.x), kotlin.math.abs(b.y)))
    require(distance(a, b) <= zone.maximumShortEdgeDisplacement + rounding) { "Outside original extraction displacement bound" }
}
