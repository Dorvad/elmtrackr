package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Every clock face, grouped.
 *
 * Split off the appearance screen rather than expanded in place. Nineteen faces
 * is a browsing task, and browsing wants its own surface: room for group names,
 * and a back gesture that returns you to where you were instead of leaving you
 * scrolled halfway down a settings page.
 *
 * Each group is its own lazy item, so a group's four animated previews are
 * composed when it scrolls into view. The flat grid this replaces lived in a
 * single item, which meant all nineteen previews — and their nineteen infinite
 * animations — were composed the moment the screen opened.
 *
 * Selecting a face returns immediately: the tap is the answer to the question the
 * screen asks, and the value still lands through the appearance screen's normal
 * unsaved-changes flow, so nothing is committed behind the user's back.
 */
@Composable
internal fun ClockFaceGalleryScreen(
    selected: ClockStyle,
    onSelect: (ClockStyle) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            SettingsDetailHeader(
                title = stringResource(R.string.settings_all_clock_faces),
                onBack = onBack,
            )
        }
        items(ClockFaceGroup.entries, key = { it.name }) { group ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column {
                    Text(
                        clockFaceGroupName(group),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.auroraHeading(),
                    )
                    Text(
                        clockFaceGroupDescription(group),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ClockFaceGrid(
                    faces = group.faces,
                    selected = selected,
                    onSelect = {
                        onSelect(it)
                        onBack()
                    },
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}
