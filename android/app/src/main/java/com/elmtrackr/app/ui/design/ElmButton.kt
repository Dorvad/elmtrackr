package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

private val buttonShape = RoundedCornerShape(14.dp)

@Composable
fun ElmGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
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
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier
            .fillMaxWidth()
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
    OutlinedButton(
        onClick  = onClick,
        enabled  = enabled,
        shape    = buttonShape,
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor         = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}
