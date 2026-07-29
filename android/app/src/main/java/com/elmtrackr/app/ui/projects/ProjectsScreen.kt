package com.elmtrackr.app.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.components.states.EmptyState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Placeholder destination for the Paid Projects module.
 *
 * The activation shell ships the flag, the onboarding option and the fifth
 * navigation destination; project creation and tracking land in later
 * increments. The create action is deliberately disabled rather than absent so
 * the screen reads as "not built yet" rather than "broken".
 *
 * Renders the same when reached with the feature disabled — the navigation
 * guard redirects within a frame, and a benign screen is better than a crash
 * if that ever fails.
 */
@Composable
fun ProjectsScreen() {
    AuroraListScreen {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.projects_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = stringResource(R.string.projects_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Large),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                EmptyState(
                    icon = Icons.Outlined.WorkOutline,
                    title = stringResource(R.string.projects_empty_title),
                    subtitle = stringResource(R.string.projects_empty_subtitle),
                    action = {
                        ElmGradientButton(onClick = {}, enabled = false) {
                            Text(
                                text = stringResource(R.string.projects_create_cta),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                )
            }
            Text(
                text = stringResource(R.string.projects_placeholder_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProjectsScreenPreview() {
    ElmTrackrTheme { ProjectsScreen() }
}
