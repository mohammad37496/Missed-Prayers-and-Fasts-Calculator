package ir.hirmand.missedprayers.domain

import java.time.LocalDate

enum class DayDecision { OBLIGATION, NOT_OBLIGATORY, NEEDS_REVIEW }

data class FiqhEvaluationContext(
    val gender: String?,
    val recordedPeriods: List<RecordedPeriod>
)

data class RecordedPeriod(val kind: String, val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) }
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

interface ObligationRuleSet {
    val id: String
    val version: String
    val sourceTitle: String
    val sourceUrl: String
    fun prayerDecision(date: LocalDate, context: FiqhEvaluationContext): DayDecision
    fun fastingDecision(date: LocalDate, context: FiqhEvaluationContext): DayDecision
}

/** No fiqh rule is silently inferred. Dates remain provisional until a reviewed source-backed rule set is supplied. */
object ManualEstimateRuleSet : ObligationRuleSet {
    override val id = "manual-estimate-only"
    override val version = "1"
    override val sourceTitle = "برآورد خام کاربر"
    override val sourceUrl = ""
    override fun prayerDecision(date: LocalDate, context: FiqhEvaluationContext) = DayDecision.NEEDS_REVIEW
    override fun fastingDecision(date: LocalDate, context: FiqhEvaluationContext) = DayDecision.NEEDS_REVIEW
}

data class DebtEstimate(
    val prayerUnitsByType: Map<PrayerType, Int>,
    val fastingDays: Int,
    val prayerDaysByGregorianYear: Map<Int, Int>,
    val fastingDaysByGregorianYear: Map<Int, Int>,
    val prayerDaysNeedingReview: Int,
    val fastingDaysNeedingReview: Int,
    val ruleSetId: String,
    val ruleSetVersion: String,
    val sourceTitle: String,
    val sourceUrl: String
) {
    val requiresReview: Boolean get() = prayerDaysNeedingReview > 0 || fastingDaysNeedingReview > 0
}

object DebtEstimateEngine {
    fun estimate(
        start: LocalDate,
        endInclusive: LocalDate,
        context: FiqhEvaluationContext,
        rules: ObligationRuleSet = ManualEstimateRuleSet
    ): DebtEstimate {
        require(!endInclusive.isBefore(start))
        require(java.time.temporal.ChronoUnit.DAYS.between(start, endInclusive) <= 100_000) { "بازه برآورد بیش از حد بزرگ است" }
        var prayerDays = 0
        var fastingDays = 0
        var prayerReview = 0
        var fastingReview = 0
        val prayerByYear = linkedMapOf<Int, Int>()
        val fastingByYear = linkedMapOf<Int, Int>()
        var date = start
        while (!date.isAfter(endInclusive)) {
            when (rules.prayerDecision(date, context)) {
                DayDecision.OBLIGATION -> { prayerDays++; prayerByYear[date.year] = (prayerByYear[date.year] ?: 0) + 1 }
                DayDecision.NOT_OBLIGATORY -> Unit
                DayDecision.NEEDS_REVIEW -> { prayerDays++; prayerReview++; prayerByYear[date.year] = (prayerByYear[date.year] ?: 0) + 1 }
            }
            when (rules.fastingDecision(date, context)) {
                DayDecision.OBLIGATION -> { fastingDays++; fastingByYear[date.year] = (fastingByYear[date.year] ?: 0) + 1 }
                DayDecision.NOT_OBLIGATORY -> Unit
                DayDecision.NEEDS_REVIEW -> { fastingDays++; fastingReview++; fastingByYear[date.year] = (fastingByYear[date.year] ?: 0) + 1 }
            }
            date = date.plusDays(1)
        }
        val prayerCount = prayerDays.coerceAtMost(Int.MAX_VALUE)
        return DebtEstimate(PrayerType.entries.associateWith { prayerCount }, fastingDays.coerceAtMost(Int.MAX_VALUE), prayerByYear, fastingByYear, prayerReview, fastingReview, rules.id, rules.version, rules.sourceTitle, rules.sourceUrl)
    }
}
