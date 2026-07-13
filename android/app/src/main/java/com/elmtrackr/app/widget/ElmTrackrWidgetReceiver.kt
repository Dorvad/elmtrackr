package com.elmtrackr.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.elmtrackr.app.di.entrypoint.AppEntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseElmTrackrWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // goAsync keeps the process alive until the repaint lands; a plain
        // fire-and-forget scope can be killed with the receiver, leaving a
        // freshly added widget stuck on its placeholder state.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { refreshWidgets(context.applicationContext) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun refreshWidgets(context: Context) {
        val deps = AppEntryPoints.background(context)
        val userId = deps.currentUserProvider().currentUserId()
        val widgetContext = if (userId == null) {
            WidgetContext(null, null, emptyList(), null, isSignedIn = false)
        } else {
            WidgetContextLoader.load(deps, userId)
        }
        ElmTrackrWidgetUpdater.update(context, widgetContext)
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
