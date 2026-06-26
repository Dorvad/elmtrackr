package com.elmtrackr.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.elmtrackr.app.ElmTrackrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ElmTrackrWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget = ElmTrackrWidget()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scope.launch {
            try {
                val app = context.applicationContext as ElmTrackrApp
                val userId = app.currentUserProvider.currentUserId() ?: return@launch
                val shift = app.shiftsRepository.observeActiveShift(userId).firstOrNull()
                ElmTrackrWidgetUpdater.update(context, shift)
            } catch (_: Exception) {
                // Widget update is non-critical; fall back to empty state.
            }
        }
    }
}
