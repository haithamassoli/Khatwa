package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingLinksTest {
    private fun assertPt(lat: Double, lng: Double, pt: Pt?) {
        assertNotNull("expected a point", pt)
        assertEquals(lat, pt!!.lat, 1e-6)
        assertEquals(lng, pt.lng, 1e-6)
    }

    // geo: URIs

    @Test fun geoPlainCoordinates() {
        val p = IncomingLinks.parseGeo("geo:31.9539,35.9106")!!
        assertPt(31.9539, 35.9106, p.pt)
        assertEquals("", p.label)
    }

    @Test fun geoWithZoom() = assertPt(31.9539, 35.9106, IncomingLinks.parseGeo("geo:31.9539,35.9106?z=17")!!.pt)

    @Test fun geoZeroWithCoordinatesAndLabel() {
        val p = IncomingLinks.parseGeo("geo:0,0?q=31.9853,35.8721(University%20of%20Jordan)")!!
        assertPt(31.9853, 35.8721, p.pt)
        assertEquals("University of Jordan", p.label)
    }

    @Test fun geoArabicLabel() {
        val p = IncomingLinks.parseGeo("geo:0,0?q=31.9566,35.9101(%D8%A7%D9%84%D8%B9%D8%A8%D8%AF%D9%84%D9%8A)")!!
        assertPt(31.9566, 35.9101, p.pt)
        assertEquals("العبدلي", p.label)
    }

    @Test fun geoAddressQuery() {
        val p = IncomingLinks.parseGeo("geo:0,0?q=Abdali+Mall,+Amman")!!
        assertNull(p.pt)
        assertEquals("Abdali Mall, Amman", p.label)
    }

    @Test fun geoCoordinatesWithAddressQueryKeepsPathPoint() {
        val p = IncomingLinks.parseGeo("geo:31.95,35.91?q=Rainbow+Street")!!
        assertPt(31.95, 35.91, p.pt)
        assertEquals("Rainbow Street", p.label)
    }

    @Test fun notGeo() = assertNull(IncomingLinks.parseGeo("https://maps.google.com/?q=1,2"))

    // Google Maps URLs

    @Test fun placeUrlPrefersPinOverViewport() {
        val url = "https://www.google.com/maps/place/Abdali+Mall/@31.9601,35.9050,17z/data=!3m1!4b1!4m6!3m5!1s0x151ca1e0d3b0c3a9:0x8e9b!8m2!3d31.9612345!4d35.9123456!16s%2Fg%2F11b6"
        assertPt(31.9612345, 35.9123456, IncomingLinks.coordsFromUrl(url))
        assertEquals("Abdali Mall", IncomingLinks.nameFromUrl(url))
    }

    @Test fun placeUrlArabicName() {
        val url = "https://www.google.com/maps/place/%D8%AC%D8%A7%D9%85%D8%B9%D8%A9+%D8%A7%D9%84%D8%A3%D8%B1%D8%AF%D9%86/@31.98,35.87,15z"
        assertPt(31.98, 35.87, IncomingLinks.coordsFromUrl(url))
        assertEquals("جامعة الأردن", IncomingLinks.nameFromUrl(url))
    }

    @Test fun viewportOnly() = assertPt(31.95, 35.93, IncomingLinks.coordsFromUrl("https://www.google.com/maps/@31.95,35.93,15z"))

    @Test fun qParam() = assertPt(31.9539, 35.9106, IncomingLinks.coordsFromUrl("https://maps.google.com/?q=31.9539,35.9106"))

    @Test fun qParamWithSpace() = assertPt(31.9539, 35.9106, IncomingLinks.coordsFromUrl("https://maps.google.com/maps?q=31.9539,+35.9106&z=16"))

    @Test fun llParam() = assertPt(31.9, 35.8, IncomingLinks.coordsFromUrl("https://maps.google.com/maps?ll=31.9,35.8&z=12"))

    @Test fun queryParamApi() = assertPt(31.97, 35.84,
        IncomingLinks.coordsFromUrl("https://www.google.com/maps/search/?api=1&query=31.97%2C35.84"))

    @Test fun destinationParam() = assertPt(31.99, 35.86,
        IncomingLinks.coordsFromUrl("https://www.google.com/maps/dir/?api=1&destination=31.99,35.86&travelmode=driving"))

    @Test fun searchPathCoordinates() = assertPt(31.95, 35.91,
        IncomingLinks.coordsFromUrl("https://www.google.com/maps/search/31.95,+35.91?entry=tts"))

    @Test fun textQueryHasNoCoordinates() {
        val url = "https://www.google.com/maps/search/?api=1&query=Taj+Mall+Amman"
        assertNull(IncomingLinks.coordsFromUrl(url))
        assertEquals("Taj Mall Amman", IncomingLinks.nameFromUrl(url))
    }

    @Test fun cidLinkHasNothing() {
        val url = "https://maps.google.com/?cid=10281119596374313554&entry=gps"
        assertNull(IncomingLinks.coordsFromUrl(url))
        assertEquals("", IncomingLinks.nameFromUrl(url))
    }

    @Test fun consentWrapperIsUnwrapped() {
        val url = "https://consent.google.com/ml?continue=https://www.google.com/maps/place/Mecca%2BMall/@31.97,35.84,17z/data%3D!3d31.9755!4d35.8442&gl=JO"
        assertPt(31.9755, 35.8442, IncomingLinks.coordsFromUrl(url))
    }

    @Test fun shortLinks() {
        assertTrue(IncomingLinks.isShortLink("https://maps.app.goo.gl/AbCd123XyZ"))
        assertTrue(IncomingLinks.isShortLink("https://goo.gl/maps/AbCd123"))
        assertFalse(IncomingLinks.isShortLink("https://goo.gl/AbCd123"))
        assertFalse(IncomingLinks.isShortLink("https://www.google.com/maps/@31.95,35.93,15z"))
    }

    @Test fun invalidCoordinatesRejected() {
        assertNull(IncomingLinks.parseLatLng("0,0"))
        assertNull(IncomingLinks.parseLatLng("95,35"))
        assertNull(IncomingLinks.coordsFromUrl("https://maps.google.com/?q=0,0"))
    }

    // Shared text (Google Maps "Share")

    @Test fun mapsShareNameAndShortLink() {
        val p = IncomingLinks.plan("Abdali Mall\nhttps://maps.app.goo.gl/AbCd123XyZ", null)!!
        assertNull(p.pt)
        assertEquals("Abdali Mall", p.name)
        assertEquals("Abdali Mall", p.query)
        assertEquals("https://maps.app.goo.gl/AbCd123XyZ", p.shortUrl)
    }

    @Test fun mapsShareNameAddressAndLink() {
        val text = "مكة مول\nشارع مكة، عمّان\nhttps://maps.app.goo.gl/q1W2e3R4t5Y6u7I8"
        val p = IncomingLinks.plan(text, null)!!
        assertEquals("مكة مول", p.name)
        assertEquals("مكة مول, شارع مكة، عمّان", p.query)
        assertEquals("https://maps.app.goo.gl/q1W2e3R4t5Y6u7I8", p.shortUrl)
    }

    @Test fun mapsShareLinkOnly() {
        val p = IncomingLinks.plan("https://maps.app.goo.gl/q1W2e3R4t5Y6u7I8", null)!!
        assertEquals("", p.name)
        assertEquals("https://maps.app.goo.gl/q1W2e3R4t5Y6u7I8", p.shortUrl)
    }

    @Test fun droppedPinWithCoordinates() {
        val p = IncomingLinks.plan("Dropped pin\n31.9539, 35.9106\nhttps://maps.app.goo.gl/abc", null)!!
        assertPt(31.9539, 35.9106, p.pt)
        assertEquals("", p.name) // generic label: the app reverse-geocodes instead
    }

    @Test fun droppedPinSameLine() {
        val p = IncomingLinks.plan("Dropped pin 31.9700, 35.9100", null)!!
        assertPt(31.97, 35.91, p.pt)
        assertEquals("", p.name)
    }

    @Test fun shareWithFullUrl() {
        val p = IncomingLinks.plan("Rainbow Street https://www.google.com/maps/place/Rainbow+St/@31.9515,35.9230,17z/data=!3d31.9517!4d35.9231", null)!!
        assertPt(31.9517, 35.9231, p.pt)
        assertEquals("Rainbow Street", p.name)
        assertNull(p.shortUrl)
    }

    @Test fun plainCoordinatesText() = assertPt(31.9539, 35.9106, IncomingLinks.plan("31.9539, 35.9106", null)!!.pt)

    @Test fun plainAddressText() {
        val p = IncomingLinks.plan("Taj Mall, Abdoun", null)!!
        assertNull(p.pt)
        assertEquals("Taj Mall, Abdoun", p.query)
    }

    @Test fun viewGeoData() {
        val p = IncomingLinks.plan(null, "geo:0,0?q=31.9853,35.8721(Dropped pin)")!!
        assertPt(31.9853, 35.8721, p.pt)
        assertEquals("", p.name)
    }

    @Test fun viewMapsUrlData() {
        val p = IncomingLinks.plan(null, "https://maps.google.com/?q=31.9539,35.9106")!!
        assertPt(31.9539, 35.9106, p.pt)
    }

    @Test fun emptyInput() {
        assertNull(IncomingLinks.plan("", null))
        assertNull(IncomingLinks.plan(null, null))
    }

    // Trip launch helpers

    @Test fun quickPlacePrefersWorkInTheMorningAndHomeLater() {
        val home = SavedPlace("h", PlaceKind.HOME, "البيت", Pt(31.9, 35.9))
        val work = SavedPlace("w", PlaceKind.WORK, "العمل", Pt(31.95, 35.85))
        val other = SavedPlace("o", PlaceKind.OTHER, "النادي", Pt(31.99, 35.88))
        assertEquals(work, TripLaunch.quickPlace(listOf(home, work), 8))
        assertEquals(home, TripLaunch.quickPlace(listOf(home, work), 18))
        assertEquals(home, TripLaunch.quickPlace(listOf(home), 8))
        assertEquals(other, TripLaunch.quickPlace(listOf(other), 18))
        assertNull(TripLaunch.quickPlace(emptyList(), 10))
        assertEquals(listOf(home, work, other), TripLaunch.ordered(listOf(other, work, home)))
    }

    @Test fun uberQueryIsEncoded() {
        val q = Integrations.uberQuery(Pt(31.95, 35.91), "مكة مول", Pt(31.98, 35.87), "")
        assertTrue(q.startsWith("action=setPickup&pickup%5Blatitude%5D=31.950000&pickup%5Blongitude%5D=35.910000&pickup%5Bnickname%5D=%D9%85"))
        assertFalse(q.contains("dropoff%5Bnickname%5D"))
        assertTrue(q.contains("dropoff%5Blatitude%5D=31.980000"))
    }

    @Test fun clipText() {
        assertEquals("مكة مول", Integrations.pickupClipText(Pt(31.95, 35.91), "مكة مول"))
        assertEquals("صيدلية الشفاء", Integrations.pickupClipText(Pt(31.95, 35.91), "أمام صيدلية الشفاء"))
        assertEquals("31.950000,35.910000", Integrations.pickupClipText(Pt(31.95, 35.91), "نقطة مقترحة · 120 م شمال"))
    }
}
