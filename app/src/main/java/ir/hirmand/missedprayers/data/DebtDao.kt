package ir.hirmand.missedprayers.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM prayer_debts ORDER BY prayerType")
    fun observePrayers(): Flow<List<PrayerDebtEntity>>

    @Query("SELECT * FROM fasting_debt WHERE id = 1")
    fun observeFasting(): Flow<FastingDebtEntity?>

    @Upsert
    suspend fun savePrayer(debt: PrayerDebtEntity)

    @Upsert
    suspend fun saveFasting(debt: FastingDebtEntity)

    @Query("UPDATE prayer_debts SET completed = MIN(total, completed + :amount), updatedAt = :now WHERE prayerType = :type")
    suspend fun recordPrayer(type: String, amount: Int, now: Long): Int

    @Query("UPDATE fasting_debt SET completed = MIN(total, completed + :amount), updatedAt = :now WHERE id = 1")
    suspend fun recordFasting(amount: Int, now: Long): Int

    @Query("SELECT * FROM prayer_debts WHERE prayerType = :type")
    suspend fun getPrayer(type: String): PrayerDebtEntity?

    @Query("SELECT * FROM fasting_debt WHERE id = 1")
    suspend fun getFasting(): FastingDebtEntity?

    @Query("UPDATE prayer_debts SET completed = MAX(0, completed - :amount), updatedAt = :now WHERE prayerType = :type")
    suspend fun undoPrayer(type: String, amount: Int, now: Long): Int

    @Query("UPDATE fasting_debt SET completed = MAX(0, completed - :amount), updatedAt = :now WHERE id = 1")
    suspend fun undoFasting(amount: Int, now: Long): Int

    @Query("UPDATE prayer_debts SET completed = :completed, updatedAt = :now WHERE prayerType = :type")
    suspend fun setPrayerCompleted(type: String, completed: Int, now: Long): Int

    @Query("UPDATE fasting_debt SET completed = :completed, updatedAt = :now WHERE id = 1")
    suspend fun setFastingCompleted(completed: Int, now: Long): Int

    @Insert
    suspend fun insertRecord(record: DebtRecordEntity): Long

    @Query("SELECT * FROM debt_records ORDER BY createdAt DESC LIMIT 20")
    fun observeRecentRecords(): Flow<List<DebtRecordEntity>>

    @Query("SELECT * FROM debt_records WHERE id = :id")
    suspend fun getRecord(id: Long): DebtRecordEntity?

    @Query("DELETE FROM debt_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun saveProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM excuse_periods ORDER BY startDate DESC")
    fun observeExcusePeriods(): Flow<List<ExcusePeriodEntity>>

    @Insert
    suspend fun insertExcusePeriod(period: ExcusePeriodEntity): Long

    @Query("DELETE FROM excuse_periods WHERE id = :id")
    suspend fun deleteExcusePeriod(id: Long)

    @Query("SELECT * FROM planner_settings WHERE id = 1")
    fun observePlannerSettings(): Flow<PlannerSettingsEntity?>

    @Upsert
    suspend fun savePlannerSettings(settings: PlannerSettingsEntity)

    @Query("SELECT * FROM fiqh_preferences WHERE id = 1")
    fun observeFiqhPreferences(): Flow<FiqhPreferencesEntity?>

    @Upsert
    suspend fun saveFiqhPreferences(preferences: FiqhPreferencesEntity)

    @androidx.room.Upsert
    suspend fun saveDailyVerse(verse: DailyVerseEntity)

    @Query("SELECT * FROM daily_verses WHERE dayKey = :dayKey")
    suspend fun getDailyVerse(dayKey: String): DailyVerseEntity?

    @Query("SELECT * FROM daily_verses ORDER BY dayKey DESC LIMIT 1")
    suspend fun getLatestDailyVerse(): DailyVerseEntity?

    @Query("DELETE FROM daily_verses WHERE dayKey < :oldestDay")
    suspend fun pruneDailyVerses(oldestDay: String)

    @Query("SELECT * FROM prayer_debts ORDER BY prayerType")
    suspend fun getAllPrayers(): List<PrayerDebtEntity>

    @Query("SELECT * FROM fasting_debt WHERE id = 1")
    suspend fun getFastingSnapshot(): FastingDebtEntity?

    @Query("SELECT * FROM debt_records ORDER BY id")
    suspend fun getAllRecords(): List<DebtRecordEntity>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileSnapshot(): UserProfileEntity?

    @Query("SELECT * FROM excuse_periods ORDER BY id")
    suspend fun getAllExcusePeriods(): List<ExcusePeriodEntity>

    @Query("SELECT * FROM planner_settings WHERE id = 1")
    suspend fun getPlannerSettingsSnapshot(): PlannerSettingsEntity?

    @Query("SELECT * FROM fiqh_preferences WHERE id = 1")
    suspend fun getFiqhPreferencesSnapshot(): FiqhPreferencesEntity?

    @Query("SELECT * FROM daily_verses ORDER BY dayKey")
    suspend fun getAllDailyVerses(): List<DailyVerseEntity>

    @Query("DELETE FROM prayer_debts") suspend fun clearPrayers()
    @Query("DELETE FROM fasting_debt") suspend fun clearFasting()
    @Query("DELETE FROM debt_records") suspend fun clearRecords()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Query("DELETE FROM excuse_periods") suspend fun clearExcusePeriods()
    @Query("DELETE FROM planner_settings") suspend fun clearPlannerSettings()
    @Query("DELETE FROM fiqh_preferences") suspend fun clearFiqhPreferences()
    @Query("DELETE FROM daily_verses") suspend fun clearDailyVerses()

    @Upsert suspend fun saveAllPrayers(items: List<PrayerDebtEntity>)
    @Upsert suspend fun saveAllFasting(item: FastingDebtEntity)
    @Insert suspend fun saveAllRecords(items: List<DebtRecordEntity>)
    @Upsert suspend fun saveAllProfiles(item: UserProfileEntity)
    @Insert suspend fun saveAllExcusePeriods(items: List<ExcusePeriodEntity>)
    @Upsert suspend fun saveAllPlannerSettings(item: PlannerSettingsEntity)
    @Upsert suspend fun saveAllFiqhPreferences(item: FiqhPreferencesEntity)
    @Upsert suspend fun saveAllDailyVerses(items: List<DailyVerseEntity>)
}
