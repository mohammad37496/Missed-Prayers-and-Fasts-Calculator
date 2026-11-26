package ir.hirmand.missedprayers.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.hirmand.missedprayers.domain.CalendarDates
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CalendarDatesInputTest {
    @Test fun preferredCalendarInputAcceptsLatinPersianAndArabicIndicDigits() {
        val expected = LocalDate.of(2026, 11, 26)
        val persian = CalendarDates.toPersian(expected)
        val islamic = CalendarDates.toIslamicCivil(expected)

        assertEquals(expected, CalendarDates.parsePreferredInput(persian.iso(), "PERSIAN"))
        assertEquals(expected, CalendarDates.parsePreferredInput(persian.iso().toPersianDigits(), "PERSIAN"))
        assertEquals(expected, CalendarDates.parsePreferredInput(islamic.iso().toArabicIndicDigits(), "ISLAMIC_CIVIL"))
        assertEquals(expected, CalendarDates.parsePreferredInput("2026-11-26", "GREGORIAN"))
    }

    private fun String.toPersianDigits(): String = mapDigits('۰')
    private fun String.toArabicIndicDigits(): String = mapDigits('٠')
    private fun String.mapDigits(zero: Char): String = map { character ->
        if (character in '0'..'9') (zero.code + character.code - '0'.code).toChar() else character
    }.joinToString("")
}
