package com.elmtrackr.wear.sync

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Watch-local punch arithmetic.
 *
 * The watch used to be a mirror of the phone: no signed-in snapshot meant no
 * punch, and a signed-out snapshot from the phone wiped whatever the wrist
 * was showing. That is the shape of "functionality not working as described"
 * on a store reviewer's unpaired watch. These functions are the source of
 * truth for a punch that never left the wrist, and for when a later phone
 * snapshot is allowed to replace it.
 */
object WearLocalShift {

    private val timeLabel: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun punchIn(snapshot: WearShiftSnapshot, nowMillis: Long): WearShiftSnapshot {
        if (snapshot.isActive) return snapshot.copy(updatedAtEpochMillis = nowMillis)
        return snapshot.copy(
            signedIn = true,
            isActive = true,
            shiftId = snapshot.shiftId.ifBlank { "watch-$nowMillis" },
            shiftStartEpochMillis = nowMillis,
            startTimeLabel = formatHm(nowMillis),
            updatedAtEpochMillis = nowMillis,
        )
    }

    fun punchOut(snapshot: WearShiftSnapshot, nowMillis: Long): WearShiftSnapshot {
        if (!snapshot.isActive) return snapshot.copy(updatedAtEpochMillis = nowMillis)
        val start = snapshot.shiftStartEpochMillis
        val minutes = if (start > 0L) {
            ((nowMillis - start) / 60_000L).toInt().coerceAtLeast(0)
        } else {
            0
        }
        return snapshot.copy(
            isActive = false,
            lastPunchEndEpochMillis = nowMillis,
            lastPunchLabel = "",
            todayMinutes = snapshot.todayMinutes + minutes,
            shiftStartEpochMillis = 0L,
            updatedAtEpochMillis = nowMillis,
        )
    }

    /**
     * A signed-out phone snapshot must not erase a shift the watch recorded
     * while unpaired. A signed-in snapshot may replace local state only after
     * offline punches have been replayed onto the phone — otherwise the phone
     * would paint "clocked out" over a running wrist shift that has not been
     * sent yet.
     */
    fun shouldApplyPhoneSnapshot(
        local: WearShiftSnapshot,
        phone: WearShiftSnapshot,
        hasPendingReplay: Boolean,
    ): Boolean {
        if (hasPendingReplay) return false
        if (phone.signedIn) return true
        return !hasLocalWork(local)
    }

    fun hasLocalWork(snapshot: WearShiftSnapshot): Boolean =
        snapshot.isActive ||
            snapshot.todayMinutes > 0 ||
            snapshot.lastPunchEndEpochMillis > 0L

    fun mergeConsent(local: WearShiftSnapshot, phone: WearShiftSnapshot): WearShiftSnapshot =
        local.copy(crashReportingEnabled = phone.crashReportingEnabled)

    fun shouldFallbackToLocal(errorCode: String?): Boolean = when (errorCode) {
        null,
        "phone_unreachable",
        "not_signed_in",
        "timeout",
        "unknown_sender",
        "sync_disabled",
        "app_locked",
        -> true
        else -> false
    }

    fun formatHm(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeLabel)
}
