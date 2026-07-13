package com.elmtrackr.wear.ui

import android.content.Context
import com.elmtrackr.wear.R
import com.elmtrackr.wear.sync.WearShiftSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WearLabels {

    /**
     * Localized "Last out • Today 17:30" built on the watch from the punch
     * timestamp. The phone-built [WearShiftSnapshot.lastPunchLabel] is
     * hardcoded English and its "Today" goes stale at midnight, so it is only
     * the fallback for old snapshots that lack the timestamp.
     */
    fun lastPunch(context: Context, snapshot: WearShiftSnapshot): String {
        val endMillis = snapshot.lastPunchEndEpochMillis
        if (endMillis <= 0L) return snapshot.lastPunchLabel
        val zone = ZoneId.systemDefault()
        val end = Instant.ofEpochMilli(endMillis).atZone(zone)
        val today = LocalDate.now(zone)
        val time = end.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        return when (end.toLocalDate()) {
            today -> context.getString(R.string.wear_last_out_today, time)
            today.minusDays(1) -> context.getString(R.string.wear_last_out_yesterday, time)
            else -> {
                val day = end.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                context.getString(R.string.wear_last_out_other, "$day $time")
            }
        }
    }
}
