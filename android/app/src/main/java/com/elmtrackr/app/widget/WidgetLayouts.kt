package com.elmtrackr.app.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R

enum class WidgetStyle {
    CLASSIC,
    MINIMAL,
    AURORA,
}

internal fun primaryActionClick(state: WidgetPreferences.DisplayState): Action =
    if (state.isActive) {
        actionRunCallback<ClockOutWidgetAction>(
            actionParametersOf(ClockOutWidgetAction.SHIFT_ID_KEY to state.shiftId),
        )
    } else {
        actionRunCallback<ClockInWidgetAction>()
    }

internal fun openAppClick(): Action = actionStartActivity<MainActivity>()

@androidx.compose.runtime.Composable
internal fun WidgetStatusDot(isActive: Boolean) {
    Box(
        modifier = GlanceModifier
            .size(6.dp)
            .background(
                ImageProvider(
                    if (isActive) R.drawable.widget_status_dot_active
                    else R.drawable.widget_status_dot_inactive,
                ),
            ),
    ) {}
}

@androidx.compose.runtime.Composable
internal fun WidgetActionButton(
    state: WidgetPreferences.DisplayState,
    style: WidgetStyle,
    modifier: GlanceModifier = GlanceModifier,
) {
    val background = when {
        state.isActive -> R.drawable.widget_button_clock_out
        style == WidgetStyle.AURORA -> R.drawable.widget_button_aurora
        else -> R.drawable.widget_button_clock_in
    }
    val icon = if (state.isActive) R.drawable.widget_icon_logout else R.drawable.widget_icon_bolt

    Box(
        modifier = modifier
            .background(ImageProvider(background))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = state.actionLabel,
                modifier = GlanceModifier.size(14.dp),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = state.actionLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = if (style == WidgetStyle.MINIMAL) 11.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@androidx.compose.runtime.Composable
internal fun ClassicWidgetContent(state: WidgetPreferences.DisplayState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .clickable(openAppClick())
                .padding(end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .background(ImageProvider(R.drawable.widget_logo_circle)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_logo_icon),
                    contentDescription = "ElmTrackr",
                    modifier = GlanceModifier.size(20.dp),
                )
            }
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = "elmtrackr",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Column(
            modifier = GlanceModifier
                .width(150.dp)
                .clickable(openAppClick())
                .padding(end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetStatusDot(state.isActive)
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    text = state.statusLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.90f)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.height(1.dp))
            Text(
                text = state.primaryTimeLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = if (state.isActive) 24.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = state.subtitleLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.72f)),
                    fontSize = 10.sp,
                ),
            )
        }

        WidgetActionButton(state, WidgetStyle.CLASSIC)
    }
}

@androidx.compose.runtime.Composable
internal fun MinimalWidgetContent(state: WidgetPreferences.DisplayState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background_minimal))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .width(170.dp)
                .clickable(openAppClick()),
        ) {
            Text(
                text = state.primaryTimeLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
            Spacer(GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetStatusDot(state.isActive)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = if (state.isActive) state.subtitleLabel else state.statusLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.65f)),
                        fontSize = 10.sp,
                    ),
                )
            }
            if (!state.isActive && state.subtitleLabel.isNotBlank()) {
                Text(
                    text = state.subtitleLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.50f)),
                        fontSize = 9.sp,
                    ),
                )
            }
        }

        WidgetActionButton(state, WidgetStyle.MINIMAL)
    }
}

@androidx.compose.runtime.Composable
internal fun AuroraWidgetContent(state: WidgetPreferences.DisplayState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background_aurora))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier
                .width(170.dp)
                .clickable(openAppClick()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetStatusDot(state.isActive)
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = state.statusLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.92f)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (state.pendingCount > 0) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = "${state.pendingCount} pending",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFD8A8)),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state.primaryTimeLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = state.subtitleLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.78f)),
                    fontSize = 10.sp,
                ),
            )
        }

        WidgetActionButton(state, WidgetStyle.AURORA)
    }
}
