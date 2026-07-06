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
import androidx.glance.layout.fillMaxWidth
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

private val Indigo = Color(0xFF5B4DF2)
private val Aqua = Color(0xFF22D3EE)

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
internal fun WidgetLogoColumn(modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier,
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
                color = ColorProvider(Color.White.copy(alpha = 0.88f)),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

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
internal fun WidgetStatusBadge(state: WidgetPreferences.DisplayState) {
    Row(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_status_badge))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetStatusDot(state.isActive)
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = state.statusLabel,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@androidx.compose.runtime.Composable
internal fun WidgetProgressBar(percent: Int, barWidthDp: Int = 0) {
    val width = if (barWidthDp > 0) barWidthDp else 130
    val filled = (width * percent / 100).coerceAtLeast(4)
    val empty = (width - filled).coerceAtLeast(0)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(filled.dp)
                .height(6.dp)
                .background(ImageProvider(R.drawable.widget_progress_fill)),
        ) {}
        if (empty > 0) {
            Box(
                modifier = GlanceModifier
                    .width(empty.dp)
                    .height(6.dp)
                    .background(ImageProvider(R.drawable.widget_progress_track)),
            ) {}
        }
    }
}

@androidx.compose.runtime.Composable
internal fun PunchInPillButton(state: WidgetPreferences.DisplayState) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_button_punch_in_white))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(18.dp)
                    .background(ImageProvider(R.drawable.widget_button)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_icon_bolt),
                    contentDescription = "Clock in",
                    modifier = GlanceModifier.size(10.dp),
                )
            }
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = state.actionLabel,
                style = TextStyle(
                    color = ColorProvider(Indigo),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@androidx.compose.runtime.Composable
internal fun PunchOutPillButton(state: WidgetPreferences.DisplayState) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_button_punch_out_outline))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(18.dp)
                    .background(ImageProvider(R.drawable.widget_button_round_outline)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_icon_stop),
                    contentDescription = "Clock out",
                    modifier = GlanceModifier.size(8.dp),
                )
            }
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = state.actionLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@androidx.compose.runtime.Composable
internal fun RoundToggleButton(state: WidgetPreferences.DisplayState) {
    val bg = if (state.isActive) R.drawable.widget_button_round_outline else R.drawable.widget_button_round_white
    val icon = if (state.isActive) R.drawable.widget_icon_stop else R.drawable.widget_icon_bolt
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .background(ImageProvider(bg))
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = state.actionLabel,
            modifier = GlanceModifier.size(if (state.isActive) 12.dp else 18.dp),
        )
    }
}

/** 4×1 single-toggle wide bar (mockup: logo + status/timer + one CTA pill). */
@androidx.compose.runtime.Composable
internal fun SingleToggleWidgetContent(state: WidgetPreferences.DisplayState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetLogoColumn(
            modifier = GlanceModifier
                .clickable(openAppClick())
                .padding(end = 8.dp),
        )

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openAppClick()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetStatusDot(state.isActive)
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        text = state.statusLabel,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Text(
                    text = state.primaryTimeLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = if (state.isActive) 22.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Column(modifier = GlanceModifier.padding(start = 4.dp)) {
                Text(
                    text = state.singleToggleSecondaryTop,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.65f)),
                        fontSize = 8.sp,
                    ),
                )
                Text(
                    text = state.singleToggleSecondaryBottom,
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.80f)),
                        fontSize = 9.sp,
                    ),
                )
            }
        }

        if (state.isActive) PunchOutPillButton(state) else PunchInPillButton(state)
    }
}

/** 4×1 progress bar + round toggle (mockup: day-goal progress). */
@androidx.compose.runtime.Composable
internal fun ProgressWidgetContent(state: WidgetPreferences.DisplayState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background_aurora))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(34.dp)
                .background(ImageProvider(R.drawable.widget_logo_circle))
                .clickable(openAppClick()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_logo_icon),
                contentDescription = "ElmTrackr",
                modifier = GlanceModifier.size(18.dp),
            )
        }

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clickable(openAppClick()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetStatusDot(state.isActive)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = state.statusLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            WidgetProgressBar(state.progressPercent)
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = state.progressSubLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.72f)),
                    fontSize = 8.sp,
                ),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (state.isActive) state.elapsedHms else state.todayHms,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = " / ${state.goalHoursLabel}",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.65f)),
                        fontSize = 9.sp,
                    ),
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            RoundToggleButton(state)
        }
    }
}

/** 4×2 tall card with full-width action bar (mockup: oversized clock + base CTA). */
@androidx.compose.runtime.Composable
internal fun TallCardWidgetContent(state: WidgetPreferences.DisplayState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background_aurora))
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetLogoColumn(modifier = GlanceModifier.clickable(openAppClick()))
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                WidgetStatusBadge(state)
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        Column(modifier = GlanceModifier.clickable(openAppClick())) {
            Text(
                text = if (state.isActive) "ON SHIFT" else "LAST PUNCH",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.70f)),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = state.primaryTimeLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = if (state.isActive) state.tallActiveSubLabel else state.tallLoggedLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                    fontSize = 11.sp,
                ),
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(
                    ImageProvider(
                        if (state.isActive) R.drawable.widget_button_punch_out_outline
                        else R.drawable.widget_button_punch_in_white,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(primaryActionClick(state)),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isActive) {
                    Box(
                        modifier = GlanceModifier
                            .size(22.dp)
                            .background(ImageProvider(R.drawable.widget_button_round_outline)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_icon_stop),
                            contentDescription = "Clock out",
                            modifier = GlanceModifier.size(10.dp),
                        )
                    }
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(22.dp)
                            .background(ImageProvider(R.drawable.widget_button)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_icon_bolt),
                            contentDescription = "Clock in",
                            modifier = GlanceModifier.size(12.dp),
                        )
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = state.actionLabel,
                    style = TextStyle(
                        color = ColorProvider(if (state.isActive) Color.White else Indigo),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = state.actionHint,
                    style = TextStyle(
                        color = ColorProvider(
                            if (state.isActive) Color.White.copy(alpha = 0.65f) else Color.Gray,
                        ),
                        fontSize = 9.sp,
                    ),
                )
            }
        }
    }
}

/** 1×1 progress ring (mockup: open ring → filled ring when active or progressing). */
@androidx.compose.runtime.Composable
internal fun RingWidgetContent(state: WidgetPreferences.DisplayState) {
    val showProgressRing = state.isActive || state.progressPercent > 0
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(52.dp)
                .background(
                    ImageProvider(
                        if (showProgressRing) R.drawable.widget_ring_progress
                        else R.drawable.widget_ring_open,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isActive) {
                    Text(
                        text = state.elapsedHms.ifEmpty { "0:00" },
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "${state.progressPercent}%",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                            fontSize = 7.sp,
                        ),
                    )
                } else if (state.progressPercent > 0) {
                    Text(
                        text = "${state.progressPercent}%",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "today",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = 7.sp,
                        ),
                    )
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.widget_icon_bolt),
                        contentDescription = "Clock in",
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = "PUNCH IN",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}

/** 1×1 big action (mockup: maximal tap target). */
@androidx.compose.runtime.Composable
internal fun BigActionWidgetContent(state: WidgetPreferences.DisplayState) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background_aurora))
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isActive) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.elapsedHms.ifEmpty { "0:00" },
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(6.dp))
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(ImageProvider(R.drawable.widget_button_round_outline)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_icon_stop),
                        contentDescription = "Clock out",
                        modifier = GlanceModifier.size(12.dp),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = "PUNCH OUT",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier
                        .size(48.dp)
                        .background(ImageProvider(R.drawable.widget_button_round_white)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_icon_bolt),
                        contentDescription = "Clock in",
                        modifier = GlanceModifier.size(22.dp),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "PUNCH IN",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "tap to clock in",
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                        fontSize = 7.sp,
                    ),
                )
            }
        }
    }
}

// Backward-compatible aliases used by widget classes
@androidx.compose.runtime.Composable
internal fun ClassicWidgetContent(state: WidgetPreferences.DisplayState) =
    SingleToggleWidgetContent(state)

@androidx.compose.runtime.Composable
internal fun MinimalWidgetContent(state: WidgetPreferences.DisplayState) =
    ProgressWidgetContent(state)

@androidx.compose.runtime.Composable
internal fun AuroraWidgetContent(state: WidgetPreferences.DisplayState) =
    TallCardWidgetContent(state)
