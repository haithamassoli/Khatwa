package com.haithamassoli.khatwa

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.YearMonth
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class QuotaExceededException(val resetsOn: String) : Exception("quota")

// OWNER: data agent. Candidate selection + matrix calls + Engine.rank.
class Searcher(private val google: Google, private val store: Store) {
    /** Validates input, reserves quota BEFORE any paid call, picks <=5 candidates per side, ranks. */
    suspend fun search(requestId: Long, input: SearchInput): SearchResult {
        validate(input)
        if (!store.reserveSearch()) throw QuotaExceededException(YearMonth.now().plusMonths(1).atDay(1).toString())

        val warnings: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val elements = AtomicInteger()
        try {
            val (pickups, dropoffs) = coroutineScope {
                val p = async { pick(input.origin, input.maxWalkStartS, PointUse.PICKUP, Candidate("direct-start", input.origin, "نقطة البداية", isDirect = true), warnings) }
                val d = async { pick(input.destination, input.maxWalkEndS, PointUse.DROPOFF, Candidate("direct-end", input.destination, "الوجهة", isDirect = true), warnings) }
                p.await() to d.await()
            }
            val walkPickups = pickups.filterNot { it.isDirect }
            val walkDropoffs = dropoffs.filterNot { it.isDirect }
            val (startRaw, endRaw) = coroutineScope {
                val s = async {
                    if (walkPickups.isEmpty()) emptyList()
                    else google.walkMatrix(listOf(input.origin), walkPickups.map { it.pt })[0].also { elements.addAndGet(walkPickups.size) }
                }
                val e = async {
                    if (walkDropoffs.isEmpty()) emptyList()
                    else google.walkMatrix(walkDropoffs.map { it.pt }, listOf(input.destination)).map { it[0] }
                        .also { elements.addAndGet(walkDropoffs.size) }
                }
                s.await() to e.await()
            }
            if ((startRaw + endRaw).any { it.durationS == null }) warnings += "تعذر حساب مسار المشي لبعض النقاط، فاستُبعدت."
            // Drop points whose walk failed or exceeds the limit before paying for drive elements (PRD §15.4).
            val (pickOk, walkStart) = keepWalkable(pickups, startRaw, input.maxWalkStartS).unzip()
            val (dropOk, walkEnd) = keepWalkable(dropoffs, endRaw, input.maxWalkEndS).unzip()

            // PRD §8.4: no driver-wait data, so pickup time ≈ now + average walk to the pickup points.
            val avgWalkS = walkStart.mapNotNull { it.durationS }.average().takeIf { !it.isNaN() } ?: 0.0
            val minutes = (avgWalkS / 60).roundToInt()
            warnings += if (minutes == 0) "يُفترض أن الركوب يتم الآن، دون احتساب انتظار السائق."
            else "يُفترض أن الركوب بعد نحو $minutes د (متوسط مشي البداية)، دون احتساب انتظار السائق."
            val (drive, trafficAware) = google.driveMatrix(pickOk.map { it.pt }, dropOk.map { it.pt }, System.currentTimeMillis() + (avgWalkS * 1000).toLong())
            elements.addAndGet(pickOk.size * dropOk.size)

            val ranked = Engine.rank(input, pickOk, dropOk, walkStart, walkEnd, drive)
            if (!trafficAware) warnings += "التقدير دون زحمة حية"
            return SearchResult(
                requestId = requestId,
                input = input,
                baseline = ranked.baseline,
                alternatives = ranked.alternatives,
                warnings = ranked.warnings + warnings,
                trafficAware = trafficAware,
                calculatedAtMs = System.currentTimeMillis(),
                pairsChecked = ranked.pairsChecked,
                pairsFailed = ranked.pairsFailed,
                elementsUsed = elements.get(),
            )
        } finally {
            store.addElements(elements.get()) // count what was billed even if a later call failed
        }
    }

    /** Detailed polylines for the chosen option (3 computeRoutes calls; skip walk legs for isDirect points). */
    suspend fun detail(option: RouteOption, input: SearchInput): OptionDetail = coroutineScope {
        val none = emptyList<Pt>() to 0
        val ws = async { if (option.pickup.isDirect) none else google.route(input.origin, option.pickup.pt, walk = true) }
        val dr = async { google.route(option.pickup.pt, option.dropoff.pt, walk = false) }
        val we = async { if (option.dropoff.isDirect) none else google.route(option.dropoff.pt, input.destination, walk = true) }
        val (wsPts, wsS) = ws.await()
        val (drPts, drS) = dr.await()
        val (wePts, weS) = we.await()
        // PRD §8.4: the detailed walk can differ from the matrix; the UI compares against input limits to flag it.
        if ((wsS ?: 0) > input.maxWalkStartS || (weS ?: 0) > input.maxWalkEndS) {
            Log.w("Searcher", "detailed walk exceeds limit: start=$wsS/${input.maxWalkStartS} end=$weS/${input.maxWalkEndS}")
        }
        OptionDetail(wsPts, drPts, wePts, wsS, drS, weS)
    }

    private fun validate(input: SearchInput) {
        fun ok(p: Pt) = p.lat.isFinite() && p.lng.isFinite() && p.lat in -90.0..90.0 && p.lng in -180.0..180.0
        require(ok(input.origin) && ok(input.destination)) { "إحداثيات البداية أو الوجهة غير صالحة." }
        require(distanceM(input.origin, input.destination) >= 30) { "البداية والوجهة متطابقتان تقريباً؛ اختر وجهة أبعد." }
        require(input.maxWalkStartS in 0..600 && input.maxWalkEndS in 0..600) { "حد المشي يجب أن يكون بين 0 و600 ثانية." }
    }

    /** Direct point first, then saved points nearest-first, then auto-suggested road points; max 5. */
    private suspend fun pick(center: Pt, maxWalkS: Int, use: PointUse, direct: Candidate, warnings: MutableList<String>): List<Candidate> {
        val reach = maxWalkS * WALK_MPS // straight-line prefilter only; the walk matrix is the real check (FR-04)
        val all = store.candidates.value
        val chosen = mutableListOf(direct)
        chosen += all.asSequence()
            .filter { it.enabled && it.verification != Verification.REPORTED && (it.use == use || it.use == PointUse.BOTH) }
            .map { it to distanceM(center, it.pt) }
            .filter { it.second <= reach }
            .sortedBy { it.second }
            .take(MAX_WALK_CHECK - 1)
            .map { it.first }
        if (maxWalkS == 0 || chosen.size >= MAX_WALK_CHECK) return chosen

        // Amman walks are ~1.5-2.5x the straight line (hills, divided roads), so sample close rings.
        val ring = listOf(0.15, 0.3, 0.45).flatMap { f ->
            val r = minOf(reach * f, 400.0)
            (0 until 8).map { offset(center, it * 45.0, r) }
        }
        val snapped = try {
            google.nearestRoads(ring)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            warnings += "تعذر اقتراح نقاط إضافية تلقائياً."
            return chosen
        }
        val reported = all.filter { it.verification == Verification.REPORTED }.map { it.pt }
        for (p in snapped.sortedBy { distanceM(center, it) }) {
            if (chosen.size >= MAX_WALK_CHECK) break
            if (distanceM(center, p) < 25 || chosen.any { distanceM(it.pt, p) < 40 } || reported.any { distanceM(it, p) < 40 }) continue
            chosen += Candidate(
                id = String.format(Locale.US, "auto-%.5f-%.5f", p.lat, p.lng),
                pt = p,
                name = autoName(center, p),
                use = use,
                verification = Verification.UNVERIFIED,
                auto = true,
            )
        }
        return chosen
    }

    /**
     * [legs] covers non-direct candidates in order. Direct points always stay with a 0 m / 0 s walk.
     * Keeps walk-valid points, then trims to MAX_PER_SIDE for the drive matrix: direct and saved first, then shortest-walk auto points.
     */
    private fun keepWalkable(cands: List<Candidate>, legs: List<Leg>, maxS: Int): List<Pair<Candidate, Leg>> {
        val rest = legs.iterator()
        return cands.map { it to if (it.isDirect) Leg(0, 0) else rest.next() }
            .filter { (c, leg) -> c.isDirect || (leg.durationS != null && leg.durationS <= maxS) }
            .sortedWith(compareBy({ it.first.auto }, { if (it.first.auto) it.second.durationS else 0 }))
            .take(MAX_PER_SIDE)
    }

    companion object {
        private const val MAX_PER_SIDE = 5     // drive matrix: max 25 elements per search
        private const val MAX_WALK_CHECK = 9   // walk matrix is 1 element per point, so over-sample then trim
        private const val WALK_MPS = 1.4
        private const val EARTH_M = 6_371_000.0
        private val DIRECTIONS = listOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")

        fun distanceM(a: Pt, b: Pt): Double {
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLng = Math.toRadians(b.lng - a.lng)
            val h = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
            return 2 * EARTH_M * atan2(sqrt(h), sqrt(1 - h))
        }

        /** Flat-earth offset; accurate enough for a few hundred meters. */
        private fun offset(p: Pt, bearingDeg: Double, m: Double): Pt {
            val b = Math.toRadians(bearingDeg)
            val dLat = m * cos(b) / EARTH_M
            val dLng = m * sin(b) / (EARTH_M * cos(Math.toRadians(p.lat)))
            return Pt(p.lat + Math.toDegrees(dLat), p.lng + Math.toDegrees(dLng))
        }

        private fun autoName(from: Pt, to: Pt): String {
            val y = sin(Math.toRadians(to.lng - from.lng)) * cos(Math.toRadians(to.lat))
            val x = cos(Math.toRadians(from.lat)) * sin(Math.toRadians(to.lat)) -
                sin(Math.toRadians(from.lat)) * cos(Math.toRadians(to.lat)) * cos(Math.toRadians(to.lng - from.lng))
            val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
            val dir = DIRECTIONS[((bearing + 22.5) / 45).toInt() % 8]
            val m = (distanceM(from, to) / 10).roundToInt() * 10
            return "نقطة مقترحة · $m م $dir"
        }
    }
}
