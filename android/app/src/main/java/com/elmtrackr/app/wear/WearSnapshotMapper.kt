package com.elmtrackr.app.wear

import com.elmtrackr.app.widget.WidgetShiftState
import com.elmtrackr.wear.sync.WearShiftSnapshot

fun WidgetShiftState.toWearSnapshot(signedIn: Boolean): WearShiftSnapshot =
    WearShiftSnapshot(
        signedIn = signedIn,
        isActive = isActive,
        shiftId = shiftId,
        shiftStartEpochMillis = shiftStartEpochMillis,
        lastPunchEndEpochMillis = lastPunchEndEpochMillis,
        startTimeLabel = startTimeLabel,
        lastPunchLabel = lastPunchLabel,
        todayMinutes = todayMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
        updatedAtEpochMillis = System.currentTimeMillis(),
    )
