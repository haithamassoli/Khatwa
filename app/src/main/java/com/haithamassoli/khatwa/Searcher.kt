package com.haithamassoli.khatwa

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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

// OWNER: data agent. Candidate selection + matrix calls + Engine.rank + verify, naming, cache.
class Searcher(private val google: Google, private val store: Store) {
    /**
     * Validates input, serves a cached result unless [force], else reserves quota BEFORE any paid call,
     * picks <=5 candidates per side, ranks, verifies the shown options and names auto points.
     */
    suspend fun search(requestId: Long, input: SearchInput, force: Boolean = false, onStage: (SearchStage) -> Unit = {}): SearchResult {
        validate(input)
        val cacheKey = ResultCache.key(input)
        if (!force) {
            val hit = withContext(Dispatchers.IO) { store.cacheGet(cacheKey, ResultCache.MAX_AGE_MS) }?.let { ResultCache.decode(it, requestId) }
            if (hit != null && !stale(hit)) return hit.copy(input = input, fromCache = true) // keeps the original calculatedAtMs
        }
        if (!store.reserveSearch()) throw QuotaExceededException(YearMonth.now().plusMonths(1).atDay(1).toString())

        val warnings: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val elements = AtomicInteger()
        try {
            onStage(SearchStage.CANDIDATES)
            val (pickups, dropoffs) = coroutineScope {
                val p = async { pick(input.origin, input.maxWalkStartS, PointUse.PICKUP, Candidate("direct-start", input.origin, "نقطة البداية", isDirect = true), warnings) }
                val d = async { pick(input.destination, input.maxWalkEndS, PointUse.DROPOFF, Candidate("direct-end", input.destination, "الوجهة", isDirect = true), warnings) }
                p.await() to d.await()
            }
            onStage(SearchStage.WALKING)
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
            // Uphill penalty first, then drop points whose walk failed or exceeds the limit before paying for drive elements (PRD §15.4).
            val (startUp, endUp) = climb(input, walks(pickups, startRaw), walks(dropoffs, endRaw))
            val pickW = keepWalkable(startUp, input.maxWalkStartS)
            val dropW = keepWalkable(endUp, input.maxWalkEndS)

            // PRD §8.4: no driver-wait data, so pickup time ≈ now + average walk to the pickup points.
            onStage(SearchStage.DRIVING)
            val avgWalkS = pickW.mapNotNull { it.leg.durationS }.average().takeIf { !it.isNaN() } ?: 0.0
            val (drive, trafficAware) = google.driveMatrix(pickW.map { it.c.pt }, dropW.map { it.c.pt }, System.currentTimeMillis() + (avgWalkS * 1000).toLong())
            elements.addAndGet(pickW.size * dropW.size)

            val ranked = Engine.rank(input, pickW.map { it.c }, dropW.map { it.c }, pickW.map { it.leg }, dropW.map { it.leg }, drive,
                pickW.map { it.climbM }, dropW.map { it.climbM })
            if (!trafficAware) warnings += "التقدير دون زحمة حية"

            // Verify before show: matrix estimates can differ from the detailed route the user will see.
            onStage(SearchStage.VERIFYING)
            val (baseline, verified) = verify(ranked.baseline, ranked.alternatives)
            if ((listOfNotNull(baseline) + verified).any { !it.verified }) warnings += "تعذر التحقق من بعض المسارات؛ الأرقام تقديرية"
            val checked = Engine.recheck(input.objective, baseline, verified)

            onStage(SearchStage.NAMING)
            val named = name(checked.alternatives)
            val result = SearchResult(
                requestId = requestId,
                input = input,
                baseline = baseline?.priced(),
                alternatives = named.map { it.priced() },
                warnings = checked.warnings + ranked.warnings + warnings,
                trafficAware = trafficAware,
                calculatedAtMs = System.currentTimeMillis(),
                pairsChecked = ranked.pairsChecked,
                pairsFailed = ranked.pairsFailed,
                elementsUsed = elements.get(),
                droppedAfterCheck = checked.dropped,
                baselinePolyline = baseline?.drivePolyline.orEmpty(),
            )
            // Only cache complete, verified answers; anything partial is worth retrying.
            if (result.baseline != null && result.pairsFailed == 0 && (listOf(result.baseline) + result.alternatives).all { it.verified }) {
                runCatching { withContext(Dispatchers.IO) { store.cachePut(cacheKey, ResultCache.encode(result)) } }
                    .onFailure { if (it is CancellationException) throw it; Log.w("Searcher", "cache write failed", it) }
            }
            return result
        } finally {
            store.addElements(elements.get()) // count what was billed even if a later call failed
        }
    }

    /** Detailed polylines for the chosen option; the drive leg is reused when the search already verified it. */
    suspend fun detail(option: RouteOption, input: SearchInput): OptionDetail = coroutineScope {
        val none = emptyList<Pt>() to 0
        val ws = async { if (option.pickup.isDirect) none else google.route(input.origin, option.pickup.pt, walk = true) }
        val dr = async {
            if (option.verified && option.drivePolyline.isNotEmpty()) option.drivePolyline to option.driveS
            else google.route(option.pickup.pt, option.dropoff.pt, walk = false)
        }
        val we = async { if (option.dropoff.isDirect) none else google.route(option.dropoff.pt, input.destination, walk = true) }
        val (wsPts, wsRaw) = ws.await()
        val (drPts, drS) = dr.await()
        val (wePts, weRaw) = we.await()
        // Same uphill penalty as the search so the walk numbers match the results screen.
        val wsS = wsRaw?.plus(Engine.climbPenaltyS(option.climbStartM))
        val weS = weRaw?.plus(Engine.climbPenaltyS(option.climbEndM))
        // PRD §8.4: the detailed walk can differ from the matrix; the UI compares against input limits to flag it.
        if ((wsS ?: 0) > input.maxWalkStartS || (weS ?: 0) > input.maxWalkEndS) {
            Log.w("Searcher", "detailed walk exceeds limit: start=$wsS/${input.maxWalkStartS} end=$weS/${input.maxWalkEndS}")
        }
        OptionDetail(wsPts, drPts, wePts, wsS, drS, weS)
    }

    /** A cached answer is stale once one of its points was disabled or reported since. */
    private fun stale(r: SearchResult): Boolean {
        val all = store.candidates.value
        val off = all.filter { !it.enabled || it.verification == Verification.REPORTED }
        return r.alternatives.flatMap { listOf(it.pickup, it.dropoff) }.filterNot { it.isDirect }.any { c ->
            off.any { it.id == c.id || (it.verification == Verification.REPORTED && distanceM(it.pt, c.pt) < 40) }
        }
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

    private data class Walk(val c: Candidate, val leg: Leg, val climbM: Int = 0)

    /** [legs] covers non-direct candidates in order. Direct points always stay with a 0 m / 0 s walk. */
    private fun walks(cands: List<Candidate>, legs: List<Leg>): List<Walk> {
        val rest = legs.iterator()
        return cands.map { Walk(it, if (it.isDirect) Leg(0, 0) else rest.next()) }
    }

    /**
     * One Elevation request for origin, destination and every walkable point; adds the net ascent penalty
     * (origin -> pickup, dropoff -> destination) to the walk. Failure means no penalty.
     */
    private suspend fun climb(input: SearchInput, start: List<Walk>, end: List<Walk>): Pair<List<Walk>, List<Walk>> {
        fun ok(w: Walk, max: Int) = !w.c.isDirect && w.leg.durationS != null && w.leg.durationS <= max
        val s = start.filter { ok(it, input.maxWalkStartS) }
        val e = end.filter { ok(it, input.maxWalkEndS) }
        if (s.isEmpty() && e.isEmpty()) return start to end
        val elev = try {
            google.elevations(listOf(input.origin, input.destination) + s.map { it.c.pt } + e.map { it.c.pt })
        } catch (ex: Exception) {
            if (ex is CancellationException) throw ex
            Log.w("Searcher", "elevation failed, no uphill penalty", ex)
            return start to end
        }
        val up = s.withIndex().associate { (k, w) -> w.c.id to elev[2 + k] - elev[0] }
        val down = e.withIndex().associate { (k, w) -> w.c.id to elev[1] - elev[2 + s.size + k] }
        fun apply(list: List<Walk>, rise: Map<String, Double>) = list.map { w ->
            val m = rise[w.c.id]?.let { maxOf(0, it.roundToInt()) } ?: return@map w
            w.copy(leg = w.leg.copy(durationS = w.leg.durationS?.plus(Engine.climbPenaltyS(m))), climbM = m)
        }
        return apply(start, up) to apply(end, down)
    }

    /** Keeps walk-valid points, then trims to MAX_PER_SIDE for the drive matrix: direct and saved first, then shortest-walk auto points. */
    private fun keepWalkable(walks: List<Walk>, maxS: Int): List<Walk> = walks
        .filter { it.c.isDirect || (it.leg.durationS != null && it.leg.durationS <= maxS) }
        .sortedWith(compareBy({ it.c.auto }, { if (it.c.auto) it.leg.durationS else 0 }))
        .take(MAX_PER_SIDE)

    /** Detailed TRAFFIC_AWARE routes for the baseline and alternatives, in parallel. Failed ones stay unverified. */
    private suspend fun verify(baseline: RouteOption?, alternatives: List<RouteOption>): Pair<RouteOption?, List<RouteOption>> = coroutineScope {
        val now = System.currentTimeMillis()
        suspend fun check(o: RouteOption): RouteOption = try {
            val r = google.routeLeg(o.pickup.pt, o.dropoff.pt, walk = false, departAtMs = now + o.walkStartS * 1000L)
            if (r.durationS == null || r.durationS <= 0 || r.distanceM == null) o
            else o.copy(driveS = r.durationS, driveM = r.distanceM, verified = true, estimatedDriveS = o.driveS, drivePolyline = r.points)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("Searcher", "verify failed", e)
            o
        }
        val b = baseline?.let { async { check(it) } }
        val alts = alternatives.take(MAX_VERIFY).map { async { check(it) } }
        b?.await() to alts.awaitAll()
    }

    /** Names auto points in the final options by nearest landmark and street; any failure keeps the old name. */
    private suspend fun name(options: List<RouteOption>): List<RouteOption> = coroutineScope {
        val autos = options.flatMap { listOf(it.pickup, it.dropoff) }.filter { it.auto }.distinctBy { it.id }.take(MAX_NAMED)
        if (autos.isEmpty()) return@coroutineScope options
        val named = autos.map { async { describe(it) } }.awaitAll().associateBy { it.id }
        options.map { o -> o.copy(pickup = named[o.pickup.id] ?: o.pickup, dropoff = named[o.dropoff.id] ?: o.dropoff) }
    }

    private suspend fun describe(c: Candidate): Candidate = coroutineScope {
        val place = async { quiet { google.nearbyPlaces(c.pt, NAME_RADIUS_M).firstOrNull() } }
        val street = async { quiet { google.streetName(c.pt) } }
        val p = place.await()
        val s = street.await().orEmpty()
        val name = when {
            p != null -> (if (distanceM(c.pt, p.pt) <= FACING_M) "أمام " else "قرب ") + p.name
            s.isNotEmpty() -> "على $s"
            else -> c.name
        }
        c.copy(name = name, landmark = p?.name ?: c.landmark, street = s.ifEmpty { c.street })
    }

    private suspend fun <T> quiet(f: suspend () -> T): T? = try {
        f()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("Searcher", "naming call failed", e)
        null
    }

    private fun RouteOption.priced() = copy(fareJod = Fare.estimateJod(driveM, driveS))

    companion object {
        private const val MAX_PER_SIDE = 5     // drive matrix: max 25 elements per search
        private const val MAX_WALK_CHECK = 9   // walk matrix is 1 element per point, so over-sample then trim
        private const val MAX_VERIFY = 3       // computeRoutes calls for alternatives (+1 for the baseline)
        private const val MAX_NAMED = 6        // landmark + street lookups per search
        private const val NAME_RADIUS_M = 60.0
        private const val FACING_M = 25.0      // closer than this to the landmark reads "أمام", else "قرب"
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
