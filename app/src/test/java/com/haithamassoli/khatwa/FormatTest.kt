package com.haithamassoli.khatwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    private val origin = Pt(31.95, 35.90)
    private val dest = Pt(31.99, 35.87)
    private val direct = Candidate("o", origin, "البداية", isDirect = true)
    private val directEnd = Candidate("d", dest, "الوجهة", isDirect = true)
    private val pharmacy = Candidate("p", Pt(31.951, 35.901), "أمام صيدلية الشفاء", street = "شارع الجامعة", auto = true)
    private val drop = Candidate("q", Pt(31.989, 35.871), "دوار الواحة", auto = true)
    private val base = RouteOption(direct, directEnd, 0, 39 * 60, 7300, 0)
    private val alt = RouteOption(pharmacy, drop, 180, 31 * 60, 8300, 120)

    @Test fun minutesWords() {
        assertEquals("أقل من دقيقة", fmtMinutesAr(20))
        assertEquals("دقيقة", fmtMinutesAr(60))
        assertEquals("دقيقتين", fmtMinutesAr(120))
        assertEquals("3 دقائق", fmtMinutesAr(180))
        assertEquals("11 دقيقة", fmtMinutesAr(660))
    }

    @Test fun headlineNamesTheTradeOff() {
        val b = base.copy(fareJod = 2.5)
        assertEquals("وفّر 3 دقائق", savingTitle(alt.copy(fareJod = 2.5), b, Objective.FASTEST))
        assertEquals("وفّر 3 دقائق · أغلى بـ 0.50 د.أ", savingTitle(alt.copy(fareJod = 3.0), b, Objective.FASTEST))
        assertEquals("أرخص بـ 0.50 د.أ", savingTitle(alt.copy(fareJod = 2.0), b, Objective.CHEAPEST))
        // Cheaper but slower: the loss is in the headline, not hidden in the numbers.
        assertEquals("أرخص بـ 0.50 د.أ · أبطأ بـ 4 دقائق", savingTitle(alt.copy(fareJod = 2.0, walkStartS = 600), b, Objective.CHEAPEST))
    }

    @Test fun widgetTipFromResult() {
        val r = SearchResult(1, SearchInput(origin, dest), base.copy(fareJod = 2.5), listOf(alt), emptyList(), true, 0, 1, 0, 1)
        assertEquals("امشِ 5 د ووفّر 3 د", widgetTip(r))
        assertEquals("المباشر أفضل", widgetTip(r.copy(alternatives = emptyList())))
    }

    @Test fun sentence() {
        assertEquals(
            "امشِ 3 دقائق إلى أمام صيدلية الشفاء (شارع الجامعة)، وانزل قبل الوجهة بدقيقتين مشياً",
            tripSentence(alt),
        )
        assertEquals("اركب من مكانك، وانزل عند الوجهة", tripSentence(base))
        assertEquals("اركب من مكانك، وانزل قبل الوجهة بـ 3 دقائق مشياً", tripSentence(alt.copy(pickup = direct, walkStartS = 0, walkEndS = 180)))
    }

    @Test fun deltasCarryArrows() {
        assertTrue(deltaDuration(-480).text.startsWith("↓"))
        assertTrue(deltaDuration(300).text.startsWith("↑"))
        assertEquals(0, deltaDuration(10).sign)
        assertEquals("↑ الأجرة التقديرية أعلى بنحو 0.30 د.أ", fareDelta(alt.copy(fareJod = 2.8), base.copy(fareJod = 2.5))?.text)
        assertNull(fareDelta(alt, base.copy(fareJod = 2.5)))
    }

    @Test fun climb() {
        assertNull(climbText(alt.copy(climbStartM = 10)))
        assertEquals("فيه طلعة ~20 م في المشي إلى نقطة الركوب", climbText(alt.copy(climbStartM = 20)))
    }

    @Test fun mismatchWarnsWhenSavingDisappears() {
        val d = OptionDetail(emptyList(), emptyList(), emptyList(), 180, 39 * 60, 120)
        val w = detailMismatch(alt, base, d)
        assertTrue(w!!.contains("اختفى"))
        assertNull(detailMismatch(alt, base, d.copy(driveS = 31 * 60 + 20)))
    }

    @Test fun warningsDropDriverWaitAndDuplicates() {
        val ws = listOf("يفترض أن الركوب بعد نحو 2 د، دون احتساب انتظار السائق.", "تنبيه", "تنبيه")
        assertEquals(listOf("يفترض أن الركوب بعد نحو 2 د.", "تنبيه"), cleanWarnings(ws))
    }

    @Test fun agoAndAmount() {
        assertEquals("5 دقائق", fmtAgo(0, 5 * 60_000L))
        assertEquals("ساعتين", fmtAgo(0, 2 * 3_600_000L))
        assertEquals(2.5, parseAmount("٢٫٥")!!, 1e-9)
        assertTrue(parseAmount("0")!!.isNaN())
    }
}
