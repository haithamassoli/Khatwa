package com.haithamassoli.khatwa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Shared pickup/dropoff points voted on by other users, served by the Khatwa proxy (server/, `/points/nearby` and `/points/feedback`).
 * Everything is a no-op when the proxy isn't configured. Network errors are thrown as IOException.
 */
class CommunityPoints(private val proxyUrl: String, private val installId: String) {
    val enabled get() = proxyUrl.isNotEmpty()

    /** Community points within [radiusM] of [center], nearest first. The server may clamp the radius (see server/README.md). */
    suspend fun nearby(center: Pt, radiusM: Int): List<Candidate> {
        if (!enabled) return emptyList()
        val url = String.format(Locale.US, "%s/points/nearby?lat=%.6f&lng=%.6f&radius=%d", base, center.lat, center.lng, radiusM)
        return parseNearby(request(url, null))
    }

    /** Post-trip 👍/👎 for a point. Direct points (the exact start/destination) aren't shared. */
    suspend fun feedback(c: Candidate, good: Boolean) {
        if (!enabled || c.isDirect) return
        val body = JSONObject().put("lat", c.pt.lat).put("lng", c.pt.lng).put("use", c.use.name).put("good", good)
        if (!c.auto && c.name.isNotBlank()) body.put("name", c.name) // auto names are relative ("120 م شمال"), useless to others
        request("$base/points/feedback", body)
    }

    private val base get() = proxyUrl.trimEnd('/')

    private suspend fun request(url: String, body: JSONObject?): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty(Google.INSTALL_HEADER, installId)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                throw IOException(msg ?: "HTTP $code")
            }
            text
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Parses GET /points/nearby. Points the community voted down (same rule as Store.vote) are dropped. */
        fun parseNearby(json: String): List<Candidate> {
            val arr = JSONObject(json).optJSONArray("points") ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val up = o.optInt("up")
                val down = o.optInt("down")
                if (down >= 2 && down > up) return@mapNotNull null
                Candidate(
                    id = "community-" + o.getString("id"),
                    pt = Pt(o.getDouble("lat"), o.getDouble("lng")),
                    name = o.optString("name").ifBlank { "نقطة من المستخدمين" },
                    use = runCatching { PointUse.valueOf(o.optString("use")) }.getOrDefault(PointUse.BOTH),
                    verification = verification(up, down),
                    auto = false,
                    upVotes = up,
                    downVotes = down,
                )
            }
        }

        /** Trusted once several people liked it and likes clearly outnumber dislikes. */
        fun verification(up: Int, down: Int): Verification =
            if (up >= 3 && up >= 2 * down) Verification.VERIFIED else Verification.UNVERIFIED
    }
}
