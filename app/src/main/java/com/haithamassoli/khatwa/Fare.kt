package com.haithamassoli.khatwa

import kotlin.math.roundToInt

// Rough approximation of Amman economy ride-hailing tariffs (2026), not any app's real price.
object Fare {
    const val BASE_JOD = 0.35
    const val PER_KM_JOD = 0.27
    const val PER_MIN_JOD = 0.04
    const val MIN_JOD = 1.20

    /** Estimated fare in JOD, rounded to the nearest 0.05. */
    fun estimateJod(driveM: Int, driveS: Int): Double {
        val raw = BASE_JOD + PER_KM_JOD * driveM / 1000.0 + PER_MIN_JOD * driveS / 60.0
        return (maxOf(MIN_JOD, raw) * 20).roundToInt() / 20.0
    }
}

/** Fare for ranking and headlines: the priced value, or the estimate before pricing. */
fun RouteOption.fare(): Double = fareJod ?: Fare.estimateJod(driveM, driveS)
