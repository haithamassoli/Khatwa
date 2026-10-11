package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
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
            SearchInput(o, d, objective = Objective.DRIVE_DISTANCE),
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
        val r2 = rank(ps, listOf(directQ to null), drives, SearchInput(o, d, objective = Objective.DRIVE_TIME))
        assertEquals(listOf("P5", "P4", "P3"), r2.alternatives.map { it.pickup.id })
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
        assertTrue(Engine.comparator(Objective.MODELED).compare(b, a) < 0)
        assertTrue(Engine.comparator(Objective.DRIVE_TIME).compare(a, b) < 0)
        assertTrue(Engine.comparator(Objective.DRIVE_DISTANCE).compare(b, a) < 0)
    }
}
