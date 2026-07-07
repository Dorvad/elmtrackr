package com.elmtrackr.app.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

/** 4×1 day-goal progress bar + round toggle. */
class ElmTrackrMinimalWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.barSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ProgressWidgetContent(WidgetPreferences.read(currentState<Preferences>()))
        }
    }
}
