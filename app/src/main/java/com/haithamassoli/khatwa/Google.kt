package com.haithamassoli.khatwa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

// OWNER: data agent. Google Maps Platform REST calls (Routes v2, Roads, Places New, Geocoding, Elevation).
class Google(private val key: String) {
    /** WALK matrix, result[i][j] = origins[i] -> destinations[j]. */
    suspend fun walkMatrix(origins: List<Pt>, destinations: List<Pt>): List<List<Leg>> =
        matrix(origins, destinations, JSONObject().put("travelMode", "WALK"))

    /** DRIVE matrix. Returns (legs, trafficAware). Falls back to non-traffic routing if traffic call fails. */
    suspend fun driveMatrix(origins: List<Pt>, destinations: List<Pt>, departAtMs: Long?): Pair<List<List<Leg>>, Boolean> {
        val traffic = JSONObject().put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_AWARE")
        departure(departAtMs)?.let { traffic.put("departureTime", it) }
        return try {
            matrix(origins, destinations, traffic) to true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            matrix(origins, destinations, JSONObject().put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_UNAWARE")) to false
        }
    }

    /** Single detailed route polyline + duration (seconds). */
    suspend fun route(from: Pt, to: Pt, walk: Boolean): Pair<List<Pt>, Int?> = routeLeg(from, to, walk).let { it.points to it.durationS }

    /** Detailed route with distance. DRIVE is TRAFFIC_AWARE and departs at [departAtMs] when that's in the future. */
    suspend fun routeLeg(from: Pt, to: Pt, walk: Boolean, departAtMs: Long? = null): RouteLeg {
        val body = JSONObject()
            .put("origin", waypoint(from))
            .put("destination", waypoint(to))
            .put("travelMode", if (walk) "WALK" else "DRIVE")
        if (!walk) {
            body.put("routingPreference", "TRAFFIC_AWARE")
            departure(departAtMs)?.let { body.put("departureTime", it) }
        }
        val res = JSONObject(post(ROUTES_URL, body, "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"))
        val r = res.optJSONArray("routes")?.optJSONObject(0) ?: return RouteLeg(emptyList(), null, null)
        val poly = r.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
        return RouteLeg(decodePolyline(poly), seconds(r.optString("duration", "0s")),
            if (r.has("distanceMeters")) r.getInt("distanceMeters") else null)
    }

    /** Roads API nearestRoads; may return several points per input (both directions). */
    suspend fun nearestRoads(points: List<Pt>): List<Pt> {
        if (points.isEmpty()) return emptyList()
        require(points.size <= 100) { "nearestRoads: max 100 points" }
        val q = points.joinToString("|") { "${it.lat},${it.lng}" }
        val res = JSONObject(get("https://roads.googleapis.com/v1/nearestRoads?points=${enc(q)}&key=$key"))
        val arr = res.optJSONArray("snappedPoints") ?: return emptyList()
        return (0 until arr.length()).map { latLng(arr.getJSONObject(it).getJSONObject("location")) }
    }

    suspend fun searchPlaces(query: String, near: Pt?): List<PlaceHit> {
        val c = near ?: AMMAN
        val body = JSONObject()
            .put("textQuery", query)
            .put("languageCode", "ar")
            .put("regionCode", "JO")
            .put("locationBias", JSONObject().put("circle", JSONObject()
                .put("center", JSONObject().put("latitude", c.lat).put("longitude", c.lng))
                .put("radius", 30000.0)))
        val res = JSONObject(post("https://places.googleapis.com/v1/places:searchText", body,
            "places.displayName,places.formattedAddress,places.location"))
        return places(res)
    }

    /** Places (New) searchNearby: named places within [radiusM] of [pt], nearest first. */
    suspend fun nearbyPlaces(pt: Pt, radiusM: Double = 60.0, max: Int = 3): List<PlaceHit> {
        val body = JSONObject()
            .put("languageCode", "ar")
            .put("regionCode", "JO")
            .put("maxResultCount", max)
            .put("rankPreference", "DISTANCE")
            .put("locationRestriction", JSONObject().put("circle", JSONObject()
                .put("center", JSONObject().put("latitude", pt.lat).put("longitude", pt.lng))
                .put("radius", radiusM)))
        val res = JSONObject(post("https://places.googleapis.com/v1/places:searchNearby", body, "places.displayName,places.location"))
        return places(res).filter { it.name.isNotBlank() }
    }

    /** Name of the street at [pt] (reverse geocode filtered to routes), or null. */
    suspend fun streetName(pt: Pt): String? {
        val res = JSONObject(get("https://maps.googleapis.com/maps/api/geocode/json?latlng=${pt.lat},${pt.lng}&result_type=route&language=ar&key=$key"))
        when (val status = res.optString("status")) {
            "OK" -> Unit
            "ZERO_RESULTS" -> return null
            else -> throw IOException("Geocoding $status: ${res.optString("error_message")}".trimEnd(' ', ':'))
        }
        val results = res.getJSONArray("results")
        for (i in 0 until results.length()) {
            val parts = results.getJSONObject(i).optJSONArray("address_components") ?: continue
            for (k in 0 until parts.length()) {
                val c = parts.getJSONObject(k)
                val types = c.optJSONArray("types") ?: continue
                if ((0 until types.length()).none { types.optString(it) == "route" }) continue
                val name = c.optString("long_name").trim()
                if (name.isNotEmpty() && !PLUS_CODE.matches(name) && name != "Unnamed Road" && "بدون اسم" !in name) return name
            }
        }
        return null
    }

    /** Elevation API, meters above sea level, one value per point, in one request. */
    suspend fun elevations(points: List<Pt>): List<Double> {
        if (points.isEmpty()) return emptyList()
        require(points.size <= 512) { "elevation: max 512 points" }
        val q = points.joinToString("|") { "${it.lat},${it.lng}" }
        val res = JSONObject(get("https://maps.googleapis.com/maps/api/elevation/json?locations=${enc(q)}&key=$key"))
        val status = res.optString("status")
        if (status != "OK") throw IOException("Elevation $status: ${res.optString("error_message")}".trimEnd(' ', ':'))
        val arr = res.getJSONArray("results")
        if (arr.length() != points.size) throw IOException("Elevation: ${arr.length()} of ${points.size} results")
        return (0 until arr.length()).map { arr.getJSONObject(it).getDouble("elevation") }
    }

    suspend fun reverseGeocode(pt: Pt): String? {
        val res = JSONObject(get("https://maps.googleapis.com/maps/api/geocode/json?latlng=${pt.lat},${pt.lng}&language=ar&key=$key"))
        when (val status = res.optString("status")) {
            "OK" -> Unit
            "ZERO_RESULTS" -> return null
            else -> throw IOException("Geocoding $status: ${res.optString("error_message")}".trimEnd(' ', ':'))
        }
        val results = res.getJSONArray("results")
        // Address parts without the country; prefer the first result that isn't built on a plus code ("XW36+H76").
        val names = (0 until results.length()).map { i ->
            results.getJSONObject(i).optString("formatted_address").split("،", ",").map { it.trim() }
                .filter { it.isNotEmpty() && it != "الأردن" && it != "Jordan" }
        }
        val best = names.firstOrNull { p -> p.isNotEmpty() && p.none { PLUS_CODE.matches(it) } }
            ?: names.firstOrNull()?.filterNot { PLUS_CODE.matches(it) }
        return best?.joinToString("، ")?.takeIf { it.isNotEmpty() }
    }

    private fun places(res: JSONObject): List<PlaceHit> {
        val arr = res.optJSONArray("places") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.getJSONObject(i)
            val loc = p.optJSONObject("location") ?: return@mapNotNull null
            PlaceHit(
                name = p.optJSONObject("displayName")?.optString("text").orEmpty(),
                address = p.optString("formattedAddress"),
                pt = latLng(loc),
            )
        }
    }

    private suspend fun matrix(origins: List<Pt>, destinations: List<Pt>, base: JSONObject): List<List<Leg>> {
        if (origins.isEmpty() || destinations.isEmpty()) return origins.map { emptyList() }
        base.put("origins", JSONArray(origins.map { JSONObject().put("waypoint", waypoint(it)) }))
        base.put("destinations", JSONArray(destinations.map { JSONObject().put("waypoint", waypoint(it)) }))
        val arr = JSONArray(post(MATRIX_URL, base, "originIndex,destinationIndex,duration,distanceMeters,status,condition"))
        val out = Array(origins.size) { Array(destinations.size) { Leg(null, null) } }
        for (k in 0 until arr.length()) {
            val e = arr.getJSONObject(k)
            val i = e.optInt("originIndex", 0)
            val j = e.optInt("destinationIndex", 0)
            val code = e.optJSONObject("status")?.optInt("code", 0) ?: 0
            if (e.optString("condition") != "ROUTE_EXISTS" || code != 0) continue
            if (i !in out.indices || j !in out[i].indices) continue
            out[i][j] = Leg(seconds(e.optString("duration", "0s")), if (e.has("distanceMeters")) e.getInt("distanceMeters") else 0)
        }
        return out.map { it.toList() }
    }

    private suspend fun post(url: String, body: JSONObject, fieldMask: String): String = http(url) {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("X-Goog-Api-Key", key)
        setRequestProperty("X-Goog-FieldMask", fieldMask)
        outputStream.use { it.write(body.toString().toByteArray()) }
    }

    private suspend fun get(url: String): String = http(url) {}

    private suspend fun http(url: String, setup: HttpURLConnection.() -> Unit): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setup()
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code${errorMessage(text)?.let { ": $it" } ?: ""}")
            text
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val MATRIX_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"
        private const val ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private val AMMAN = Pt(31.9539, 35.9106)
        private val PLUS_CODE = Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{0,3}.*")

        private fun waypoint(p: Pt) = JSONObject().put("location", JSONObject().put("latLng",
            JSONObject().put("latitude", p.lat).put("longitude", p.lng)))

        private fun latLng(o: JSONObject) = Pt(o.getDouble("latitude"), o.getDouble("longitude"))

        /** Google defaults to now and rejects past times, so near-now departures are omitted. */
        private fun departure(ms: Long?): String? =
            if (ms != null && ms > System.currentTimeMillis() + 30_000) Instant.ofEpochMilli(ms).toString() else null

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** "898s" -> 898. */
        private fun seconds(s: String): Int? = s.removeSuffix("s").toDoubleOrNull()?.toInt()

        /** Google errors come as {"error":{...}} or, for Routes matrix, [{"error":{...}}]. */
        private fun errorMessage(body: String): String? = runCatching {
            val t = body.trim()
            val o = if (t.startsWith("[")) JSONArray(t).getJSONObject(0) else JSONObject(t)
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() } ?: o.optString("error_message").takeIf { it.isNotEmpty() }
        }.getOrNull()

        /** Standard Google encoded polyline algorithm (1e5 precision). */
        fun decodePolyline(s: String): List<Pt> {
            val out = ArrayList<Pt>()
            var i = 0
            var lat = 0
            var lng = 0
            fun next(): Int {
                var result = 0
                var shift = 0
                var b: Int
                do {
                    b = s[i++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                return if (result and 1 != 0) (result shr 1).inv() else result shr 1
            }
            while (i < s.length) {
                lat += next()
                lng += next()
                out += Pt(lat / 1e5, lng / 1e5)
            }
            return out
        }

        fun encodePolyline(pts: List<Pt>): String {
            val sb = StringBuilder()
            var lat = 0
            var lng = 0
            fun put(v: Int) {
                var x = if (v < 0) (v shl 1).inv() else v shl 1
                while (x >= 0x20) {
                    sb.append(((0x20 or (x and 0x1f)) + 63).toChar())
                    x = x ushr 5
                }
                sb.append((x + 63).toChar())
            }
            for (p in pts) {
                val a = Math.round(p.lat * 1e5).toInt()
                val b = Math.round(p.lng * 1e5).toInt()
                put(a - lat)
                put(b - lng)
                lat = a
                lng = b
            }
            return sb.toString()
        }
    }
}

data class RouteLeg(val points: List<Pt>, val durationS: Int?, val distanceM: Int?)
