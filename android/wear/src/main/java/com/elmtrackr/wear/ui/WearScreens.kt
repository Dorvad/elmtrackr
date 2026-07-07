package com.elmtrackr.wear.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.elmtrackr.wear.R

@Composable
fun SetupScreen(onRefresh: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.setup_body),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Button(
            onClick = onRefresh,
            modifier = Modifier.wearPressScale(interactionSource),
        ) {
            Text(stringResource(R.string.wear_refresh))
        }
    }
}

@Composable
fun IdleScreen(
    systemTime: String,
    lastPunchLabel: String,
    todayShort: String,
    onPunchIn: () -> Unit,
    isLoading: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WearAnimatedTimeLabel(systemTime)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.idle_last_punch),
                style = MaterialTheme.typography.labelSmall,
            )
            WearAnimatedTimeLabel(
                value = lastPunchLabel.ifBlank { "--:--" },
                style = MaterialTheme.typography.displaySmall,
            )
            if (todayShort.isNotBlank()) {
                Text(
                    text = stringResource(R.string.wear_today, todayShort),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(contentAlignment = Alignment.Center) {
            Button(
                onClick = onPunchIn,
                enabled = !isLoading,
                modifier = Modifier.wearPressScale(interactionSource),
            ) {
                Text(stringResource(R.string.punch_in))
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun RunningScreen(
    elapsed: String,
    sinceLabel: String,
    progressPercent: Int,
    onPunchOut: () -> Unit,
    isLoading: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WearAnimatedTimeLabel(elapsed, style = MaterialTheme.typography.displayLarge)
        Text(
            text = stringResource(R.string.running_since, sinceLabel),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.wear_percent_of_day, progressPercent),
            style = MaterialTheme.typography.labelSmall,
        )
        Box(contentAlignment = Alignment.Center) {
            Button(
                onClick = onPunchOut,
                enabled = !isLoading,
                modifier = Modifier.wearPressScale(interactionSource),
            ) {
                Text(stringResource(R.string.punch_out))
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun ConfirmationOverlay(message: String) {
    var visible by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message) {
        visible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (!wearMotionEnabled() || visible) 1f else 0.88f,
        animationSpec = tween(if (wearMotionEnabled()) 260 else 0),
        label = "wear-confirmation-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (!wearMotionEnabled() || visible) 1f else 0f,
        animationSpec = tween(if (wearMotionEnabled()) 220 else 0),
        label = "wear-confirmation-alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WearAnimatedTimeLabel(
    value: String,
    style: TextStyle = MaterialTheme.typography.labelSmall,
) {
    if (!wearMotionEnabled()) {
        Text(text = value, style = style)
        return
    }
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            fadeIn(tween(180)) + slideInVertically { it / 3 } togetherWith
                fadeOut(tween(120)) + slideOutVertically { -it / 3 }
        },
        label = "wear-time-label",
    ) { label ->
        Text(text = label, style = style)
    }
}
