package com.haithamassoli.khatwa

import kotlin.math.abs
import kotlin.math.roundToInt

// Shared display formatting (Arabic). Internal values stay in seconds/meters.
fun fmtDuration(s: Int): String {
    val m = (s / 60.0).roundToInt()
    return if (m < 60) "$m د" else "${m / 60} س ${m % 60} د"
}

fun fmtDistance(m: Int): String = if (m < 1000) "$m م" else "%.1f كم".format(m / 1000.0)

/** Signed difference vs baseline. Negative = better. Never call an increase a saving (FR-10). */
fun fmtDeltaDuration(deltaS: Int): String = when {
    abs(deltaS) < 30 -> "نفس الوقت تقريباً"
    deltaS < 0 -> "أقل بـ ${fmtDuration(-deltaS)}"
    else -> "أكثر بـ ${fmtDuration(deltaS)}"
}

fun fmtDeltaDistance(deltaM: Int): String = when {
    abs(deltaM) < 50 -> "نفس المسافة تقريباً"
    deltaM < 0 -> "أقصر بـ ${fmtDistance(-deltaM)}"
    else -> "أطول بـ ${fmtDistance(deltaM)}"
}
