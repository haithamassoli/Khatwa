package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val o = Pt(31.95, 35.91)
    private val d = Pt(31.99, 35.87)
    private val directP = Candidate("O", o, "البداية", isDirect = true)
    private val directQ = Candidate("D", d, "الوجهة", isDirect = true)
    private fun c(id: String, enabled: Boolean = true, v: Verification = Verification.VERIFIED) =
        Candidate(id, o, id, enabled = enabled, verification = v)

    private fun leg(s: Int?, m: Int? = 1000) = Leg(s, m)

    // pickups/dropoffs pair each candidate with its walk seconds; drive[i][j] = (seconds, meters).
    private fun rank(
        pickups: List<Pair<Candidate, Int?>>,
        dropoffs: List<Pair<Candidate, Int?>>,
        drive: List<List<Leg>>,
        input: SearchInput = SearchInput(o, d),
    ) = Engine.rank(input, pickups.map { it.first }, dropoffs.map { it.first },
        pickups.map { leg(it.second) }, dropoffs.map { leg(it.second) }, drive)

    @Test fun modeledImprovementIsNetOfWalk() {
        val r = rank(
            listOf(directP to null, c("P") to 180),
            listOf(directQ to null),
            listOf(listOf(leg(1800, 9000)), listOf(leg(1380, 8000))),
        )
        val alt = r.alternatives.single()
        assertEquals(1800, r.baseline!!.modeledS)
        assertEquals(1560, alt.modeledS)
        assertEquals(240, r.baseline!!.modeledS - alt.modeledS)
    }

    @Test fun directIgnoresWalkLeg() {
        val r = rank(listOf(directP to 999), listOf(directQ to 999), listOf(listOf(leg(1000))))
        assertEquals(0, r.baseline!!.walkTotalS)
    }

    @Test fun walk601Rejected() {
        val r = rank(listOf(c("P") to 601), listOf(directQ to 0), listOf(listOf(leg(100))))
        assertTrue(r.alternatives.isEmpty())
        assertEquals(1, r.pairsChecked)
        assertEquals(0, r.pairsFailed)
    }

    @Test fun walk600Both600Accepted() {
        val r = rank(listOf(c("P") to 600), listOf(c("Q") to 600), listOf(listOf(leg(100))))
        assertEquals(1, r.alternatives.size)
    }

    @Test fun limitsAreIndependentPerSide() {
        val r = rank(listOf(c("P") to 700), listOf(c("Q") to 200), listOf(listOf(leg(100))),
            SearchInput(o, d, maxWalkStartS = 900))
        assertEquals(1, r.alternatives.size) // 700 ok under a 900 start limit
        val r2 = rank(listOf(c("P") to 700), listOf(c("Q") to 200), listOf(listOf(leg(100))))
        assertTrue(r2.alternatives.isEmpty()) // total 900 but 700 > 600 at start
    }

    @Test fun endLimitZeroAllowsOnlyDirectDropoff() {
        val r = rank(
            listOf(directP to null, c("P") to 100),
            listOf(directQ to null, c("Q") to 0),
            listOf(listOf(leg(1800), leg(500)), listOf(leg(1500), leg(400))),
            SearchInput(o, d, maxWalkEndS = 0),
        )
        assertTrue(r.alternatives.all { it.dropoff.isDirect })
        assertEquals("P", r.alternatives.single().pickup.id)
        assertTrue(r.warnings.any { "النزول" in it && "صفر" in it })
    }

    @Test fun samePairUnderSeveralObjectivesShownOnce() {
        val pickups = listOf(directP to null, c("P") to 100)
        val drives = listOf(listOf(leg(1800, 9000)), listOf(leg(1000, 5000)))
        for (obj in Objective.entries) {
            val r = rank(pickups, listOf(directQ to null, directQ to null), drives.map { it + it },
                SearchInput(o, d, objective = obj))
            assertEquals(1, r.alternatives.size)
            assertTrue(r.alternatives.none { it.pickup.isDirect && it.dropoff.isDirect })
        }
    }

    @Test fun missingBaselineIsNull() {
        val r = rank(listOf(directP to null, c("P") to 100), listOf(directQ to null),
            listOf(listOf(leg(null)), listOf(leg(1000))))
        assertNull(r.baseline)
        assertEquals(1, r.alternatives.size)
        assertEquals(1, r.pairsFailed)
        assertTrue(r.warnings.any { "المرجع" in it })
    }

    @Test fun partialFailuresCounted() {
        val r = rank(
            listOf(directP to null, c("P1") to 100, c("P2") to null),
            listOf(directQ to null, c("Q") to 100),
            listOf(
                listOf(leg(1800), leg(1700, null)),
                listOf(leg(1500), leg(1400)),
                listOf(leg(1200), leg(1100)),
            ),
        )
        assertEquals(6, r.pairsChecked)
        assertEquals(3, r.pairsFailed) // missing drive distance + two pairs from P2's walk
        assertNotNull(r.baseline)
        assertTrue(r.warnings.any { it.startsWith("بحث جزئي") && "3" in it && "6" in it })
    }

    @Test fun excludedCandidatesSkipped() {
        val r = rank(
            listOf(directP to null, c("X", enabled = false) to 10, c("R", v = Verification.REPORTED) to 10),
            listOf(directQ to null),
            listOf(listOf(leg(1800)), listOf(leg(100)), listOf(leg(100))),
        )
        assertTrue(r.alternatives.isEmpty())
        assertEquals(1, r.pairsChecked)
    }

    @Test fun dominatedPruned() {
        val r = rank(
            listOf(directP to null, c("A") to 100, c("B") to 200),
            listOf(directQ to null),
            listOf(listOf(leg(1800, 9000)), listOf(leg(1000, 5000)), listOf(leg(1000, 5000))),
        )
        assertEquals(listOf("A"), r.alternatives.map { it.pickup.id })
    }

    @Test fun onlyBetterThanBaselineOnPrimary() {
        val r = rank(
            listOf(directP to null, c("A") to 300),
            listOf(directQ to null),
            listOf(listOf(leg(1800, 9000)), listOf(leg(1600, 5000))), // modeled 1900 > 1800
        )
        assertTrue(r.alternatives.isEmpty())
        val r2 = rank(
            listOf(directP to null, c("A") to 300),
            listOf(directQ to null),
            listOf(listOf(leg(1800, 9000)), listOf(leg(1600, 5000))),
            SearchInput(o, d, objective = Objective.CHEAPEST),
        )
        assertEquals(1, r2.alternatives.size)
    }

    @Test fun maxThreeSortedByObjective() {
        // Trade-off walk vs drive so none is dominated.
        val ps = (1..5).map { c("P$it") to it * 60 }
        val drives = (1..5).map { listOf(leg(1500 - it * 50, 5000 + it)) }
        val r = rank(ps, listOf(directQ to null), drives)
        assertEquals(3, r.alternatives.size)
        assertEquals(listOf("P1", "P2", "P3"), r.alternatives.map { it.pickup.id })
        // Fares 2.65, 2.65, 2.60, 2.55, 2.55: cheapest first, ties by total time.
        val r2 = rank(ps, listOf(directQ to null), drives, SearchInput(o, d, objective = Objective.CHEAPEST))
        assertEquals(listOf("P4", "P5", "P3"), r2.alternatives.map { it.pickup.id })
    }

    @Test fun noValidOptionWarns() {
        val r = rank(listOf(c("P") to 700), listOf(c("Q") to 700), listOf(listOf(leg(100))))
        assertNull(r.baseline)
        assertTrue(r.alternatives.isEmpty())
        assertTrue(r.warnings.any { it.startsWith("لا يوجد خيار صالح") })
    }

    @Test fun comparatorTieBreaks() {
        val a = RouteOption(directP, directQ, 100, 1000, 5000, 0)
        val b = RouteOption(directP, directQ, 0, 1100, 4000, 0) // same modeled, less walk
        assertTrue(Engine.comparator(Objective.FASTEST).compare(b, a) < 0)
        assertTrue(Engine.comparator(Objective.CHEAPEST).compare(b, a) < 0) // 2.15 vs 2.35 د.أ
    }

    @Test fun savingBelowFloorRejected() {
        // Modeled saving 150 s < 180 s floor.
        val r = rank(listOf(directP to null, c("P") to 50), listOf(directQ to null),
            listOf(listOf(leg(1200)), listOf(leg(1000))))
        assertTrue(r.alternatives.isEmpty())
        assertTrue(r.warnings.any { "3 دقائق على الأقل" in it && "المباشرة هي الأفضل" in it })
    }

    @Test fun savingBelowTenPercentRejected() {
        // Baseline 3600 s: the threshold is 360 s, so 300 s is rejected and 400 s accepted.
        val ps = listOf(directP to null, c("P") to 0)
        assertTrue(rank(ps, listOf(directQ to null), listOf(listOf(leg(3600)), listOf(leg(3300)))).alternatives.isEmpty())
        assertEquals(1, rank(ps, listOf(directQ to null), listOf(listOf(leg(3600)), listOf(leg(3200)))).alternatives.size)
    }

    @Test fun fareThreshold() {
        val input = SearchInput(o, d, objective = Objective.CHEAPEST)
        val ps = listOf(directP to null, c("P") to 100)
        // Baseline 1.50 د.أ: the 0.25 floor wins over 10%. 1.35 saves 0.15 (no deal), 1.20 saves 0.30.
        val r = rank(ps, listOf(directQ to null), listOf(listOf(leg(900, 2000)), listOf(leg(900, 1500))), input)
        assertTrue(r.alternatives.isEmpty())
        assertTrue(r.warnings.any { "0.25 د.أ" in it })
        assertEquals(1, rank(ps, listOf(directQ to null), listOf(listOf(leg(900, 2000)), listOf(leg(900, 1000))), input).alternatives.size)
    }

    @Test fun walkLongerThanDriveSavedIsNoDeal() {
        val base = RouteOption(directP, directQ, 0, 1500, 9000, 0)
        val alt = RouteOption(c("P"), directQ, 300, 1300, 8000, 0) // walk 5 min to save 3 min of driving
        assertFalse(Engine.worthIt(Objective.FASTEST, base, alt))
        assertTrue(Engine.worthIt(Objective.CHEAPEST, base.copy(driveS = 1600), alt)) // the fare goal ignores walking
    }

    @Test fun climbAnnotated() {
        val r = rank(listOf(directP to null, c("P") to 120), listOf(directQ to null),
            listOf(listOf(leg(1800)), listOf(leg(1000))))
        assertEquals(0, r.alternatives.single().climbStartM)
        val r2 = Engine.rank(SearchInput(o, d), listOf(directP, c("P")), listOf(directQ), listOf(leg(0), leg(120 + Engine.climbPenaltyS(10))),
            listOf(leg(0)), listOf(listOf(leg(1800)), listOf(leg(1000))), climbStart = listOf(0, 10))
        assertEquals(10, r2.alternatives.single().climbStartM)
        assertEquals(180, r2.alternatives.single().walkStartS)
        assertEquals(0, Engine.climbPenaltyS(-5))
    }

    private fun verified(o: RouteOption, driveS: Int) = o.copy(driveS = driveS, verified = true, estimatedDriveS = o.driveS)

    @Test fun recheckDropsVanishedSaving() {
        // Matrix said 31 vs 39 min; the detailed route says 39 for both.
        val base = verified(RouteOption(directP, directQ, 0, 2340, 9000, 0), 2340)
        val alt = verified(RouteOption(c("P"), directQ, 120, 1860, 8000, 0), 2340)
        val r = Engine.recheck(Objective.FASTEST, base, listOf(alt))
        assertTrue(r.alternatives.isEmpty())
        assertEquals(1, r.dropped)
        assertTrue(r.warnings.single().startsWith("تراجع التوفير بعد التحقق من المسار الفعلي، فاستُبعد بديل"))
    }

    @Test fun recheckWarnsWhenSavingShrinks() {
        val base = verified(RouteOption(directP, directQ, 0, 2340, 9000, 0), 2340)
        val alt = verified(RouteOption(c("P"), directQ, 60, 1500, 8000, 0), 2040) // 780 s -> 240 s
        val r = Engine.recheck(Objective.FASTEST, base, listOf(alt))
        assertEquals(1, r.alternatives.size)
        assertEquals(0, r.dropped)
        assertEquals(listOf("التوفير تراجع إلى 4 د بسبب الزحمة"), r.warnings)
    }

    @Test fun recheckQuietWhenSavingHolds() {
        val base = verified(RouteOption(directP, directQ, 0, 2340, 9000, 0), 2400)
        val a = verified(RouteOption(c("A"), directQ, 60, 1500, 8000, 0), 1530)
        val b = verified(RouteOption(c("B"), directQ, 30, 1600, 8000, 0), 1700)
        val r = Engine.recheck(Objective.FASTEST, base, listOf(b, a))
        assertEquals(listOf("A", "B"), r.alternatives.map { it.pickup.id })
        assertTrue(r.warnings.isEmpty())
        assertEquals(listOf(b, a).sortedBy { it.modeledS }, Engine.recheck(Objective.FASTEST, null, listOf(b, a)).alternatives)
    }
}
