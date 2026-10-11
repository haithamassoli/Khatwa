package com.haithamassoli.khatwa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** A place received from another app (Google Maps share, geo: link, maps URL). */
data class SharedPlace(val pt: Pt, val name: String)

/**
 * Parsing of shared text / geo URIs / Google Maps URLs. Everything except [resolve] and [followRedirects]
 * is pure (no android.* types) so it runs in JVM unit tests.
 */
object IncomingLinks {

    /** Result of parsing one link: coordinates if present, and a human label / search query if present. */
    data class Parsed(val pt: Pt?, val label: String)

    private val URL_RE = Regex("""https?://[^\s<>"]+""")
    private val LATLNG_RE = Regex("""^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*\+?(-?\d{1,3}(?:\.\d+)?)\s*$""")
    private val LATLNG_IN_TEXT_RE = Regex("""(?<![\d.])(-?\d{1,2}\.\d{3,})\s*,\s*(-?\d{1,3}\.\d{3,})(?![\d.])""")
    private val PIN_RE = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val AT_RE = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val COORD_PARAMS = listOf("q", "query", "ll", "destination", "daddr", "center", "sll")
    private val SHORT_HOSTS = setOf("maps.app.goo.gl", "goo.gl", "g.co")

    /** Labels Google Maps uses for unnamed places; treated as "no name" so the app reverse-geocodes instead. */
    private val GENERIC_LABELS = setOf("dropped pin", "دبوس مثبت", "دبوس تم إسقاطه", "موقع محدد", "my location", "موقعي", "موقعي الحالي")

    fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    fun validPt(lat: Double, lng: Double): Pt? =
        if (lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)) Pt(lat, lng) else null

    /** "31.95,35.91" (optionally with spaces / "+") → Pt. */
    fun parseLatLng(s: String): Pt? {
        val m = LATLNG_RE.find(decode(s).replace('+', ' ')) ?: return null
        return validPt(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    }

    /** Query parameters of a URL or geo URI, decoded. First value wins. */
    fun queryParams(uri: String): Map<String, String> {
        val q = uri.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in q.split('&')) {
            if (part.isEmpty()) continue
            val k = decode(part.substringBefore('='))
            val v = decode(part.substringAfter('=', "").replace("+", "%20"))
            out.putIfAbsent(k, v)
        }
        return out
    }

    /**
     * geo:lat,lng / geo:lat,lng?z=15 / geo:lat,lng?q=lat,lng(Label) / geo:0,0?q=lat,lng(Label) / geo:0,0?q=address.
     * Returns null if [uri] is not a geo URI.
     */
    fun parseGeo(uri: String): Parsed? {
        if (!uri.startsWith("geo:", ignoreCase = true)) return null
        val body = uri.substring(4).removePrefix("//")
        val path = body.substringBefore('?').substringBefore(';')
        val pathPt = path.split(',').takeIf { it.size >= 2 }?.let { (a, b) ->
            val lat = a.trim().toDoubleOrNull(); val lng = b.trim().toDoubleOrNull()
            if (lat != null && lng != null) validPt(lat, lng) else null
        }
        val q = queryParams(uri)["q"]?.trim().orEmpty()
        if (q.isEmpty()) return Parsed(pathPt, "")
        // "lat,lng(Label)" or "lat,lng"
        val label = Regex("""\((.*)\)\s*$""").find(q)?.groupValues?.get(1)?.trim().orEmpty()
        val qPt = parseLatLng(q.substringBefore('('))
        return if (qPt != null) Parsed(qPt, label) else Parsed(pathPt, q)
    }

    fun host(url: String): String =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':').lowercase()

    fun isShortLink(url: String): Boolean {
        val h = host(url)
        if (h == "maps.app.goo.gl") return true
        val path = url.substringAfter(h, "").lowercase()
        return h in SHORT_HOSTS && (path.startsWith("/maps") || path.startsWith("/app"))
    }

    /** Coordinates from a full Google Maps URL. The place pin (!3d..!4d..) wins over the viewport center (@lat,lng). */
    fun coordsFromUrl(rawUrl: String): Pt? {
        val url = unwrap(rawUrl)
        PIN_RE.findAll(url).lastOrNull()?.let { m ->
            validPt(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())?.let { return it }
        }
        val params = queryParams(url)
        for (k in COORD_PARAMS) params[k]?.let { v -> parseLatLng(v.removePrefix("loc:"))?.let { return it } }
        // /maps/place/31.95,35.91 or /maps/search/31.95,+35.91
        Regex("""/maps/(?:place|search|dir/[^/]*)/([^/?@]+)""").find(url)?.let { m ->
            parseLatLng(m.groupValues[1])?.let { return it }
        }
        AT_RE.find(url)?.let { m -> validPt(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())?.let { return it } }
        return null
    }

    /** Place name from /maps/place/<Name>/... or a non-coordinate q= / query= / destination= parameter. */
    fun nameFromUrl(rawUrl: String): String {
        val url = unwrap(rawUrl)
        Regex("""/maps/place/([^/?@]+)""").find(url)?.let { m ->
            val n = decode(m.groupValues[1].replace("+", "%20")).trim()
            if (n.isNotEmpty() && parseLatLng(n) == null) return n
        }
        val params = queryParams(url)
        for (k in listOf("q", "query", "destination", "daddr")) {
            val v = params[k]?.trim().orEmpty()
            if (v.isNotEmpty() && parseLatLng(v) == null && !v.startsWith("place_id:")) return v
        }
        return ""
    }

    /** Consent / redirect wrappers (consent.google.com/ml?continue=..., google.com/url?q=...) → the inner URL. */
    fun unwrap(url: String): String {
        var u = url
        repeat(3) {
            val p = queryParams(u)
            val inner = p["continue"] ?: if (u.contains("/url?")) p["url"] ?: p["q"] else null
            if (inner == null || !inner.startsWith("http")) return u
            u = inner
        }
        return u
    }

    fun firstUrl(text: String): String? = URL_RE.find(text)?.value?.trimEnd('.', ',', ')', '،')

    /** Text without URLs, cleaned up: non-blank lines. Google Maps shares "Name\nAddress\nhttps://maps.app.goo.gl/..". */
    fun textLines(text: String): List<String> =
        URL_RE.replace(text, " ").lines().map { it.trim().trim(',', '،', '-', '·').trim() }.filter { it.isNotEmpty() }

    /** Plain coordinates anywhere in the text, e.g. "31.9539, 35.9106". */
    fun coordsInText(text: String): Pt? {
        val m = LATLNG_IN_TEXT_RE.find(URL_RE.replace(text, " ")) ?: return null
        return validPt(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    }

    /** Generic labels, bare coordinates, or a generic label followed by coordinates ("Dropped pin 31.95, 35.91"). */
    fun isGenericLabel(s: String): Boolean {
        if (parseLatLng(s) != null) return true
        val rest = LATLNG_IN_TEXT_RE.replace(s, " ").trim().trim(',', '،', ':', '-').trim().lowercase()
        return rest.isEmpty() || rest in GENERIC_LABELS
    }

    /**
     * Everything that can be decided without network: coordinates (if directly present), a display name
     * and a search query for the fallback. [url] is the link that still needs resolving (short link), if any.
     */
    data class Plan(val pt: Pt?, val name: String, val query: String, val shortUrl: String?)

    fun plan(text: String?, data: String?): Plan? {
        val t = text.orEmpty().trim()
        val d = data.orEmpty().trim()
        // 1) geo: URI (from VIEW data, or pasted as text)
        val geo = parseGeo(d) ?: Regex("""geo:[^\s]+""", RegexOption.IGNORE_CASE).find(t)?.value?.let(::parseGeo)
        if (geo != null) {
            val name = geo.label.takeUnless { isGenericLabel(it) }.orEmpty()
            if (geo.pt != null || geo.label.isNotBlank()) return Plan(geo.pt, name, geo.label, null)
        }
        val lines = textLines(t)
        val textName = lines.firstOrNull { !isGenericLabel(it) }.orEmpty()
        val textQuery = lines.filterNot { isGenericLabel(it) }.joinToString(", ")
        // 2) maps URL (VIEW data or inside the shared text)
        val url = d.takeIf { it.startsWith("http", ignoreCase = true) } ?: firstUrl(t)
        if (url != null) {
            val pt = coordsFromUrl(url)
            val urlName = nameFromUrl(url)
            val name = textName.ifEmpty { urlName }
            val query = textQuery.ifEmpty { urlName }
            if (pt != null) return Plan(pt, name, query, null)
            if (isShortLink(url)) return Plan(coordsInText(t), name, query, url)
            if (query.isNotEmpty() || coordsInText(t) != null) return Plan(coordsInText(t), name, query, null)
            return null
        }
        // 3) bare coordinates or a plain address / place name
        coordsInText(t)?.let { return Plan(it, textName.takeIf { n -> parseLatLng(n) == null }.orEmpty(), textQuery, null) }
        if (textQuery.isNotEmpty()) return Plan(null, textName, textQuery, null)
        return null
    }

    /** Follows HTTP redirects manually (max [maxHops]) and returns the last URL seen. Blocking: call on IO. */
    fun followRedirects(start: String, maxHops: Int = 5): String {
        var url = start
        repeat(maxHops) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Khatwa")
            }
            try {
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")
                if (code !in 300..399 || loc.isNullOrBlank()) return url
                url = URL(URL(url), loc).toString()
                if (coordsFromUrl(url) != null) return url
            } finally {
                conn.disconnect()
            }
        }
        return url
    }

    /** Full resolution: parse, resolve short links, then fall back to a Places text search. Null = couldn't find a place. */
    suspend fun resolve(text: String?, data: String?, google: Google): SharedPlace? {
        val p = plan(text, data) ?: return null
        var pt = p.pt
        var name = p.name
        var query = p.query
        if (pt == null && p.shortUrl != null) {
            val finalUrl = runCatching { withContext(Dispatchers.IO) { followRedirects(p.shortUrl) } }.getOrNull()
            if (finalUrl != null) {
                pt = coordsFromUrl(finalUrl)
                val urlName = nameFromUrl(finalUrl)
                if (name.isEmpty()) name = urlName
                if (query.isEmpty()) query = urlName
            }
        }
        if (pt != null) return SharedPlace(pt, name)
        if (query.isBlank()) return null
        val hit = runCatching { google.searchPlaces(query, null).firstOrNull() }.getOrNull() ?: return null
        return SharedPlace(hit.pt, name.ifEmpty { hit.name })
    }
}
