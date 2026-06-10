package com.elmtrackr.app.data.local.converter

import androidx.room.TypeConverter
import com.elmtrackr.app.data.local.entity.SyncStatus

class Converters {
    @TypeConverter
    fun syncStatusToString(status: SyncStatus): String = status.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
