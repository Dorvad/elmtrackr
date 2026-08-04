package com.elmtrackr.app.notification

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The at-threshold overtime alert moved off WorkManager.
 *
 * `setInitialDelay` can be deferred by hours under Doze, and this is the
 * notification the whole reminder feature exists for — one delivered an hour into
 * unpaid overtime has already failed. What matters here is the scheduling contract,
 * since whether Doze actually defers anything is not observable on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OvertimeThresholdAlarmTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    private fun scheduledAlarms() = Shadows.shadowOf(alarmManager).scheduledAlarms

    @Test
    fun `a threshold in the future schedules one alarm`() {
        val now = Instant.parse("2026-08-04T09:00:00Z")

        val scheduled = OvertimeThresholdAlarm.schedule(
            context = context,
            shiftStart = now,
            thresholdMinutes = 480,
            now = now,
        )

        assertTrue(scheduled)
        assertEquals(1, scheduledAlarms().size)
    }

    /**
     * A threshold already passed — a re-arm after a reboot mid-overtime — returns
     * false so [OvertimeReminderScheduler] falls through to the WorkManager path
     * instead of dropping the notification entirely.
     */
    @Test
    fun `a threshold already passed schedules nothing and says so`() {
        val start = Instant.parse("2026-08-04T09:00:00Z")

        val scheduled = OvertimeThresholdAlarm.schedule(
            context = context,
            shiftStart = start,
            thresholdMinutes = 480,
            now = start.plusSeconds(9 * 3600),
        )

        assertFalse(scheduled)
        assertTrue(scheduledAlarms().isEmpty())
    }

    /** Exactly at the threshold is not still ahead of it. */
    @Test
    fun `a threshold reached exactly this instant is not scheduled`() {
        val start = Instant.parse("2026-08-04T09:00:00Z")

        val scheduled = OvertimeThresholdAlarm.schedule(
            context = context,
            shiftStart = start,
            thresholdMinutes = 480,
            now = start.plusSeconds(480 * 60L),
        )

        assertFalse(scheduled)
    }

    @Test
    fun `cancelling removes the pending alarm`() {
        val now = Instant.parse("2026-08-04T09:00:00Z")
        OvertimeThresholdAlarm.schedule(context, now, 480, now)
        assertEquals(1, scheduledAlarms().size)

        OvertimeThresholdAlarm.cancel(context)

        assertTrue(scheduledAlarms().isEmpty())
    }

    /** Cancelling with nothing pending must not mint a PendingIntent or throw. */
    @Test
    fun `cancelling with nothing scheduled is a no-op`() {
        OvertimeThresholdAlarm.cancel(context)

        assertTrue(scheduledAlarms().isEmpty())
    }

    /**
     * Re-scheduling replaces rather than stacks — a shift whose reminders are re-armed
     * (settings change, notification permission granted, reboot) must not queue a
     * second alert.
     */
    @Test
    fun `re-scheduling replaces the pending alarm`() {
        val now = Instant.parse("2026-08-04T09:00:00Z")

        OvertimeThresholdAlarm.schedule(context, now, 480, now)
        OvertimeThresholdAlarm.schedule(context, now, 516, now)

        assertEquals(1, scheduledAlarms().size)
    }

    /** The alert has to survive Doze, which is what the AllowWhileIdle variants are for. */
    @Test
    fun `the alarm wakes the device rather than waiting for the next window`() {
        val now = Instant.parse("2026-08-04T09:00:00Z")

        OvertimeThresholdAlarm.schedule(context, now, 480, now)

        assertEquals(AlarmManager.RTC_WAKEUP, scheduledAlarms().single().type)
    }
}
