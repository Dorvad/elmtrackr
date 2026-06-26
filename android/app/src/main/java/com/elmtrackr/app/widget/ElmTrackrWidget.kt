package com.elmtrackr.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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
            val startTimeLabel = prefs[ElmTrackrWidgetUpdater.KEY_START_TIME_LABEL] ?: "--:--"
            val lastPunchLabel = prefs[ElmTrackrWidgetUpdater.KEY_LAST_PUNCH_LABEL] ?: "--:--"

            // Punch In: only triggers clock-in when not already active (prevents duplicate shifts).
            // Punch Out: only triggers clock-out when there is an active shift to end.
            // Wrong-state taps simply open the app.
            val punchInClick = if (!isActive) {
                actionRunCallback<ClockInWidgetAction>()
            } else {
                actionStartActivity<MainActivity>()
            }
            val punchOutClick = if (isActive) {
                actionRunCallback<ClockOutWidgetAction>(
                    actionParametersOf(ClockOutWidgetAction.SHIFT_ID_KEY to shiftId),
                )
            } else {
                actionStartActivity<MainActivity>()
            }

            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(R.drawable.widget_background))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                // ── Logo ───────────────────────────────────────────────────────
                Column(
                    modifier = GlanceModifier.padding(end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(40.dp)
                            .background(ImageProvider(R.drawable.widget_logo_circle)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_logo_icon),
                            contentDescription = "elmtrackr",
                            modifier = GlanceModifier.size(22.dp),
                        )
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = "elmtrackr",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                // ── Time / status info ─────────────────────────────────────────
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(end = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = GlanceModifier
                                .size(6.dp)
                                .background(ImageProvider(R.drawable.widget_status_dot)),
                        ) {}
                        Spacer(GlanceModifier.width(5.dp))
                        Text(
                            text = if (isActive) "CLOCKED IN" else "CLOCKED OUT",
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.90f)),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    Spacer(GlanceModifier.height(1.dp))
                    Text(
                        text = startTimeLabel,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = if (isActive) "Since • $lastPunchLabel"
                               else "Last punch • $lastPunchLabel",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.70f)),
                            fontSize = 10.sp,
                        ),
                    )
                }

                // ── Action buttons ─────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {

                    // Punch In
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_button))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .clickable(punchInClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.widget_icon_bolt),
                                contentDescription = null,
                                modifier = GlanceModifier.size(13.dp),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = "Punch In",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }

                    Spacer(GlanceModifier.width(6.dp))

                    // Punch Out
                    Box(
                        modifier = GlanceModifier
                            .background(ImageProvider(R.drawable.widget_button_outlined))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .clickable(punchOutClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                provider = ImageProvider(R.drawable.widget_icon_logout),
                                contentDescription = null,
                                modifier = GlanceModifier.size(13.dp),
                            )
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = "Punch Out",
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
