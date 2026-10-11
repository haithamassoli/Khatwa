package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ResultCacheTest {
    private val o = Pt(31.95123, 35.91234)
    private val d = Pt(31.99001, 35.87002)
    private val input = SearchInput(o, d, 300, 240, Objective.DRIVE_TIME)

    private fun result(): SearchResult {
        val p = Candidate("auto-1", Pt(31.95201, 35.91301), "أمام صيدلية الشفاء", landmark = "صيدلية الشفاء", street = "شارع الجامعة",
            use = PointUse.PICKUP, verification = Verification.UNVERIFIED, auto = true)
        val q = Candidate("saved", Pt(31.98901, 35.87101), "باب المول", "جهة المول", use = PointUse.BOTH, upVotes = 2, downVotes = 1)
        val dp = Candidate("direct-start", o, "نقطة البداية", isDirect = true)
        val dq = Candidate("direct-end", d, "الوجهة", isDirect = true)
        val line = listOf(Pt(31.95201, 35.91301), Pt(31.96, 35.9), Pt(31.98901, 35.87101))
        return SearchResult(
            requestId = 7,
            input = input,
            baseline = RouteOption(dp, dq, 0, 2340, 9000, 0, verified = true, estimatedDriveS = 2100, drivePolyline = line, fareJod = 3.6),
            alternatives = listOf(RouteOption(p, q, 140, 1800, 8000, 90, verified = true, estimatedDriveS = 1700,
                drivePolyline = line.reversed(), climbStartM = 12, climbEndM = 0, fareJod = 2.95)),
            warnings = listOf("التوفير تراجع إلى 4 د بسبب الزحمة"),
            trafficAware = true,
            calculatedAtMs = 1_790_000_000_000,
            pairsChecked = 20,
            pairsFailed = 0,
            elementsUsed = 32,
            droppedAfterCheck = 1,
            baselinePolyline = line,
        )
    }

    @Test fun roundTrip() {
        val r = result()
        assertEquals(r.copy(requestId = 9), ResultCache.decode(ResultCache.encode(r), requestId = 9))
    }

    @Test fun roundTripWithoutBaseline() {
        val r = result().copy(baseline = null, alternatives = emptyList(), baselinePolyline = emptyList())
        assertEquals(r.copy(requestId = 0), ResultCache.decode(ResultCache.encode(r)))
    }

    @Test fun garbageIsNull() = assertNull(ResultCache.decode("{not json"))

    private fun at(y: Int, m: Int, day: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, day, h, min, 0, 0, ZoneId.of("Asia/Amman")).toInstant().toEpochMilli()

    @Test fun keyGroupsNearbyTripsAtTheSameHour() {
        // Sun 11 Oct 2026, 08:05 vs 08:50, origin moved ~20 m.
        val k = ResultCache.key(input, at(2026, 10, 11, 8, 5))
        assertEquals(k, ResultCache.key(input.copy(origin = Pt(31.95141, 35.91240)), at(2026, 10, 11, 8, 50)))
        assertEquals(k, ResultCache.key(input, at(2026, 10, 12, 8, 30))) // Monday, also a weekday
        assertNotEquals(k, ResultCache.key(input, at(2026, 10, 11, 9, 5)))
        assertNotEquals(k, ResultCache.key(input, at(2026, 10, 16, 8, 5))) // Friday = weekend
        assertNotEquals(k, ResultCache.key(input.copy(maxWalkEndS = 300), at(2026, 10, 11, 8, 5)))
        assertNotEquals(k, ResultCache.key(input.copy(objective = Objective.MODELED), at(2026, 10, 11, 8, 5)))
        assertNotEquals(k, ResultCache.key(input.copy(destination = Pt(31.992, 35.87002)), at(2026, 10, 11, 8, 5)))
    }
}
