package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Test

class FareTest {
    @Test fun shortTripHitsMinimum() = assertEquals(1.20, Fare.estimateJod(1000, 180), 1e-9)

    @Test fun typicalTrip() {
        // 0.35 + 9 km * 0.27 + 20 min * 0.04 = 3.58 -> 3.60
        assertEquals(3.60, Fare.estimateJod(9000, 1200), 1e-9)
    }

    @Test fun longerTripCostsMore() = assert(Fare.estimateJod(12000, 1800) > Fare.estimateJod(9000, 1200))
}
