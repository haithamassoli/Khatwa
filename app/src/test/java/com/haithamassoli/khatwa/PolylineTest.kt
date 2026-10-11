package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {
    @Test
    fun decodesGoogleReferenceExample() {
        // Example from Google's "Encoded Polyline Algorithm Format" docs.
        val pts = Google.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        val expected = listOf(Pt(38.5, -120.2), Pt(40.7, -120.95), Pt(43.252, -126.453))
        assertEquals(expected.size, pts.size)
        expected.zip(pts).forEach { (e, a) ->
            assertEquals(e.lat, a.lat, 1e-9)
            assertEquals(e.lng, a.lng, 1e-9)
        }
    }

    @Test
    fun emptyStringDecodesToEmptyList() = assertEquals(emptyList<Pt>(), Google.decodePolyline(""))
}
