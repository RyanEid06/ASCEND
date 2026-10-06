package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide

internal const val PROFILE_PREVIEW_POLICY_VERSION = "wp09-local-preview-v1"
internal enum class ProfileFacing { LEFT, RIGHT }

/** UI/storage envelope around the single WP10 source and audit model. */
internal data class ProfileAssistSession(
    val revision: ProfileRevision,
    val policy: ProfileAssistancePolicy,
    val facing: ProfileFacing,
    val revisionToken: Long = revision.revision,
) {
    init {
        require(policy.imageRevision == revision.input.imageRevision && policy.side == revision.input.side)
        require(revision.input.orientationConfirmed)
        require(revisionToken >= revision.revision)
        if (revision.input.origin == ProfileOrigin.DEMO_LOCAL) require(policy == previewGuidance(
            revision.input.imageRevision, revision.input.resolution, requireNotNull(revision.input.side), facing))
    }
    val requiredPoints: List<LandmarkId> get() = ProfileCatalog.mappings.flatMap { it.anglePoints.orEmpty() }.distinct()
    val complete: Boolean get() = requiredPoints.all { revision.input.points[it]?.confirmed == true }
    fun imagePoints(): Map<LandmarkId, Point2> = revision.input.points.mapValues { it.value.point }
    fun measure(policy: ProfilePolicy?) = ProfileMeasurements(policy).measure(revision.input)
    fun confirm(id: LandmarkId, target: Point2, expectedRevision: Long, atEpochMillis: Long = revision.revision): ProfileAssistSession {
        require(expectedRevision == revisionToken) { "Stale profile correction revision" }
        return copy(revision = revision.confirm(id, target, policy, revision.input.imageRevision, revision.revision,
            "guided-profile-confirmation-v1", atEpochMillis), revisionToken = revisionToken + 1)
    }

    companion object {
        /** Fixed illustrative guides only. These coordinates are explicitly NOT validated anatomy. */
        private fun previewGuidance(imageRevision: String, resolution: PixelResolution, side: ProfileSide, facing: ProfileFacing): ProfileAssistancePolicy {
            val anchors = mapOf(
                Landmarks.GLABELLA to Point2(.58, .23), Landmarks.NASION to Point2(.61, .30),
                Landmarks.SUPRATIP to Point2(.72, .39), Landmarks.COLUMELLA to Point2(.70, .46),
                Landmarks.SUBNASALE to Point2(.64, .50), Landmarks.LABRALE_SUPERIUS to Point2(.67, .56),
                Landmarks.LABRALE_INFERIUS to Point2(.66, .62), Landmarks.SUBLABIALE to Point2(.60, .69),
                Landmarks.POGONION to Point2(.65, .75), Landmarks.RAMUS_REFERENCE to Point2(.35, .60),
                ProfileCatalog.visibleGonion to Point2(.39, .79), Landmarks.MANDIBULAR_BORDER_REFERENCE to Point2(.54, .82),
            ).mapValues { (_, p) -> if (facing == ProfileFacing.LEFT) Point2(1 - p.x, p.y) else p }
            val halfX = .09 * resolution.shortEdge / resolution.width
            val halfY = .09 * resolution.shortEdge / resolution.height
            return ProfileAssistancePolicy(PROFILE_PREVIEW_POLICY_VERSION,
                "Illustrative local preview regions; not empirically validated and not permitted for measurements or scores",
                imageRevision, side, anchors.mapValues { (_, p) ->
                    ProfileZone(p.x - halfX, p.x + halfX, p.y - halfY, p.y + halfY, p, .09)
                })
        }
        fun preview(imageRevision: String, resolution: PixelResolution, side: ProfileSide, facing: ProfileFacing): ProfileAssistSession {
            val input = ProfileInput(imageRevision, resolution, null, ProfileOrigin.DEMO_LOCAL, side, true,
                "explicit-user-side-v1", null, null, Point2(.5, .5), null, "unavailable", null, emptyMap())
            return ProfileAssistSession(ProfileRevision.start(input), previewGuidance(imageRevision, resolution, side, facing), facing)
        }
    }
}

/** Uniform fit and inverse on the exact standardized, unmirrored image coordinates. */
internal data class ProfileImageFit(val left: Double, val top: Double, val width: Double, val height: Double) {
    fun project(point: Point2) = Point2(left + point.x * width, top + point.y * height)
    fun unproject(point: Point2) = Point2((point.x - left) / width, (point.y - top) / height)
    companion object {
        fun fit(resolution: PixelResolution, viewportWidth: Double, viewportHeight: Double): ProfileImageFit {
            require(viewportWidth.isFinite() && viewportHeight.isFinite() && viewportWidth > 0 && viewportHeight > 0)
            val scale = minOf(viewportWidth / resolution.width, viewportHeight / resolution.height)
            val width = resolution.width * scale
            val height = resolution.height * scale
            return ProfileImageFit((viewportWidth - width) / 2, (viewportHeight - height) / 2, width, height)
        }
    }
}
