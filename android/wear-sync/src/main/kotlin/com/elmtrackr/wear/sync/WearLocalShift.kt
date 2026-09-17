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

    fun punchIn(
        snapshot: WearShiftSnapshot,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WearShiftSnapshot {
        val rolled = rollToLocalDay(snapshot, nowMillis, zone)
        if (rolled.isActive) return rolled.copy(updatedAtEpochMillis = nowMillis)
        return rolled.copy(
            signedIn = true,
            isActive = true,
            shiftId = rolled.shiftId.ifBlank { "watch-$nowMillis" },
            shiftStartEpochMillis = nowMillis,
            startTimeLabel = formatHm(nowMillis, zone),
            todayEpochDay = todayEpochDay(nowMillis, zone),
            updatedAtEpochMillis = nowMillis,
        )
    }

    fun punchOut(
        snapshot: WearShiftSnapshot,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WearShiftSnapshot {
        if (!snapshot.isActive) return snapshot.copy(updatedAtEpochMillis = nowMillis)
        val start = snapshot.shiftStartEpochMillis
        val today = todayEpochDay(nowMillis, zone)
        val completedToday = if (snapshot.todayEpochDay == 0L || snapshot.todayEpochDay == today) {
            snapshot.todayMinutes
        } else {
            0
        }
        val minutes = if (start > 0L) {
            minutesOnLocalDay(start, nowMillis, nowMillis, zone)
        } else {
            0
        }
        return snapshot.copy(
            isActive = false,
            lastPunchEndEpochMillis = nowMillis,
            lastPunchLabel = "",
            todayMinutes = completedToday + minutes,
            todayEpochDay = today,
            shiftStartEpochMillis = 0L,
            updatedAtEpochMillis = nowMillis,
        )
    }

    /**
     * Drop or keep a replayed event after the phone answers. A lost ACK that
     * left the phone already matching the punch is treated as settled so the
     * queue cannot jam behind [no_active_shift] forever.
     *
     * Matching phone state is only trusted after a timeout — that is the path
     * that actually sent the punch. An unreachable phone with a stale running
     * snapshot must not retire a punch that never left the watch.
     */
    fun replayEventSettled(
        event: WearPunchEvent,
        result: PunchResult,
        phone: WearShiftSnapshot?,
    ): Boolean {
        if (result.success) return true
        if (!event.isPunchIn && result.errorCode == "no_active_shift") return true
        if (result.errorCode != "timeout") return false
        if (phone == null || !phone.signedIn) return false
        return if (event.isPunchIn) phone.isActive else !phone.isActive
    }

    /**
     * A live punch that timed out may still have landed on the phone; trust that
     * only when the phone snapshot contains evidence for this specific punch.
     * Coarse active/inactive state is not enough, because a stale snapshot can
     * already match the requested action and would turn a lost punch into a
     * false success.
     */
    fun livePunchSettledByPhone(
        isPunchIn: Boolean,
        punchEpochMillis: Long,
        phone: WearShiftSnapshot?,
    ): Boolean {
        if (phone == null || !phone.signedIn) return false
        return if (isPunchIn) {
            phone.isActive && phone.shiftStartEpochMillis >= punchEpochMillis
        } else {
            !phone.isActive && phone.lastPunchEndEpochMillis >= punchEpochMillis
        }
    }

    /**
     * Reset cached "today" when the calendar day has moved, and credit only
     * the part of an in-progress shift that sits on the current local day.
     * Zero [WearShiftSnapshot.todayEpochDay] means an older producer that did
     * not stamp a day — those totals must not be rolled.
     */
    fun rollToLocalDay(
        snapshot: WearShiftSnapshot,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WearShiftSnapshot {
        if (snapshot.todayEpochDay == 0L) return snapshot
        val today = todayEpochDay(nowMillis, zone)
        if (snapshot.todayEpochDay == today) return snapshot
        val start = snapshot.shiftStartEpochMillis
        val minutesToday = if (snapshot.isActive && start > 0L) {
            minutesOnLocalDay(start, nowMillis, nowMillis, zone)
        } else {
            0
        }
        return snapshot.copy(
            todayMinutes = minutesToday,
            todayEpochDay = today,
            updatedAtEpochMillis = nowMillis,
        )
    }

    fun minutesOnLocalDay(
        startMillis: Long,
        endMillis: Long,
        nowMillis: Long,
        zone: ZoneId,
    ): Int {
        val startOfToday = startOfLocalDayMillis(nowMillis, zone)
        val creditedStart = maxOf(startMillis, startOfToday)
        if (endMillis <= creditedStart) return 0
        return ((endMillis - creditedStart) / 60_000L).toInt().coerceAtLeast(0)
    }

    fun todayEpochDay(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    fun startOfLocalDayMillis(nowMillis: Long, zone: ZoneId): Long {
        val zoned = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return zoned.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * A signed-out phone snapshot must not erase a shift the watch recorded
     * while unpaired. A signed-in snapshot may replace local state only after
     * offline punches have been replayed onto the phone — otherwise the phone
     * would paint "clocked out" over a running wrist shift that has not been
     * sent yet. Once there is no replay waiting, however, a newer signed-out
     * snapshot is an explicit clear from the phone (sign-out or Wear sync off)
     * and must blank the watch even if it was showing earlier shift data.
     */
    fun shouldApplyPhoneSnapshot(
        local: WearShiftSnapshot,
        phone: WearShiftSnapshot,
        hasPendingReplay: Boolean,
    ): Boolean {
        if (hasPendingReplay) return false
        if (phone.signedIn) return true
        if (!hasLocalWork(local)) return true
        return phone.updatedAtEpochMillis > latestWorkEpochMillis(local)
    }

    fun hasLocalWork(snapshot: WearShiftSnapshot): Boolean =
        snapshot.isActive ||
            snapshot.todayMinutes > 0 ||
            snapshot.lastPunchEndEpochMillis > 0L

    private fun latestWorkEpochMillis(snapshot: WearShiftSnapshot): Long =
        maxOf(snapshot.shiftStartEpochMillis, snapshot.lastPunchEndEpochMillis)
            .takeIf { it > 0L }
            ?: snapshot.updatedAtEpochMillis

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
