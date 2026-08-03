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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.elmtrackr.app.ui.theme.AuroraShadow
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Elevation
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.auroraShadow

@Composable
fun ElmCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CornerRadius.Large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    elevation: AuroraShadow = Elevation.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Card(
        modifier  = modifier
            .fillMaxWidth()
            .auroraShadow(elevation, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape     = shape,
        colors    = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content   = content,
    )
}

@Composable
fun ElmCardPadded(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CornerRadius.Large,
    elevation: AuroraShadow = Elevation.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElmCard(modifier = modifier, cornerRadius = cornerRadius, elevation = elevation) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Layout.cardPadding),
            content  = content,
        )
    }
}
