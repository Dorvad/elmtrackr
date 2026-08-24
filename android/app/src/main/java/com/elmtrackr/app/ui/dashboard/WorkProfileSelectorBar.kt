package com.elmtrackr.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.ui.common.WorkProfileIdentity
import com.elmtrackr.app.ui.common.WorkProfileTile
import com.elmtrackr.app.ui.design.AuroraHaptics
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Which job the next clock-in books to.
 *
 * **Renders nothing for a single work profile.** Most people have one job, and a
 * row above the clock that offers one choice is a row that only takes space and
 * asks a question with one answer. It appears when — and only when — there is a
 * second profile, which is also the moment the question becomes real.
 *
 * It sits directly above the task bar because the two are one narrowing question:
 * *which job*, then *what am I doing there*. Scoping tasks to the profile is what
 * pays for the extra row — the task bar below it gets shorter as jobs are added
 * rather than longer, so two rows here still show less than one unscoped row did.
 *
 * A `LazyRow` rather than a wrapping `FlowRow`: the number of jobs is the user's
 * to decide, and a bar that grows to three lines pushes the clock off screen. One
 * scrolling line has a fixed height whatever they do.
 */
@Composable
fun WorkProfileSelectorBar(
    profiles: List<CompensationProfile>,
    selectedProfileId: String?,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.size <= 1) return
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.dashboard_work_profile_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.s8))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
            contentPadding = PaddingValues(end = Spacing.s8),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val selected = profile.id == selectedProfileId
                FilterChip(
                    selected = selected,
                    onClick = {
                        AuroraHaptics.toggle(haptic)
                        onSelectProfile(profile.id)
                    },
                    label = {
                        Text(
                            profile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    // The tile carries the identity, so a glance at the clock tells
                    // the user which job is armed without reading the name.
                    leadingIcon = {
                        WorkProfileTile(
                            colorHex = WorkProfileIdentity.colorHexFor(profile),
                            emoji = WorkProfileIdentity.emojiFor(profile),
                        )
                    },
                )
            }
        }
    }
}
