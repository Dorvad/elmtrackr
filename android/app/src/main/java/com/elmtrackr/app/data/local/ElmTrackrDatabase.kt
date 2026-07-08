package com.elmtrackr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elmtrackr.app.data.local.converter.Converters
import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.ReceiptDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.ReceiptEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.TaskEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ShiftEntity::class,
        UserSettingsEntity::class,
        ProfileEntity::class,
        RefundClaimEntity::class,
        ReceiptEntity::class,
        CompensationProfileEntity::class,
        PremiumProfileEntity::class,
        TaskEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ElmTrackrDatabase : RoomDatabase() {

    abstract fun shiftDao(): ShiftDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao
    abstract fun refundClaimDao(): RefundClaimDao

    abstract fun receiptDao(): ReceiptDao

    abstract fun compensationProfileDao(): CompensationProfileDao

    abstract fun premiumProfileDao(): PremiumProfileDao

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: ElmTrackrDatabase? = null
        @Volatile private var preWarmStarted = false

        /** Opens the database on a background thread as early as possible. */
        fun preWarm(context: Context) {
            if (INSTANCE != null || preWarmStarted) return
            synchronized(this) {
                if (INSTANCE != null || preWarmStarted) return
                preWarmStarted = true
                Thread(
                    { getInstance(context) },
                    "elmtrackr-db-prewarm",
                ).start()
            }
        }

        fun getInstance(context: Context): ElmTrackrDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildEncryptedDatabase(context.appContext())
                    .also { INSTANCE = it }
            }

        private fun Context.appContext(): Context = applicationContext ?: this

        private fun buildEncryptedDatabase(context: Context): ElmTrackrDatabase {
            val appContext = context.appContext()
            System.loadLibrary("sqlcipher")
            val passphrase = DatabasePassphraseStore(appContext).getOrCreatePassphrase()
            PlaintextDatabaseMigrator.migrateIfNeeded(appContext, passphrase)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                appContext,
                ElmTrackrDatabase::class.java,
                "elmtrackr.db",
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                )
                .build()
        }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN currency TEXT NOT NULL DEFAULT 'ILS'")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN regionCode TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN currencyCode TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN defaultCompensationProfileId TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN compensationProfileId TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN compensationSnapshotJson TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS compensation_profiles (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        regionCode TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        timezone TEXT NOT NULL,
                        baseHourlyRate REAL,
                        rulesJson TEXT NOT NULL,
                        stackingPolicy TEXT NOT NULL,
                        effectiveFrom INTEGER NOT NULL,
                        effectiveUntil INTEGER,
                        isDefault INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_compensation_profiles_userId ON compensation_profiles(userId)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN taskId TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN taskNameSnapshot TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN taskIconSnapshot TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN taskHourlyRateSnapshot REAL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tasks (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        hourlyRate REAL NOT NULL,
                        isArchived INTEGER NOT NULL,
                        lastUsedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_userId ON tasks(userId)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN featuresOvertimeReminders INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_shifts_userId_startTime ON shifts(userId, startTime)",
                )
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN color TEXT")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_shifts_userId_syncStatus ON shifts(userId, syncStatus)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shifts_remoteId ON shifts(remoteId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tasks_userId_syncStatus ON tasks(userId, syncStatus)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_remoteId ON tasks(remoteId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_refund_claims_userId_syncStatus " +
                        "ON refund_claims(userId, syncStatus)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_refund_claims_remoteId ON refund_claims(remoteId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_compensation_profiles_userId_syncStatus " +
                        "ON compensation_profiles(userId, syncStatus)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_compensation_profiles_remoteId " +
                        "ON compensation_profiles(remoteId)",
                )
            }
        }

        // MIGRATION_7_8 created indexes that were never declared on the entities, so
        // Room's post-migration schema validation rejected upgraded databases while
        // fresh installs never got the indexes at all. Version 9 declares them on the
        // entities; IF NOT EXISTS makes this a no-op for databases that came through
        // the 7→8 path and creates them for databases first created at version 8.
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_shifts_userId_syncStatus ON shifts(userId, syncStatus)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shifts_remoteId ON shifts(remoteId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tasks_userId_syncStatus ON tasks(userId, syncStatus)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_remoteId ON tasks(remoteId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_refund_claims_userId_syncStatus " +
                        "ON refund_claims(userId, syncStatus)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_refund_claims_remoteId ON refund_claims(remoteId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_compensation_profiles_userId_syncStatus " +
                        "ON compensation_profiles(userId, syncStatus)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_compensation_profiles_remoteId " +
                        "ON compensation_profiles(remoteId)",
                )
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receipts (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT,
                        refundClaimId TEXT,
                        localImageUri TEXT NOT NULL,
                        merchantName TEXT,
                        amount REAL,
                        currency TEXT,
                        receiptDate INTEGER,
                        rawOcrText TEXT,
                        parserVersion TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(refundClaimId) REFERENCES refund_claims(localId) ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_userId ON receipts(userId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_refundClaimId ON receipts(refundClaimId)")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS premium_profiles (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        multiplier REAL NOT NULL,
                        premiumType TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        isArchived INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_premium_profiles_userId ON premium_profiles(userId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_premium_profiles_userId_syncStatus " +
                        "ON premium_profiles(userId, syncStatus)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_premium_profiles_remoteId ON premium_profiles(remoteId)")
                db.execSQL("ALTER TABLE shifts ADD COLUMN premiumProfileId TEXT")
            }
        }

        /**
         * Repair for installs whose original 2→3 migration created snake_case
         * columns (region_code, compensation_profile_id, …) that never matched
         * the camelCase entity fields — Room's schema validation rejected those
         * databases on every launch. Rebuild the two affected tables with the
         * expected schema, copying data across. No-op for healthy installs.
         */
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (db.hasColumn("user_settings", "region_code")) {
                    db.execSQL(
                        """
                        CREATE TABLE user_settings_repair (`localId` TEXT NOT NULL, `remoteId` TEXT, `userId` TEXT NOT NULL, `timezone` TEXT NOT NULL, `dailyOvertimeThresholdMinutes` INTEGER NOT NULL, `weeklyOvertimeThresholdMinutes` INTEGER NOT NULL, `weekendDays` TEXT NOT NULL, `hourlyRate` REAL, `currency` TEXT NOT NULL, `regionCode` TEXT, `currencyCode` TEXT, `defaultCompensationProfileId` TEXT, `onboardingCompleted` INTEGER NOT NULL, `onboardingCompletedAt` INTEGER, `featuresTravelRefunds` INTEGER NOT NULL, `featuresPaidProjects` INTEGER NOT NULL, `featuresInsights` INTEGER NOT NULL, `featuresClockStyles` INTEGER NOT NULL, `featuresOvertimeReminders` INTEGER NOT NULL, `clockStyle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncStatus` TEXT NOT NULL, `lastSyncError` TEXT, `lastSyncedAt` INTEGER, PRIMARY KEY(`localId`))
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO user_settings_repair (
                            localId, remoteId, userId, timezone, dailyOvertimeThresholdMinutes,
                            weeklyOvertimeThresholdMinutes, weekendDays, hourlyRate, currency,
                            regionCode, currencyCode, defaultCompensationProfileId,
                            onboardingCompleted, onboardingCompletedAt, featuresTravelRefunds,
                            featuresPaidProjects, featuresInsights, featuresClockStyles,
                            featuresOvertimeReminders, clockStyle, createdAt, updatedAt,
                            deletedAt, syncStatus, lastSyncError, lastSyncedAt
                        )
                        SELECT localId, remoteId, userId, timezone, dailyOvertimeThresholdMinutes,
                            weeklyOvertimeThresholdMinutes, weekendDays, hourlyRate, currency,
                            region_code, currency_code, default_compensation_profile_id,
                            onboardingCompleted, onboardingCompletedAt, featuresTravelRefunds,
                            featuresPaidProjects, featuresInsights, featuresClockStyles,
                            featuresOvertimeReminders, clockStyle, createdAt, updatedAt,
                            deletedAt, syncStatus, lastSyncError, lastSyncedAt
                        FROM user_settings
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE user_settings")
                    db.execSQL("ALTER TABLE user_settings_repair RENAME TO user_settings")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_settings_userId` ON `user_settings` (`userId`)")
                }
                if (db.hasColumn("shifts", "compensation_profile_id")) {
                    db.execSQL(
                        """
                        CREATE TABLE shifts_repair (`localId` TEXT NOT NULL, `remoteId` TEXT, `userId` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `breakMinutes` INTEGER NOT NULL, `notes` TEXT, `isSpecialDay` INTEGER NOT NULL, `premiumProfileId` TEXT, `refundAction` TEXT, `compensationProfileId` TEXT, `compensationSnapshotJson` TEXT, `taskId` TEXT, `taskNameSnapshot` TEXT, `taskIconSnapshot` TEXT, `taskHourlyRateSnapshot` REAL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, `syncStatus` TEXT NOT NULL, `lastSyncError` TEXT, `lastSyncedAt` INTEGER, PRIMARY KEY(`localId`))
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO shifts_repair (
                            localId, remoteId, userId, startTime, endTime, breakMinutes, notes,
                            isSpecialDay, premiumProfileId, refundAction, compensationProfileId,
                            compensationSnapshotJson, taskId, taskNameSnapshot, taskIconSnapshot,
                            taskHourlyRateSnapshot, createdAt, updatedAt, deletedAt, syncStatus,
                            lastSyncError, lastSyncedAt
                        )
                        SELECT localId, remoteId, userId, startTime, endTime, breakMinutes, notes,
                            isSpecialDay, premiumProfileId, refundAction, compensation_profile_id,
                            compensation_snapshot_json, taskId, taskNameSnapshot, taskIconSnapshot,
                            taskHourlyRateSnapshot, createdAt, updatedAt, deletedAt, syncStatus,
                            lastSyncError, lastSyncedAt
                        FROM shifts
                        """.trimIndent(),
                    )
                    db.execSQL("DROP TABLE shifts")
                    db.execSQL("ALTER TABLE shifts_repair RENAME TO shifts")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_userId` ON `shifts` (`userId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_userId_endTime` ON `shifts` (`userId`, `endTime`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_userId_startTime` ON `shifts` (`userId`, `startTime`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_syncStatus` ON `shifts` (`syncStatus`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_userId_syncStatus` ON `shifts` (`userId`, `syncStatus`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_shifts_remoteId` ON `shifts` (`remoteId`)")
                }
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN forceRegularRate INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
            query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }
    }
}
