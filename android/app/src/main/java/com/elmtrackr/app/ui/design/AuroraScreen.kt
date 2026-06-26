package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Shared screen shell matching the web app's `max-w-md mx-auto px-5` layout
 * with proper status-bar insets instead of a hard-coded top padding.
 */
@Composable
fun AuroraScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    horizontalPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = modifier
                    .widthIn(max = 448.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .then(
                        if (scrollable) Modifier.verticalScroll(scrollState)
                        else Modifier,
                    )
                    .then(
                        if (horizontalPadding) {
                            Modifier.padding(horizontal = Spacing.screenH)
                        } else {
                            Modifier
                        },
                    )
                    .padding(top = Spacing.lg, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

/**
 * Screen shell for lazy-list screens that manage their own scrolling.
 */
@Composable
fun AuroraListScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter,
            content = {
                Box(
                    modifier = Modifier
                        .widthIn(max = 448.dp)
                        .fillMaxWidth(),
                    content = content,
                )
            },
        )
    }
}
