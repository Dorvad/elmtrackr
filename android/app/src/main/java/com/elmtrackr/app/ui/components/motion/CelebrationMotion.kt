package com.elmtrackr.app.ui.components.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.AuroraSoftEase
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import kotlinx.coroutines.delay

@Composable
fun FirstClockInCelebrationDialog(onDismiss: () -> Unit) {
    var animateIn by remember { mutableStateOf(false) }
    val motionEnabled = auroraMotionEnabled()

    LaunchedEffect(Unit) {
        if (motionEnabled) delay(50)
        animateIn = true
    }

    val iconScale by animateFloatAsState(
        targetValue = if (!motionEnabled || animateIn) 1f else 0.72f,
        animationSpec = if (motionEnabled) {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        } else {
            tween(0)
        },
        label = "celebration-icon-scale",
    )

    val pulseTransition = rememberInfiniteTransition(label = "celebration-pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = AuroraSoftEase),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "celebration-pulse-scale",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = iconScale * if (motionEnabled) pulseScale else 1f
                        scaleY = iconScale * if (motionEnabled) pulseScale else 1f
                    }
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
        title = {
            Text(
                stringResource(R.string.celebration_tracking_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().scale(iconScale),
            )
        },
        text = {
            Text(
                stringResource(R.string.celebration_tracking_body),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            ElmGradientButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.celebration_got_it), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
