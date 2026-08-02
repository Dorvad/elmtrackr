package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.wear.WearConnectionStatus
import com.elmtrackr.app.wear.WearWatch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun WearSettingsScreen(
    onBack: () -> Unit,
    viewModel: WearSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncEnabled by viewModel.syncEnabled.collectAsState()
    WearSettingsContent(
        uiState = uiState,
        syncEnabled = syncEnabled,
        onBack = onBack,
        onRefresh = viewModel::refreshStatus,
        onSyncEnabledChange = viewModel::setSyncEnabled,
        onSyncNow = viewModel::syncNow,
    )
}

@Composable
internal fun WearSettingsContent(
    uiState: WearSettingsUiState,
    syncEnabled: Boolean?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    val status = uiState.status
    val connected = status?.anyWatchConnected == true
    val syncOn = syncEnabled == true
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = stringResource(R.string.settings_wear_title), onBack = onBack) }

        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WatchGraphic(
                    connected = connected && syncOn,
                    appInstalled = status?.anyWatchWithApp == true,
                    modifier = Modifier.size(190.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = statusHeadline(status, syncOn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                status?.featuredWatch?.let { watch ->
                    Text(
                        text = watch.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item {
            SettingsSectionCard(title = stringResource(R.string.settings_wear_section_watch)) {
                when {
                    status == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.settings_wear_checking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    !status.wearApiAvailable -> Text(
                        stringResource(R.string.settings_wear_status_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    status.watches.isEmpty() -> Text(
                        stringResource(R.string.settings_wear_pairing_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column {
                        status.watches.forEach { watch ->
                            SettingsInfoRow(
                                label = watch.name,
                                value = watchDetailLine(watch),
                            )
                        }
                        if (!status.anyWatchWithApp) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.settings_wear_app_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (status?.wearApiAvailable != false) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !uiState.refreshing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.refreshing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.settings_wear_refresh))
                    }
                }
            }
        }

        item {
            SettingsSectionCard(title = stringResource(R.string.settings_wear_section_sync)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_wear_sync_toggle),
                    description = stringResource(R.string.settings_wear_sync_toggle_desc),
                    checked = syncOn,
                    onCheckedChange = { if (syncEnabled != null) onSyncEnabledChange(it) },
                )
                if (syncEnabled == false) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_wear_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = syncOn && connected && !uiState.syncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.syncing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.settings_wear_sync_now))
                }
                uiState.lastSyncSentAtMs?.let { sentAt ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_wear_sync_sent, formatSyncTime(sentAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun statusHeadline(status: WearConnectionStatus?, syncOn: Boolean): String = when {
    status == null -> stringResource(R.string.settings_wear_checking)
    !status.wearApiAvailable -> stringResource(R.string.settings_wear_status_unavailable_short)
    !status.anyWatchConnected -> stringResource(R.string.settings_wear_status_no_watch)
    !syncOn -> stringResource(R.string.settings_wear_status_sync_off)
    status.anyWatchWithApp -> stringResource(R.string.settings_wear_status_connected)
    else -> stringResource(R.string.settings_wear_status_connected_no_app)
}

@Composable
private fun watchDetailLine(watch: WearWatch): String {
    val proximity = stringResource(
        if (watch.isNearby) R.string.settings_wear_watch_nearby else R.string.settings_wear_watch_remote,
    )
    return if (watch.appInstalled) {
        "$proximity · ${stringResource(R.string.settings_wear_app_installed)}"
    } else {
        proximity
    }
}

private fun formatSyncTime(atMs: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(atMs))

/**
 * A hand-drawn Wear OS watch: strap, aurora-gradient bezel, dark dial with
 * ticks and hands frozen at the watchmaker's 10:09, a side crown, and a status
 * LED. When [connected], the bezel gradient slowly revolves and the LED
 * breathes green; both animations sit still under reduce-motion. No bitmap
 * assets — it inherits the theme and scales to any size.
 */
@Composable
internal fun WatchGraphic(
    connected: Boolean,
    appInstalled: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val animate = connected && !reduceMotion
    // The infinite transition only exists while animating, so a disconnected or
    // reduce-motion graphic costs no frames at all.
    val bezelAngle: Float
    val ledPulse: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "watch-graphic")
        bezelAngle = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
            label = "bezel-sweep",
        ).value
        ledPulse = transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400), repeatMode = RepeatMode.Reverse),
            label = "led-pulse",
        ).value
    } else {
        bezelAngle = 0f
        ledPulse = 1f
    }

    val strapColor = MaterialTheme.colorScheme.surfaceVariant
    val strapEdge = MaterialTheme.colorScheme.outlineVariant
    val caseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val dialColor = Color(0xFF181530)
    val tickColor = Color(0xFF615C8A)
    val handColor = Color(0xFFF6F6FD)
    val accentColor = AuroraIndigo
    val disconnectedBezel = MaterialTheme.colorScheme.outlineVariant
    val ledOn = Color(0xFF2FBF71)
    val ledOff = MaterialTheme.colorScheme.outline

    val statusDescription = stringResource(
        if (connected) R.string.settings_wear_graphic_connected else R.string.settings_wear_graphic_disconnected,
    )
    Box(modifier.semantics { contentDescription = statusDescription }) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val caseRadius = min(w, h) * 0.31f
            val strapWidth = caseRadius * 1.05f

            // Strap, drawn first so the case overlaps it.
            val strapBrush = Brush.verticalGradient(listOf(strapColor, strapEdge))
            listOf(
                Rect(center.x - strapWidth / 2f, h * 0.04f, center.x + strapWidth / 2f, center.y),
                Rect(center.x - strapWidth / 2f, center.y, center.x + strapWidth / 2f, h * 0.96f),
            ).forEach { r ->
                drawRoundRect(
                    brush = strapBrush,
                    topLeft = r.topLeft,
                    size = Size(r.width, r.height),
                    cornerRadius = GeometryCornerRadius(strapWidth * 0.22f),
                )
            }
            // Strap keeper lines.
            listOf(h * 0.115f, h * 0.845f).forEach { y ->
                drawLine(
                    color = strapEdge,
                    start = Offset(center.x - strapWidth / 2.6f, y),
                    end = Offset(center.x + strapWidth / 2.6f, y),
                    strokeWidth = caseRadius * 0.05f,
                    cap = StrokeCap.Round,
                )
            }

            // Crown and side button on the right edge of the case.
            drawRoundRect(
                color = caseColor,
                topLeft = Offset(center.x + caseRadius * 0.96f, center.y - caseRadius * 0.32f),
                size = Size(caseRadius * 0.18f, caseRadius * 0.24f),
                cornerRadius = GeometryCornerRadius(caseRadius * 0.06f),
            )
            drawRoundRect(
                color = caseColor,
                topLeft = Offset(center.x + caseRadius * 0.92f, center.y + caseRadius * 0.12f),
                size = Size(caseRadius * 0.16f, caseRadius * 0.30f),
                cornerRadius = GeometryCornerRadius(caseRadius * 0.06f),
            )

            // Case body behind the bezel.
            drawCircle(color = caseColor, radius = caseRadius * 1.06f, center = center)

            // Dial.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(dialColor, Color(0xFF0D0B22)),
                    center = center,
                    radius = caseRadius,
                ),
                radius = caseRadius * 0.94f,
                center = center,
            )

            // Bezel: revolving aurora sweep when connected, dashed grey when not.
            if (connected) {
                rotate(bezelAngle, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(AuroraIndigo, AuroraPlum, AuroraAqua, AuroraIndigo),
                            center = center,
                        ),
                        radius = caseRadius,
                        center = center,
                        style = Stroke(width = caseRadius * 0.12f),
                    )
                }
            } else {
                drawCircle(
                    color = disconnectedBezel,
                    radius = caseRadius,
                    center = center,
                    style = Stroke(
                        width = caseRadius * 0.10f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(caseRadius * 0.22f, caseRadius * 0.16f),
                        ),
                    ),
                )
            }

            // Hour ticks.
            repeat(12) { i ->
                val angle = Math.toRadians(i * 30.0)
                val outer = caseRadius * 0.84f
                val inner = if (i % 3 == 0) caseRadius * 0.70f else caseRadius * 0.77f
                drawLine(
                    color = tickColor,
                    start = center + Offset(
                        (cos(angle) * inner).toFloat(),
                        (sin(angle) * inner).toFloat(),
                    ),
                    end = center + Offset(
                        (cos(angle) * outer).toFloat(),
                        (sin(angle) * outer).toFloat(),
                    ),
                    strokeWidth = caseRadius * if (i % 3 == 0) 0.05f else 0.03f,
                    cap = StrokeCap.Round,
                )
            }

            if (connected) {
                // Hands at 10:09.
                val hourAngle = Math.toRadians((10.15 / 12.0) * 360.0 - 90.0)
                val minuteAngle = Math.toRadians((9.0 / 60.0) * 360.0 - 90.0)
                drawLine(
                    color = handColor,
                    start = center,
                    end = center + Offset(
                        (cos(hourAngle) * caseRadius * 0.42f).toFloat(),
                        (sin(hourAngle) * caseRadius * 0.42f).toFloat(),
                    ),
                    strokeWidth = caseRadius * 0.07f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = handColor,
                    start = center,
                    end = center + Offset(
                        (cos(minuteAngle) * caseRadius * 0.62f).toFloat(),
                        (sin(minuteAngle) * caseRadius * 0.62f).toFloat(),
                    ),
                    strokeWidth = caseRadius * 0.05f,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = accentColor, radius = caseRadius * 0.07f, center = center)
                // Tiny goal arc, echoing the watch app's progress ring.
                if (appInstalled) {
                    drawArc(
                        color = AuroraAqua,
                        startAngle = -90f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = center - Offset(caseRadius * 0.56f, caseRadius * 0.56f),
                        size = Size(caseRadius * 1.12f, caseRadius * 1.12f),
                        style = Stroke(width = caseRadius * 0.045f, cap = StrokeCap.Round),
                    )
                }
            } else {
                // Sleeping face: a dim horizontal dash where the time would be.
                drawLine(
                    color = tickColor,
                    start = center - Offset(caseRadius * 0.28f, 0f),
                    end = center + Offset(caseRadius * 0.28f, 0f),
                    strokeWidth = caseRadius * 0.07f,
                    cap = StrokeCap.Round,
                )
            }

            // Status LED at the case's lower-right, outside the dial.
            val ledAngle = Math.toRadians(45.0)
            val ledCenter = center + Offset(
                (cos(ledAngle) * caseRadius * 1.28f).toFloat(),
                (sin(ledAngle) * caseRadius * 1.28f).toFloat(),
            )
            drawCircle(
                color = if (connected) ledOn.copy(alpha = ledPulse) else ledOff,
                radius = caseRadius * 0.09f,
                center = ledCenter,
            )
            if (connected) {
                drawCircle(
                    color = ledOn.copy(alpha = ledPulse * 0.25f),
                    radius = caseRadius * 0.17f,
                    center = ledCenter,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WearSettingsPreview() {
    ElmTrackrTheme {
        WearSettingsContent(
            uiState = WearSettingsUiState(
                status = WearConnectionStatus.from(
                    connectedNodes = listOf(Triple("node1", "Pixel Watch 3", true)),
                    capabilityNodeIds = setOf("node1"),
                ),
            ),
            syncEnabled = true,
            onBack = {},
            onRefresh = {},
            onSyncEnabledChange = {},
            onSyncNow = {},
        )
    }
}
