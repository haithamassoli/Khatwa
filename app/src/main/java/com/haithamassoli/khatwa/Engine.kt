package com.haithamassoli.khatwa

import kotlin.math.ceil
import kotlin.math.roundToInt

// OWNER: engine agent. Pure Kotlin, no Android imports.
object Engine {
    // An alternative must save at least max(floor, 10% of the baseline) on the chosen objective.
    const val MIN_SAVE_S = 180
    const val MIN_SAVE_M = 300
    const val MIN_SAVE_FRACTION = 0.10
    const val CLIMB_S_PER_M = 6       // Naismith: ~1 min per 10 m of ascent
    const val SHRINK_WARN_S = 60      // warn when verification cut the top saving by at least this much

    data class Ranked(
        val baseline: RouteOption?,
        val alternatives: List<RouteOption>,
        val pairsChecked: Int,
        val pairsFailed: Int,
        val warnings: List<String>,
    )

    data class Checked(val alternatives: List<RouteOption>, val dropped: Int, val warnings: List<String>)

    fun climbPenaltyS(climbM: Int) = maxOf(0, climbM) * CLIMB_S_PER_M

    /**
     * walkStart[i] = walk origin -> pickups[i]; walkEnd[j] = walk dropoffs[j] -> destination;
     * drive[i][j] = drive pickups[i] -> dropoffs[j]. Candidates with isDirect have walk 0 by definition.
     * Walk durations already include any uphill penalty; climbStart/climbEnd (meters) only annotate the options.
     */
    fun rank(
        input: SearchInput,
        pickups: List<Candidate>,
        dropoffs: List<Candidate>,
        walkStart: List<Leg>,
        walkEnd: List<Leg>,
        drive: List<List<Leg>>,
        climbStart: List<Int> = emptyList(),
        climbEnd: List<Int> = emptyList(),
    ): Ranked {
        val usable = { c: Candidate -> c.enabled && c.verification != Verification.REPORTED }
        // Walk seconds for one side: 0 for direct, null if the leg failed.
        fun walk(c: Candidate, leg: Leg?) = if (c.isDirect) 0 else leg?.durationS
        // A non-direct point always counts as walking, so limit 0 excludes it even at 0 s.
        fun overLimit(c: Candidate, s: Int?, limit: Int) =
            !c.isDirect && (limit == 0 || (s != null && s > limit))

        var checked = 0
        var failed = 0
        val valid = mutableListOf<RouteOption>()
        for ((i, p) in pickups.withIndex()) {
            if (!usable(p)) continue
            val ws = walk(p, walkStart.getOrNull(i))
            for ((j, q) in dropoffs.withIndex()) {
                if (!usable(q)) continue
                checked++
                val we = walk(q, walkEnd.getOrNull(j))
                // Known limit violations reject the pair even if another leg failed.
                val startOver = overLimit(p, ws, input.maxWalkStartS)
                val endOver = overLimit(q, we, input.maxWalkEndS)
                if (startOver || endOver) continue
                val d = drive.getOrNull(i)?.getOrNull(j)
                if (ws == null || we == null || d?.durationS == null || d.distanceM == null) {
                    failed++
                    continue
                }
                valid += RouteOption(p, q, ws, d.durationS, d.distanceM, we,
                    climbStartM = if (p.isDirect) 0 else climbStart.getOrNull(i) ?: 0,
                    climbEndM = if (q.isDirect) 0 else climbEnd.getOrNull(j) ?: 0)
            }
        }

        val baseline = valid.firstOrNull { it.pickup.isDirect && it.dropoff.isDirect }
        val others = valid.filter { key(it) != baseline?.let(::key) }
        val alternatives = others
            .filter { o -> others.none { dominates(it, o) } }
            .filter { baseline == null || worthIt(input.objective, baseline, it) }
            .sortedWith(comparator(input.objective))
            .distinctBy(::key)
            .take(3)

        val warnings = buildList {
            if (failed > 0) add("بحث جزئي: تعذر حساب $failed من $checked أزواج")
            if (baseline == null) add("تعذر حساب المرجع المباشر؛ لا تُعرض فروق عنه")
            if (valid.isEmpty()) add("لا يوجد خيار صالح ضمن حدود المشي")
            else if (alternatives.isEmpty()) add(if (baseline != null) noSaving(input.objective, baseline)
                else "لا يوجد بديل أفضل من المرجع المباشر ضمن حدود المشي")
            if (input.maxWalkStartS == 0 && pickups.any { usable(it) && !it.isDirect })
                add("حد المشي قبل الركوب صفر: استُبعدت نقاط الركوب التي تتطلب مشياً")
            if (input.maxWalkEndS == 0 && dropoffs.any { usable(it) && !it.isDirect })
                add("حد المشي بعد النزول صفر: استُبعدت نقاط النزول التي تتطلب مشياً")
        }
        return Ranked(baseline, alternatives, checked, failed, warnings)
    }

    /**
     * Re-applies the ranking rules after verify-before-show replaced driveS/driveM with detailed routes.
     * Options keep their matrix value in estimatedDriveS, used to detect a shrunken saving.
     */
    fun recheck(objective: Objective, baseline: RouteOption?, alternatives: List<RouteOption>): Checked {
        if (baseline == null) return Checked(alternatives.sortedWith(comparator(objective)), 0, emptyList())
        val (kept, gone) = alternatives.partition { worthIt(objective, baseline, it) }
        val best = kept.filter { o -> kept.none { dominates(it, o) } }.sortedWith(comparator(objective))
        val warnings = buildList {
            if (gone.isNotEmpty()) {
                val what = when (gone.size) { 1 -> "فاستُبعد بديل"; 2 -> "فاستُبعد بديلان"; else -> "فاستُبعدت ${gone.size} بدائل" }
                add("تراجع التوفير بعد التحقق من المسار الفعلي، $what" + if (best.isEmpty()) "؛ الرحلة المباشرة هي الأفضل" else "")
            }
            val top = best.firstOrNull()
            if (top != null && objective != Objective.DRIVE_DISTANCE) {
                val now = saving(objective, baseline, top)
                if (saving(objective, baseline.estimate(), top.estimate()) - now >= SHRINK_WARN_S)
                    add("التوفير تراجع إلى ${(now / 60.0).roundToInt()} د بسبب الزحمة")
            }
        }
        return Checked(best, gone.size, warnings)
    }

    /** Saving of [o] vs [baseline] on the objective's primary metric (seconds or meters; positive = better). */
    fun saving(objective: Objective, baseline: RouteOption, o: RouteOption) = primary(objective)(baseline) - primary(objective)(o)

    fun minSaving(objective: Objective, baseline: RouteOption): Int {
        val floor = if (objective == Objective.DRIVE_DISTANCE) MIN_SAVE_M else MIN_SAVE_S
        return maxOf(floor, ceil(primary(objective)(baseline) * MIN_SAVE_FRACTION).toInt())
    }

    fun worthIt(objective: Objective, baseline: RouteOption, o: RouteOption): Boolean {
        if (saving(objective, baseline, o) < minSaving(objective, baseline)) return false
        // Walking must cost less than the ride time it saves (walk 5 min to save 3 is no deal).
        return objective != Objective.MODELED || baseline.driveS - o.driveS > o.walkTotalS
    }

    fun comparator(objective: Objective): Comparator<RouteOption> = when (objective) {
        Objective.MODELED -> compareBy<RouteOption>({ it.modeledS }, { it.walkTotalS }, { it.driveM })
        Objective.DRIVE_TIME -> compareBy({ it.driveS }, { it.modeledS }, { it.walkTotalS })
        Objective.DRIVE_DISTANCE -> compareBy({ it.driveM }, { it.modeledS }, { it.walkTotalS })
    }

    private fun noSaving(objective: Objective, baseline: RouteOption): String {
        val min = minSaving(objective, baseline)
        if (objective == Objective.DRIVE_DISTANCE)
            return "لا يوجد بديل يختصر ${(min + 9) / 10 * 10} م على الأقل من القيادة؛ الرحلة المباشرة هي الأفضل"
        val m = (min + 59) / 60
        return "لا يوجد بديل يوفّر $m ${if (m in 3..10) "دقائق" else "دقيقة"} على الأقل؛ الرحلة المباشرة هي الأفضل"
    }

    private fun key(o: RouteOption) = o.pickup.id to o.dropoff.id

    private fun RouteOption.estimate() = copy(driveS = estimatedDriveS ?: driveS)

    private fun primary(objective: Objective): (RouteOption) -> Int = when (objective) {
        Objective.MODELED -> { o -> o.modeledS }
        Objective.DRIVE_TIME -> { o -> o.driveS }
        Objective.DRIVE_DISTANCE -> { o -> o.driveM }
    }

    // a dominates b: no worse on every metric, strictly better on at least one (PRD §8.3).
    private fun dominates(a: RouteOption, b: RouteOption): Boolean {
        val am = listOf(a.walkTotalS, a.driveS, a.driveM, a.modeledS)
        val bm = listOf(b.walkTotalS, b.driveS, b.driveM, b.modeledS)
        return am.zip(bm).all { (x, y) -> x <= y } && am.zip(bm).any { (x, y) -> x < y }
    }
}
