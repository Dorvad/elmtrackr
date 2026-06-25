package com.elmtrackr.app.ui.design

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum

private val auroraGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to AuroraIndigo,
        0.42f to AuroraPlum,
        1.00f to AuroraAqua,
    ),
)

private val buttonShape = RoundedCornerShape(CornerRadius.Medium)

@Composable
fun ElmGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
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
            .auroraPressScale(interactionSource)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = buttonShape,
                clip = false,
                ambientColor = AuroraIndigo.copy(alpha = 0.22f),
                spotColor = AuroraIndigo.copy(alpha = 0.7f),
            )
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
