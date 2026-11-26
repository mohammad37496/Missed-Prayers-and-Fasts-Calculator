package ir.hirmand.missedprayers.notifications

import ir.hirmand.missedprayers.data.PlannerSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderSchedulerTest {
    @Test fun nextDelayHonorsSelectedWeekdayAndTime() {
        val settings = PlannerSettingsEntity(dailyPrayerTarget = 5, dailyFastingTarget = 1, activeWeekdaysMask = 1 shl 1, reminderEnabled = true, reminderHour = 20, reminderMinute = 0, updatedAt = 0)
        val now = LocalDateTime.parse("2026-11-26T21:00:00") // Thursday; Tuesday is next week.
        val delay = ReminderScheduler.nextDelayMillis(settings, now)
        assertEquals(java.time.Duration.between(now, LocalDateTime.parse("2026-12-01T20:00:00")).toMillis(), delay)
    }
}
