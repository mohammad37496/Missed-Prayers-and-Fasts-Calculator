package ir.hirmand.missedprayers.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedBackupIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        System.loadLibrary("sqlcipher")
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory("integration-test-passphrase-32bytes".toByteArray()))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    @After fun tearDown() { database.close() }

    @Test fun backupRestoresAllCoreRecords() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("FAJR", 40, 7, 1000))
        dao.saveFasting(FastingDebtEntity(total = 20, completed = 3, updatedAt = 1000))
        dao.insertRecord(DebtRecordEntity(kind = "PRAYER", reference = "FAJR", amount = 2, createdAt = 1100, beforeCompleted = 5, afterCompleted = 7))
        dao.saveProfile(UserProfileEntity(displayName = "Test", gender = "WOMAN", taklifStartDate = "2010-01-01", taklifEndDate = null, timezoneId = "Asia/Tehran", calendarPreference = "PERSIAN", marjaId = null, updatedAt = 1000))

        val manager = EncryptedBackupManager(database)
        val password = "correct-backup-password".toCharArray()
        val backup = manager.createBackup(password)
        dao.clearPrayers(); dao.clearFasting(); dao.clearRecords(); dao.clearProfile()
        manager.restoreBackup(backup, "correct-backup-password".toCharArray())

        assertEquals(7, dao.getPrayer("FAJR")?.completed)
        assertEquals(3, dao.getFasting()?.completed)
        assertEquals("Test", dao.getProfileSnapshot()?.displayName)
        assertEquals(1, dao.getAllRecords().size)
        assertEquals(5, dao.getAllRecords().single().beforeCompleted)
    }

    @Test fun adjustmentsAndUndoPreserveHistoryAndRejectStaleUndo() = runBlocking {
        val repository = DebtRepository(database)
        val dao = database.debtDao()
        repository.initializePrayer(ir.hirmand.missedprayers.domain.PrayerType.FAJR, 100, 5)
        repository.initializePrayer(ir.hirmand.missedprayers.domain.PrayerType.FAJR, 120, 7)
        val adjustment = dao.getAllRecords().single()
        assertEquals(5, adjustment.beforeCompleted)
        assertEquals(7, adjustment.afterCompleted)
        repository.undoRecord(adjustment.id)
        assertEquals(5, dao.getPrayer("FAJR")?.completed)

        repository.recordPrayer(ir.hirmand.missedprayers.domain.PrayerType.FAJR, 2)
        val first = dao.getAllRecords().last()
        repository.recordPrayer(ir.hirmand.missedprayers.domain.PrayerType.FAJR, 1)
        val second = dao.getAllRecords().last()
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.undoRecord(first.id) } }
        assertEquals(8, dao.getPrayer("FAJR")?.completed)
        repository.undoRecord(second.id)
        repository.undoRecord(first.id)
        assertEquals(5, dao.getPrayer("FAJR")?.completed)
    }

    @Test fun invalidBackupDoesNotDeleteExistingData() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("ASR", 12, 4, 1000))
        val manager = EncryptedBackupManager(database)
        val valid = manager.createBackup("correct-backup-password".toCharArray())
        val corrupted = valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.restoreBackup(corrupted, "correct-backup-password".toCharArray()) }
        }
        assertEquals(4, dao.getPrayer("ASR")?.completed)
    }

    @Test fun encryptedDatabaseFileDoesNotExposePlainSqliteHeader() = runBlocking {
        val name = "sqlcipher-integration-test.db"
        context.deleteDatabase(name)
        val keyProvider = DatabaseKeyProvider(context)
        val passphrase = keyProvider.getOrCreatePassphrase()
        assertEquals(32, passphrase.size)
        assertArrayEquals(passphrase, keyProvider.getOrCreatePassphrase())
        val fileDb = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            fileDb.debtDao().savePrayer(PrayerDebtEntity("ISHA", 9, 1, 1000))
            val header = context.getDatabasePath(name).inputStream().use { it.readNBytes(16) }
            assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
            assertEquals(1, fileDb.debtDao().getPrayer("ISHA")?.completed)
        } finally {
            fileDb.close()
            passphrase.fill(0)
            context.deleteDatabase(name)
        }
    }

    @Test fun dailyVerseCanBeReadFromEncryptedCacheWithoutNetwork() = runBlocking {
        val date = LocalDate.parse("2026-11-26")
        val cached = DailyVerseEntity(
            dayKey = date.toString(), surah = 1, ayah = 1, globalNumber = 1,
            arabicText = "بِسْمِ ٱللَّهِ", persianTranslation = "به نام خدا",
            translationSource = "QuranEnc", translationVersion = "test",
            tafsirText = null, tafsirSource = null, recitationUrl = null,
            reciterName = null, fetchedAt = 1_000L
        )
        database.debtDao().saveDailyVerse(cached)

        val repository = QuranContentRepository(
            database.debtDao(),
            TanzilAssetTextProvider(context.assets)
        )
        val result = repository.loadFor(date, forceRefresh = false)

        assertEquals(true, result.fromCache)
        assertEquals(cached, result.verse)
        assertEquals(null, result.message)
    }

    @Test fun changingProfileAndEstimatePreservesExcuseHistoryAndCompletedCounts() = runBlocking {
        val repository = DebtRepository(database)
        repository.saveProfile(
            UserProfileEntity(
                displayName = null, gender = "WOMAN", taklifStartDate = "2010-01-01",
                taklifEndDate = null, timezoneId = "Asia/Tehran",
                calendarPreference = "PERSIAN", marjaId = null, updatedAt = 1_000L
            )
        )
        repository.addExcusePeriod(
            "MENSTRUATION", LocalDate.parse("2020-01-10"),
            LocalDate.parse("2020-01-14"), "ثبت خصوصی کاربر"
        )
        repository.initializePrayer(ir.hirmand.missedprayers.domain.PrayerType.FAJR, 40, 10)
        repository.initializeFasting(20, 5)

        repository.applyRawDateEstimate(prayerPerType = 50, fastingDays = 30)
        repository.saveProfile(
            UserProfileEntity(
                displayName = null, gender = "MAN", taklifStartDate = "2012-01-01",
                taklifEndDate = null, timezoneId = "Asia/Tehran",
                calendarPreference = "PERSIAN", marjaId = null, updatedAt = 2_000L
            )
        )

        assertEquals(10, database.debtDao().getPrayer("FAJR")?.completed)
        assertEquals(50, database.debtDao().getPrayer("FAJR")?.total)
        assertEquals(5, database.debtDao().getFasting()?.completed)
        assertEquals(30, database.debtDao().getFasting()?.total)
        assertEquals("MENSTRUATION", database.debtDao().getAllExcusePeriods().single().kind)
        assertEquals("2020-01-10", database.debtDao().getAllExcusePeriods().single().startDate)
    }

    @Test fun searchableHistoryIncludesRecordsOlderThanDashboardRecentWindow() = runBlocking {
        val dao = database.debtDao()
        repeat(25) { index ->
            dao.insertRecord(
                DebtRecordEntity(
                    kind = "PRAYER",
                    reference = "FAJR",
                    amount = 1,
                    createdAt = 1_000L + index,
                    beforeCompleted = index,
                    afterCompleted = index + 1
                )
            )
        }

        assertEquals(20, dao.observeRecentRecords().first().size)
        val history = dao.observeAllRecords().first()
        assertEquals(25, history.size)
        assertEquals((25L downTo 1L).toList(), history.map { it.id })
    }

    @Test fun restoreRejectsHistoryWithoutMatchingDebtWithoutReplacingExistingData() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("ISHA", 9, 2, 1_000L))
        dao.insertRecord(
            DebtRecordEntity(
                kind = "PRAYER",
                reference = "FAJR",
                amount = 1,
                createdAt = 1_100L,
                beforeCompleted = 0,
                afterCompleted = 1
            )
        )
        val manager = EncryptedBackupManager(database)
        val backup = manager.createBackup("correct-backup-password".toCharArray())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.restoreBackup(backup, "correct-backup-password".toCharArray()) }
        }
        assertEquals(2, dao.getPrayer("ISHA")?.completed)
        assertEquals(null, dao.getPrayer("FAJR"))
    }

    @Test fun undoCannotRestoreCompletedCountAboveCurrentDebtTotal() = runBlocking {
        val dao = database.debtDao()
        dao.savePrayer(PrayerDebtEntity("FAJR", 5, 5, 1_000L))
        val id = dao.insertRecord(
            DebtRecordEntity(
                kind = "PRAYER_ADJUSTMENT",
                reference = "FAJR",
                amount = 5,
                createdAt = 1_100L,
                beforeCompleted = 10,
                afterCompleted = 5
            )
        )

        val repository = DebtRepository(database)
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.undoRecord(id) } }
        assertEquals(5, dao.getPrayer("FAJR")?.completed)
    }

    @Test fun missingKeystoreKeyIsNotSilentlyReplaced() {
        val alias = "missed_worship_database_key_test"
        val preferences = context.getSharedPreferences("database_key_test_v1", Context.MODE_PRIVATE)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        preferences.edit().clear().commit()
        try {
            val provider = DatabaseKeyProvider(context, "database_key_test_v1", alias)
            provider.getOrCreatePassphrase().fill(0)
            keyStore.deleteEntry(alias)

            assertThrows(IllegalStateException::class.java) { provider.getOrCreatePassphrase() }
            assertFalse(keyStore.containsAlias(alias))
            assertEquals(true, preferences.contains("encrypted_passphrase"))
        } finally {
            preferences.edit().clear().commit()
            keyStore.deleteEntry(alias)
        }
    }
}
