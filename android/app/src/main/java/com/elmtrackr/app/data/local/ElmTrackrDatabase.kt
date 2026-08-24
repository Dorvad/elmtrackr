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
import com.elmtrackr.app.data.local.dao.ProjectBillingRecordDao
import com.elmtrackr.app.data.local.dao.ProjectDao
import com.elmtrackr.app.data.local.dao.ProjectPaymentDao
import com.elmtrackr.app.data.local.dao.ReceiptDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.dao.AbsenceAllocationDao
import com.elmtrackr.app.data.local.dao.AbsenceEventDao
import com.elmtrackr.app.data.local.dao.LeaveBalanceSnapshotDao
import com.elmtrackr.app.data.local.dao.LeavePolicyDao
import com.elmtrackr.app.data.local.dao.WorkplaceDao
import com.elmtrackr.app.data.local.entity.AbsenceAllocationEntity
import com.elmtrackr.app.data.local.entity.AbsenceEventEntity
import com.elmtrackr.app.data.local.entity.LeaveBalanceSnapshotEntity
import com.elmtrackr.app.data.local.entity.LeavePolicyEntity
import com.elmtrackr.app.data.local.entity.WorkplaceEntity
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.PremiumProfileEntity
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
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
        ProjectEntity::class,
        ProjectBillingRecordEntity::class,
        ProjectPaymentEntity::class,
        WorkplaceEntity::class,
        LeavePolicyEntity::class,
        AbsenceEventEntity::class,
        AbsenceAllocationEntity::class,
        LeaveBalanceSnapshotEntity::class,
    ],
    version = 20,
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

    abstract fun projectDao(): ProjectDao

    abstract fun projectBillingRecordDao(): ProjectBillingRecordDao

    abstract fun projectPaymentDao(): ProjectPaymentDao

    abstract fun workplaceDao(): WorkplaceDao

    abstract fun leavePolicyDao(): LeavePolicyDao

    abstract fun absenceEventDao(): AbsenceEventDao

    abstract fun absenceAllocationDao(): AbsenceAllocationDao

    abstract fun leaveBalanceSnapshotDao(): LeaveBalanceSnapshotDao

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
                    {
                        // Only an optimisation, so a failure here is left for the
                        // first real caller to hit. That is not a way of ignoring
                        // it: INSTANCE is still null, so the next getInstance
                        // retries and throws on a thread whose stack says which
                        // screen wanted the database — far more useful in a crash
                        // report than a thread called "elmtrackr-db-prewarm".
                        runCatching { getInstance(context) }
                    },
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
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
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

        /**
         * Enforces one shift per (userId, startTime), which the sync engine already
         * assumed. Duplicates are collapsed first, keeping the row most likely to be the
         * server's: a synced row with a remoteId wins, then the most recently updated.
         * Losing rows are deleted rather than soft-deleted — a tombstone would push a
         * delete for the remote row the surviving duplicate shares.
         */
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM shifts WHERE localId NOT IN (
                        SELECT localId FROM shifts s
                        WHERE s.localId = (
                            SELECT localId FROM shifts d
                            WHERE d.userId = s.userId AND d.startTime = s.startTime
                            ORDER BY (d.remoteId IS NULL), d.updatedAt DESC, d.localId
                            LIMIT 1
                        )
                    )
                    """.trimIndent(),
                )
                db.execSQL("DROP INDEX IF EXISTS `index_shifts_userId_startTime`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_shifts_userId_startTime` " +
                        "ON `shifts` (`userId`, `startTime`)",
                )
            }
        }

        /**
         * Paid Projects activation shell: per-user defaults for newly created
         * projects (country/region, currency, tax label, optional tax rate and
         * tax-inclusive preference).
         *
         * Purely additive. The nullable columns default to NULL and the two
         * NOT NULL columns default to 0, which reads as "no project tax
         * configured" — the module ships disabled for every existing user, so
         * nothing about their hours, pay, or navigation changes. No table is
         * rebuilt and no index is added, so there is nothing here for Room's
         * post-migration validation to reject.
         */
        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN projectsDefaultRegionCode TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN projectsDefaultCurrencyCode TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN projectsTaxLabel TEXT")
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN projectsTaxRateBasisPoints " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN projectsTaxInclusive " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Paid Projects data model: projects, their billing records and their
         * payments, plus a nullable project link on shifts.
         *
         * Purely additive and non-destructive:
         *  - three new tables, so no existing row is read or rewritten;
         *  - the two new `shifts` columns are nullable with no default and are
         *    never backfilled, so existing shifts stay exactly as they were and
         *    no wage calculation changes;
         *  - no existing column, index or constraint is touched.
         *
         * Money columns are TEXT holding canonical decimal strings — never REAL.
         * Binary floating point cannot represent most decimal amounts exactly,
         * and a stored fee must round-trip byte for byte. Calendar dates are
         * INTEGER epoch days; timestamps are INTEGER epoch millis.
         *
         * No SQL foreign keys, matching how shifts reference tasks: this schema
         * soft-deletes, and the repository cascades a project delete to its
         * children. Every index created here is declared on the corresponding
         * entity — the 7→8 migration once created indexes that were not, and
         * Room rejected every upgraded database until 8→9 repaired it.
         */
        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS projects (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        clientName TEXT,
                        clientId TEXT,
                        description TEXT,
                        workStatus TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        baseFee TEXT NOT NULL,
                        taxLabel TEXT,
                        taxRatePercent TEXT NOT NULL,
                        taxMode TEXT NOT NULL,
                        taxAmount TEXT NOT NULL,
                        clientTotal TEXT NOT NULL,
                        hourBudgetMinutes INTEGER,
                        targetHourlyRate TEXT,
                        startDate INTEGER,
                        deadline INTEGER,
                        completionDate INTEGER,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        archivedAt INTEGER,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_userId` ON `projects` (`userId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_projects_userId_workStatus` " +
                        "ON `projects` (`userId`, `workStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_projects_userId_syncStatus` " +
                        "ON `projects` (`userId`, `syncStatus`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_remoteId` ON `projects` (`remoteId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_billing_records (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        projectLocalId TEXT NOT NULL,
                        baseAmount TEXT NOT NULL,
                        taxLabel TEXT,
                        taxRatePercent TEXT NOT NULL,
                        taxMode TEXT NOT NULL,
                        taxAmount TEXT NOT NULL,
                        totalAmount TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        externalReference TEXT,
                        billedOn INTEGER NOT NULL,
                        dueOn INTEGER,
                        cancelledAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_billing_records_userId` " +
                        "ON `project_billing_records` (`userId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_billing_records_projectLocalId` " +
                        "ON `project_billing_records` (`projectLocalId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_billing_records_userId_syncStatus` " +
                        "ON `project_billing_records` (`userId`, `syncStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_billing_records_remoteId` " +
                        "ON `project_billing_records` (`remoteId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_payments (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        projectLocalId TEXT NOT NULL,
                        billingRecordLocalId TEXT NOT NULL,
                        paidOn INTEGER NOT NULL,
                        amount TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        method TEXT,
                        externalReference TEXT,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_payments_userId` " +
                        "ON `project_payments` (`userId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_payments_projectLocalId` " +
                        "ON `project_payments` (`projectLocalId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_payments_billingRecordLocalId` " +
                        "ON `project_payments` (`billingRecordLocalId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_payments_userId_syncStatus` " +
                        "ON `project_payments` (`userId`, `syncStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_project_payments_remoteId` " +
                        "ON `project_payments` (`remoteId`)",
                )

                db.execSQL("ALTER TABLE shifts ADD COLUMN projectId TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN projectNameSnapshot TEXT")
            }
        }

        /**
         * Marks what a shift's time is compensated by: employee wages or project
         * time. See CompensationSource.
         *
         * Purely additive and deliberately not backfilled. The column is nullable
         * with no default, and NULL is read as EMPLOYEE, so every shift written
         * before this upgrade keeps its exact wage, overtime and premium
         * behaviour. Writing a value into existing rows would risk changing
         * someone's recorded pay during an upgrade, which is why it is left NULL.
         */
        internal val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shifts ADD COLUMN compensationSource TEXT")
            }
        }

        /**
         * Notes on a billing record, entered on the "record billing details"
         * form.
         *
         * Purely additive: one nullable column, no default, no backfill, no
         * table rebuilt. Existing billing records read back with a null note and
         * every billed amount is untouched — a billing snapshot must never be
         * restated by an upgrade.
         */
        internal val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE project_billing_records ADD COLUMN notes TEXT")
            }
        }

        /**
         * Workplaces, leave policies, reported absences and payslip balance
         * snapshots.
         *
         * Purely additive and non-destructive:
         *  - five new tables, so no existing row is read or rewritten;
         *  - the two new `workplaceId` columns are nullable with no default and
         *    are never backfilled, so every existing shift and compensation
         *    profile stays exactly as it was and no wage calculation changes.
         *
         * The absence of a backfill is the deliberate part. Assigning historical
         * shifts to a workplace during an upgrade would rewrite the user's
         * recorded history invisibly, and this schema's rule is that an upgrade
         * never restates a recorded shift or its pay. Existing rows join a
         * workplace the first time the user has one — see
         * `WorkplaceDao.adoptShifts` — where it is an ordinary edit that syncs
         * like any other.
         *
         * Absences are deliberately not shifts. A shift means worked time and
         * feeds net minutes, overtime, premiums and the shift count; modelling a
         * sick day as an eight-hour shift would inflate all four and invent
         * overtime nobody worked.
         *
         * Calendar dates are INTEGER epoch days (`affectedDate`, `asOfDate`,
         * `startDate`) so an absence cannot move by a day through a timezone
         * conversion; timestamps stay INTEGER epoch millis.
         *
         * `estimatedGrossPay` is REAL, unlike the TEXT that Paid Projects money
         * uses. The two are different kinds of number: a billed fee is an amount
         * that must round-trip byte for byte, while this is a derived estimate
         * computed from an hourly rate that is itself REAL, and it is labelled an
         * estimate everywhere it appears.
         *
         * No SQL foreign keys, matching how shifts reference tasks: this schema
         * soft-deletes and the repositories cascade. Every index created here is
         * declared on the corresponding entity — the 7→8 migration once created
         * indexes that were not, and Room rejected every upgraded database until
         * 8→9 repaired it.
         */
        /**
         * Work profiles get a visual identity, and tasks get scoped to one.
         *
         * `color` and `icon` on `compensation_profiles` are the same pair `tasks`
         * already carries: a job and a task are two levels of the same "what am I
         * clocking into" question, and they read as one system only if they are
         * drawn from one visual language.
         *
         * `tasks.compensationProfileId` is added nullable and **not** backfilled by
         * this migration. A task with no profile is treated as belonging to the
         * default one, so an upgrading user keeps their whole task list; writing a
         * profile id into their rows here would instead rewrite their data during an
         * upgrade, silently and before they had seen the feature. The rows join a
         * profile the first time the user edits or uses them, the same way
         * `adoptShifts` handles workplaces.
         */
        internal val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE compensation_profiles ADD COLUMN color TEXT")
                db.execSQL("ALTER TABLE compensation_profiles ADD COLUMN icon TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN compensationProfileId TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_compensationProfileId` " +
                        "ON `tasks` (`compensationProfileId`)",
                )
            }
        }

        internal val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workplaces (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        regionCode TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        timezone TEXT NOT NULL,
                        employmentStartDate INTEGER,
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workplaces_userId` ON `workplaces` (`userId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workplaces_userId_syncStatus` " +
                        "ON `workplaces` (`userId`, `syncStatus`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workplaces_remoteId` ON `workplaces` (`remoteId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS leave_policies (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        workplaceLocalId TEXT NOT NULL,
                        regionCode TEXT NOT NULL,
                        rulesJson TEXT NOT NULL,
                        effectiveFrom INTEGER NOT NULL,
                        effectiveUntil INTEGER,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_leave_policies_userId` ON `leave_policies` (`userId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_leave_policies_workplaceLocalId` " +
                        "ON `leave_policies` (`workplaceLocalId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_leave_policies_userId_syncStatus` " +
                        "ON `leave_policies` (`userId`, `syncStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_leave_policies_remoteId` ON `leave_policies` (`remoteId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS absence_events (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        startDate INTEGER NOT NULL,
                        endDate INTEGER NOT NULL,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_absence_events_userId` ON `absence_events` (`userId`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_events_userId_startDate` " +
                        "ON `absence_events` (`userId`, `startDate`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_events_userId_syncStatus` " +
                        "ON `absence_events` (`userId`, `syncStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_events_remoteId` ON `absence_events` (`remoteId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS absence_allocations (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        absenceEventLocalId TEXT NOT NULL,
                        workplaceLocalId TEXT NOT NULL,
                        affectedDate INTEGER NOT NULL,
                        entitlementUnits REAL NOT NULL,
                        unit TEXT NOT NULL,
                        expectedWorkMinutes INTEGER,
                        policySnapshotJson TEXT,
                        calculationSnapshotJson TEXT,
                        estimatedGrossPay REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_allocations_userId` " +
                        "ON `absence_allocations` (`userId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_allocations_absenceEventLocalId` " +
                        "ON `absence_allocations` (`absenceEventLocalId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_allocations_workplaceLocalId_affectedDate` " +
                        "ON `absence_allocations` (`workplaceLocalId`, `affectedDate`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_allocations_userId_syncStatus` " +
                        "ON `absence_allocations` (`userId`, `syncStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_absence_allocations_remoteId` " +
                        "ON `absence_allocations` (`remoteId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS leave_balance_snapshots (
                        localId TEXT NOT NULL PRIMARY KEY,
                        remoteId TEXT,
                        userId TEXT NOT NULL,
                        workplaceLocalId TEXT NOT NULL,
                        balanceType TEXT NOT NULL,
                        balance REAL NOT NULL,
                        unit TEXT NOT NULL,
                        asOfDate INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        label TEXT,
                        notes TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER,
                        syncStatus TEXT NOT NULL,
                        lastSyncError TEXT,
                        lastSyncedAt INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_leave_balance_snapshots_userId` " +
                        "ON `leave_balance_snapshots` (`userId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_leave_balance_snapshots_workplaceLocalId_balanceType_asOfDate` " +
                        "ON `leave_balance_snapshots` (`workplaceLocalId`, `balanceType`, `asOfDate`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_leave_balance_snapshots_userId_syncStatus` " +
                        "ON `leave_balance_snapshots` (`userId`, `syncStatus`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_leave_balance_snapshots_remoteId` " +
                        "ON `leave_balance_snapshots` (`remoteId`)",
                )

                // Guarded because a database first created at version 19 already has
                // the column from the entity, and this migration also runs as part
                // of the 1→19 chain the migration test walks.
                if (!db.hasColumn("shifts", "workplaceId")) {
                    db.execSQL("ALTER TABLE shifts ADD COLUMN workplaceId TEXT")
                }
                if (!db.hasColumn("compensation_profiles", "workplaceId")) {
                    db.execSQL("ALTER TABLE compensation_profiles ADD COLUMN workplaceId TEXT")
                }
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
