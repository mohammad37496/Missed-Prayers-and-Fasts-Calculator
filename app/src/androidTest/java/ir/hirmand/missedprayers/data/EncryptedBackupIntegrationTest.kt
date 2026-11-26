package ir.hirmand.missedprayers.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
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
}
