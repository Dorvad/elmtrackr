package com.elmtrackr.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.elmtrackr.app.data.local.converter.Converters
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ElmTrackrDatabase : RoomDatabase() {

    abstract fun shiftDao(): ShiftDao
    abstract fun settingsDao(): SettingsDao
    abstract fun profileDao(): ProfileDao
    abstract fun refundClaimDao(): RefundClaimDao

    companion object {
        @Volatile private var INSTANCE: ElmTrackrDatabase? = null

        fun getInstance(context: Context): ElmTrackrDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ElmTrackrDatabase::class.java,
                    "elmtrackr.db",
                ).build().also { INSTANCE = it }
            }
    }
}
