package ir.hirmand.missedprayers.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(entities = [PrayerDebtEntity::class, FastingDebtEntity::class, DebtRecordEntity::class, UserProfileEntity::class, ExcusePeriodEntity::class, PlannerSettingsEntity::class, FiqhPreferencesEntity::class, DailyVerseEntity::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun debtDao(): DebtDao

    @Volatile private var sqlCipherPassphrase: ByteArray? = null

    private fun retainSqlCipherPassphrase(passphrase: ByteArray) {
        sqlCipherPassphrase = passphrase
    }

    override fun close() {
        try {
            super.close()
        } finally {
            sqlCipherPassphrase?.fill(0)
            sqlCipherPassphrase = null
        }
    }

    companion object {
        private const val DATABASE_NAME = "missed_worship.db"
        private const val DATABASE_SELECTION_PREFERENCES = "database_selection"
        private const val ACTIVE_DATABASE_KEY = "active_database_name"
        private const val RECOVERY_DATABASE_PREFIX = "missed_worship_recovered_"
        private const val RECOVERY_DATABASE_SUFFIX = ".db"
        private const val PENDING_RECOVERY_PREFIX = "pending_recovery_"
        private val lock = Any()
        @Volatile private var instance: AppDatabase? = null
        @Volatile private var activeRecoveryId: String? = null

        init {
            // WorkManager may open the encrypted database before MainActivity is created.
            System.loadLibrary("sqlcipher")
        }

        fun get(context: Context): AppDatabase = synchronized(lock) {
            check(activeRecoveryId == null) { "بازیابی پایگاه داده در حال اجراست؛ دسترسی هم‌زمان موقتاً متوقف شد." }
            instance ?: run {
                val appContext = context.applicationContext
                cleanupInterruptedRecoveries(appContext)
                val selectedName = appContext.getSharedPreferences(DATABASE_SELECTION_PREFERENCES, Context.MODE_PRIVATE)
                    .getString(ACTIVE_DATABASE_KEY, DATABASE_NAME) ?: DATABASE_NAME
                val recoveryId = if (selectedName == DATABASE_NAME) null else {
                    val id = selectedName.removePrefix(RECOVERY_DATABASE_PREFIX).removeSuffix(RECOVERY_DATABASE_SUFFIX)
                    check(selectedName == "$RECOVERY_DATABASE_PREFIX$id$RECOVERY_DATABASE_SUFFIX" && id.matches(Regex("[0-9a-f]{32}"))) {
                        "شناسه پایگاه داده بازیابی‌شده معتبر نیست؛ از ساخت دیتابیس جایگزین خودداری شد."
                    }
                    id
                }
                val databaseFile = appContext.getDatabasePath(selectedName)
                val existingDatabase = databaseFile.exists() || File(databaseFile.path + "-wal").exists() || File(databaseFile.path + "-shm").exists()
                check(recoveryId == null || databaseFile.exists()) {
                    "فایل پایگاه داده بازیابی‌شده پیدا نشد؛ از ساخت دیتابیس خالی به‌جای اطلاعات بازیابی‌شده خودداری شد."
                }
                val keyProvider = if (recoveryId == null) {
                    DatabaseKeyProvider(appContext, requireExistingDatabaseKey = existingDatabase)
                } else {
                    DatabaseKeyProvider(
                        appContext,
                        preferencesName = "database_key_recovery_$recoveryId",
                        keyAlias = "missed_worship_recovery_key_$recoveryId",
                        requireExistingDatabaseKey = true
                    )
                }
                check(existingDatabase || !keyProvider.hasStoredKeyMaterial()) {
                    "فایل پایگاه داده وجود ندارد اما مواد کلید آن باقی مانده است؛ برای جلوگیری از ایجاد دیتابیس خالی، راه‌اندازی متوقف شد."
                }
                val passphrase = keyProvider.getOrCreatePassphrase()
                try {
                    Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        selectedName
                    ).openHelperFactory(
                        SupportOpenHelperFactory(passphrase)
                    )
                        .addMigrations(*MIGRATIONS)
                        .build().also { database ->
                            // SQLCipher retains this exact array for database re-opens.
                            // Keep it only for the database lifetime and wipe it on close.
                            database.retainSqlCipherPassphrase(passphrase)
                            instance = database
                        }
                } catch (error: Throwable) {
                    passphrase.fill(0)
                    throw error
                }
            }
        }

        /** Restores into a fresh encrypted database, then atomically switches the active database pointer. */
        @Suppress("ApplySharedPref", "UseKtx") // Synchronous commits are required before DB creation and when switching the active DB pointer.
        suspend fun restoreFromBackup(context: Context, encryptedBackup: ByteArray, password: CharArray) = withContext(Dispatchers.IO) {
            require(encryptedBackup.size <= EncryptedBackupCodec.MAX_FILE_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
            val appContext = context.applicationContext
            val recoveryId = UUID.randomUUID().toString().replace("-", "")
            val recoveryName = "$RECOVERY_DATABASE_PREFIX$recoveryId$RECOVERY_DATABASE_SUFFIX"
            val selectionPreferences = appContext.getSharedPreferences(DATABASE_SELECTION_PREFERENCES, Context.MODE_PRIVATE)
            val pendingKey = "$PENDING_RECOVERY_PREFIX$recoveryId"
            val keyProvider = DatabaseKeyProvider(
                appContext,
                preferencesName = "database_key_recovery_$recoveryId",
                keyAlias = "missed_worship_recovery_key_$recoveryId"
            )
            synchronized(lock) {
                check(activeRecoveryId == null) { "بازیابی دیگری در حال اجراست؛ کمی بعد دوباره تلاش کنید." }
                check(selectionPreferences.edit().putString(pendingKey, recoveryName).commit()) {
                    "ثبت وضعیت موقت بازیابی انجام نشد؛ دیتابیس قبلی تغییری نکرد."
                }
                activeRecoveryId = recoveryId
            }
            var passphrase: ByteArray? = null
            var recoveryDatabase: AppDatabase? = null
            var activated = false
            try {
                val databasePassphrase = keyProvider.getOrCreatePassphrase()
                passphrase = databasePassphrase
                val newDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, recoveryName)
                    .openHelperFactory(SupportOpenHelperFactory(databasePassphrase))
                    .addMigrations(*MIGRATIONS)
                    .build()
                recoveryDatabase = newDatabase
                newDatabase.openHelper.writableDatabase
                EncryptedBackupManager(newDatabase).restoreBackup(encryptedBackup, password)
                newDatabase.close()
                synchronized(lock) {
                    runCatching { instance?.close() }
                    instance = null
                }
                val pointerCommitted = selectionPreferences.edit()
                    .putString(ACTIVE_DATABASE_KEY, recoveryName)
                    .remove(pendingKey)
                    .commit()
                if (pointerCommitted) activated = true
                check(pointerCommitted) { "ثبت پایگاه داده بازیابی‌شده انجام نشد؛ دیتابیس قبلی همچنان فعال است." }
            } finally {
                if (!activated) {
                    runCatching { recoveryDatabase?.close() }
                    if (deleteRecoveryDatabaseFiles(appContext, recoveryName) && keyProvider.deleteKeyMaterial()) {
                        selectionPreferences.edit().remove(pendingKey).commit()
                    }
                }
                passphrase?.fill(0)
                synchronized(lock) { activeRecoveryId = null }
            }
        }

        @Suppress("ApplySharedPref", "UseKtx") // Cleanup must be durably recorded before startup proceeds.
        private fun cleanupInterruptedRecoveries(context: Context) {
            val preferences = context.getSharedPreferences(DATABASE_SELECTION_PREFERENCES, Context.MODE_PRIVATE)
            val pendingKeys = preferences.all.keys.filter { it.startsWith(PENDING_RECOVERY_PREFIX) }
            if (pendingKeys.isEmpty()) return
            val activeName = preferences.getString(ACTIVE_DATABASE_KEY, DATABASE_NAME) ?: DATABASE_NAME
            val editor = preferences.edit()
            pendingKeys.forEach { pendingKey ->
                val id = pendingKey.removePrefix(PENDING_RECOVERY_PREFIX)
                val expectedName = "$RECOVERY_DATABASE_PREFIX$id$RECOVERY_DATABASE_SUFFIX"
                if (id == activeRecoveryId) return@forEach
                if (id.matches(Regex("[0-9a-f]{32}")) && preferences.getString(pendingKey, null) == expectedName) {
                    if (activeName == expectedName) {
                        editor.remove(pendingKey)
                        return@forEach
                    }
                    if (!deleteRecoveryDatabaseFiles(context, expectedName)) return@forEach
                    val keyRemoved = DatabaseKeyProvider(
                        context,
                        preferencesName = "database_key_recovery_$id",
                        keyAlias = "missed_worship_recovery_key_$id",
                        requireExistingDatabaseKey = true
                    ).deleteKeyMaterial()
                    if (!keyRemoved) return@forEach
                }
                editor.remove(pendingKey)
            }
            editor.commit()
        }

        private fun deleteRecoveryDatabaseFiles(context: Context, name: String): Boolean {
            val databaseFile = context.getDatabasePath(name)
            context.deleteDatabase(name)
            return listOf(
                databaseFile,
                File(databaseFile.path + "-wal"),
                File(databaseFile.path + "-shm"),
                File(databaseFile.path + "-journal")
            ).none(File::exists)
        }

        internal fun clearActiveInstanceForTests() {
            synchronized(lock) {
                runCatching { instance?.close() }
                instance = null
            }
        }

        internal fun cleanupInterruptedRecoveriesForTests(context: Context) {
            synchronized(lock) { cleanupInterruptedRecoveries(context.applicationContext) }
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

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_verses ADD COLUMN translationFootnotes TEXT")
            }
        }

        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
    }
}
