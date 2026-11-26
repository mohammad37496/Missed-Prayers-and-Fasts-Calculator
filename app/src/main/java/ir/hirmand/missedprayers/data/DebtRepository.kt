package ir.hirmand.missedprayers.data

import androidx.room.withTransaction
import ir.hirmand.missedprayers.domain.PrayerType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

class DebtRepository(private val database: AppDatabase) {
    private val dao = database.debtDao()
    val prayers: Flow<List<PrayerDebtEntity>> = dao.observePrayers()
    val fasting: Flow<FastingDebtEntity?> = dao.observeFasting()
    val recentRecords: Flow<List<DebtRecordEntity>> = dao.observeRecentRecords()
    val allRecords: Flow<List<DebtRecordEntity>> = dao.observeAllRecords()
    val profile: Flow<UserProfileEntity?> = dao.observeProfile()
    val excusePeriods: Flow<List<ExcusePeriodEntity>> = dao.observeExcusePeriods()
    val plannerSettings: Flow<PlannerSettingsEntity?> = dao.observePlannerSettings()
    val fiqhPreferences: Flow<FiqhPreferencesEntity?> = dao.observeFiqhPreferences()

    suspend fun saveProfile(profile: UserProfileEntity) {
        validateProfile(profile)
        dao.saveProfile(profile)
    }

    suspend fun saveProfileAndApplyRawDateEstimate(profile: UserProfileEntity, prayerPerType: Int, fastingDays: Int) {
        validateProfile(profile)
        validateRawEstimate(prayerPerType, fastingDays)
        database.withTransaction {
            dao.saveProfile(profile)
            applyRawDateEstimateInTransaction(prayerPerType, fastingDays)
        }
    }

    private fun validateProfile(profile: UserProfileEntity) {
        require(profile.gender in setOf("UNSPECIFIED", "WOMAN", "MAN", "PREFER_NOT_TO_SAY"))
        require(profile.calendarPreference in setOf("PERSIAN", "GREGORIAN", "ISLAMIC_CIVIL"))
        require(profile.timezoneId.isNotBlank())
        ZoneId.of(profile.timezoneId)
        val start = profile.taklifStartDate?.let(LocalDate::parse)
        val end = profile.taklifEndDate?.let(LocalDate::parse)
        val today = LocalDate.now(ZoneId.of(profile.timezoneId))
        require(start == null || !start.isAfter(today)) { "تاریخ شروع برآورد نمی‌تواند در آینده باشد" }
        require(end == null || !end.isAfter(today)) { "تاریخ پایان برآورد نمی‌تواند در آینده باشد" }
        require(start == null || end == null || !end.isBefore(start))
    }

    private fun validateRawEstimate(prayerPerType: Int, fastingDays: Int) {
        require(prayerPerType >= 0 && fastingDays >= 0)
    }

    suspend fun addExcusePeriod(kind: String, start: LocalDate, end: LocalDate, note: String?) {
        require(kind in setOf("MENSTRUATION", "POSTPARTUM", "ILLNESS", "OTHER_USER_RECORDED"))
        require(!end.isBefore(start))
        require(java.time.temporal.ChronoUnit.DAYS.between(start, end) < 4000)
        dao.insertExcusePeriod(ExcusePeriodEntity(kind = kind, startDate = start.toString(), endDate = end.toString(), note = note?.take(160), createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteExcusePeriod(id: Long) = dao.deleteExcusePeriod(id)

    suspend fun savePlannerSettings(settings: PlannerSettingsEntity) {
        require(settings.dailyPrayerTarget in 1..100 && settings.dailyFastingTarget in 1..30)
        require(settings.activeWeekdaysMask in 1..127 && settings.reminderHour in 0..23 && settings.reminderMinute in 0..59)
        dao.savePlannerSettings(settings)
    }

    suspend fun saveFiqhPreferences(preferences: FiqhPreferencesEntity) {
        // A selected marja is a preference only. No rule is applied until a versioned, reviewed source is configured.
        require(preferences.marjaId == null || preferences.marjaId.matches(Regex("[a-z0-9_-]{2,48}")))
        require(preferences.acceptedSourceUrl == null || preferences.acceptedSourceUrl.startsWith("https://"))
        dao.saveFiqhPreferences(preferences)
    }

    suspend fun deleteAllData() {
        database.withTransaction {
            dao.clearPrayers(); dao.clearFasting(); dao.clearRecords(); dao.clearProfile(); dao.clearExcusePeriods()
            dao.clearPlannerSettings(); dao.clearFiqhPreferences(); dao.clearDailyVerses()
        }
    }

    suspend fun initializePrayer(type: PrayerType, total: Int, completed: Int = 0) {
        require(total >= 0 && completed in 0..total)
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = dao.getPrayer(type.name)
            if (existing == null) {
                dao.savePrayer(PrayerDebtEntity(type.name, total, completed, now))
            } else {
                val safeTotal = maxOf(total, completed, existing.completed)
                if (completed != existing.completed) {
                    dao.setPrayerCompleted(type.name, completed, now)
                    dao.insertRecord(DebtRecordEntity(kind = PRAYER_ADJUSTMENT_KIND, reference = type.name, amount = kotlin.math.abs(completed - existing.completed), createdAt = now, beforeCompleted = existing.completed, afterCompleted = completed))
                }
                dao.savePrayer(PrayerDebtEntity(type.name, safeTotal, completed, now))
            }
        }
    }

    suspend fun initializeFasting(total: Int, completed: Int = 0) {
        require(total >= 0 && completed in 0..total)
        database.withTransaction {
            val now = System.currentTimeMillis()
            val existing = dao.getFasting()
            if (existing == null) {
                dao.saveFasting(FastingDebtEntity(total = total, completed = completed, updatedAt = now))
            } else {
                val safeTotal = maxOf(total, completed, existing.completed)
                if (completed != existing.completed) {
                    dao.setFastingCompleted(completed, now)
                    dao.insertRecord(DebtRecordEntity(kind = FASTING_ADJUSTMENT_KIND, reference = FASTING_KIND, amount = kotlin.math.abs(completed - existing.completed), createdAt = now, beforeCompleted = existing.completed, afterCompleted = completed))
                }
                dao.saveFasting(FastingDebtEntity(total = safeTotal, completed = completed, updatedAt = now))
            }
        }
    }

    suspend fun applyRawDateEstimate(prayerPerType: Int, fastingDays: Int) {
        validateRawEstimate(prayerPerType, fastingDays)
        database.withTransaction {
            applyRawDateEstimateInTransaction(prayerPerType, fastingDays)
        }
    }

    private suspend fun applyRawDateEstimateInTransaction(prayerPerType: Int, fastingDays: Int) {
        val now = System.currentTimeMillis()
        PrayerType.entries.forEach { type ->
            val existing = dao.getPrayer(type.name)
            val completed = existing?.completed ?: 0
            // A profile re-estimate may raise a total, but must never erase a larger
            // manually entered debt without an explicit adjustment operation.
            dao.savePrayer(PrayerDebtEntity(type.name, maxOf(prayerPerType, completed, existing?.total ?: 0), completed, now))
        }
        val existingFast = dao.getFasting()
        val completedFast = existingFast?.completed ?: 0
        dao.saveFasting(FastingDebtEntity(total = maxOf(fastingDays, completedFast, existingFast?.total ?: 0), completed = completedFast, updatedAt = now))
    }

    suspend fun recordPrayer(type: PrayerType, amount: Int = 1) {
        require(amount > 0)
        database.withTransaction {
            val before = dao.getPrayer(type.name)?.completed ?: return@withTransaction
            val now = System.currentTimeMillis()
            dao.recordPrayer(type.name, amount, now)
            val applied = (dao.getPrayer(type.name)?.completed ?: before) - before
            if (applied > 0) dao.insertRecord(DebtRecordEntity(kind = PRAYER_KIND, reference = type.name, amount = applied, createdAt = now, beforeCompleted = before, afterCompleted = before + applied))
        }
    }

    suspend fun recordFasting(amount: Int = 1) {
        require(amount > 0)
        database.withTransaction {
            val before = dao.getFasting()?.completed ?: return@withTransaction
            val now = System.currentTimeMillis()
            dao.recordFasting(amount, now)
            val applied = (dao.getFasting()?.completed ?: before) - before
            if (applied > 0) dao.insertRecord(DebtRecordEntity(kind = FASTING_KIND, reference = FASTING_KIND, amount = applied, createdAt = now, beforeCompleted = before, afterCompleted = before + applied))
        }
    }

    suspend fun undoRecord(id: Long) {
        database.withTransaction {
            val record = dao.getRecord(id) ?: return@withTransaction
            val now = System.currentTimeMillis()
            when (record.kind) {
                PRAYER_KIND, PRAYER_ADJUSTMENT_KIND -> {
                    val debt = dao.getPrayer(record.reference) ?: error("رکورد نماز دیگر وجود ندارد")
                    val current = debt.completed
                    check(current == record.afterCompleted) { "این ثبت پس از فعالیت‌های بعدی ایجاد شده؛ برای جلوگیری از تغییر نادرست، بازگردانی مستقیم ممکن نیست." }
                    check(record.beforeCompleted in 0..debt.total && record.afterCompleted in 0..debt.total) {
                        "مقدار پیشین این ثبت از سقف فعلی بدهی بیشتر است؛ بازگردانی مستقیم ممکن نیست."
                    }
                    dao.setPrayerCompleted(record.reference, record.beforeCompleted, now)
                }
                FASTING_KIND, FASTING_ADJUSTMENT_KIND -> {
                    val debt = dao.getFasting() ?: error("رکورد روزه دیگر وجود ندارد")
                    val current = debt.completed
                    check(current == record.afterCompleted) { "این ثبت پس از فعالیت‌های بعدی ایجاد شده؛ برای جلوگیری از تغییر نادرست، بازگردانی مستقیم ممکن نیست." }
                    check(record.beforeCompleted in 0..debt.total && record.afterCompleted in 0..debt.total) {
                        "مقدار پیشین این ثبت از سقف فعلی بدهی بیشتر است؛ بازگردانی مستقیم ممکن نیست."
                    }
                    dao.setFastingCompleted(record.beforeCompleted, now)
                }
                else -> error("نوع رکورد برای بازگردانی شناخته‌شده نیست")
            }
            dao.deleteRecord(id)
        }
    }

    private companion object {
        const val PRAYER_KIND = "PRAYER"
        const val FASTING_KIND = "FASTING"
        const val PRAYER_ADJUSTMENT_KIND = "PRAYER_ADJUSTMENT"
        const val FASTING_ADJUSTMENT_KIND = "FASTING_ADJUSTMENT"
    }
}
