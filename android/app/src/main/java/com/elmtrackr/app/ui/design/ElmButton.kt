package com.elmtrackr.app.ui.design

import com.elmtrackr.app.ui.theme.AuroraLegibleGradient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.Elevation
import com.elmtrackr.app.ui.theme.auroraShadow

// The legible sweep: the decorative plum/aqua stops put the white label at
// 3.7–4.3:1. See AuroraGradients.kt.
private val auroraGradient = AuroraLegibleGradient

private val buttonShape = RoundedCornerShape(CornerRadius.Button)

@Composable
fun ElmGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    accessibilityLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick       = onClick,
        enabled       = enabled,
        shape         = buttonShape,
        colors        = ButtonDefaults.buttonColors(
            containerColor         = Color.Transparent,
            contentColor           = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor   = Color.White.copy(alpha = 0.4f),
        ),
        contentPadding = if (compact) {
            PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        } else {
            PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        },
        interactionSource = interactionSource,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = if (compact) 36.dp else 52.dp)
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics { contentDescription = accessibilityLabel }
                } else {
                    Modifier
                },
            )
            .auroraPressScale(interactionSource)
            .auroraShadow(if (enabled) Elevation.Button else Elevation.None, buttonShape)
            .background(
                brush  = if (enabled) auroraGradient else Brush.linearGradient(
                    listOf(AuroraIndigo.copy(alpha = 0.4f), AuroraAqua.copy(alpha = 0.4f))
                ),
                shape  = buttonShape,
            ),
    ) {
        content()
    }
}

@Composable
fun ElmOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        shape    = buttonShape,
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor         = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .auroraPressScale(interactionSource),
    ) {
        content()
    }
}
