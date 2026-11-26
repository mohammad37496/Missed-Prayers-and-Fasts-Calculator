package ir.hirmand.missedprayers.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtEstimateEngineTest {
    private val context = FiqhEvaluationContext("WOMAN", listOf(RecordedPeriod("MENSTRUATION", LocalDate.parse("2024-01-02"), LocalDate.parse("2024-01-04"))))

    @Test fun unknownRulesNeverSilentlySubtractRecordedPeriods() {
        val result = DebtEstimateEngine.estimate(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-05"), context)
        assertEquals(5, result.prayerUnitsByType[PrayerType.FAJR])
        assertEquals(5, result.fastingDays)
        assertEquals(5, result.prayerDaysNeedingReview)
        assertEquals(5, result.fastingDaysNeedingReview)
        assertTrue(result.requiresReview)
    }

    @Test fun reviewedRuleSetCanDistinguishPrayerAndFasting() {
        val rules = object : ObligationRuleSet {
            override val id = "test-rules"
            override val version = "1"
            override val sourceTitle = "Test source"
            override val sourceUrl = "https://example.org/rules"
            override fun prayerDecision(date: LocalDate, context: FiqhEvaluationContext) = if (date.dayOfMonth == 2) DayDecision.NOT_OBLIGATORY else DayDecision.OBLIGATION
            override fun fastingDecision(date: LocalDate, context: FiqhEvaluationContext) = if (date.dayOfMonth in 2..3) DayDecision.NOT_OBLIGATORY else DayDecision.OBLIGATION
        }
        val result = DebtEstimateEngine.estimate(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-01-04"), context, rules)
        assertEquals(3, result.prayerUnitsByType[PrayerType.ASR])
        assertEquals(2, result.fastingDays)
        assertEquals(0, result.prayerDaysNeedingReview)
    }

    @Test fun estimateBreaksDownAcrossGregorianYearBoundary() {
        val result = DebtEstimateEngine.estimate(LocalDate.parse("2023-12-31"), LocalDate.parse("2024-01-02"), context)
        assertEquals(mapOf(2023 to 1, 2024 to 2), result.prayerDaysByGregorianYear)
        assertEquals(mapOf(2023 to 1, 2024 to 2), result.fastingDaysByGregorianYear)
    }

    @Test fun plannerCalculatesCompletionAcrossNonConsecutiveWeekdays() {
        val tuesdayAndThursday = (1 shl 1) or (1 shl 3)
        val projection = PlanProjection(remaining = 5, dailyTarget = 2, activeWeekdaysMask = tuesdayAndThursday)
        assertEquals(LocalDate.parse("2026-12-03"), projection.estimatedCompletionDate(LocalDate.parse("2026-11-26")))
    }

    @Test fun plannerUsesArithmeticForVeryLargeRemainingCounts() {
        val projection = PlanProjection(remaining = Int.MAX_VALUE, dailyTarget = 100, activeWeekdaysMask = 127)
        assertEquals(21_474_837L, projection.estimatedActiveDays())
        assertEquals(LocalDate.of(60823, 1, 3), projection.estimatedCompletionDate(LocalDate.parse("2026-11-26")))
    }
}
