package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProxyTest {
    private val proxy = "https://khatwa-proxy.example.workers.dev/"

    private fun p(url: String) = Google.proxied(url, proxy)

    @Test
    fun routesAndPlacesPosts() {
        assertEquals("https://khatwa-proxy.example.workers.dev/routes/matrix",
            p("https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"))
        assertEquals("https://khatwa-proxy.example.workers.dev/routes/route",
            p("https://routes.googleapis.com/directions/v2:computeRoutes"))
        assertEquals("https://khatwa-proxy.example.workers.dev/places/searchText",
            p("https://places.googleapis.com/v1/places:searchText"))
        assertEquals("https://khatwa-proxy.example.workers.dev/places/searchNearby",
            p("https://places.googleapis.com/v1/places:searchNearby"))
    }

    @Test
    fun getEndpointsDropTheKey() {
        assertEquals("https://khatwa-proxy.example.workers.dev/roads/nearestRoads?points=31.9%2C35.9%7C32.0%2C35.8",
            p("https://roads.googleapis.com/v1/nearestRoads?points=31.9%2C35.9%7C32.0%2C35.8&key=AIzaSECRET"))
        assertEquals("https://khatwa-proxy.example.workers.dev/geocode/json?latlng=31.95,35.91&language=ar",
            p("https://maps.googleapis.com/maps/api/geocode/json?latlng=31.95,35.91&language=ar&key=AIzaSECRET"))
        assertEquals("https://khatwa-proxy.example.workers.dev/elevation/json?locations=31.95,35.91%7C31.96,35.92",
            p("https://maps.googleapis.com/maps/api/elevation/json?locations=31.95,35.91%7C31.96,35.92&key=AIzaSECRET"))
    }

    @Test
    fun keyFirstOrEmptyOrAlone() {
        assertEquals("https://khatwa-proxy.example.workers.dev/elevation/json?locations=1,2",
            p("https://maps.googleapis.com/maps/api/elevation/json?key=AIzaSECRET&locations=1,2"))
        assertEquals("https://khatwa-proxy.example.workers.dev/geocode/json?latlng=1,2",
            p("https://maps.googleapis.com/maps/api/geocode/json?latlng=1,2&key="))
        assertEquals("https://khatwa-proxy.example.workers.dev/geocode/json",
            p("https://maps.googleapis.com/maps/api/geocode/json?key=x"))
    }

    @Test
    fun proxyWithoutTrailingSlash() = assertEquals("https://proxy.test/routes/matrix",
        Google.proxied("https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix", "https://proxy.test"))

    @Test
    fun unknownEndpointIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            p("https://maps.googleapis.com/maps/api/directions/json?origin=a&destination=b&key=x")
        }
    }

    @Test
    fun parsesCommunityPoints() {
        val json = """{"radius":90,"points":[
            {"id":"a","lat":31.95,"lng":35.91,"use":"PICKUP","name":"باب المول","up":4,"down":1,"distanceM":10},
            {"id":"b","lat":31.951,"lng":35.911,"use":"WEIRD","name":"","up":1,"down":0,"distanceM":40},
            {"id":"c","lat":31.952,"lng":35.912,"use":"BOTH","name":"x","up":1,"down":3,"distanceM":60}]}"""
        val pts = CommunityPoints.parseNearby(json)
        assertEquals(listOf("community-a", "community-b"), pts.map { it.id })
        assertEquals(Verification.VERIFIED, pts[0].verification)
        assertEquals(PointUse.PICKUP, pts[0].use)
        assertEquals(4 to 1, pts[0].upVotes to pts[0].downVotes)
        assertEquals(Verification.UNVERIFIED, pts[1].verification)
        assertEquals(PointUse.BOTH, pts[1].use)
        assertEquals("نقطة من المستخدمين", pts[1].name)
        assertEquals(false, pts[1].auto)
    }

    @Test
    fun communityPointsAreNoOpWithoutProxy() = kotlinx.coroutines.runBlocking {
        val cp = CommunityPoints("", "id")
        assertEquals(emptyList<Candidate>(), cp.nearby(Pt(31.95, 35.91), 300))
        cp.feedback(Candidate("x", Pt(31.95, 35.91), "x"), true) // must not touch the network
    }
}
