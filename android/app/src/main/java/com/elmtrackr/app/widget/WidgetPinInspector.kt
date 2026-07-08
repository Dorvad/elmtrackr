package com.elmtrackr.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reports whether any ElmTrackr home-screen widget is placed, and lets the app
 * ask the launcher to pin one. Abstracted so ViewModels stay unit-testable.
 */
interface WidgetPinInspector {
    fun isPinSupported(): Boolean
    fun hasPinnedWidget(): Boolean
    fun requestPinWidget(): Boolean
}

class AppWidgetPinInspector @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetPinInspector {

    private val providers = listOf(
        ElmTrackrWidgetReceiver::class.java,
        ElmTrackrMinimalWidgetReceiver::class.java,
        ElmTrackrAuroraWidgetReceiver::class.java,
        ElmTrackrRingWidgetReceiver::class.java,
        ElmTrackrBigActionWidgetReceiver::class.java,
    )

    private fun manager(): AppWidgetManager? = AppWidgetManager.getInstance(context)

    override fun isPinSupported(): Boolean =
        manager()?.isRequestPinAppWidgetSupported == true

    override fun hasPinnedWidget(): Boolean {
        val manager = manager() ?: return false
        return providers.any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty()
        }
    }

    override fun requestPinWidget(): Boolean {
        val manager = manager() ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(
            ComponentName(context, ElmTrackrWidgetReceiver::class.java),
            null,
            null,
        )
    }
}
