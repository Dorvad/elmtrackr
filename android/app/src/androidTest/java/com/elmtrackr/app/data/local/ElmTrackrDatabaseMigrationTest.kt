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

    @Test
    fun fullChainFrom2ProducesValidSchema() {
        helper.createDatabase(TEST_DB_CHAIN, 2).apply {
            execSQL(
                """
                INSERT INTO user_settings (
                    localId, userId, timezone, dailyOvertimeThresholdMinutes,
                    weeklyOvertimeThresholdMinutes, weekendDays, currency, onboardingCompleted,
                    featuresTravelRefunds, featuresPaidProjects, featuresInsights,
                    featuresClockStyles, clockStyle, createdAt, updatedAt, syncStatus
                ) VALUES (
                    'settings', 'user', 'UTC', 480, 2400, '5,6', 'ILS', 1,
                    1, 0, 1, 1, 'CLASSIC', 0, 0, 'SYNCED'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO shifts (
                    localId, userId, startTime, breakMinutes, isSpecialDay,
                    createdAt, updatedAt, syncStatus
                ) VALUES ('shift1', 'user', 1000, 0, 0, 0, 0, 'SYNCED')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_CHAIN, 15, true,
            ElmTrackrDatabase.MIGRATION_2_3,
            ElmTrackrDatabase.MIGRATION_3_4,
            ElmTrackrDatabase.MIGRATION_4_5,
            ElmTrackrDatabase.MIGRATION_5_6,
            ElmTrackrDatabase.MIGRATION_6_7,
            ElmTrackrDatabase.MIGRATION_7_8,
            ElmTrackrDatabase.MIGRATION_8_9,
            ElmTrackrDatabase.MIGRATION_9_10,
            ElmTrackrDatabase.MIGRATION_10_11,
            ElmTrackrDatabase.MIGRATION_11_12,
            ElmTrackrDatabase.MIGRATION_12_13,
            ElmTrackrDatabase.MIGRATION_13_14,
            ElmTrackrDatabase.MIGRATION_14_15,
        ).use { db ->
            db.query("SELECT regionCode FROM user_settings WHERE localId = 'settings'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
            }
            db.query("SELECT compensationProfileId FROM shifts WHERE localId = 'shift1'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
            }
        }
    }

    @Test
    fun migration11To12RepairsSnakeCaseColumns() {
        helper.createDatabase(TEST_DB_REPAIR, 11).apply {
            // Recreate the two tables the way the broken legacy 2→3 migration
            // left them: snake_case columns instead of the entity names.
            execSQL("DROP TABLE user_settings")
            execSQL(
                "CREATE TABLE user_settings (`localId` TEXT NOT NULL, `remoteId` TEXT, `userId` TEXT NOT NULL, " +
                    "`timezone` TEXT NOT NULL, `dailyOvertimeThresholdMinutes` INTEGER NOT NULL, " +
                    "`weeklyOvertimeThresholdMinutes` INTEGER NOT NULL, `weekendDays` TEXT NOT NULL, " +
                    "`hourlyRate` REAL, `currency` TEXT NOT NULL, `region_code` TEXT, `currency_code` TEXT, " +
                    "`default_compensation_profile_id` TEXT, `onboardingCompleted` INTEGER NOT NULL, " +
                    "`onboardingCompletedAt` INTEGER, `featuresTravelRefunds` INTEGER NOT NULL, " +
                    "`featuresPaidProjects` INTEGER NOT NULL, `featuresInsights` INTEGER NOT NULL, " +
                    "`featuresClockStyles` INTEGER NOT NULL, `featuresOvertimeReminders` INTEGER NOT NULL, " +
                    "`clockStyle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`deletedAt` INTEGER, `syncStatus` TEXT NOT NULL, `lastSyncError` TEXT, `lastSyncedAt` INTEGER, " +
                    "PRIMARY KEY(`localId`))",
            )
            execSQL(
                """
                INSERT INTO user_settings (
                    localId, userId, timezone, dailyOvertimeThresholdMinutes,
                    weeklyOvertimeThresholdMinutes, weekendDays, currency, region_code,
                    onboardingCompleted, featuresTravelRefunds, featuresPaidProjects,
                    featuresInsights, featuresClockStyles, featuresOvertimeReminders,
                    clockStyle, createdAt, updatedAt, syncStatus
                ) VALUES (
                    'settings', 'user', 'UTC', 480, 2400, '5,6', 'ILS', 'IL',
                    1, 1, 0, 1, 1, 1, 'CLASSIC', 0, 0, 'SYNCED'
                )
                """.trimIndent(),
            )
            execSQL("DROP TABLE shifts")
            execSQL(
                "CREATE TABLE shifts (`localId` TEXT NOT NULL, `remoteId` TEXT, `userId` TEXT NOT NULL, " +
                    "`startTime` INTEGER NOT NULL, `endTime` INTEGER, `breakMinutes` INTEGER NOT NULL, " +
                    "`notes` TEXT, `isSpecialDay` INTEGER NOT NULL, `premiumProfileId` TEXT, `refundAction` TEXT, " +
                    "`compensation_profile_id` TEXT, `compensation_snapshot_json` TEXT, `taskId` TEXT, " +
                    "`taskNameSnapshot` TEXT, `taskIconSnapshot` TEXT, `taskHourlyRateSnapshot` REAL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                    "`syncStatus` TEXT NOT NULL, `lastSyncError` TEXT, `lastSyncedAt` INTEGER, " +
                    "PRIMARY KEY(`localId`))",
            )
            execSQL(
                """
                INSERT INTO shifts (
                    localId, userId, startTime, breakMinutes, isSpecialDay,
                    compensation_profile_id, createdAt, updatedAt, syncStatus
                ) VALUES ('shift1', 'user', 1000, 0, 0, 'cp1', 0, 0, 'SYNCED')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_REPAIR, 12, true, ElmTrackrDatabase.MIGRATION_11_12,
        ).use { db ->
            db.query("SELECT regionCode FROM user_settings WHERE localId = 'settings'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("IL", cursor.getString(0))
            }
            db.query("SELECT compensationProfileId FROM shifts WHERE localId = 'shift1'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("cp1", cursor.getString(0))
            }
        }
    }


    @Test
    fun migration13To14CollapsesDuplicateStartTimesKeepingTheSyncedRow() {
        helper.createDatabase(TEST_DB_DEDUPE, 13).apply {
            // Two local rows for the same (userId, startTime) — reachable before the
            // unique index existed, via an unguarded manual add or a cross-account backup
            // import. The synced row carrying the remoteId must survive; the other must be
            // hard-deleted, because a tombstone would push a delete for the remote row the
            // survivor now owns.
            execSQL(
                """
                INSERT INTO shifts (
                    localId, remoteId, userId, startTime, breakMinutes, isSpecialDay,
                    forceRegularRate, createdAt, updatedAt, syncStatus
                ) VALUES ('keep', 'remote-1', 'user', 1000, 0, 0, 0, 0, 50, 'SYNCED')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO shifts (
                    localId, remoteId, userId, startTime, breakMinutes, isSpecialDay,
                    forceRegularRate, createdAt, updatedAt, syncStatus
                ) VALUES ('drop', NULL, 'user', 1000, 0, 0, 0, 0, 90, 'PENDING_CREATE')
                """.trimIndent(),
            )
            // A distinct start time for the same user must be untouched.
            execSQL(
                """
                INSERT INTO shifts (
                    localId, remoteId, userId, startTime, breakMinutes, isSpecialDay,
                    forceRegularRate, createdAt, updatedAt, syncStatus
                ) VALUES ('other', NULL, 'user', 2000, 0, 0, 0, 0, 10, 'PENDING_CREATE')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_DEDUPE, 14, true, ElmTrackrDatabase.MIGRATION_13_14,
        ).use { db ->
            db.query("SELECT localId FROM shifts WHERE startTime = 1000").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("keep", cursor.getString(0))
            }
            db.query("SELECT localId FROM shifts WHERE startTime = 2000").use { cursor ->
                assertEquals(1, cursor.count)
            }
        }
    }

    @Test
    fun migration14To15AddsProjectDefaultsWithoutTouchingExistingData() {
        helper.createDatabase(TEST_DB_PROJECTS, 14).apply {
            execSQL(
                """
                INSERT INTO user_settings (
                    localId, userId, timezone, dailyOvertimeThresholdMinutes,
                    weeklyOvertimeThresholdMinutes, weekendDays, hourlyRate, currency,
                    onboardingCompleted, featuresTravelRefunds, featuresPaidProjects,
                    featuresInsights, featuresClockStyles, featuresOvertimeReminders,
                    clockStyle, createdAt, updatedAt, syncStatus
                ) VALUES (
                    'settings', 'user', 'Asia/Jerusalem', 516, 2520, '5,6', 62.5, 'ILS',
                    1, 1, 0, 1, 1, 1, 'AURORA', 0, 0, 'SYNCED'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO shifts (
                    localId, userId, startTime, endTime, breakMinutes, isSpecialDay,
                    forceRegularRate, createdAt, updatedAt, syncStatus
                ) VALUES ('shift1', 'user', 1000, 5000, 30, 0, 0, 0, 0, 'SYNCED')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_PROJECTS, 15, true, ElmTrackrDatabase.MIGRATION_14_15,
        ).use { db ->
            // New columns exist and read as "Paid Projects not configured".
            db.query(
                "SELECT projectsDefaultRegionCode, projectsDefaultCurrencyCode, projectsTaxLabel, " +
                    "projectsTaxRateBasisPoints, projectsTaxInclusive, featuresPaidProjects " +
                    "FROM user_settings WHERE localId = 'settings'",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(true, cursor.isNull(0))
                assertEquals(true, cursor.isNull(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
            }
            // The existing user's pay setup and shift survive untouched.
            db.query(
                "SELECT hourlyRate, currency, clockStyle, weekendDays " +
                    "FROM user_settings WHERE localId = 'settings'",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(62.5, cursor.getDouble(0), 0.0001)
                assertEquals("ILS", cursor.getString(1))
                assertEquals("AURORA", cursor.getString(2))
                assertEquals("5,6", cursor.getString(3))
            }
            db.query("SELECT startTime, endTime, breakMinutes FROM shifts WHERE localId = 'shift1'")
                .use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals(1000, cursor.getLong(0))
                    assertEquals(5000, cursor.getLong(1))
                    assertEquals(30, cursor.getInt(2))
                }
        }
    }

    private companion object {
        const val TEST_DB = "currency-migration-test"
        const val TEST_DB_CHAIN = "full-chain-migration-test"
        const val TEST_DB_REPAIR = "column-repair-migration-test"
        const val TEST_DB_DEDUPE = "migration-dedupe-db"
        const val TEST_DB_PROJECTS = "paid-projects-migration-test"
    }
}
