package ir.hirmand.missedprayers.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EncryptedBackupRestoreValidationTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("restore-validation-test-passphrase".toByteArray()))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    @After fun tearDown() { database.close() }

    @Test fun releaseSafeContentModeDoesNotRequestOrExposeUnverifiedTafsirOrAudio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var tafsirCatalogueCalls = 0
        var recitationCalls = 0
        val provider = object : QuranEncContentProvider {
            override fun listTranslations() = JSONArray().put(
                JSONObject().put("key", "persian_ih").put("title", "ترجمه فارسی - مرکز ترجمه رواد")
                    .put("version", "1.1.3").put("language_iso_code", "fa")
            )
            override fun listTafsirs(): JSONArray {
                tafsirCatalogueCalls++
                return JSONArray().put(JSONObject().put("key", "persian_saadi").put("title", "تفسیر سعدی").put("version", "1.0.0").put("language_iso_code", "fa"))
            }
            override fun translationForSurah(key: String, surah: Int) = JSONArray().put(
                JSONObject().put("id", "1").put("sura", surah.toString()).put("aya", "1")
                    .put("translation", "ترجمه معتبر").put("footnotes", "")
            )
            override fun tafsirForAyah(key: String, surah: Int, ayah: Int) = JSONArray()
                .put(JSONObject().put("type", "تفسير آية").put("from_aya", ayah).put("to_aya", ayah).put("text", "تفسیر آزمایشی"))
        }
        val recitation = object : QuranRecitationProvider {
            override suspend fun resolve(globalAyahNumber: Int): RecitationAudio? {
                recitationCalls++
                return RecitationAudio("https://download.quranicaudio.com/test.mp3", "قاری آزمایشی", "test")
            }
        }
        val repository = QuranContentRepository(
            database.debtDao(), TanzilAssetTextProvider(context.assets), recitation, provider,
            allowUnverifiedMedia = false
        )

        val result = repository.loadFor(LocalDate.parse("2026-11-26"), forceRefresh = true)

        assertEquals("ترجمه معتبر", result.verse?.persianTranslation)
        assertNull(result.verse?.tafsirText)
        assertNull(result.verse?.tafsirSource)
        assertNull(result.verse?.recitationUrl)
        assertNull(result.verse?.reciterName)
        assertEquals(0, tafsirCatalogueCalls)
        assertEquals(0, recitationCalls)

        database.debtDao().saveDailyVerse(
            result.verse!!.copy(
                tafsirText = "مقدار قدیمی ذخیره‌شده", tafsirSource = "منبع قدیمی",
                recitationUrl = "https://download.quranicaudio.com/legacy.mp3", reciterName = "قاری قدیمی"
            )
        )
        val cached = repository.loadFor(LocalDate.parse("2026-11-26"), forceRefresh = false)
        assertNull(cached.verse?.tafsirText)
        assertNull(cached.verse?.tafsirSource)
        assertNull(cached.verse?.recitationUrl)
        assertNull(cached.verse?.reciterName)
    }

    @Test fun releaseSafeBackupOmitsAndDoesNotRestoreUnverifiedMedia() = runBlocking {
        val dao = database.debtDao()
        dao.saveDailyVerse(
            DailyVerseEntity(
                dayKey = "2026-11-26", surah = 1, ayah = 1, globalNumber = 1,
                arabicText = "بِسْمِ اللَّهِ", persianTranslation = "به نام خدا",
                translationSource = "QuranEnc · Rowwad", translationVersion = "1.1.3",
                tafsirText = "تفسیر آزمایشی", tafsirSource = "QuranEnc · As-Saadi",
                recitationUrl = "https://download.quranicaudio.com/test.mp3", reciterName = "قاری آزمایشی",
                fetchedAt = 1_000L
            )
        )
        val developmentBackup = EncryptedBackupManager(database, includeUnverifiedMedia = true)
            .createBackup("development-backup-password".toCharArray())
        val releaseManager = EncryptedBackupManager(database, includeUnverifiedMedia = false)
        val safeBackup = releaseManager.createBackup("release-backup-password".toCharArray())
        val safePayload = JSONObject(
            EncryptedBackupCodec.decrypt(safeBackup, "release-backup-password".toCharArray()).toString(Charsets.UTF_8)
        )
        val exportedVerse = safePayload.getJSONArray("dailyVerses").getJSONObject(0)
        assertTrue(exportedVerse.isNull("tafsirText"))
        assertTrue(exportedVerse.isNull("tafsirSource"))
        assertTrue(exportedVerse.isNull("recitationUrl"))
        assertTrue(exportedVerse.isNull("reciterName"))

        releaseManager.restoreBackup(developmentBackup, "development-backup-password".toCharArray())

        val restored = dao.getDailyVerse("2026-11-26")
        assertNull(restored?.tafsirText)
        assertNull(restored?.tafsirSource)
        assertNull(restored?.recitationUrl)
        assertNull(restored?.reciterName)
        assertEquals("به نام خدا", restored?.persianTranslation)
    }

    @Test fun malformedOptionalObjectsAreRejectedWithoutDeletingExistingData() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("FAJR", 20, 7, 1_000L))
        dao.saveFasting(FastingDebtEntity(total = 12, completed = 4, updatedAt = 1_000L))
        dao.saveProfile(
            UserProfileEntity(
                displayName = null,
                gender = "WOMAN",
                taklifStartDate = "2010-01-01",
                taklifEndDate = null,
                timezoneId = "Asia/Tehran",
                calendarPreference = "PERSIAN",
                marjaId = null,
                updatedAt = 1_000L
            )
        )
        val base = JSONObject()
            .put("schemaVersion", 5)
            .put("createdAt", 1_000L)
            .put("prayers", JSONArray())
            .put("fasting", JSONObject.NULL)
            .put("records", JSONArray())
            .put("profile", JSONObject.NULL)
            .put("periods", JSONArray())
            .put("planner", JSONObject.NULL)
            .put("fiqh", JSONObject.NULL)
            .put("dailyVerses", JSONArray())
        val password = "restore-validation-password".toCharArray()
        val manager = EncryptedBackupManager(database)

        listOf("fasting", "profile", "planner", "fiqh").forEach { key ->
            val malformed = JSONObject(base.toString()).put(key, "not-an-object")
            val encrypted = EncryptedBackupCodec.encrypt(malformed.toString().toByteArray(Charsets.UTF_8), password)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { manager.restoreBackup(encrypted, password.copyOf()) }
            }
            assertEquals(7, dao.getPrayer("FAJR")?.completed)
            assertEquals(4, dao.getFasting()?.completed)
            assertEquals("WOMAN", dao.getProfileSnapshot()?.gender)
        }
    }

    @Test fun historyCountersOutsideDebtRangeAreRejectedBeforeRestoreTransaction() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("FAJR", 30, 8, 2_000L))
        val payload = JSONObject()
            .put("schemaVersion", 5)
            .put("createdAt", 3_000L)
            .put("prayers", JSONArray().put(JSONObject().put("type", "FAJR").put("total", 20).put("completed", 7).put("updatedAt", 1_000L)))
            .put("fasting", JSONObject.NULL)
            .put("records", JSONArray().put(JSONObject().put("id", 1L).put("kind", "PRAYER").put("reference", "FAJR").put("amount", 1).put("createdAt", 1_000L).put("beforeCompleted", 20).put("afterCompleted", 21)))
            .put("profile", JSONObject.NULL)
            .put("periods", JSONArray())
            .put("planner", JSONObject.NULL)
            .put("fiqh", JSONObject.NULL)
            .put("dailyVerses", JSONArray())
        val password = "history-range-validation".toCharArray()
        val encrypted = EncryptedBackupCodec.encrypt(payload.toString().toByteArray(Charsets.UTF_8), password)

        try {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { EncryptedBackupManager(database).restoreBackup(encrypted, password.copyOf()) }
            }
            assertEquals(30, dao.getPrayer("FAJR")?.total)
            assertEquals(8, dao.getPrayer("FAJR")?.completed)
            assertTrue(dao.getAllRecords().isEmpty())
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun databaseFailureDuringRestoreRollsBackEveryClearedTable() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("FAJR", 30, 8, 1_000L))
        dao.saveProfile(
            UserProfileEntity(
                displayName = "Backup profile",
                gender = "WOMAN",
                taklifStartDate = "2010-01-01",
                taklifEndDate = null,
                timezoneId = "Asia/Tehran",
                calendarPreference = "PERSIAN",
                marjaId = null,
                updatedAt = 1_000L
            )
        )
        val manager = EncryptedBackupManager(database)
        val backup = manager.createBackup("restore-atomicity-password".toCharArray())

        dao.savePrayer(PrayerDebtEntity("FAJR", 40, 9, 2_000L))
        dao.saveProfile(
            UserProfileEntity(
                displayName = "Current profile",
                gender = "MAN",
                taklifStartDate = "2000-01-01",
                taklifEndDate = null,
                timezoneId = "Europe/Oslo",
                calendarPreference = "GREGORIAN",
                marjaId = null,
                updatedAt = 2_000L
            )
        )
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_profile_restore BEFORE INSERT ON user_profile BEGIN SELECT RAISE(ABORT, 'forced restore failure'); END"
        )

        assertThrows(RuntimeException::class.java) {
            runBlocking { manager.restoreBackup(backup, "restore-atomicity-password".toCharArray()) }
        }

        assertEquals(9, dao.getPrayer("FAJR")?.completed)
        assertEquals("Current profile", dao.getProfileSnapshot()?.displayName)
        assertEquals("MAN", dao.getProfileSnapshot()?.gender)
    }

    @Test fun previousDaysVerseIsMarkedStaleWhenTodayCannotBeFetched() = runBlocking {
        val yesterday = LocalDate.parse("2026-11-25")
        val today = yesterday.plusDays(1)
        database.debtDao().saveDailyVerse(
            DailyVerseEntity(
                dayKey = yesterday.toString(),
                surah = 1,
                ayah = 1,
                globalNumber = 1,
                arabicText = "test-arabic",
                persianTranslation = "test-translation",
                translationSource = "QuranEnc",
                translationVersion = "1.1.3",
                tafsirText = null,
                tafsirSource = null,
                recitationUrl = null,
                reciterName = null,
                fetchedAt = 1_000L
            )
        )
        val unavailableProvider = object : QuranEncContentProvider {
            override fun listTranslations(): JSONArray = error("offline")
            override fun listTafsirs(): JSONArray = error("offline")
            override fun translationForSurah(key: String, surah: Int): JSONArray = error("offline")
            override fun tafsirForAyah(key: String, surah: Int, ayah: Int): JSONArray = error("offline")
        }
        val repository = QuranContentRepository(
            database.debtDao(),
            object : ArabicQuranTextProvider {
                override fun getAyah(surah: Int, ayah: Int): String? = null
            },
            contentProvider = unavailableProvider
        )

        val result = repository.loadFor(today, forceRefresh = true)

        assertEquals(yesterday.toString(), result.verse?.dayKey)
        assertEquals(true, result.stale)
        assertEquals(true, result.fromCache)
    }

    @Test fun sqlCipherOpenFailsImmediatelyForAnIncorrectDatabasePassphrase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "restore-validation-wrong-key.db"
        context.deleteDatabase(name)
        val correct = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory("correct-sqlcipher-passphrase".toByteArray()))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        correct.openHelper.writableDatabase
        correct.close()

        val incorrect = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory("incorrect-sqlcipher-passphrase".toByteArray()))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            assertThrows(RuntimeException::class.java) { incorrect.openHelper.writableDatabase }
        } finally {
            runCatching { incorrect.close() }
            context.deleteDatabase(name)
        }
    }

    @Test fun missingKeyEnvelopeForExistingDatabaseFailsClosedWithoutGeneratingANewKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "restore-validation-missing-key.db"
        val preferencesName = "restore-validation-missing-key"
        context.deleteDatabase(databaseName)
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        context.getDatabasePath(databaseName).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val provider = DatabaseKeyProvider(
            context,
            preferencesName = preferencesName,
            keyAlias = "restore_validation_missing_key_alias",
            requireExistingDatabaseKey = true
        )

        try {
            assertThrows(IllegalStateException::class.java) { provider.getOrCreatePassphrase() }
            assertNull(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).getString("encrypted_passphrase", null))
        } finally {
            context.deleteDatabase(databaseName)
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun profileAndEstimateChangesRollbackTogetherWhenDebtWriteFails() = runBlocking {
        val dao = database.debtDao()
        val repository = DebtRepository(database)
        dao.saveProfile(
            UserProfileEntity(
                displayName = "Original profile",
                gender = "WOMAN",
                taklifStartDate = "2010-01-01",
                taklifEndDate = null,
                timezoneId = "Asia/Tehran",
                calendarPreference = "PERSIAN",
                marjaId = null,
                updatedAt = 1_000L
            )
        )
        dao.savePrayer(PrayerDebtEntity("FAJR", 40, 10, 1_000L))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_estimate_write BEFORE INSERT ON prayer_debts BEGIN SELECT RAISE(ABORT, 'forced estimate failure'); END"
        )
        val replacementProfile = UserProfileEntity(
            displayName = "Replacement profile",
            gender = "MAN",
            taklifStartDate = "2000-01-01",
            taklifEndDate = null,
            timezoneId = "Europe/Oslo",
            calendarPreference = "GREGORIAN",
            marjaId = null,
            updatedAt = 2_000L
        )

        assertThrows(RuntimeException::class.java) {
            runBlocking { repository.saveProfileAndApplyRawDateEstimate(replacementProfile, 50, 30) }
        }

        assertEquals("Original profile", dao.getProfileSnapshot()?.displayName)
        assertEquals("WOMAN", dao.getProfileSnapshot()?.gender)
        assertEquals(40, dao.getPrayer("FAJR")?.total)
        assertEquals(10, dao.getPrayer("FAJR")?.completed)
    }

    @Test fun recoveryActivatesANewEncryptedDatabaseAndLeavesThePreviousFileUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selection = context.getSharedPreferences("database_selection", Context.MODE_PRIVATE)
        val previousActiveName = selection.getString("active_database_name", null)
        val previousActiveFile = previousActiveName?.let(context::getDatabasePath)
        val previousActiveBytes = previousActiveFile?.takeIf { it.exists() }?.readBytes()
        val primaryFile = context.getDatabasePath("missed_worship.db")
        val previousPrimaryBytes = primaryFile.takeIf { it.exists() }?.readBytes()
        val password = "new-database-recovery-test".toCharArray()
        val sourceDao = database.debtDao()
        sourceDao.saveProfile(
            UserProfileEntity(
                displayName = "Recovered profile",
                gender = "MAN",
                taklifStartDate = "2000-01-01",
                taklifEndDate = null,
                timezoneId = "Europe/Oslo",
                calendarPreference = "GREGORIAN",
                marjaId = null,
                updatedAt = 3_000L
            )
        )
        sourceDao.savePrayer(PrayerDebtEntity("FAJR", 35, 12, 3_000L))
        val backup = EncryptedBackupManager(database).createBackup(password)
        var recoveredName: String? = null

        try {
            AppDatabase.clearActiveInstanceForTests()
            AppDatabase.restoreFromBackup(context, backup, password)
            recoveredName = selection.getString("active_database_name", null)
            assertTrue(recoveredName?.startsWith("missed_worship_recovered_") == true)
            val recoveredDatabase = AppDatabase.get(context)
            assertEquals("Recovered profile", recoveredDatabase.debtDao().getProfileSnapshot()?.displayName)
            assertEquals(35, recoveredDatabase.debtDao().getPrayer("FAJR")?.total)
            assertEquals(12, recoveredDatabase.debtDao().getPrayer("FAJR")?.completed)
            if (previousPrimaryBytes == null) assertFalse(primaryFile.exists())
            else assertArrayEquals(previousPrimaryBytes, primaryFile.readBytes())
            if (previousActiveFile != null) {
                if (previousActiveBytes == null) assertFalse(previousActiveFile.exists())
                else assertArrayEquals(previousActiveBytes, previousActiveFile.readBytes())
            }
        } finally {
            password.fill('\u0000')
            AppDatabase.clearActiveInstanceForTests()
            if (previousActiveName == null) selection.edit().remove("active_database_name").commit()
            else selection.edit().putString("active_database_name", previousActiveName).commit()
            recoveredName?.let { name ->
                context.deleteDatabase(name)
                val id = name.removePrefix("missed_worship_recovered_").removeSuffix(".db")
                DatabaseKeyProvider(
                    context,
                    preferencesName = "database_key_recovery_$id",
                    keyAlias = "missed_worship_recovery_key_$id",
                    requireExistingDatabaseKey = true
                ).deleteKeyMaterial()
            }
        }
    }

    @Test fun invalidRecoveryDoesNotSwitchActiveDatabaseOrLeaveTemporaryFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selection = context.getSharedPreferences("database_selection", Context.MODE_PRIVATE)
        val previousActiveName = selection.getString("active_database_name", null)
        val databaseDirectory = context.getDatabasePath("missed_worship.db").parentFile!!
        val recoveryFilesBefore = databaseDirectory.listFiles()
            .orEmpty().filter { it.name.startsWith("missed_worship_recovered_") }.map { it.name }.toSet()
        val password = "invalid-recovery-test".toCharArray()

        try {
            assertThrows(RuntimeException::class.java) {
                runBlocking { AppDatabase.restoreFromBackup(context, byteArrayOf(1, 2, 3, 4), password) }
            }
            assertEquals(previousActiveName, selection.getString("active_database_name", null))
            val recoveryFilesAfter = databaseDirectory.listFiles()
                .orEmpty().filter { it.name.startsWith("missed_worship_recovered_") }.map { it.name }.toSet()
            assertEquals(recoveryFilesBefore, recoveryFilesAfter)
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun interruptedRecoveryIsCleanedWithoutTouchingTheActiveDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString().replace("-", "")
        val recoveryName = "missed_worship_recovered_$id.db"
        val preferencesName = "database_key_recovery_$id"
        val keyAlias = "missed_worship_recovery_key_$id"
        val recoveryFile = context.getDatabasePath(recoveryName)
        recoveryFile.parentFile?.mkdirs()
        recoveryFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        val keyProvider = DatabaseKeyProvider(context, preferencesName, keyAlias)
        keyProvider.getOrCreatePassphrase().fill(0)
        val selection = context.getSharedPreferences("database_selection", Context.MODE_PRIVATE)
        val pendingKey = "pending_recovery_$id"
        assertTrue(selection.edit().putString(pendingKey, recoveryName).commit())

        AppDatabase.cleanupInterruptedRecoveriesForTests(context)

        assertFalse(recoveryFile.exists())
        assertFalse(selection.contains(pendingKey))
        assertFalse(keyProvider.hasStoredKeyMaterial())
    }

    @Test fun cleanupPreservesARecoveryDatabaseAlreadySelectedAsActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString().replace("-", "")
        val recoveryName = "missed_worship_recovered_$id.db"
        val keyProvider = DatabaseKeyProvider(
            context,
            preferencesName = "database_key_recovery_$id",
            keyAlias = "missed_worship_recovery_key_$id"
        )
        val file = context.getDatabasePath(recoveryName)
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(5, 6, 7, 8))
        keyProvider.getOrCreatePassphrase().fill(0)
        val selection = context.getSharedPreferences("database_selection", Context.MODE_PRIVATE)
        val previousActiveName = selection.getString("active_database_name", null)
        val pendingKey = "pending_recovery_$id"
        try {
            assertTrue(selection.edit().putString("active_database_name", recoveryName).putString(pendingKey, recoveryName).commit())
            AppDatabase.cleanupInterruptedRecoveriesForTests(context)
            assertTrue(file.exists())
            assertTrue(keyProvider.hasStoredKeyMaterial())
            assertFalse(selection.contains(pendingKey))
        } finally {
            if (previousActiveName == null) selection.edit().remove("active_database_name").commit()
            else selection.edit().putString("active_database_name", previousActiveName).commit()
            context.deleteDatabase(recoveryName)
            keyProvider.deleteKeyMaterial()
        }
    }

    @Test fun quranEncTranslationFootnotesAreStoredVerbatimAndSurviveEncryptedBackupRestore() = runBlocking {
        val footnotes = "[۱] پانوشت نخست، با حفظ خط و نشانه‌گذاری منبع.\n[۲] پانوشت دوم."
        val provider = object : QuranEncContentProvider {
            override fun listTranslations() = JSONArray().put(
                JSONObject()
                    .put("key", "persian_ih")
                    .put("language_iso_code", "fa")
                    .put("version", "1.1.3")
                    .put("title", "Persian Translation - Rowwad Translation Center")
            )
            override fun listTafsirs() = JSONArray()
            override fun translationForSurah(key: String, surah: Int) = JSONArray().put(
                JSONObject()
                    .put("id", "1")
                    .put("sura", "1")
                    .put("aya", "1")
                    .put("translation", "ترجمه فارسی عین منبع")
                    .put("footnotes", footnotes)
            )
            override fun tafsirForAyah(key: String, surah: Int, ayah: Int) = JSONArray()
        }
        val repository = QuranContentRepository(
            database.debtDao(),
            object : ArabicQuranTextProvider {
                override fun getAyah(surah: Int, ayah: Int) = "بِسْمِ اللَّهِ"
            },
            contentProvider = provider
        )
        val day = LocalDate.parse("2026-01-01")
        val loaded = repository.loadFor(day, forceRefresh = true)
        assertEquals(footnotes, loaded.verse?.translationFootnotes)

        val password = "quran-footnotes-backup-test".toCharArray()
        try {
            val manager = EncryptedBackupManager(database)
            val backup = manager.createBackup(password)
            database.debtDao().clearDailyVerses()
            manager.restoreBackup(backup, password)
            assertEquals(footnotes, database.debtDao().getDailyVerse(day.toString())?.translationFootnotes)
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun dailyVerseCachePrunesToSevenCalendarDaysEvenOnCacheHit() = runBlocking {
        val today = LocalDate.parse("2026-11-26")
        val dao = database.debtDao()
        (0L..7L).forEach { daysAgo ->
            val date = today.minusDays(daysAgo)
            dao.saveDailyVerse(
                DailyVerseEntity(
                    dayKey = date.toString(),
                    surah = 1,
                    ayah = 1,
                    globalNumber = 1,
                    arabicText = "بِسْمِ اللَّهِ",
                    persianTranslation = "ترجمه",
                    translationSource = "QuranEnc · Rowwad",
                    translationVersion = "1.1.3",
                    tafsirText = null,
                    tafsirSource = null,
                    recitationUrl = null,
                    reciterName = null,
                    fetchedAt = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                )
            )
        }
        val repository = QuranContentRepository(
            dao,
            object : ArabicQuranTextProvider {
                override fun getAyah(surah: Int, ayah: Int): String? = "بِسْمِ اللَّهِ"
            }
        )

        val result = repository.loadFor(today)
        val retained = dao.getAllDailyVerses()

        assertTrue(result.fromCache)
        assertEquals(today.toString(), result.verse?.dayKey)
        assertEquals(7, retained.size)
        assertTrue(retained.all { !LocalDate.parse(it.dayKey).isBefore(today.minusDays(6)) })
    }

    @Test fun quranEncMissingVerseCoordinatesAreRejectedInsteadOfGuessed() = runBlocking {
        val provider = object : QuranEncContentProvider {
            override fun listTranslations() = JSONArray().put(
                JSONObject().put("key", "persian_ih").put("language_iso_code", "fa")
                    .put("version", "1.1.3").put("title", "Persian Translation - Rowwad Translation Center")
            )
            override fun listTafsirs() = JSONArray()
            override fun translationForSurah(key: String, surah: Int) = JSONArray().put(
                JSONObject().put("id", "1").put("translation", "ترجمه بدون مختصات آیه").put("footnotes", "")
            )
            override fun tafsirForAyah(key: String, surah: Int, ayah: Int) = JSONArray()
        }
        val repository = QuranContentRepository(
            database.debtDao(),
            object : ArabicQuranTextProvider {
                override fun getAyah(surah: Int, ayah: Int): String? = "بِسْمِ اللَّهِ"
            },
            contentProvider = provider
        )

        val result = repository.loadFor(LocalDate.parse("2026-01-01"), forceRefresh = true)

        assertNull(result.verse)
        assertTrue(result.message?.contains("شماره سوره") == true)
        assertNull(database.debtDao().getLatestDailyVerse())
    }
}
