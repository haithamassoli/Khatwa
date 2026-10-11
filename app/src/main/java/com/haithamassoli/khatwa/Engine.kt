package com.haithamassoli.khatwa

// OWNER: engine agent. Pure Kotlin, no Android imports.
object Engine {
    data class Ranked(
        val baseline: RouteOption?,
        val alternatives: List<RouteOption>,
        val pairsChecked: Int,
        val pairsFailed: Int,
        val warnings: List<String>,
    )

    /**
     * walkStart[i] = walk origin -> pickups[i]; walkEnd[j] = walk dropoffs[j] -> destination;
     * drive[i][j] = drive pickups[i] -> dropoffs[j]. Candidates with isDirect have walk 0 by definition.
     */
    fun rank(
        input: SearchInput,
        pickups: List<Candidate>,
        dropoffs: List<Candidate>,
        walkStart: List<Leg>,
        walkEnd: List<Leg>,
        drive: List<List<Leg>>,
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
                valid += RouteOption(p, q, ws, d.durationS, d.distanceM, we)
            }
        }

        val baseline = valid.firstOrNull { it.pickup.isDirect && it.dropoff.isDirect }
        val key = { o: RouteOption -> o.pickup.id to o.dropoff.id }
        val primary = primary(input.objective)
        val others = valid.filter { key(it) != baseline?.let(key) }
        val alternatives = others
            .filter { o -> others.none { dominates(it, o) } }
            .filter { baseline == null || primary(it) < primary(baseline) }
            .sortedWith(comparator(input.objective))
            .distinctBy(key)
            .take(3)

        val warnings = buildList {
            if (failed > 0) add("بحث جزئي: تعذر حساب $failed من $checked أزواج")
            if (baseline == null) add("تعذر حساب المرجع المباشر؛ لا تُعرض فروق عنه")
            if (valid.isEmpty()) add("لا يوجد خيار صالح ضمن حدود المشي")
            else if (alternatives.isEmpty()) add("لا يوجد بديل أفضل من المرجع المباشر ضمن حدود المشي")
            if (input.maxWalkStartS == 0 && pickups.any { usable(it) && !it.isDirect })
                add("حد المشي قبل الركوب صفر: استُبعدت نقاط الركوب التي تتطلب مشياً")
            if (input.maxWalkEndS == 0 && dropoffs.any { usable(it) && !it.isDirect })
                add("حد المشي بعد النزول صفر: استُبعدت نقاط النزول التي تتطلب مشياً")
        }
        return Ranked(baseline, alternatives, checked, failed, warnings)
    }

    fun comparator(objective: Objective): Comparator<RouteOption> = when (objective) {
        Objective.MODELED -> compareBy<RouteOption>({ it.modeledS }, { it.walkTotalS }, { it.driveM })
        Objective.DRIVE_TIME -> compareBy({ it.driveS }, { it.modeledS }, { it.walkTotalS })
        Objective.DRIVE_DISTANCE -> compareBy({ it.driveM }, { it.modeledS }, { it.walkTotalS })
    }

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
