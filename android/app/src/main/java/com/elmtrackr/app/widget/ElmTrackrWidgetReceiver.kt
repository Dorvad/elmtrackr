package com.elmtrackr.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.elmtrackr.app.ElmTrackrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

abstract class BaseElmTrackrWidgetReceiver : GlanceAppWidgetReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scope.launch {
            runCatching { refreshWidgets(context) }
        }
    }

    private suspend fun refreshWidgets(context: Context) {
        val app = context.applicationContext as ElmTrackrApp
        val userId = app.currentUserProvider.currentUserId() ?: return
        val shift = app.shiftsRepository.observeActiveShift(userId).first()
        val lastCompleted = app.shiftsRepository
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        ElmTrackrWidgetUpdater.update(context, shift, lastCompleted)
    }
}

class ElmTrackrWidgetReceiver : BaseElmTrackrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ElmTrackrWidget()
}

class ElmTrackrMinimalWidgetReceiver : BaseElmTrackrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ElmTrackrMinimalWidget()
}

class ElmTrackrAuroraWidgetReceiver : BaseElmTrackrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ElmTrackrAuroraWidget()
}
