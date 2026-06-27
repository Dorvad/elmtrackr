package com.elmtrackr.app.widget

import android.content.Context
import com.elmtrackr.app.ElmTrackrApp
import com.elmtrackr.app.shortcuts.ClockOutActions

object WidgetActions {

    suspend fun clockIn(context: Context): Boolean {
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId() ?: return false
        return runCatching {
            app.shiftsRepository.clockIn(userId)
            true
        }.getOrDefault(false)
    }

    suspend fun clockOut(context: Context): ClockOutActions.Result {
        val result = ClockOutActions.clockOutActiveShift(context)
        ClockOutActions.showShortcutFeedback(context, result)
        return result
    }
}
