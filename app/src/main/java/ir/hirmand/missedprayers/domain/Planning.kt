package ir.hirmand.missedprayers.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun estimatedActiveDays(remaining: Long, dailyTarget: Int): Long {
    require(remaining >= 0 && dailyTarget > 0)
    return if (remaining == 0L) 0L else 1L + (remaining - 1L) / dailyTarget
}

data class PlanProjection(val remaining: Long, val dailyTarget: Int, val activeWeekdaysMask: Int) {
    init { require(remaining >= 0 && dailyTarget > 0 && activeWeekdaysMask in 1..127 && activeWeekdaysMask != 0) }

    fun estimatedCompletionDate(from: LocalDate): LocalDate {
        if (remaining == 0L) return from
        val activePerWeek = Integer.bitCount(activeWeekdaysMask)
        var first = from
        while (activeWeekdaysMask and (1 shl (first.dayOfWeek.value - 1)) == 0) {
            require(first < LocalDate.MAX) { "روز فعال دیگری در محدوده تاریخ قابل نمایش وجود ندارد" }
            first = first.plusDays(1)
        }
        var remainingActiveDays = estimatedActiveDays() - 1
        require(remainingActiveDays <= ChronoUnit.DAYS.between(first, LocalDate.MAX)) {
            "زمان پایان برآوردشده خارج از محدوده تاریخ قابل نمایش است"
        }
        val fullWeeks = remainingActiveDays / activePerWeek
        remainingActiveDays %= activePerWeek
        var candidate = first.plusDays(fullWeeks * 7)
        while (remainingActiveDays > 0) {
            candidate = candidate.plusDays(1)
            if (activeWeekdaysMask and (1 shl (candidate.dayOfWeek.value - 1)) != 0) remainingActiveDays--
        }
        return candidate
    }

    fun estimatedActiveDays(): Long = estimatedActiveDays(remaining, dailyTarget)
}

data class EstimateInputs(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val excludedDays: Set<LocalDate> = emptySet(),
    val prayerCountPerDay: Int = 5
) {
    init {
        require(!endDate.isBefore(startDate))
        require(prayerCountPerDay in 1..5)
        require(excludedDays.all { !it.isBefore(startDate) && !it.isAfter(endDate) })
    }

    val eligibleDays: Long get() = ChronoUnit.DAYS.between(startDate, endDate) + 1 - excludedDays.size
    val estimatedPrayerUnits: Long get() = eligibleDays.coerceAtLeast(0) * prayerCountPerDay
    val estimatedFastingDays: Long get() = eligibleDays.coerceAtLeast(0)
}
