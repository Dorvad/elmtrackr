package com.elmtrackr.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ElmTrackrDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElmTrackrDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration1To2AddsIlsCurrencyDefault() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO user_settings (
                    localId, userId, timezone, dailyOvertimeThresholdMinutes,
                    weeklyOvertimeThresholdMinutes, weekendDays, onboardingCompleted,
                    featuresTravelRefunds, featuresPaidProjects, featuresInsights,
                    featuresClockStyles, clockStyle, createdAt, updatedAt, syncStatus
                ) VALUES (
                    'settings', 'user', 'UTC', 480, 2400, '5,6', 1,
                    1, 0, 1, 1, 'CLASSIC', 0, 0, 'SYNCED'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, ElmTrackrDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT currency FROM user_settings WHERE localId = 'settings'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("ILS", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "currency-migration-test"
    }
}
