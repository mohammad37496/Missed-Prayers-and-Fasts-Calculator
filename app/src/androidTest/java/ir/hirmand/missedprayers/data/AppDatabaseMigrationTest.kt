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
        val name = "migration-v1-to-v5"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO prayer_debts (prayerType,total,completed,updatedAt) VALUES ('FAJR',100,12,1000)")
            execSQL("INSERT INTO fasting_debt (id,total,completed,updatedAt) VALUES (1,30,4,1000)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 5, true, *AppDatabase.MIGRATIONS)
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
        migrated.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
}
