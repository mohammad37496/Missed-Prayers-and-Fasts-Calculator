package ir.hirmand.missedprayers.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(entities = [PrayerDebtEntity::class, FastingDebtEntity::class, DebtRecordEntity::class, UserProfileEntity::class, ExcusePeriodEntity::class, PlannerSettingsEntity::class, FiqhPreferencesEntity::class, DailyVerseEntity::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun debtDao(): DebtDao

    companion object {
        private const val DATABASE_NAME = "missed_worship.db"
        @Volatile private var instance: AppDatabase? = null

        init {
            // WorkManager may open the encrypted database before MainActivity is created.
            System.loadLibrary("sqlcipher")
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            val existingDatabase = databaseFile.exists() || File(databaseFile.path + "-wal").exists() || File(databaseFile.path + "-shm").exists()
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).openHelperFactory(
                SupportOpenHelperFactory(
                    DatabaseKeyProvider(
                        context,
                        requireExistingDatabaseKey = existingDatabase
                    ).getOrCreatePassphrase()
                )
            )
                .addMigrations(*MIGRATIONS)
                .build().also { instance = it }
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS debt_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, reference TEXT NOT NULL, amount INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_profile (id INTEGER NOT NULL PRIMARY KEY, displayName TEXT, gender TEXT NOT NULL, taklifStartDate TEXT, taklifEndDate TEXT, timezoneId TEXT NOT NULL, calendarPreference TEXT NOT NULL, marjaId TEXT, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS excuse_periods (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, kind TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT NOT NULL, note TEXT, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS planner_settings (id INTEGER NOT NULL PRIMARY KEY, dailyPrayerTarget INTEGER NOT NULL, dailyFastingTarget INTEGER NOT NULL, activeWeekdaysMask INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL, reminderHour INTEGER NOT NULL, reminderMinute INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS fiqh_preferences (id INTEGER NOT NULL PRIMARY KEY, marjaId TEXT, rulesetVersion TEXT, acceptedSourceUrl TEXT, updatedAt INTEGER NOT NULL)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_verses (dayKey TEXT NOT NULL PRIMARY KEY, surah INTEGER NOT NULL, ayah INTEGER NOT NULL, globalNumber INTEGER NOT NULL, arabicText TEXT NOT NULL, persianTranslation TEXT NOT NULL, translationSource TEXT NOT NULL, translationVersion TEXT NOT NULL, tafsirText TEXT, tafsirSource TEXT, recitationUrl TEXT, reciterName TEXT, fetchedAt INTEGER NOT NULL)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE debt_records ADD COLUMN beforeCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE debt_records ADD COLUMN afterCompleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
