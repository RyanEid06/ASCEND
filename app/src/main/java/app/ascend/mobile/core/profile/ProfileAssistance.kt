package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide

internal data class ProfileZone(
    val minimumX: Double, val maximumX: Double,
    val minimumY: Double, val maximumY: Double,
    /** Guidance for absent points only; it is not an extracted anatomical observation. */
    val missingPointAnchor: Point2,
    val maximumShortEdgeDisplacement: Double,
) {
    init {
        require(listOf(minimumX, maximumX, minimumY, maximumY).all { it.isFinite() && it in 0.0..1.0 })
        require(minimumX <= maximumX && minimumY <= maximumY)
        require(maximumShortEdgeDisplacement.isFinite() && maximumShortEdgeDisplacement > 0.0)
        require(contains(missingPointAnchor))
    }
    fun contains(point: Point2) = point.x in minimumX..maximumX && point.y in minimumY..maximumY
}

/** Image/side-specific synthetic guidance. No production anatomical regions or bounds are invented. */
internal data class ProfileAssistancePolicy(
    val version: String, val evidence: String, val imageRevision: String, val side: ProfileSide,
    val zones: Map<LandmarkId, ProfileZone>,
) {
    init {
        require(listOf(version, evidence, imageRevision).all(String::isNotBlank))
        require(zones.isNotEmpty() && zones.keys.all { it in ProfileCatalog.semanticIds })
    }
}

internal enum class ProfileActionKind { INITIALIZE, CONFIRM, MOVE }
internal data class ProfileAction(
    val revision: Long,
    val imageRevision: String,
    val pointId: LandmarkId,
    val originalProposal: ProfilePoint?,
    val anchor: Point2,
    val previous: ProfilePoint?,
    val current: ProfilePoint,
    val kind: ProfileActionKind,
    val policyVersion: String,
    val policyEvidence: String,
    val methodVersion: String,
    val atEpochMillis: Long,
)

/** Construction/restoration replays constraints; no unchecked copy of a corrected revision. */
internal class ProfileRevision private constructor(
    private val originalSnapshot: ProfileInput,
    private val pointSnapshot: Map<LandmarkId, ProfilePoint>,
    private val auditSnapshot: List<ProfileAction>,
    private val policySnapshot: ProfileAssistancePolicy? = null,
) {
    val revision: Long get() = auditSnapshot.size.toLong()
    // Return copies so mutable casts cannot alter a revision's audit or original anchor.
    val original: ProfileInput get() = originalSnapshot.copy(points = originalSnapshot.points.toMap())
    val input: ProfileInput get() = originalSnapshot.copy(points = pointSnapshot.toMap())
    val audit: List<ProfileAction> get() = auditSnapshot.toList()

    fun confirm(
        pointId: LandmarkId, target: Point2, policy: ProfileAssistancePolicy,
        expectedImageRevision: String, expectedRevision: Long,
        methodVersion: String, atEpochMillis: Long,
    ): ProfileRevision {
        require(expectedImageRevision == originalSnapshot.imageRevision && expectedRevision == revision) { "Stale profile revision" }
        require(originalSnapshot.origin == ProfileOrigin.SYNTHETIC ||
            (originalSnapshot.origin == ProfileOrigin.DEMO_LOCAL && policy.version == PROFILE_PREVIEW_POLICY_VERSION)) {
            "No reviewed assistance policy for this photo"
        }
        require(originalSnapshot.orientationConfirmed && originalSnapshot.side != null) { "Confirm orientation first" }
        require(policy.imageRevision == originalSnapshot.imageRevision && policy.side == originalSnapshot.side) { "Guidance belongs to another image/side" }
        require(methodVersion.isNotBlank() && atEpochMillis >= 0)
        require(auditSnapshot.isEmpty() || atEpochMillis >= auditSnapshot.last().atEpochMillis)
        require(auditSnapshot.size < 256) { "Correction audit limit reached" }
        val zone = requireNotNull(policy.zones[pointId]) { "Point is not eligible for assistance" }
        val proposal = originalSnapshot.points[pointId]
        val anchor = proposal?.point ?: zone.missingPointAnchor
        require(zone.contains(anchor) && zone.contains(target)) { "Outside anatomical region" }
        val a = originalSnapshot.isotropic(anchor)
        val b = originalSnapshot.isotropic(target)
        val rounding = 8 * Math.ulp(maxOf(1.0, kotlin.math.abs(a.x), kotlin.math.abs(a.y), kotlin.math.abs(b.x), kotlin.math.abs(b.y)))
        require(distance(a, b) <= zone.maximumShortEdgeDisplacement + rounding) { "Outside fixed anchor displacement" }
        // Policy must remain identical for repeated interactions, including bounds and missing-point guidance.
        val priorPolicy = policySnapshot
        require(priorPolicy == null || priorPolicy == policy) { "Cannot change guidance within a revision" }
        val previous = pointSnapshot[pointId]
        val current = ProfilePoint(target, proposal?.confidence, ProfilePointSource.ASSISTED, confirmed = true)
        val kind = when {
            previous == null -> ProfileActionKind.INITIALIZE
            previous.point == target -> ProfileActionKind.CONFIRM
            else -> ProfileActionKind.MOVE
        }
        val action = ProfileAction(revision + 1, originalSnapshot.imageRevision, pointId, proposal,
            anchor, previous, current, kind, policy.version, policy.evidence, methodVersion, atEpochMillis)
        return ProfileRevision(originalSnapshot, pointSnapshot + (pointId to current), auditSnapshot + action,
            policy.copy(zones = policy.zones.toMap()))
    }

    companion object {
        fun start(source: ProfileInput): ProfileRevision {
            require(source.points.values.all { it.source == ProfilePointSource.AUTOMATIC && !it.confirmed })
            val snapshot = source.copy(points = source.points.toMap())
            return ProfileRevision(snapshot, snapshot.points.toMap(), emptyList())
        }

        fun restore(original: ProfileInput, actions: List<ProfileAction>, policy: ProfileAssistancePolicy): ProfileRevision {
            require(actions.size <= 256)
            var revision = start(original)
            actions.forEach { action ->
                val next = revision.confirm(action.pointId, action.current.point, policy, action.imageRevision,
                    action.revision - 1, action.methodVersion, action.atEpochMillis)
                require(next.audit.last() == action) { "Invalid correction provenance" }
                revision = next
            }
            return revision
        }
    }
}
