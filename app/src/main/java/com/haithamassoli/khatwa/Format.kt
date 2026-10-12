package com.haithamassoli.khatwa

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Shared display formatting (Arabic). Internal values stay in seconds/meters.
fun fmtDuration(s: Int): String {
    val m = (s / 60.0).roundToInt()
    return if (m < 60) "$m د" else "${m / 60} س ${m % 60} د"
}

fun fmtDistance(m: Int): String = if (m < 1000) "$m م" else String.format(Locale.US, "%.1f كم", m / 1000.0)

fun fmtJod(v: Double): String = String.format(Locale.US, "%.2f د.أ", v)

/** Arabic counted noun: "دقيقة" / "دقيقتين" / "3 دقائق" / "11 دقيقة". */
fun fmtMinutesAr(s: Int): String {
    val m = (s / 60.0).roundToInt()
    return when {
        m < 1 -> "أقل من دقيقة"
        m == 1 -> "دقيقة"
        m == 2 -> "دقيقتين"
        m <= 10 -> "$m دقائق"
        m < 60 -> "$m دقيقة"
        else -> fmtDuration(s)
    }
}

private fun counted(n: Int, one: String, two: String, few: String) = when {
    n <= 1 -> one
    n == 2 -> two
    n <= 10 -> "$n $few"
    else -> "$n $one"
}

/** "بدقيقتين" / "بـ 3 دقائق". */
private fun bi(x: String) = if (x.first().isDigit()) "بـ $x" else "ب$x"

/** How long ago, for "نتيجة محفوظة من …". */
fun fmtAgo(atMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val min = ((nowMs - atMs) / 60_000).toInt().coerceAtLeast(0)
    val h = min / 60
    return when {
        min < 1 -> "لحظات"
        min < 60 -> fmtMinutesAr(min * 60)
        h < 24 -> counted(h, "ساعة", "ساعتين", "ساعات")
        else -> counted(h / 24, "يوم", "يومين", "أيام")
    }
}

/** "✓ تحقّقنا من المسار الفعلي قبل 4 دقائق": traffic changes, so the age matters. */
fun verifiedText(r: SearchResult, nowMs: Long = System.currentTimeMillis()) = "✓ تحقّقنا من المسار الفعلي قبل ${fmtAgo(r.calculatedAtMs, nowMs)}"

fun fmtClock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

/** A difference vs the direct trip. [sign]: -1 better, 0 about the same, 1 worse. The arrow carries the meaning, colour is secondary. */
data class Delta(val text: String, val sign: Int)

fun deltaDuration(deltaS: Int): Delta = when {
    abs(deltaS) < 30 -> Delta("≈ نفس الوقت تقريباً", 0)
    deltaS < 0 -> Delta("↓ أقل بـ ${fmtDuration(-deltaS)}", -1)
    else -> Delta("↑ أكثر بـ ${fmtDuration(deltaS)}", 1)
}

fun deltaDistance(deltaM: Int): Delta = when {
    abs(deltaM) < 50 -> Delta("≈ نفس المسافة تقريباً", 0)
    deltaM < 0 -> Delta("↓ أقصر بـ ${fmtDistance(-deltaM)}", -1)
    else -> Delta("↑ أطول بـ ${fmtDistance(deltaM)}", 1)
}

fun deltaFare(deltaJod: Double): Delta = when {
    abs(deltaJod) < 0.05 -> Delta("≈ الأجرة التقديرية نفسها تقريباً", 0)
    deltaJod < 0 -> Delta("↓ الأجرة التقديرية أقل بنحو ${fmtJod(-deltaJod)}", -1)
    else -> Delta("↑ الأجرة التقديرية أعلى بنحو ${fmtJod(deltaJod)}", 1)
}

/** Signed difference vs baseline. Negative = better. Never call an increase a saving (FR-10). */
fun fmtDeltaDuration(deltaS: Int): String = deltaDuration(deltaS).text

fun fmtDeltaDistance(deltaM: Int): String = deltaDistance(deltaM).text

/**
 * Card headline: the saving on the chosen goal, then what it costs on the other one, so a "deal" never hides
 * a loss: "وفّر 8 دقائق" / "أرخص بـ 0.30 د.أ · أبطأ بـ 4 دقائق".
 */
fun savingTitle(o: RouteOption, base: RouteOption?, objective: Objective): String {
    if (base == null) return "خيار بديل"
    val dS = base.modeledS - o.modeledS          // positive = faster
    val dJod = base.fare() - o.fare()            // positive = cheaper
    val time = when {
        dS >= 30 -> "وفّر ${fmtMinutesAr(dS)}"
        dS > -30 -> "نفس الوقت تقريباً"
        else -> "أبطأ ${bi(fmtMinutesAr(-dS))}"
    }
    val money = when {
        dJod >= 0.05 -> "أرخص بـ ${fmtJod(dJod)}"
        dJod > -0.05 -> "نفس الأجرة تقريباً"
        else -> "أغلى بـ ${fmtJod(-dJod)}"
    }
    return when (objective) {
        Objective.FASTEST -> if (dJod <= -0.10) "$time · $money" else time
        Objective.CHEAPEST -> if (dS <= -60) "$money · $time" else money
    }
}

/** One-line widget hint from the last search: "امشِ 3 د ووفّر 6 د" / "المباشر أفضل". */
fun widgetTip(r: SearchResult): String {
    val base = r.baseline ?: return ""
    val o = r.alternatives.firstOrNull() ?: return "المباشر أفضل"
    val gain = when (r.input.objective) {
        Objective.FASTEST -> fmtDuration(base.modeledS - o.modeledS)
        Objective.CHEAPEST -> fmtJod(base.fare() - o.fare())
    }
    return if (o.walkTotalS > 0) "امشِ ${fmtDuration(o.walkTotalS)} ووفّر $gain" else "وفّر $gain"
}

/** "أمام صيدلية الشفاء (شارع الجامعة)": the street is added only when the name doesn't already contain it. */
fun placeLabel(c: Candidate): String =
    if (c.street.isBlank() || c.name.contains(c.street)) c.name else "${c.name} (${c.street})"

/** One sentence telling the user what to do. */
fun tripSentence(o: RouteOption): String {
    val start = if (o.pickup.isDirect) "اركب من مكانك" else "امشِ ${fmtMinutesAr(o.walkStartS)} إلى ${placeLabel(o.pickup)}"
    val end = if (o.dropoff.isDirect) "وانزل عند الوجهة" else "وانزل قبل الوجهة ${bi(fmtMinutesAr(o.walkEndS))} مشياً"
    return "$start، $end"
}

const val CLIMB_WARN_M = 15

fun climbText(o: RouteOption): String? {
    val s = o.climbStartM >= CLIMB_WARN_M && !o.pickup.isDirect
    val e = o.climbEndM >= CLIMB_WARN_M && !o.dropoff.isDirect
    return when {
        s && e -> "فيه طلعة ~${o.climbStartM} م قبل الركوب و~${o.climbEndM} م بعد النزول"
        s -> "فيه طلعة ~${o.climbStartM} م في المشي إلى نقطة الركوب"
        e -> "فيه طلعة ~${o.climbEndM} م في المشي إلى الوجهة"
        else -> null
    }
}

fun fareDelta(o: RouteOption, base: RouteOption?): Delta? {
    val a = o.fareJod ?: return null
    val b = base?.fareJod ?: return null
    return deltaFare(a - b)
}

/**
 * Warning when the detailed route contradicts the numbers shown on the card (e.g. an unverified matrix estimate);
 * null when they agree. Detail walking legs fall back to the card values when missing.
 */
fun detailMismatch(o: RouteOption, base: RouteOption?, d: OptionDetail): String? {
    val driveS = d.driveS ?: return null
    if (abs(driveS - o.driveS) < max(60, o.driveS / 10)) return null
    val head = "تنبيه: المسار الفعلي بالسيارة يستغرق ${fmtDuration(driveS)} لا ${fmtDuration(o.driveS)}"
    if (base == null || o == base) return "$head."
    val actual = o.copy(driveS = driveS, walkStartS = d.walkStartS ?: o.walkStartS, walkEndS = d.walkEndS ?: o.walkEndS)
    val before = base.modeledS - o.modeledS
    val after = base.modeledS - actual.modeledS
    return when {
        after < 30 -> "$head، فالتوفير اختفى مقارنة بالرحلة المباشرة."
        after < before - 30 -> "$head، فالتوفير تراجع إلى ${fmtMinutesAr(after)}."
        after > before + 30 -> "$head، فالتوفير الفعلي ${fmtMinutesAr(after)}."
        else -> "$head."
    }
}

fun stageText(s: SearchStage): String = when (s) {
    SearchStage.CANDIDATES -> "نبحث عن نقاط ركوب قريبة…"
    SearchStage.WALKING -> "نحسب مسافات المشي…"
    SearchStage.DRIVING -> "نقارن مسارات السيارة…"
    SearchStage.VERIFYING -> "نتحقق من المسار الفعلي…"
    SearchStage.NAMING -> "نحدد المعالم القريبة…"
}

/** Drops the per-warning "دون احتساب انتظار السائق" (shown once as a footnote) and duplicates. */
fun cleanWarnings(ws: List<String>): List<String> = ws
    .map { it.replace(Regex("[،,]?\\s*و?دون احتساب انتظار السائق"), "").trim().trimEnd('،', ',', ' ') }
    .filter { it.isNotBlank() && it != "." }
    .distinct()

/** Accepts Arabic-Indic digits and separators. Returns null for blank; NaN for invalid / non-positive. */
fun parseAmount(s: String): Double? {
    val t = s.trim().map { ch ->
        when (ch) {
            in '٠'..'٩' -> '0' + (ch - '٠')
            '٫', ',' -> '.'
            else -> ch
        }
    }.joinToString("")
    if (t.isEmpty()) return null
    val v = t.toDoubleOrNull() ?: return Double.NaN
    return if (v > 0) v else Double.NaN
}
