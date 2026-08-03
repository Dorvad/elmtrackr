package com.elmtrackr.app.ui.components.states

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.Spacing

@Composable
private fun rememberStateEntranceScale(): Float {
    var visible by remember { mutableStateOf(false) }
    // Was a local re-implementation that checked the system animator scale but
    // not LocalReduceMotion, so these states animated even with the app's own
    // setting on. The branch below also used to assign the same value either
    // way, which made the guard look load-bearing when it was not.
    val motionEnabled = auroraMotionEnabled()
    LaunchedEffect(Unit) { visible = true }
    if (!motionEnabled) return 1f
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = tween(AuroraMotion.RiseMillis, easing = AuroraEaseOut),
        label = "state-entrance-scale",
    )
    return scale
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val scale = rememberStateEntranceScale()
    Box(modifier.fillMaxSize().auroraEnter(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.scale(scale),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val iconScale = rememberStateEntranceScale()
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl).auroraEnter(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp).scale(iconScale),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.let {
            Spacer(Modifier.height(Spacing.lg))
            it()
        }
    }
}

internal enum class ErrorMessageKind { NETWORK, GENERIC, PASSTHROUGH }

/**
 * Classifies raw exception text: messages that already read like a sentence
 * pass through; class names, stack-trace-ish text, and well-known network
 * failures get a plain-language, localized replacement.
 */
internal fun classifyErrorMessage(raw: String?): ErrorMessageKind {
    val message = raw?.trim().orEmpty()
    val lower = message.lowercase()
    return when {
        lower.contains("unknownhost") || lower.contains("unable to resolve host") ||
            lower.contains("connect") && lower.contains("timed out") ||
            lower.contains("timeout") || lower.contains("failed to connect") ->
            ErrorMessageKind.NETWORK
        message.isBlank() || lower == "unknown error" ||
            message.contains("Exception") || message.contains("java.") ||
            message.contains("kotlin") || message.contains("$") ->
            ErrorMessageKind.GENERIC
        else -> ErrorMessageKind.PASSTHROUGH
    }
}

@Composable
internal fun humanizeErrorMessage(raw: String?): String = when (classifyErrorMessage(raw)) {
    ErrorMessageKind.NETWORK -> stringResource(R.string.error_network)
    ErrorMessageKind.GENERIC -> stringResource(R.string.error_generic)
    ErrorMessageKind.PASSTHROUGH -> raw?.trim().orEmpty()
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val iconScale = rememberStateEntranceScale()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl)
            .auroraEnter(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp).scale(iconScale),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.common_something_went_wrong),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = humanizeErrorMessage(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        ElmGradientButton(onClick = onRetry, compact = true) {
            Text(stringResource(R.string.common_try_again), fontWeight = FontWeight.SemiBold)
        }
    }
}
