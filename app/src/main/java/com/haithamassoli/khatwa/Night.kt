package com.haithamassoli.khatwa

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// Pure Kotlin sunrise/sunset (NOAA simplified), accurate to a few minutes; enough for "after maghrib".
object Night {
    /** True between sunset and the next sunrise at [pt]. */
    fun isNight(pt: Pt, nowMs: Long = System.currentTimeMillis()): Boolean {
        val (rise, set) = sunUtcMinutes(pt, nowMs) ?: return false
        val t = Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.UTC).let { it.hour * 60 + it.minute }.toDouble()
        return if (rise < set) t < rise || t >= set else t >= set && t < rise
    }

    /** (sunrise, sunset) in UTC minutes of the day, or null in polar day/night. */
    fun sunUtcMinutes(pt: Pt, nowMs: Long): Pair<Double, Double>? {
        val day = Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.UTC).dayOfYear
        val g = 2 * Math.PI / 365 * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(pt.lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
        if (cosHa !in -1.0..1.0) return null
        val ha = Math.toDegrees(acos(cosHa))
        val rise = (720 - 4 * (pt.lng + ha) - eqTime).mod(1440.0)
        val set = (720 - 4 * (pt.lng - ha) - eqTime).mod(1440.0)
        return rise to set
    }
}
