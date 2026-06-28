package com.elmtrackr.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import com.elmtrackr.wear.sync.WearAuroraColors

@Composable
fun WearAuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Color(WearAuroraColors.SURFACE),
            onBackground = Color(WearAuroraColors.ON_SURFACE),
            primary = Color(WearAuroraColors.INDIGO),
            secondary = Color(WearAuroraColors.AQUA),
        ),
        content = content,
    )
}
