package ir.hirmand.missedprayers.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test fun migrationFromVersionOnePreservesDebtAndAddsNewTables() {
        val name = "migration-v1-to-v6"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO prayer_debts (prayerType,total,completed,updatedAt) VALUES ('FAJR',100,12,1000)")
            execSQL("INSERT INTO fasting_debt (id,total,completed,updatedAt) VALUES (1,30,4,1000)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 6, true, *AppDatabase.MIGRATIONS)
        migrated.query("SELECT total,completed FROM prayer_debts WHERE prayerType='FAJR'").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(100, cursor.getInt(0))
            assertEquals(12, cursor.getInt(1))
        }
        migrated.query("SELECT total,completed FROM fasting_debt WHERE id=1").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals(30, cursor.getInt(0))
            assertEquals(4, cursor.getInt(1))
        }
        migrated.query("PRAGMA table_info(debt_records)").use { cursor ->
            val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
            check("beforeCompleted" in names && "afterCompleted" in names)
        }
        migrated.query("PRAGMA table_info(daily_verses)").use { cursor ->
            val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
            check("translationFootnotes" in names)
        }
        migrated.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }

    @Test fun migrationFromVersionFivePreservesCachedVerseAndAddsNullableFootnotes() {
        val name = "migration-v5-to-v6-footnotes"
        helper.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO daily_verses (dayKey,surah,ayah,globalNumber,arabicText,persianTranslation,translationSource,translationVersion,tafsirText,tafsirSource,recitationUrl,reciterName,fetchedAt) " +
                    "VALUES ('2026-11-26',1,1,1,'بِسْمِ اللَّهِ','ترجمه محفوظ','QuranEnc · Rowwad','1.1.3',NULL,NULL,NULL,NULL,123456789)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(name, 6, true, AppDatabase.MIGRATION_5_6)
        migrated.query("SELECT arabicText,persianTranslation,translationVersion,translationFootnotes FROM daily_verses WHERE dayKey='2026-11-26'").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("بِسْمِ اللَّهِ", cursor.getString(0))
            assertEquals("ترجمه محفوظ", cursor.getString(1))
            assertEquals("1.1.3", cursor.getString(2))
            check(cursor.isNull(3))
        }
        migrated.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
}
