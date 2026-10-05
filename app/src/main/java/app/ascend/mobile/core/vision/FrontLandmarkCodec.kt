package app.ascend.mobile.core.vision

import app.ascend.mobile.core.geometry.PixelResolution
import app.ascend.mobile.core.geometry.PoseDeviation
import java.io.*
import java.security.MessageDigest

fun frontImageSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Bounded, versioned scan payload. Encryption is provided by the existing SQLCipher database. */
object FrontLandmarkCodec {
    /** Only a derived landmark cache can be discarded; completed numerical history stays strict. */
    fun decodeCached(bytes: ByteArray): FrontLandmarkSnapshot? = try { decode(bytes) }
        catch (_: IllegalArgumentException) { null }
    fun encode(snapshot: FrontLandmarkSnapshot): ByteArray = ByteArrayOutputStream().use { buffer ->
        DataOutputStream(buffer).use { output ->
            output.writeInt(1)
            output.writeUTF(snapshot.modelVersion); output.writeUTF(snapshot.providerVersion)
            output.writeUTF(snapshot.pipelineVersion); output.writeUTF(snapshot.policyVersion)
            output.writeUTF(snapshot.sourceImageSha256)
            output.writeInt(snapshot.resolution.width); output.writeInt(snapshot.resolution.height)
            output.writeDouble(snapshot.pose.yawDegrees); output.writeDouble(snapshot.pose.pitchDegrees); output.writeDouble(snapshot.pose.rollDegrees)
            output.writeInt(snapshot.points.size)
            snapshot.points.forEach { output.writeDouble(it.x); output.writeDouble(it.y); output.writeDouble(it.z) }
        }
        buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): FrontLandmarkSnapshot {
        require(bytes.size in 1..32_000)
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == 1)
                val model = input.readUTF(); val provider = input.readUTF(); val pipeline = input.readUTF(); val policy = input.readUTF()
                val hash = input.readUTF()
                val resolution = PixelResolution(input.readInt(), input.readInt())
                require(resolution.width <= 4096 && resolution.height <= 4096)
                val pose = PoseDeviation(input.readDouble(), input.readDouble(), input.readDouble())
                require(input.readInt() == FRONT_MESH_SIZE)
                val points = List(FRONT_MESH_SIZE) { MeshPoint(input.readDouble(), input.readDouble(), input.readDouble()) }
                require(input.available() == 0)
                return FrontLandmarkSnapshot(resolution, points, pose, hash, model, provider, pipeline, policy)
            }
        } catch (failure: IOException) { throw IllegalArgumentException("Invalid front landmark payload", failure) }
    }
}
