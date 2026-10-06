package app.ascend.mobile.core.profile

import app.ascend.mobile.core.geometry.*
import app.ascend.mobile.core.model.ProfileSide
import kotlinx.serialization.json.*

/** Versioned encrypted-payload codec. Restoration replays every bound and provenance check. */
internal object ProfileAssistCodec {
    private const val MAX_BYTES = 1_000_000
    fun encode(session: ProfileAssistSession): ByteArray = buildJsonObject {
        put("formatVersion", 1); put("facing", session.facing.name); put("revisionToken", session.revisionToken)
        put("source", source(session.revision.original)); put("policy", policy(session.policy))
        put("audit", JsonArray(session.revision.audit.map(::action)))
    }.toString().toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }

    fun cached(bytes: ByteArray): ProfileAssistSession? = try { decode(bytes) } catch (_: Exception) { null }
    fun decode(bytes: ByteArray): ProfileAssistSession {
        require(bytes.size in 1..MAX_BYTES)
        val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(root.keys == setOf("formatVersion", "facing", "revisionToken", "source", "policy", "audit"))
        require(root.int("formatVersion") == 1)
        val original = readSource(root.getValue("source").jsonObject)
        val guidance = readPolicy(root.getValue("policy").jsonObject)
        var revision = ProfileRevision.start(original)
        val audit = root.getValue("audit").jsonArray
        require(audit.size <= 256)
        audit.forEach { element ->
            val entry = element.jsonObject
            val current = entry.getValue("current").jsonObject
            val next = revision.confirm(LandmarkId(entry.string("pointId")), readPoint(current.getValue("point")), guidance,
                entry.string("imageRevision"), entry.long("revision") - 1, entry.string("methodVersion"), entry.long("atEpochMillis"))
            require(action(next.audit.last()) == entry) { "Invalid profile correction provenance" }
            revision = next
        }
        val session = ProfileAssistSession(revision, guidance, ProfileFacing.valueOf(root.string("facing")), root.long("revisionToken"))
        require(source(original) == root.getValue("source") && policy(guidance) == root.getValue("policy"))
        return session
    }

    private fun xy(point: Point2) = buildJsonArray { add(point.x); add(point.y) }
    private fun readPoint(element: JsonElement): Point2 {
        val values = element.jsonArray; require(values.size == 2)
        return Point2(values[0].jsonPrimitive.double, values[1].jsonPrimitive.double)
    }
    private fun observation(point: ProfilePoint?) = point?.let { buildJsonObject {
        put("point", xy(it.point)); put("confidence", it.confidence?.let(::JsonPrimitive) ?: JsonNull)
        put("source", it.source.name); put("confirmed", it.confirmed)
    } } ?: JsonNull
    private fun readObservation(element: JsonElement): ProfilePoint {
        val value = element.jsonObject
        return ProfilePoint(readPoint(value.getValue("point")), value.doubleOrNull("confidence"),
            ProfilePointSource.valueOf(value.string("source")), value.getValue("confirmed").jsonPrimitive.boolean)
    }
    private fun source(input: ProfileInput) = buildJsonObject {
        put("imageRevision", input.imageRevision); put("width", input.resolution.width); put("height", input.resolution.height)
        put("faceShortEdgePixels", input.faceShortEdgePixels?.let(::JsonPrimitive) ?: JsonNull)
        put("origin", input.origin.name); put("side", input.side?.name?.let(::JsonPrimitive) ?: JsonNull)
        put("orientationConfirmed", input.orientationConfirmed)
        put("orientationMethodVersion", input.orientationMethodVersion?.let(::JsonPrimitive) ?: JsonNull)
        put("faceCount", input.faceCount?.let(::JsonPrimitive) ?: JsonNull)
        put("pose", input.residualPose?.let { buildJsonArray { add(it.yawDegrees); add(it.pitchDegrees); add(it.rollDegrees) } } ?: JsonNull)
        put("rollOrigin", xy(input.rollOrigin)); put("overallConfidence", input.overallConfidence?.let(::JsonPrimitive) ?: JsonNull)
        put("confidenceMethodVersion", input.confidenceMethodVersion)
        put("provider", input.provider?.let { buildJsonObject {
            put("extractorVersion", it.extractorVersion); put("modelVersion", it.modelVersion?.let(::JsonPrimitive) ?: JsonNull)
            put("modelSha256", it.modelSha256?.let(::JsonPrimitive) ?: JsonNull)
        } } ?: JsonNull)
        put("points", buildJsonObject { input.points.toSortedMap(compareBy { it.value }).forEach { (id, p) -> put(id.value, observation(p)) } })
    }
    private fun readSource(value: JsonObject): ProfileInput {
        val provider = value.getValue("provider").takeUnless { it == JsonNull }?.jsonObject?.let {
            ProfileProvider(it.string("extractorVersion"), it.nullString("modelVersion"), it.nullString("modelSha256")) }
        val pose = value.getValue("pose").takeUnless { it == JsonNull }?.jsonArray?.let {
            require(it.size == 3); PoseDeviation(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double, it[2].jsonPrimitive.double) }
        return ProfileInput(value.string("imageRevision"), PixelResolution(value.int("width"), value.int("height")),
            value.intOrNull("faceShortEdgePixels"), ProfileOrigin.valueOf(value.string("origin")),
            value.nullString("side")?.let(ProfileSide::valueOf), value.getValue("orientationConfirmed").jsonPrimitive.boolean,
            value.nullString("orientationMethodVersion"), value.intOrNull("faceCount"), pose, readPoint(value.getValue("rollOrigin")),
            value.doubleOrNull("overallConfidence"), value.string("confidenceMethodVersion"), provider,
            value.getValue("points").jsonObject.map { (id, point) -> LandmarkId(id) to readObservation(point) }.toMap())
    }
    private fun policy(value: ProfileAssistancePolicy) = buildJsonObject {
        put("version", value.version); put("evidence", value.evidence); put("imageRevision", value.imageRevision); put("side", value.side.name)
        put("zones", buildJsonObject { value.zones.toSortedMap(compareBy { it.value }).forEach { (id, zone) -> put(id.value, buildJsonObject {
            put("minimumX", zone.minimumX); put("maximumX", zone.maximumX); put("minimumY", zone.minimumY); put("maximumY", zone.maximumY)
            put("anchor", xy(zone.missingPointAnchor)); put("maximumShortEdgeDisplacement", zone.maximumShortEdgeDisplacement)
        }) } })
    }
    private fun readPolicy(value: JsonObject) = ProfileAssistancePolicy(value.string("version"), value.string("evidence"),
        value.string("imageRevision"), ProfileSide.valueOf(value.string("side")), value.getValue("zones").jsonObject.map { (id, element) ->
            val zone = element.jsonObject
            LandmarkId(id) to ProfileZone(zone.double("minimumX"), zone.double("maximumX"), zone.double("minimumY"), zone.double("maximumY"),
                readPoint(zone.getValue("anchor")), zone.double("maximumShortEdgeDisplacement")) }.toMap())
    private fun action(value: ProfileAction) = buildJsonObject {
        put("revision", value.revision); put("imageRevision", value.imageRevision); put("pointId", value.pointId.value)
        put("originalProposal", observation(value.originalProposal)); put("anchor", xy(value.anchor))
        put("previous", observation(value.previous)); put("current", observation(value.current)); put("kind", value.kind.name)
        put("policyVersion", value.policyVersion); put("policyEvidence", value.policyEvidence)
        put("methodVersion", value.methodVersion); put("atEpochMillis", value.atEpochMillis)
    }
    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.nullString(key: String) = getValue(key).jsonPrimitive.contentOrNull
    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long
    private fun JsonObject.double(key: String) = getValue(key).jsonPrimitive.double
    private fun JsonObject.doubleOrNull(key: String) = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.double
    private fun JsonObject.intOrNull(key: String) = getValue(key).takeUnless { it == JsonNull }?.jsonPrimitive?.int
}
