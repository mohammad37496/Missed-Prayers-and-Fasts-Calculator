package ir.hirmand.missedprayers.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prayer_debts")
data class PrayerDebtEntity(
    @PrimaryKey val prayerType: String,
    val total: Int,
    val completed: Int,
    val updatedAt: Long
)

@Entity(tableName = "fasting_debt")
data class FastingDebtEntity(
    @PrimaryKey val id: Int = 1,
    val total: Int,
    val completed: Int,
    val updatedAt: Long
)

@Entity(tableName = "debt_records")
data class DebtRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val reference: String,
    val amount: Int,
    val createdAt: Long,
    val beforeCompleted: Int = 0,
    val afterCompleted: Int = 0
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String?,
    val gender: String,
    val taklifStartDate: String?,
    val taklifEndDate: String?,
    val timezoneId: String,
    val calendarPreference: String,
    val marjaId: String?,
    val updatedAt: Long
)

@Entity(tableName = "excuse_periods")
data class ExcusePeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val startDate: String,
    val endDate: String,
    val note: String?,
    val createdAt: Long
)

@Entity(tableName = "planner_settings")
data class PlannerSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val dailyPrayerTarget: Int,
    val dailyFastingTarget: Int,
    val activeWeekdaysMask: Int,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val updatedAt: Long
)

@Entity(tableName = "fiqh_preferences")
data class FiqhPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val marjaId: String?,
    val rulesetVersion: String?,
    val acceptedSourceUrl: String?,
    val updatedAt: Long
)

@Entity(tableName = "daily_verses")
data class DailyVerseEntity(
    @PrimaryKey val dayKey: String,
    val surah: Int,
    val ayah: Int,
    val globalNumber: Int,
    val arabicText: String,
    val persianTranslation: String,
    val translationSource: String,
    val translationVersion: String,
    val tafsirText: String?,
    val tafsirSource: String?,
    val recitationUrl: String?,
    val reciterName: String?,
    val fetchedAt: Long
)
