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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.layout.PhoneContentMaxWidth
import com.elmtrackr.app.ui.layout.TabletContentPadding
import com.elmtrackr.app.ui.layout.isTabletLayout
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Shared screen shell matching the web app's `max-w-md mx-auto px-5` layout on phones,
 * and full-width padded content on tablets.
 */
@Composable
fun AuroraScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    horizontalPadding: Boolean = true,
    showMeshBackground: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isTablet = isTabletLayout()
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        if (showMeshBackground) {
            AuroraMeshBackground(modifier = Modifier.fillMaxSize())
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (showMeshBackground) Color.Transparent else MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isTablet) Alignment.TopStart else Alignment.TopCenter,
            ) {
                Column(
                    modifier = modifier
                        .then(if (isTablet) Modifier.fillMaxWidth() else Modifier.widthIn(max = PhoneContentMaxWidth).fillMaxWidth())
                        .statusBarsPadding()
                        .then(
                            if (scrollable) Modifier.verticalScroll(scrollState)
                            else Modifier,
                        )
                        .then(
                            if (horizontalPadding) {
                                Modifier.padding(
                                    horizontal = if (isTablet) TabletContentPadding else Spacing.screenH,
                                )
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
}

/**
 * Screen shell for lazy-list screens that manage their own scrolling.
 */
@Composable
fun AuroraListScreen(
    modifier: Modifier = Modifier,
    showMeshBackground: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val isTablet = isTabletLayout()
    Box(modifier = Modifier.fillMaxSize()) {
        if (showMeshBackground) {
            AuroraMeshBackground(modifier = Modifier.fillMaxSize())
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = if (showMeshBackground) Color.Transparent else MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = if (isTablet) Alignment.TopStart else Alignment.TopCenter,
                content = {
                    Box(
                        modifier = Modifier
                            .then(if (isTablet) Modifier.fillMaxWidth() else Modifier.widthIn(max = PhoneContentMaxWidth).fillMaxWidth()),
                        content = content,
                    )
                },
            )
        }
    }
}

/** Content max width for the current form factor; [Dp.Unspecified] on tablet. */
@Composable
fun auroraContentMaxWidth(): Dp = if (isTabletLayout()) Dp.Unspecified else PhoneContentMaxWidth
