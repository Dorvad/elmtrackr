@file:OptIn(ExperimentalLayoutApi::class)

package com.elmtrackr.app.ui.shifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.PremiumProfile

@Composable
fun PremiumProfilePicker(
    profiles: List<PremiumProfile>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty()) return

    val selected = profiles.firstOrNull { it.id == selectedId || it.remoteId == selectedId }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.shifts_premium_label),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.shifts_no_premium)) },
            )
            profiles.forEach { profile ->
                FilterChip(
                    selected = selected?.id == profile.id,
                    onClick = { onSelect(profile.id) },
                    label = { Text(profile.displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            selected?.let { stringResource(R.string.shifts_premium_applies, (it.multiplier * 100).toInt()) }
                ?: stringResource(R.string.shifts_premium_standard),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
