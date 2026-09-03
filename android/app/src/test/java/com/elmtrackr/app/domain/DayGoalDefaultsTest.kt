package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.notification.OvertimeReminderPolicy
import com.elmtrackr.app.widget.WidgetShiftState
import com.elmtrackr.wear.sync.WearShiftSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The eight-hour day is declared in two places, and they have to agree.
 *
 * `WearShiftSnapshot.DEFAULT_DAILY_GOAL_MINUTES` is the single source the widget
 * and the overtime reminder already alias, and its KDoc says so. `UserSettings`
 * keeps its own copy because the settings default is a *user preference* seed
 * rather than a display fallback — a real distinction, but one that only holds
 * while the two numbers match. If they ever diverge, the dashboard ring, the
 * widget ring and the reminder threshold start describing different days.
 *
 * Pinned rather than merged: collapsing them is a Wave D change that has to
 * decide which of the two meanings survives.
 */
class DayGoalDefaultsTest {

    @Test
    fun `the settings default and the display fallback describe the same day`() {
        assertEquals(
            "UserSettings and WearShiftSnapshot must agree on the default day length",
            WearShiftSnapshot.DEFAULT_DAILY_GOAL_MINUTES,
            UserSettings.DEFAULT_DAILY_OT_MINUTES,
        )
    }

    @Test
    fun `the widget and the overtime reminder alias the shared constant`() {
        assertEquals(
            WearShiftSnapshot.DEFAULT_DAILY_GOAL_MINUTES,
            WidgetShiftState.DEFAULT_DAILY_GOAL_MINUTES,
        )
        assertEquals(
            WearShiftSnapshot.DEFAULT_DAILY_GOAL_MINUTES.toLong(),
            OvertimeReminderPolicy.FALLBACK_THRESHOLD_MINUTES,
        )
    }
}
