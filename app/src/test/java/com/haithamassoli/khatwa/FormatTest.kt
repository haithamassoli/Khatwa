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

    @Test fun headlineByObjective() {
        assertEquals("وفّر 3 دقائق", savingTitle(alt, base, Objective.MODELED))
        assertEquals("وفّر 8 دقائق بالسيارة", savingTitle(alt, base, Objective.DRIVE_TIME))
        assertEquals("مسافة أطول بـ 1.0 كم", savingTitle(alt, base, Objective.DRIVE_DISTANCE))
        assertEquals("مسافة أقصر بـ 1.2 كم", savingTitle(alt.copy(driveM = 6100), base, Objective.DRIVE_DISTANCE))
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
        val w = detailMismatch(alt, base, Objective.MODELED, d)
        assertTrue(w!!.contains("اختفى"))
        assertNull(detailMismatch(alt, base, Objective.MODELED, d.copy(driveS = 31 * 60 + 20)))
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
