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

// OWNER: data agent. Google Maps Platform REST calls (Routes v2, Roads, Places New, Geocoding).
class Google(private val key: String) {
    /** WALK matrix, result[i][j] = origins[i] -> destinations[j]. */
    suspend fun walkMatrix(origins: List<Pt>, destinations: List<Pt>): List<List<Leg>> =
        matrix(origins, destinations, JSONObject().put("travelMode", "WALK"))

    /** DRIVE matrix. Returns (legs, trafficAware). Falls back to non-traffic routing if traffic call fails. */
    suspend fun driveMatrix(origins: List<Pt>, destinations: List<Pt>, departAtMs: Long?): Pair<List<List<Leg>>, Boolean> {
        val traffic = JSONObject().put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_AWARE")
        if (departAtMs != null && departAtMs > System.currentTimeMillis() + 30_000) { // near-now: omit, Google defaults to now and rejects past times
            traffic.put("departureTime", Instant.ofEpochMilli(departAtMs).toString())
        }
        return try {
            matrix(origins, destinations, traffic) to true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            matrix(origins, destinations, JSONObject().put("travelMode", "DRIVE").put("routingPreference", "TRAFFIC_UNAWARE")) to false
        }
    }

    /** Single detailed route polyline + duration (seconds). */
    suspend fun route(from: Pt, to: Pt, walk: Boolean): Pair<List<Pt>, Int?> {
        val body = JSONObject()
            .put("origin", waypoint(from))
            .put("destination", waypoint(to))
            .put("travelMode", if (walk) "WALK" else "DRIVE")
        if (!walk) body.put("routingPreference", "TRAFFIC_AWARE")
        val res = JSONObject(post(ROUTES_URL, body, "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline"))
        val r = res.optJSONArray("routes")?.optJSONObject(0) ?: return emptyList<Pt>() to null
        val poly = r.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
        return decodePolyline(poly) to seconds(r.optString("duration", "0s"))
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
    }
}
