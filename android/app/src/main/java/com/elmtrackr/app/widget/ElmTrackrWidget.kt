package com.elmtrackr.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R

class ElmTrackrWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val isActive = prefs[ElmTrackrWidgetUpdater.KEY_IS_ACTIVE] ?: false
            val shiftId = prefs[ElmTrackrWidgetUpdater.KEY_SHIFT_ID] ?: ""
            val startTimeLabel = prefs[ElmTrackrWidgetUpdater.KEY_START_TIME_LABEL] ?: ""
            val dateLabel = prefs[ElmTrackrWidgetUpdater.KEY_DATE_LABEL] ?: ""

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background))
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = if (isActive) "Clocked in since $startTimeLabel"
                                   else "Ready to clock in",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        Text(
                            text = dateLabel,
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFCAC4D0)),
                                fontSize = 11.sp,
                            ),
                        )
                    }

                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_button))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable(
                                if (isActive) {
                                    actionRunCallback<ClockOutWidgetAction>(
                                        actionParametersOf(ClockOutWidgetAction.SHIFT_ID_KEY to shiftId),
                                    )
                                } else {
                                    actionRunCallback<ClockInWidgetAction>()
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isActive) "Clock Out" else "Clock In",
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }
}
