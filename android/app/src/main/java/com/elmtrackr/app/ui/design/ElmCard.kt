package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.elmtrackr.app.ui.theme.AuroraIndigo

@Composable
fun ElmCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Card(
        modifier  = modifier
            .fillMaxWidth()
            .shadow(
                elevation    = 8.dp,
                shape        = shape,
                clip         = false,
                ambientColor = AuroraIndigo.copy(alpha = 0.05f),
                spotColor    = AuroraIndigo.copy(alpha = 0.34f),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape     = shape,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content   = content,
    )
}

@Composable
fun ElmCardPadded(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElmCard(modifier = modifier, cornerRadius = cornerRadius) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content  = content,
        )
    }
}
