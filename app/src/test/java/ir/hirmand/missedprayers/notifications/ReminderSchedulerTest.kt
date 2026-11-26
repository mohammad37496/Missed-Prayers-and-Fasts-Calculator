package ir.hirmand.missedprayers.notifications

import ir.hirmand.missedprayers.data.PlannerSettingsEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderSchedulerTest {
    @Test fun nextDelayHonorsSelectedWeekdayAndTime() {
        val settings = PlannerSettingsEntity(dailyPrayerTarget = 5, dailyFastingTarget = 1, activeWeekdaysMask = 1 shl 1, reminderEnabled = true, reminderHour = 20, reminderMinute = 0, updatedAt = 0)
        val now = LocalDateTime.parse("2026-11-26T21:00:00") // Thursday; Tuesday is next week.
        val delay = ReminderScheduler.nextDelayMillis(settings, now)
        assertEquals(java.time.Duration.between(now, LocalDateTime.parse("2026-12-01T20:00:00")).toMillis(), delay)
    }

    @Test fun nextDelayUsesElapsedTimeAcrossSpringForward() {
        val settings = PlannerSettingsEntity(dailyPrayerTarget = 5, dailyFastingTarget = 1, activeWeekdaysMask = 1 shl 6, reminderEnabled = true, reminderHour = 3, reminderMinute = 0, updatedAt = 0)
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDateTime.parse("2026-03-08T01:30:00"), zone)
        assertEquals(Duration.ofMinutes(30).toMillis(), ReminderScheduler.nextDelayMillis(settings, now))
    }

    @Test fun nonexistentSpringForwardReminderUsesNextValidLocalTime() {
        val settings = PlannerSettingsEntity(dailyPrayerTarget = 5, dailyFastingTarget = 1, activeWeekdaysMask = 1 shl 6, reminderEnabled = true, reminderHour = 2, reminderMinute = 30, updatedAt = 0)
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(LocalDateTime.parse("2026-03-08T01:30:00"), zone)
        assertEquals(Duration.ofMinutes(60).toMillis(), ReminderScheduler.nextDelayMillis(settings, now))
    }

    @Test fun nextDelayUsesSecondOccurrenceWhenFirstFallBackTimeHasPassed() {
        val settings = PlannerSettingsEntity(dailyPrayerTarget = 5, dailyFastingTarget = 1, activeWeekdaysMask = 1 shl 6, reminderEnabled = true, reminderHour = 1, reminderMinute = 45, updatedAt = 0)
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.ofLocal(LocalDateTime.parse("2026-11-01T01:50:00"), zone, ZoneOffset.ofHours(-4))
        assertEquals(Duration.ofMinutes(55).toMillis(), ReminderScheduler.nextDelayMillis(settings, now))
    }
}
