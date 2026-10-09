package app.ascend.mobile.core.visual

import app.ascend.mobile.core.front.FixtureOrigin
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.*

/** Ephemeral pixels, copied at ingress. No filesystem, upload, serialization or pixel-bearing toString. */
internal class VisualImage private constructor(
    val width: Int, val height: Int, val imageRevision: String, val origin: FixtureOrigin,
    private val pixels: IntArray,
) : AutoCloseable {
    private var closed = false
    val sha256: String = digest {
        text("wp11-oriented-srgb-argb-v1")
        int(width); int(height)
        pixels.forEach(::int)
    }

    fun luminance(x: Int, y: Int): Double {
        check(!closed) { "Image released" }
        require(x in 0 until width && y in 0 until height)
        val c = pixels[y * width + x]
        return (0.2126 * (c ushr 16 and 255) + 0.7152 * (c ushr 8 and 255) + 0.0722 * (c and 255)) / 255.0
    }
    fun requireOpen() { check(!closed) { "Image released" } }
    override fun close() { pixels.fill(0); closed = true }

    companion object {
        const val MAX_PIXELS = 16_777_216
        fun fromArgb(width: Int, height: Int, revision: String, origin: FixtureOrigin, pixels: IntArray): VisualImage {
            require(width in 2..4096 && height in 2..4096 && width.toLong() * height <= MAX_PIXELS)
            require(pixels.size.toLong() == width.toLong() * height && revision.isNotBlank())
            require(pixels.all { it ushr 24 == 255 }) { "Opaque standardized input required" }
            return VisualImage(width, height, revision, origin, pixels.copyOf())
        }
    }
}

/** Semantic masks must come from an explicit provider, never a darker/lighter skin threshold. */
internal class VisualMask(
    val feature: VisualFeature, val imageRevision: String, val imageSha256: String,
    val width: Int, val height: Int, val providerVersion: String, val definitionVersion: String,
    val confidence: Double?, foreground: BooleanArray,
) : AutoCloseable {
    private val foreground: BooleanArray
    private var closed = false
    val sha256: String
    init {
        require(width in 2..4096 && height in 2..4096 && width.toLong() * height <= VisualImage.MAX_PIXELS)
        require(foreground.size.toLong() == width.toLong() * height)
        require(listOf(imageRevision, providerVersion, definitionVersion).all(String::isNotBlank))
        require(imageSha256.matches(Regex("[a-f0-9]{64}")))
        require(confidence == null || (confidence.isFinite() && confidence in 0.0..1.0))
        this.foreground = foreground.copyOf()
        sha256 = digest {
            text("wp11-semantic-mask-v1"); text(feature.name); text(imageSha256)
            int(width); int(height); this@VisualMask.foreground.forEach { int(if (it) 1 else 0) }
        }
    }
    fun contains(x: Int, y: Int): Boolean {
        check(!closed) { "Mask released" }
        require(x in 0 until width && y in 0 until height)
        return foreground[y * width + x]
    }
    override fun close() { foreground.fill(false); closed = true }
}

internal class VisualRoi(
    val revision: String, val sourceShortEdgePixels: Int, val edge: Int,
    val luminance: DoubleArray, val mask: BooleanArray?,
) : AutoCloseable {
    override fun close() { luminance.fill(0.0); mask?.fill(false) }
}

internal sealed interface VisualRoiResult {
    data class Ready(val roi: VisualRoi) : VisualRoiResult
    data class Failed(val reason: VisualFailure) : VisualRoiResult
}

internal object VisualRoiStandardizer {
    fun extract(image: VisualImage, proposal: VisualRoiProposal, policy: VisualPolicy, mask: VisualMask? = null): VisualRoiResult {
        image.requireOpen()
        fun fail(reason: VisualFailure) = VisualRoiResult.Failed(reason)
        if (proposal.imageRevision != image.imageRevision || proposal.imageSha256 != image.sha256) return fail(VisualFailure.STALE_SOURCE)
        if (proposal.visibility != VisualVisibility.VISIBLE) return fail(VisualFailure.ROI_UNRELIABLE)
        if (mask != null && (mask.imageRevision != image.imageRevision || mask.imageSha256 != image.sha256)) return fail(VisualFailure.STALE_SOURCE)
        if (mask != null && (mask.width != image.width || mask.height != image.height || mask.feature != proposal.feature ||
                mask.definitionVersion != proposal.definitionVersion)) return fail(VisualFailure.MASK_MISMATCH)
        val ax = proposal.across.x * (image.width - 1); val ay = proposal.across.y * (image.height - 1)
        val dx = proposal.down.x * (image.width - 1); val dy = proposal.down.y * (image.height - 1)
        val acrossLength = hypot(ax, ay); val downLength = hypot(dx, dy)
        // Reject shear, degenerate or mirrored axes. Do not clip/reconstruct invisible anatomy.
        if (acrossLength <= 0 || downLength <= 0 || ax * dy - ay * dx <= 0 ||
            abs(ax * dx + ay * dy) / (acrossLength * downLength) > 1e-6) return fail(VisualFailure.ROI_UNRELIABLE)
        val corners = listOf(proposal.imagePoint(0.0, 0.0), proposal.imagePoint(1.0, 0.0),
            proposal.imagePoint(0.0, 1.0), proposal.imagePoint(1.0, 1.0))
        if (corners.any { it.x !in 0.0..1.0 || it.y !in 0.0..1.0 }) return fail(VisualFailure.ROI_OUTSIDE_IMAGE)
        val shortEdge = floor(min(acrossLength, downLength)).toInt()
        if (shortEdge < policy.minimumSourceShortEdgePixels) return fail(VisualFailure.INSUFFICIENT_RESOLUTION)
        val edge = policy.gridEdge
        val luminance = DoubleArray(edge * edge)
        val sampledMask = mask?.let { BooleanArray(edge * edge) }
        try { for (v in 0 until edge) for (u in 0 until edge) {
            val p = proposal.imagePoint((u + 0.5) / edge, (v + 0.5) / edge)
            val x = p.x * (image.width - 1); val y = p.y * (image.height - 1)
            val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
            val x1 = min(x0 + 1, image.width - 1); val y1 = min(y0 + 1, image.height - 1)
            val fx = x - x0; val fy = y - y0
            luminance[v * edge + u] = (1 - fy) * ((1 - fx) * image.luminance(x0, y0) + fx * image.luminance(x1, y0)) +
                fy * ((1 - fx) * image.luminance(x0, y1) + fx * image.luminance(x1, y1))
            sampledMask?.set(v * edge + u, requireNotNull(mask).contains(x.roundToInt(), y.roundToInt()))
        } } catch (failure: Throwable) {
            luminance.fill(0.0); sampledMask?.fill(false)
            throw failure
        }
        val revision = digest {
            text(VISUAL_CONTRACT_VERSION); text(image.imageRevision); text(image.sha256)
            text(proposal.feature.name); text(proposal.definitionVersion); text(proposal.providerVersion)
            listOf(proposal.origin.x, proposal.origin.y, proposal.across.x, proposal.across.y,
                proposal.down.x, proposal.down.y).forEach { text(it.toString()) }
            text(policy.version); text(policy.evidence); int(edge); int(policy.minimumSourceShortEdgePixels)
            text(policy.minimumConfidence.toString()); text(policy.minimumRelativeContrast.toString())
            text(mask?.sha256 ?: "no-mask")
            text(mask?.providerVersion ?: "no-provider")
        }
        return VisualRoiResult.Ready(VisualRoi(revision, shortEdge, edge, luminance, sampledMask))
    }
}

internal class VisualDigest {
    private val digest = MessageDigest.getInstance("SHA-256")
    private val buffer = ByteBuffer.allocate(4)
    fun int(value: Int) { buffer.clear(); buffer.putInt(value); digest.update(buffer.array()) }
    fun text(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); int(bytes.size); digest.update(bytes) }
    fun finish() = digest.digest().joinToString("") { "%02x".format(it) }
}
internal fun digest(content: VisualDigest.() -> Unit): String = VisualDigest().apply(content).finish()
