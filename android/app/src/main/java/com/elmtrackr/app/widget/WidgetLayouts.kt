package com.elmtrackr.app.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
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
import android.content.Context
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.DurationText
import com.elmtrackr.app.language.withAppLocale

private val Indigo = Color(0xFF5B4DF2)
private val Aqua = Color(0xFF16C8D6)

/**
 * Responsive breakpoints. Each widget declares its subset via
 * SizeMode.Responsive; layouts read LocalSize and adapt to the bucket the
 * launcher granted, so resizing reflows content instead of clipping it.
 */
internal object WidgetSizes {
    val barSizes: Set<DpSize> = setOf(
        DpSize(180.dp, 50.dp), DpSize(260.dp, 50.dp), DpSize(340.dp, 50.dp), DpSize(420.dp, 50.dp),
        DpSize(180.dp, 100.dp), DpSize(260.dp, 100.dp), DpSize(340.dp, 100.dp), DpSize(420.dp, 100.dp),
    )
    val cardSizes: Set<DpSize> = setOf(
        DpSize(180.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(340.dp, 110.dp),
        DpSize(180.dp, 170.dp), DpSize(250.dp, 170.dp), DpSize(340.dp, 170.dp),
    )
    val squareSizes: Set<DpSize> = setOf(
        DpSize(57.dp, 57.dp), DpSize(110.dp, 110.dp), DpSize(155.dp, 155.dp), DpSize(200.dp, 200.dp),
    )
}

/** Picks the pre-rendered day-goal ring arc closest to [percent] (10% steps). */
internal fun ringArcDrawable(percent: Int): Int {
    val bucket = ((percent.coerceIn(0, 100) + 5) / 10) * 10
    return when (bucket) {
        0 -> R.drawable.widget_ring_arc_0
        10 -> R.drawable.widget_ring_arc_10
        20 -> R.drawable.widget_ring_arc_20
        30 -> R.drawable.widget_ring_arc_30
        40 -> R.drawable.widget_ring_arc_40
        50 -> R.drawable.widget_ring_arc_50
        60 -> R.drawable.widget_ring_arc_60
        70 -> R.drawable.widget_ring_arc_70
        80 -> R.drawable.widget_ring_arc_80
        90 -> R.drawable.widget_ring_arc_90
        else -> R.drawable.widget_ring_arc_100
    }
}

/** Approximate width left for the middle column at the current breakpoint. */
private fun barContentWidth(size: DpSize, showLogo: Boolean, reservedEnd: Int): Int {
    val logo = if (showLogo) 52 else 0
    return (size.width.value.toInt() - 24 - logo - reservedEnd).coerceAtLeast(60)
}

// Widget text resolves through a context wrapped with the in-app locale so
// Hebrew/English follow the language chosen inside the app, including on
// Android 12 and below where Glance otherwise uses the system locale.
@androidx.compose.runtime.Composable
private fun widgetContext(): Context = LocalContext.current.withAppLocale()

@androidx.compose.runtime.Composable
internal fun widgetStatusLabel(state: WidgetPreferences.DisplayState): String = widgetContext()
    .getString(if (state.isActive) R.string.widget_clocked_in else R.string.widget_clocked_out)

@androidx.compose.runtime.Composable
internal fun widgetActionLabel(state: WidgetPreferences.DisplayState): String = widgetContext()
    .getString(if (state.isActive) R.string.widget_punch_out else R.string.widget_punch_in)

@androidx.compose.runtime.Composable
internal fun widgetActionHint(state: WidgetPreferences.DisplayState): String = widgetContext()
    .getString(if (state.isActive) R.string.widget_tap_end_shift else R.string.widget_tap_start_shift)

@androidx.compose.runtime.Composable
internal fun widgetStatusShortUpper(state: WidgetPreferences.DisplayState): String = widgetContext()
    .getString(if (state.isActive) R.string.widget_on_shift_upper else R.string.widget_last_punch_upper)

@androidx.compose.runtime.Composable
internal fun widgetSecondaryTop(state: WidgetPreferences.DisplayState): String = widgetContext()
    .getString(if (state.isActive) R.string.widget_on_shift else R.string.widget_last_punch)

@androidx.compose.runtime.Composable
internal fun widgetSecondaryBottom(state: WidgetPreferences.DisplayState): String {
    val context = widgetContext()
    return when {
        state.isActive -> context.getString(R.string.widget_since, state.startTimeLabel)
        !state.isSignedIn -> context.getString(R.string.widget_signed_out_hint)
        state.lastPunchEndEpochMillis > 0L ->
            lastPunchShortLabel(context, state.lastPunchEndEpochMillis)
        state.lastPunchLabel.isNotBlank() ->
            state.lastPunchLabel.removePrefix("Last out \u2022 ").removePrefix("Since ")
        state.todayMinutes > 0 -> context.getString(R.string.widget_today_short, DurationText.format(context, state.todayMinutes))
        else -> context.getString(R.string.widget_tap_to_start)
    }
}

/**
 * Localized "Today 08:57" / "Yesterday 08:57" / "Wed 25 Jun 08:57" built at
 * render time from the punch timestamp, so the day word follows the in-app
 * language and rolls over at midnight without waiting for a state refresh.
 */
private fun lastPunchShortLabel(context: Context, endEpochMillis: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val end = java.time.Instant.ofEpochMilli(endEpochMillis).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val locale = context.resources.configuration.locales[0] ?: java.util.Locale.getDefault()
    val time = end.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", locale))
    return when (end.toLocalDate()) {
        today -> context.getString(R.string.widget_today_short, time)
        today.minusDays(1) -> context.getString(R.string.widget_yesterday_short, time)
        else -> {
            val day = end.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", locale))
            "$day $time"
        }
    }
}

@androidx.compose.runtime.Composable
internal fun widgetProgressSubLabel(state: WidgetPreferences.DisplayState): String {
    val context = widgetContext()
    return if (state.isActive) {
        context.getString(
            R.string.widget_progress_active,
            state.progressPercent, state.goalHoursLabel, state.startTimeLabel,
        )
    } else {
        context.getString(
            R.string.widget_progress_idle,
            state.progressPercent, state.goalHoursLabel,
            DurationText.format(context, state.progressRemainderMinutes),
        )
    }
}

@androidx.compose.runtime.Composable
internal fun widgetTallSubLabel(state: WidgetPreferences.DisplayState): String {
    val context = widgetContext()
    return when {
        state.isActive -> context.getString(
            R.string.widget_active_sub,
            state.startTimeLabel,
            DurationText.format(context, state.todayMinutes),
        )
        state.todayMinutes > 0 -> context.getString(R.string.widget_today_logged, DurationText.format(context, state.todayMinutes))
        else -> context.getString(R.string.widget_no_time_today)
    }
}

@androidx.compose.runtime.Composable
internal fun widgetClockInLabel(): String = widgetContext().getString(R.string.widget_clock_in)

@androidx.compose.runtime.Composable
internal fun widgetClockOutLabel(): String = widgetContext().getString(R.string.widget_clock_out)

internal fun primaryActionClick(state: WidgetPreferences.DisplayState): Action =
    when {
        // Punching requires a signed-in user; route the tap into the app so it
        // lands on the sign-in screen instead of doing nothing.
        !state.isSignedIn -> openAppClick()
        state.isActive -> actionRunCallback<ClockOutWidgetAction>(
            actionParametersOf(ClockOutWidgetAction.SHIFT_ID_KEY to state.shiftId),
        )
        else -> actionRunCallback<ClockInWidgetAction>()
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
                contentDescription = widgetContext().getString(R.string.a11y_app_logo),
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
            text = widgetStatusLabel(state),
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
internal fun WidgetBusySpinner(size: androidx.compose.ui.unit.Dp, onLight: Boolean = false) {
    CircularProgressIndicator(
        modifier = GlanceModifier.size(size),
        color = ColorProvider(if (onLight) Indigo else Color.White),
    )
}

@androidx.compose.runtime.Composable
internal fun PunchInPillButton(state: WidgetPreferences.DisplayState) {
    Box(
        modifier = GlanceModifier
            .background(ImageProvider(R.drawable.widget_button_punch_in_white))
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.isBusy) {
                WidgetBusySpinner(size = 18.dp, onLight = true)
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(18.dp)
                        .background(ImageProvider(R.drawable.widget_button)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_icon_bolt),
                        contentDescription = widgetClockInLabel(),
                        modifier = GlanceModifier.size(10.dp),
                    )
                }
            }
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = widgetActionLabel(state),
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
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.isBusy) {
                WidgetBusySpinner(size = 18.dp)
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(18.dp)
                        .background(ImageProvider(R.drawable.widget_button_round_outline)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.widget_icon_stop),
                        contentDescription = widgetClockOutLabel(),
                        modifier = GlanceModifier.size(8.dp),
                    )
                }
            }
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = widgetActionLabel(state),
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
            .size(48.dp)
            .background(ImageProvider(bg))
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isBusy) {
            WidgetBusySpinner(size = 22.dp, onLight = !state.isActive)
        } else {
            Image(
                provider = ImageProvider(icon),
                contentDescription = widgetActionLabel(state),
                modifier = GlanceModifier.size(if (state.isActive) 15.dp else 22.dp),
            )
        }
    }
}

/**
 * 4×1 single-toggle wide bar (mockup: logo + status/timer + one CTA pill).
 * Narrow spans drop the logo, then the secondary line; an extra row of
 * height adds the day-goal progress bar.
 */
@androidx.compose.runtime.Composable
internal fun SingleToggleWidgetContent(state: WidgetPreferences.DisplayState) {
    val size = LocalSize.current
    val showLogo = size.width >= 260.dp
    val showSecondary = size.width >= 340.dp
    val showProgress = size.height >= 100.dp
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLogo) {
            WidgetLogoColumn(
                modifier = GlanceModifier
                    .clickable(openAppClick())
                    .padding(end = 8.dp),
            )
        }

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(openAppClick()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetStatusDot(state.isActive)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = widgetStatusLabel(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.primaryTimeLabel,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = if (state.isActive) 22.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (showSecondary) {
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text(
                            text = widgetSecondaryTop(state),
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.65f)),
                                fontSize = 8.sp,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = widgetSecondaryBottom(state),
                            style = TextStyle(
                                color = ColorProvider(Color.White.copy(alpha = 0.80f)),
                                fontSize = 9.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (showProgress) {
                Spacer(GlanceModifier.height(5.dp))
                WidgetProgressBar(
                    percent = state.progressPercent,
                    barWidthDp = barContentWidth(size, showLogo, reservedEnd = 100),
                )
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = widgetProgressSubLabel(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.72f)),
                        fontSize = 8.sp,
                    ),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.width(8.dp))
        if (state.isActive) PunchOutPillButton(state) else PunchInPillButton(state)
    }
}

/**
 * 4×1 progress bar + round toggle (mockup: day-goal progress).
 * Narrow spans drop the logo, then the time-vs-goal column; the bar itself
 * stretches with the granted width.
 */
@androidx.compose.runtime.Composable
internal fun ProgressWidgetContent(state: WidgetPreferences.DisplayState) {
    val size = LocalSize.current
    val showLogo = size.width >= 260.dp
    val showGoal = size.width >= 340.dp
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background_aurora))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLogo) {
            Box(
                modifier = GlanceModifier
                    .size(34.dp)
                    .background(ImageProvider(R.drawable.widget_logo_circle))
                    .clickable(openAppClick()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.widget_logo_icon),
                    contentDescription = widgetContext().getString(R.string.a11y_app_logo),
                    modifier = GlanceModifier.size(18.dp),
                )
            }
        }

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 8.dp)
                .clickable(openAppClick()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetStatusDot(state.isActive)
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = widgetStatusLabel(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(4.dp))
            WidgetProgressBar(
                percent = state.progressPercent,
                barWidthDp = barContentWidth(size, showLogo, reservedEnd = if (showGoal) 110 else 62),
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = widgetProgressSubLabel(state),
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.72f)),
                    fontSize = 8.sp,
                ),
                maxLines = 1,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            if (showGoal) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (state.isActive) state.elapsedHms else state.todayHms,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = " / ${state.goalHoursLabel}",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.65f)),
                            fontSize = 9.sp,
                        ),
                        maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
            }
            RoundToggleButton(state)
        }
    }
}

/**
 * 4×2 tall card with full-width action bar (mockup: oversized clock + base
 * CTA). Extra height adds the day-goal progress bar; narrow spans drop the
 * CTA hint.
 */
@androidx.compose.runtime.Composable
internal fun TallCardWidgetContent(state: WidgetPreferences.DisplayState) {
    val size = LocalSize.current
    val roomy = size.height >= 170.dp
    val showHint = size.width >= 250.dp
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
                text = widgetStatusShortUpper(state),
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
                text = widgetTallSubLabel(state),
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                    fontSize = 11.sp,
                ),
            )
        }

        if (roomy) {
            Spacer(GlanceModifier.height(10.dp))
            WidgetProgressBar(
                percent = state.progressPercent,
                barWidthDp = size.width.value.toInt() - 28,
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = widgetProgressSubLabel(state),
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.72f)),
                    fontSize = 9.sp,
                ),
                maxLines = 1,
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
                if (state.isBusy) {
                    WidgetBusySpinner(size = 22.dp, onLight = !state.isActive)
                } else if (state.isActive) {
                    Box(
                        modifier = GlanceModifier
                            .size(22.dp)
                            .background(ImageProvider(R.drawable.widget_button_round_outline)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.widget_icon_stop),
                            contentDescription = widgetClockOutLabel(),
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
                            contentDescription = widgetClockInLabel(),
                            modifier = GlanceModifier.size(12.dp),
                        )
                    }
                }
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = widgetActionLabel(state),
                    style = TextStyle(
                        color = ColorProvider(if (state.isActive) Color.White else Indigo),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                if (showHint) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        text = widgetActionHint(state),
                        style = TextStyle(
                            color = ColorProvider(
                                if (state.isActive) Color.White.copy(alpha = 0.65f) else Color.Gray,
                            ),
                            fontSize = 9.sp,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 1×1 progress ring (mockup: open ring → filled ring when active or
 * progressing). The ring and its type scale with the granted cell size now
 * that the widget is resizable.
 */
@androidx.compose.runtime.Composable
internal fun RingWidgetContent(state: WidgetPreferences.DisplayState) {
    val size = LocalSize.current
    val cell = if (size.width < size.height) size.width else size.height
    val ringSize = when {
        cell >= 190.dp -> 152.dp
        cell >= 150.dp -> 116.dp
        cell >= 100.dp -> 84.dp
        else -> 52.dp
    }
    val bigFont = when {
        cell >= 190.dp -> 30.sp
        cell >= 150.dp -> 24.sp
        cell >= 100.dp -> 18.sp
        else -> 14.sp
    }
    val smallFont = when {
        cell >= 190.dp -> 14.sp
        cell >= 150.dp -> 12.sp
        cell >= 100.dp -> 9.sp
        else -> 7.sp
    }
    val boltSize = when {
        cell >= 190.dp -> 38.dp
        cell >= 150.dp -> 30.dp
        cell >= 100.dp -> 22.dp
        else -> 16.dp
    }
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .clickable(primaryActionClick(state)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(ringSize)
                .background(ImageProvider(ringArcDrawable(state.progressPercent))),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.isBusy) {
                    WidgetBusySpinner(size = boltSize)
                } else if (state.isActive) {
                    Text(
                        text = state.elapsedHms.ifEmpty { "0:00" },
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = bigFont,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = "${state.progressPercent}%",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.75f)),
                            fontSize = smallFont,
                        ),
                        maxLines = 1,
                    )
                } else if (state.progressPercent > 0) {
                    Text(
                        text = "${state.progressPercent}%",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = bigFont,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                    Text(
                        text = widgetContext().getString(R.string.widget_today_lower),
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = smallFont,
                        ),
                        maxLines = 1,
                    )
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.widget_icon_bolt),
                        contentDescription = widgetClockInLabel(),
                        modifier = GlanceModifier.size(boltSize),
                    )
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        text = widgetContext().getString(R.string.widget_punch_in),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = smallFont,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * 1×1 big action (mockup: maximal tap target). The button and labels scale
 * with the granted cell size now that the widget is resizable.
 */
@androidx.compose.runtime.Composable
internal fun BigActionWidgetContent(state: WidgetPreferences.DisplayState) {
    val size = LocalSize.current
    val cell = if (size.width < size.height) size.width else size.height
    val buttonSize = when {
        cell >= 190.dp -> 108.dp
        cell >= 150.dp -> 84.dp
        cell >= 100.dp -> 64.dp
        else -> 48.dp
    }
    val iconSize = when {
        cell >= 190.dp -> 48.dp
        cell >= 150.dp -> 38.dp
        cell >= 100.dp -> 28.dp
        else -> 22.dp
    }
    val labelFont = when {
        cell >= 190.dp -> 16.sp
        cell >= 150.dp -> 14.sp
        cell >= 100.dp -> 12.sp
        else -> 10.sp
    }
    val hintFont = when {
        cell >= 190.dp -> 12.sp
        cell >= 150.dp -> 11.sp
        cell >= 100.dp -> 9.sp
        else -> 7.sp
    }
    val activeLabelFont = when {
        cell >= 190.dp -> 15.sp
        cell >= 150.dp -> 13.sp
        cell >= 100.dp -> 11.sp
        else -> 9.sp
    }
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
                        fontSize = labelFont,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(6.dp))
                Box(
                    modifier = GlanceModifier
                        .size(buttonSize - 8.dp)
                        .background(ImageProvider(R.drawable.widget_button_round_outline)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBusy) {
                        WidgetBusySpinner(size = iconSize - 8.dp)
                    } else {
                        Image(
                            provider = ImageProvider(R.drawable.widget_icon_stop),
                            contentDescription = widgetClockOutLabel(),
                            modifier = GlanceModifier.size(iconSize - 8.dp),
                        )
                    }
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = widgetActionLabel(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = activeLabelFont,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = GlanceModifier
                        .size(buttonSize)
                        .background(ImageProvider(R.drawable.widget_button_round_white)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBusy) {
                        WidgetBusySpinner(size = iconSize, onLight = true)
                    } else {
                        Image(
                            provider = ImageProvider(R.drawable.widget_icon_bolt),
                            contentDescription = widgetClockInLabel(),
                            modifier = GlanceModifier.size(iconSize),
                        )
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = widgetActionLabel(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = labelFont,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = widgetActionHint(state),
                    style = TextStyle(
                        color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                        fontSize = hintFont,
                    ),
                    maxLines = 1,
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
