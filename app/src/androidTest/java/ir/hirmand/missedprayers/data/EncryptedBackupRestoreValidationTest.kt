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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

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
}
