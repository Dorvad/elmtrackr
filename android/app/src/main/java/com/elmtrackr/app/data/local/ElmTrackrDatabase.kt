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
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.local.dao.TaskDao
import com.elmtrackr.app.data.local.entity.TaskEntity

@Database(
    entities = [
        ShiftEntity::class,
        UserSettingsEntity::class,
        ProfileEntity::class,
        RefundClaimEntity::class,
        CompensationProfileEntity::class,
        TaskEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ElmTrackrDatabase : RoomDatabase() {

    abstract fun shiftDao(): ShiftDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao
    abstract fun refundClaimDao(): RefundClaimDao

    abstract fun compensationProfileDao(): CompensationProfileDao

    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var INSTANCE: ElmTrackrDatabase? = null

        fun getInstance(context: Context): ElmTrackrDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ElmTrackrDatabase::class.java,
                    "elmtrackr.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN currency TEXT NOT NULL DEFAULT 'ILS'")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN region_code TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN currency_code TEXT")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN default_compensation_profile_id TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN compensation_profile_id TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN compensation_snapshot_json TEXT")
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
    }
}
