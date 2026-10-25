package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.common.durationText
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics

/**
 * Asks whether a very long running shift was forgotten. See
 * [com.elmtrackr.app.domain.dashboard.LongShiftNotice] for when it appears.
 *
 * The action opens the shift's form, where the real end time can be set. Offering
 * "Clock out" here would record the wrong end time — exactly the error being flagged.
 */
@Composable
internal fun LongShiftNoticeCard(
    elapsedMinutes: Int,
    onFixEndTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElmCard(modifier = modifier, containerColor = auroraSemantics.warning.copy(alpha = 0.12f)) {
        Column(
            Modifier.padding(Layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Text(
                stringResource(R.string.dashboard_long_shift_title, durationText(elapsedMinutes)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = auroraSemantics.warningInk,
                // Polite: it appears once, while the dashboard is open, and should be
                // read without interrupting whatever TalkBack is saying.
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(
                stringResource(R.string.dashboard_long_shift_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onFixEndTime) {
                Text(stringResource(R.string.dashboard_long_shift_action), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
