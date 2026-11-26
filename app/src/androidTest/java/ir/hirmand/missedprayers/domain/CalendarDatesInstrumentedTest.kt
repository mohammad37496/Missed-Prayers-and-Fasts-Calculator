package ir.hirmand.missedprayers.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CalendarDatesInstrumentedTest {
    @Test fun knownPersianNewYearConversion() {
        assertEquals(CalendarDate(1403, 1, 1), CalendarDates.toPersian(LocalDate.of(2024, 3, 20)))
        assertEquals(LocalDate.of(2024, 3, 20), CalendarDates.fromPersian(CalendarDate(1403, 1, 1)))
    }

    @Test fun islamicCivilRoundTrip() {
        val gregorian = LocalDate.of(2024, 3, 20)
        assertEquals(gregorian, CalendarDates.fromIslamicCivil(CalendarDates.toIslamicCivil(gregorian)))
    }

    @Test fun rejectsInvalidPersianDate() {
        assertThrows(IllegalArgumentException::class.java) { CalendarDates.fromPersian(CalendarDate(1403, 13, 1)) }
    }
}
