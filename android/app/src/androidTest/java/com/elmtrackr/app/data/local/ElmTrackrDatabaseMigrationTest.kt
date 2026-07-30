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
            TEST_DB_CHAIN, 16, true,
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
            ElmTrackrDatabase.MIGRATION_15_16,
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

    /**
     * Every supported production schema version must upgrade cleanly to the
     * current one. `runMigrationsAndValidate` compares the migrated database
     * against the exported schema, so this covers both "the SQL runs" and "the
     * result is exactly what Room expects".
     *
     * Version 9 is absent from [SUPPORTED_START_VERSIONS] because `9.json` was
     * never exported (a pre-existing gap in app/schemas): MigrationTestHelper
     * cannot create a database at a version it has no schema for. Versions 8 and
     * 10 on either side are covered, and the 8→9→10 hop is exercised by every
     * chain that starts at or below 8.
     */
    @Test
    fun everySupportedStartVersionMigratesToCurrent() {
        for (startVersion in SUPPORTED_START_VERSIONS) {
            val dbName = "chain-from-$startVersion"
            helper.createDatabase(dbName, startVersion).close()
            helper.runMigrationsAndValidate(dbName, CURRENT_VERSION, true, *ALL_MIGRATIONS).close()
        }
    }

    /** Data written at the oldest supported version must survive to the newest. */
    @Test
    fun dataWrittenAtVersion1SurvivesToCurrent() {
        helper.createDatabase(TEST_DB_V1_DATA, 1).apply {
            execSQL(
                """
                INSERT INTO user_settings (
                    localId, userId, timezone, dailyOvertimeThresholdMinutes,
                    weeklyOvertimeThresholdMinutes, weekendDays, hourlyRate, onboardingCompleted,
                    featuresTravelRefunds, featuresPaidProjects, featuresInsights,
                    featuresClockStyles, clockStyle, createdAt, updatedAt, syncStatus
                ) VALUES (
                    'settings', 'user', 'Asia/Jerusalem', 480, 2400, '5,6', 55.5, 1,
                    1, 0, 1, 1, 'CLASSIC', 0, 0, 'SYNCED'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO shifts (
                    localId, userId, startTime, endTime, breakMinutes, isSpecialDay,
                    createdAt, updatedAt, syncStatus
                ) VALUES ('shift1', 'user', 1000, 29800000, 30, 0, 0, 0, 'SYNCED')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB_V1_DATA, CURRENT_VERSION, true, *ALL_MIGRATIONS)
            .use { db ->
                db.query(
                    "SELECT hourlyRate, featuresPaidProjects FROM user_settings WHERE localId = 'settings'",
                ).use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(55.5, cursor.getDouble(0), 0.0001)
                    // Paid Projects stays off for an upgrading user.
                    assertEquals(0, cursor.getInt(1))
                }
                db.query(
                    "SELECT startTime, endTime, breakMinutes, projectId, projectNameSnapshot " +
                        "FROM shifts WHERE localId = 'shift1'",
                ).use { cursor ->
                    assertEquals(1, cursor.count)
                    cursor.moveToFirst()
                    assertEquals(1000L, cursor.getLong(0))
                    assertEquals(29800000L, cursor.getLong(1))
                    assertEquals(30, cursor.getInt(2))
                    // The project relationship is nullable and never backfilled.
                    assertEquals(true, cursor.isNull(3))
                    assertEquals(true, cursor.isNull(4))
                }
            }
    }

    @Test
    fun migration15To16CreatesProjectTablesAndLeavesExistingDataAlone() {
        helper.createDatabase(TEST_DB_PROJECT_TABLES, 15).apply {
            execSQL(
                """
                INSERT INTO user_settings (
                    localId, userId, timezone, dailyOvertimeThresholdMinutes,
                    weeklyOvertimeThresholdMinutes, weekendDays, hourlyRate, currency,
                    onboardingCompleted, featuresTravelRefunds, featuresPaidProjects,
                    featuresInsights, featuresClockStyles, featuresOvertimeReminders,
                    projectsTaxRateBasisPoints, projectsTaxInclusive,
                    clockStyle, createdAt, updatedAt, syncStatus
                ) VALUES (
                    'settings', 'user', 'Asia/Jerusalem', 516, 2520, '5,6', 62.5, 'ILS',
                    1, 1, 0, 1, 1, 1, 0, 0, 'AURORA', 0, 0, 'SYNCED'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO shifts (
                    localId, userId, startTime, endTime, breakMinutes, isSpecialDay,
                    forceRegularRate, taskId, taskHourlyRateSnapshot,
                    createdAt, updatedAt, syncStatus
                ) VALUES ('shift1', 'user', 1000, 5000, 30, 0, 0, 'task-1', 120.0, 0, 0, 'SYNCED')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_PROJECT_TABLES, 16, true, ElmTrackrDatabase.MIGRATION_15_16,
        ).use { db ->
            // The three tables exist and are empty: no project data is invented.
            listOf("projects", "project_billing_records", "project_payments").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertEquals(true, cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
            // Money columns are TEXT, never REAL.
            db.query("PRAGMA table_info(`projects`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val types = mutableMapOf<String, String>()
                while (cursor.moveToNext()) {
                    types[cursor.getString(nameIndex)] = cursor.getString(typeIndex)
                }
                assertEquals("TEXT", types["baseFee"])
                assertEquals("TEXT", types["taxAmount"])
                assertEquals("TEXT", types["clientTotal"])
                assertEquals("TEXT", types["targetHourlyRate"])
            }
            db.query("PRAGMA table_info(`project_payments`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                var amountType: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "amount") amountType = cursor.getString(typeIndex)
                }
                assertEquals("TEXT", amountType)
            }
            // The existing shift keeps its task link and rate, and gains a null
            // project link.
            db.query(
                "SELECT taskId, taskHourlyRateSnapshot, projectId, projectNameSnapshot " +
                    "FROM shifts WHERE localId = 'shift1'",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("task-1", cursor.getString(0))
                assertEquals(120.0, cursor.getDouble(1), 0.0001)
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
            }
            // Pay-relevant settings are untouched.
            db.query(
                "SELECT hourlyRate, clockStyle, featuresPaidProjects FROM user_settings " +
                    "WHERE localId = 'settings'",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(62.5, cursor.getDouble(0), 0.0001)
                assertEquals("AURORA", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        }
    }

    /**
     * 16 -> 17 adds the compensation-source discriminator. The column must arrive
     * NULL on every existing shift: NULL reads as EMPLOYEE, so an upgraded
     * database pays exactly what it paid before the upgrade.
     */
    @Test
    fun migration16To17AddsCompensationSourceWithoutTouchingExistingShifts() {
        helper.createDatabase(TEST_DB_COMP_SOURCE, 16).apply {
            execSQL(
                """
                INSERT INTO shifts (
                    localId, userId, startTime, endTime, breakMinutes, isSpecialDay,
                    forceRegularRate, taskId, taskHourlyRateSnapshot, projectId,
                    createdAt, updatedAt, syncStatus
                ) VALUES ('shift1', 'user', 1000, 29800000, 30, 0, 0, 'task-1', 120.0, NULL, 0, 0, 'SYNCED')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB_COMP_SOURCE, 17, true, ElmTrackrDatabase.MIGRATION_16_17,
        ).use { db ->
            db.query("PRAGMA table_info(`shifts`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) != "compensationSource") continue
                    found = true
                    assertEquals("TEXT", cursor.getString(typeIndex))
                    // Nullable, so no existing row needs a value written into it.
                    assertEquals(0, cursor.getInt(notNullIndex))
                }
                assertEquals(true, found)
            }
            db.query(
                "SELECT startTime, endTime, breakMinutes, taskHourlyRateSnapshot, " +
                    "compensationSource FROM shifts WHERE localId = 'shift1'",
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals(1000L, cursor.getLong(0))
                assertEquals(29800000L, cursor.getLong(1))
                assertEquals(30, cursor.getInt(2))
                assertEquals(120.0, cursor.getDouble(3), 0.0001)
                // Not backfilled: NULL means EMPLOYEE, the pre-upgrade meaning.
                assertEquals(true, cursor.isNull(4))
            }
        }
    }

    private companion object {
        const val TEST_DB = "currency-migration-test"
        const val TEST_DB_CHAIN = "full-chain-migration-test"
        const val TEST_DB_REPAIR = "column-repair-migration-test"
        const val TEST_DB_DEDUPE = "migration-dedupe-db"
        const val TEST_DB_PROJECTS = "paid-projects-migration-test"
        const val TEST_DB_PROJECT_TABLES = "project-tables-migration-test"
        const val TEST_DB_V1_DATA = "v1-data-migration-test"
        const val TEST_DB_COMP_SOURCE = "compensation-source-migration-test"

        const val CURRENT_VERSION = 17

        /**
         * Schema versions a production database can currently be at. 9 is
         * missing because `9.json` was never exported; see
         * everySupportedStartVersionMigratesToCurrent.
         */
        val SUPPORTED_START_VERSIONS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16)

        val ALL_MIGRATIONS = arrayOf(
            ElmTrackrDatabase.MIGRATION_1_2,
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
            ElmTrackrDatabase.MIGRATION_15_16,
            ElmTrackrDatabase.MIGRATION_16_17,
        )
    }
}
