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

@Database(
    entities = [
        ShiftEntity::class,
        UserSettingsEntity::class,
        ProfileEntity::class,
        RefundClaimEntity::class,
        CompensationProfileEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ElmTrackrDatabase : RoomDatabase() {

    abstract fun shiftDao(): ShiftDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao
    abstract fun refundClaimDao(): RefundClaimDao

    abstract fun compensationProfileDao(): CompensationProfileDao

    companion object {
        @Volatile private var INSTANCE: ElmTrackrDatabase? = null

        fun getInstance(context: Context): ElmTrackrDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ElmTrackrDatabase::class.java,
                    "elmtrackr.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
