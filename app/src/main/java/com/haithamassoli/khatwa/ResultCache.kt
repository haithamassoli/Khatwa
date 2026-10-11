package com.haithamassoli.khatwa

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// SearchResult <-> JSON for Store.cacheGet/cachePut. Same trip, same hour, same kind of day = same answer.
object ResultCache {
    const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private val AMMAN = ZoneId.of("Asia/Amman")

    /** Origin/destination on a ~100 m grid, walk limits, objective, hour of day and weekday vs weekend (Fri/Sat). */
    fun key(input: SearchInput, nowMs: Long = System.currentTimeMillis()): String {
        val t = Instant.ofEpochMilli(nowMs).atZone(AMMAN)
        val weekend = t.dayOfWeek == DayOfWeek.FRIDAY || t.dayOfWeek == DayOfWeek.SATURDAY
        return String.format(Locale.US, "search|%.3f,%.3f|%.3f,%.3f|%d|%d|%s|%d|%s",
            input.origin.lat, input.origin.lng, input.destination.lat, input.destination.lng,
            input.maxWalkStartS, input.maxWalkEndS, input.objective.name, t.hour, if (weekend) "we" else "wd")
    }

    fun encode(r: SearchResult): String = JSONObject()
        .put("input", JSONObject().put("origin", pt(r.input.origin)).put("destination", pt(r.input.destination))
            .put("maxWalkStartS", r.input.maxWalkStartS).put("maxWalkEndS", r.input.maxWalkEndS).put("objective", r.input.objective.name))
        .put("baseline", r.baseline?.let(::option) ?: JSONObject.NULL)
        .put("alternatives", JSONArray(r.alternatives.map(::option)))
        .put("warnings", JSONArray(r.warnings))
        .put("trafficAware", r.trafficAware)
        .put("calculatedAtMs", r.calculatedAtMs)
        .put("pairsChecked", r.pairsChecked)
        .put("pairsFailed", r.pairsFailed)
        .put("elementsUsed", r.elementsUsed)
        .put("droppedAfterCheck", r.droppedAfterCheck)
        .put("baselinePolyline", Google.encodePolyline(r.baselinePolyline))
        .put("night", r.night)
        .toString()

    /** Null when [s] is not a valid entry (e.g. written by an older version). */
    fun decode(s: String, requestId: Long = 0): SearchResult? = runCatching {
        val o = JSONObject(s)
        val i = o.getJSONObject("input")
        val alts = o.getJSONArray("alternatives")
        val warnings = o.getJSONArray("warnings")
        SearchResult(
            requestId = requestId,
            input = SearchInput(pt(i.getJSONObject("origin")), pt(i.getJSONObject("destination")),
                i.getInt("maxWalkStartS"), i.getInt("maxWalkEndS"), Objective.valueOf(i.getString("objective"))),
            baseline = if (o.isNull("baseline")) null else option(o.getJSONObject("baseline")),
            alternatives = (0 until alts.length()).map { option(alts.getJSONObject(it)) },
            warnings = (0 until warnings.length()).map { warnings.getString(it) },
            trafficAware = o.getBoolean("trafficAware"),
            calculatedAtMs = o.getLong("calculatedAtMs"),
            pairsChecked = o.getInt("pairsChecked"),
            pairsFailed = o.getInt("pairsFailed"),
            elementsUsed = o.getInt("elementsUsed"),
            droppedAfterCheck = o.optInt("droppedAfterCheck"),
            baselinePolyline = Google.decodePolyline(o.optString("baselinePolyline")),
            night = o.optBoolean("night"),
        )
    }.getOrNull()

    private fun pt(p: Pt) = JSONObject().put("lat", p.lat).put("lng", p.lng)
    private fun pt(o: JSONObject) = Pt(o.getDouble("lat"), o.getDouble("lng"))

    private fun option(o: RouteOption) = JSONObject()
        .put("pickup", candidate(o.pickup)).put("dropoff", candidate(o.dropoff))
        .put("walkStartS", o.walkStartS).put("driveS", o.driveS).put("driveM", o.driveM).put("walkEndS", o.walkEndS)
        .put("verified", o.verified).put("estimatedDriveS", o.estimatedDriveS ?: JSONObject.NULL)
        .put("drivePolyline", Google.encodePolyline(o.drivePolyline))
        .put("climbStartM", o.climbStartM).put("climbEndM", o.climbEndM)
        .put("fareJod", o.fareJod ?: JSONObject.NULL)

    private fun option(o: JSONObject) = RouteOption(
        pickup = candidate(o.getJSONObject("pickup")),
        dropoff = candidate(o.getJSONObject("dropoff")),
        walkStartS = o.getInt("walkStartS"),
        driveS = o.getInt("driveS"),
        driveM = o.getInt("driveM"),
        walkEndS = o.getInt("walkEndS"),
        verified = o.optBoolean("verified"),
        estimatedDriveS = if (o.isNull("estimatedDriveS")) null else o.getInt("estimatedDriveS"),
        drivePolyline = Google.decodePolyline(o.optString("drivePolyline")),
        climbStartM = o.optInt("climbStartM"),
        climbEndM = o.optInt("climbEndM"),
        fareJod = if (o.isNull("fareJod")) null else o.getDouble("fareJod"),
    )

    private fun candidate(c: Candidate) = JSONObject()
        .put("id", c.id).put("pt", pt(c.pt)).put("name", c.name).put("roadSide", c.roadSide).put("landmark", c.landmark)
        .put("use", c.use.name).put("verification", c.verification.name).put("enabled", c.enabled).put("auto", c.auto)
        .put("isDirect", c.isDirect).put("street", c.street).put("upVotes", c.upVotes).put("downVotes", c.downVotes)

    private fun candidate(o: JSONObject) = Candidate(
        id = o.getString("id"),
        pt = pt(o.getJSONObject("pt")),
        name = o.getString("name"),
        roadSide = o.optString("roadSide"),
        landmark = o.optString("landmark"),
        use = PointUse.valueOf(o.getString("use")),
        verification = Verification.valueOf(o.getString("verification")),
        enabled = o.optBoolean("enabled", true),
        auto = o.optBoolean("auto"),
        isDirect = o.optBoolean("isDirect"),
        street = o.optString("street"),
        upVotes = o.optInt("upVotes"),
        downVotes = o.optInt("downVotes"),
    )
}
