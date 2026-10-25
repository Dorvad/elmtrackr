package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics

/**
 * One line, only when a sync problem needs the user. Kept to a single row so it
 * never competes with the clock; ordinary offline use shows nothing at all.
 */
@Composable
internal fun SyncAttentionNotice(
    attention: SyncAttention,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (attention) {
        SyncAttention.SessionExpired -> stringResource(R.string.dashboard_sync_session_expired)
        is SyncAttention.UploadFailed -> stringResource(R.string.dashboard_sync_failed, attention.count)
    }
    ElmCard(modifier = modifier, containerColor = auroraSemantics.warning.copy(alpha = 0.10f)) {
        Row(
            Modifier.padding(start = Spacing.s12, end = Spacing.s4, top = Spacing.s4, bottom = Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = auroraSemantics.warningInk,
                modifier = Modifier.size(Spacing.s18),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onReview) {
                Text(stringResource(R.string.dashboard_sync_review), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
