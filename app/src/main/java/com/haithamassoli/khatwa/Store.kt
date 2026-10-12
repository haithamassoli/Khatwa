package com.haithamassoli.khatwa

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.YearMonth

// OWNER: data agent. Local JSON persistence (filesDir) + monthly quota.
// ponytail: whole-file rewrite on every change; fine for a personal app's small lists.
class Store(context: Context) {
    private val dir = context.filesDir
    private val _candidates = MutableStateFlow(readArray("candidates.json").mapNotNull { runCatching { candidate(it) }.getOrNull() })
    val candidates: StateFlow<List<Candidate>> = _candidates
    private val _fares = MutableStateFlow(readArray("fares.json").mapNotNull { runCatching { fare(it) }.getOrNull() })
    val fares: StateFlow<List<FareObservation>> = _fares
    private val _quota = MutableStateFlow(loadQuota())
    val quota: StateFlow<Quota> = _quota
    private val _places = MutableStateFlow(readArray("places.json").mapNotNull { runCatching { place(it) }.getOrNull() })
    val places: StateFlow<List<SavedPlace>> = _places
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings
    private val _pendingTrips = MutableStateFlow(readArray("trips.json").mapNotNull { runCatching { trip(it) }.getOrNull() })
    val pendingTrips: StateFlow<List<PendingTrip>> = _pendingTrips

    @Synchronized fun upsertCandidate(c: Candidate) {
        val list = _candidates.value
        val i = list.indexOfFirst { it.id == c.id }
        saveCandidates(if (i < 0) list + c else list.toMutableList().also { it[i] = c })
    }

    @Synchronized fun deleteCandidate(id: String) = saveCandidates(_candidates.value.filterNot { it.id == id })

    @Synchronized fun addFare(f: FareObservation) = saveFares(_fares.value.filterNot { it.id == f.id } + f)

    @Synchronized fun deleteFare(id: String) = saveFares(_fares.value.filterNot { it.id == id })

    @Synchronized fun clearHistory() = saveFares(emptyList())

    @Synchronized fun upsertPlace(p: SavedPlace) {
        // One HOME / WORK / UNIVERSITY each; OTHER can repeat.
        val rest = _places.value.filterNot { it.id == p.id || (p.kind != PlaceKind.OTHER && it.kind == p.kind) }
        savePlaces(rest + p)
    }

    @Synchronized fun deletePlace(id: String) = savePlaces(_places.value.filterNot { it.id == id })

    @Synchronized fun updateSettings(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        write("settings.json", JSONObject().put("onboarded", s.onboarded).put("noWalkAtNight", s.noWalkAtNight)
            .put("defaultWalkS", s.defaultWalkS).toString())
        _settings.value = s
    }

    @Synchronized fun addPendingTrip(t: PendingTrip) = saveTrips(_pendingTrips.value.filterNot { it.id == t.id } + t)

    @Synchronized fun removePendingTrip(id: String) = saveTrips(_pendingTrips.value.filterNot { it.id == id })

    /** Post-trip feedback. Saved points get a vote; auto/direct points are saved (as reviewed) on 👍, ignored on 👎. */
    @Synchronized fun vote(c: Candidate, good: Boolean) {
        val existing = _candidates.value.firstOrNull { it.id == c.id && !it.auto && !it.isDirect }
        when {
            existing != null -> upsertCandidate(
                if (good) existing.copy(upVotes = existing.upVotes + 1)
                else existing.copy(downVotes = existing.downVotes + 1).let { if (it.downVotes >= 2 && it.downVotes > it.upVotes) it.copy(enabled = false) else it }
            )
            good && !c.isDirect -> upsertCandidate(c.copy(id = java.util.UUID.randomUUID().toString(), auto = false, isDirect = false,
                verification = Verification.VERIFIED, upVotes = 1))
        }
    }

    /** Small key/value cache (e.g. search results). Returns null when missing or older than [maxAgeMs]. */
    @Synchronized fun cacheGet(key: String, maxAgeMs: Long): String? {
        val o = readCache().optJSONObject(key) ?: return null
        return if (System.currentTimeMillis() - o.optLong("at") <= maxAgeMs) o.optString("v") else null
    }

    @Synchronized fun cachePut(key: String, value: String) {
        val all = readCache().put(key, JSONObject().put("at", System.currentTimeMillis()).put("v", value))
        // Keep the newest 40 entries.
        val keys = all.keys().asSequence().toList().sortedByDescending { all.getJSONObject(it).optLong("at") }
        keys.drop(40).forEach { all.remove(it) }
        write("cache.json", all.toString())
    }

    @Synchronized fun cacheClear() { File(dir, "cache.json").delete() }

    private fun readCache(): JSONObject = runCatching { JSONObject(File(dir, "cache.json").readText()) }.getOrDefault(JSONObject())

    /** Reserves one search for this month; false (and no change) if the quota is used up. */
    @Synchronized fun reserveSearch(): Boolean {
        val q = current()
        if (q.searches >= MAX_SEARCHES) return false
        saveQuota(q.copy(searches = q.searches + 1))
        return true
    }

    @Synchronized fun addElements(n: Int) {
        val q = current()
        saveQuota(q.copy(elements = q.elements + n))
    }

    private fun current(): Quota {
        val month = YearMonth.now().toString()
        val q = _quota.value
        return if (q.month == month) q else Quota(month, 0, 0, MAX_SEARCHES)
    }

    private fun loadQuota(): Quota {
        val month = YearMonth.now().toString()
        val o = runCatching { JSONObject(File(dir, "quota.json").readText()) }.getOrNull()
        return if (o != null && o.optString("month") == month) Quota(month, o.optInt("searches"), o.optInt("elements"), MAX_SEARCHES)
        else Quota(month, 0, 0, MAX_SEARCHES)
    }

    private fun loadSettings(): Settings {
        val o = runCatching { JSONObject(File(dir, "settings.json").readText()) }.getOrNull() ?: return Settings()
        return Settings(o.optBoolean("onboarded"), o.optBoolean("noWalkAtNight"), o.optInt("defaultWalkS", 300))
    }

    private fun savePlaces(list: List<SavedPlace>) {
        write("places.json", JSONArray(list.map { JSONObject().put("id", it.id).put("kind", it.kind.name).put("name", it.name)
            .put("lat", it.pt.lat).put("lng", it.pt.lng).put("tip", it.tip) }).toString())
        _places.value = list
    }

    private fun saveTrips(list: List<PendingTrip>) {
        write("trips.json", JSONArray(list.map { JSONObject().put("id", it.id).put("bookedAtMs", it.bookedAtMs)
            .put("askAfterMs", it.askAfterMs).put("pickup", json(it.pickup)).put("dropoff", json(it.dropoff)) }).toString())
        _pendingTrips.value = list
    }

    private fun saveCandidates(list: List<Candidate>) {
        write("candidates.json", JSONArray(list.map(::json)).toString())
        _candidates.value = list
    }

    private fun saveFares(list: List<FareObservation>) {
        write("fares.json", JSONArray(list.map(::json)).toString())
        _fares.value = list
    }

    private fun saveQuota(q: Quota) {
        write("quota.json", JSONObject().put("month", q.month).put("searches", q.searches).put("elements", q.elements).toString())
        _quota.value = q
    }

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }

    private fun readArray(name: String): List<JSONObject> = runCatching {
        val a = JSONArray(File(dir, name).readText())
        (0 until a.length()).map { a.getJSONObject(it) }
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_SEARCHES = 100

        private fun json(c: Candidate) = JSONObject()
            .put("id", c.id).put("lat", c.pt.lat).put("lng", c.pt.lng).put("name", c.name)
            .put("roadSide", c.roadSide).put("landmark", c.landmark).put("use", c.use.name)
            .put("verification", c.verification.name).put("enabled", c.enabled)
            .put("auto", c.auto).put("isDirect", c.isDirect)
            .put("street", c.street).put("upVotes", c.upVotes).put("downVotes", c.downVotes)

        private fun candidate(o: JSONObject) = Candidate(
            id = o.getString("id"),
            pt = Pt(o.getDouble("lat"), o.getDouble("lng")),
            name = o.optString("name"),
            roadSide = o.optString("roadSide"),
            landmark = o.optString("landmark"),
            use = runCatching { PointUse.valueOf(o.getString("use")) }.getOrDefault(PointUse.BOTH),
            verification = runCatching { Verification.valueOf(o.getString("verification")) }.getOrDefault(Verification.UNVERIFIED),
            enabled = o.optBoolean("enabled", true),
            auto = o.optBoolean("auto"),
            isDirect = o.optBoolean("isDirect"),
            street = o.optString("street"),
            upVotes = o.optInt("upVotes"),
            downVotes = o.optInt("downVotes"),
        )

        private fun place(o: JSONObject) = SavedPlace(
            id = o.getString("id"),
            kind = runCatching { PlaceKind.valueOf(o.getString("kind")) }.getOrDefault(PlaceKind.OTHER),
            name = o.optString("name"),
            pt = Pt(o.getDouble("lat"), o.getDouble("lng")),
            tip = o.optString("tip"),
        )

        private fun trip(o: JSONObject) = PendingTrip(
            id = o.getString("id"),
            bookedAtMs = o.getLong("bookedAtMs"),
            askAfterMs = o.getLong("askAfterMs"),
            pickup = candidate(o.getJSONObject("pickup")),
            dropoff = candidate(o.getJSONObject("dropoff")),
        )

        private fun json(f: FareObservation) = JSONObject()
            .put("id", f.id).put("atMs", f.atMs).put("provider", f.provider)
            .put("vehicleCategory", f.vehicleCategory).put("amount", f.amount ?: JSONObject.NULL)
            .put("currency", f.currency).put("isBaseline", f.isBaseline)
            .put("pickupName", f.pickupName).put("dropoffName", f.dropoffName)
            .put("actualFare", f.actualFare ?: JSONObject.NULL).put("actualWaitS", f.actualWaitS ?: JSONObject.NULL)
            .put("note", f.note)

        private fun fare(o: JSONObject) = FareObservation(
            id = o.getString("id"),
            atMs = o.getLong("atMs"),
            provider = o.optString("provider"),
            vehicleCategory = o.optString("vehicleCategory"),
            amount = o.doubleOrNull("amount"),
            currency = o.optString("currency", "JOD"),
            isBaseline = o.optBoolean("isBaseline"),
            pickupName = o.optString("pickupName"),
            dropoffName = o.optString("dropoffName"),
            actualFare = o.doubleOrNull("actualFare"),
            actualWaitS = if (o.has("actualWaitS") && !o.isNull("actualWaitS")) o.getInt("actualWaitS") else null,
            note = o.optString("note"),
        )

        private fun JSONObject.doubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    }
}
