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

    @Synchronized fun upsertCandidate(c: Candidate) {
        val list = _candidates.value
        val i = list.indexOfFirst { it.id == c.id }
        saveCandidates(if (i < 0) list + c else list.toMutableList().also { it[i] = c })
    }

    @Synchronized fun deleteCandidate(id: String) = saveCandidates(_candidates.value.filterNot { it.id == id })

    @Synchronized fun addFare(f: FareObservation) = saveFares(_fares.value.filterNot { it.id == f.id } + f)

    @Synchronized fun deleteFare(id: String) = saveFares(_fares.value.filterNot { it.id == id })

    @Synchronized fun clearHistory() = saveFares(emptyList())

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
