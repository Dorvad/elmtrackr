package com.elmtrackr.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

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
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId() ?: return
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val shift = deps.shiftsRepository().observeActiveShift(userId).first()
        val lastCompleted = deps.shiftsRepository()
            .observeRecentCompletedShifts(userId, limit = 1)
            .first()
            .firstOrNull()
        val todayShifts = deps.shiftsRepository()
            .observeShiftsByMonth(userId, today.year, today.monthValue)
            .first()
        val settings = deps.settingsRepository().getSettings(userId)
        ElmTrackrWidgetUpdater.update(
            context,
            WidgetContext(
                activeShift = shift,
                lastCompletedShift = lastCompleted,
                todayShifts = todayShifts,
                settings = settings,
            ),
        )
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

class ElmTrackrRingWidgetReceiver : BaseElmTrackrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ElmTrackrRingWidget()
}

class ElmTrackrBigActionWidgetReceiver : BaseElmTrackrWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ElmTrackrBigActionWidget()
}
