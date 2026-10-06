package app.ascend.mobile.storage

import app.ascend.mobile.core.geometry.PixelResolution
import app.ascend.mobile.core.model.ProfileSide

/** Caller releases/zeroes bytes; source identity and image are read in the same serialized operation. */
internal data class ProfilePhoto(val bytes: ByteArray, val imageRevision: String, val resolution: PixelResolution,
    val side: ProfileSide?, val readOnly: Boolean)
