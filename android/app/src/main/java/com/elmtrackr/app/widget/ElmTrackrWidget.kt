package com.elmtrackr.app.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

/** Classic widget: logo, status, elapsed time, and a single stateful Clock In / Out button. */
class ElmTrackrWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = WidgetPreferences.read(currentState<Preferences>())
            ClassicWidgetContent(state)
        }
    }
}
