package com.elmtrackr.app.data.local.converter

import androidx.room.TypeConverter
import com.elmtrackr.app.data.local.entity.SyncStatus
import java.math.BigDecimal
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun syncStatusToString(status: SyncStatus): String = status.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.fromPersisted(value)

    /**
     * Money is persisted as a canonical plain-decimal string, never as a REAL
     * column: binary floating point cannot represent most decimal amounts
     * exactly, and financial values must not drift through storage.
     * `toPlainString` avoids scientific notation so the text round-trips exactly.
     */
    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    /**
     * Falls back to zero rather than throwing, so one malformed row cannot make
     * a whole table unreadable.
     */
    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? {
        val raw = value?.trim() ?: return null
        if (raw.isEmpty()) return null
        return runCatching { BigDecimal(raw) }.getOrDefault(BigDecimal.ZERO)
    }

    /**
     * Calendar dates are stored as epoch day, not epoch millis. A due date is a
     * date, not an instant; storing millis invites a timezone bug precisely at
     * the overdue boundary.
     */
    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? =
        value?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
}
