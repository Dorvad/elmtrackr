package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Every clock face, grouped, with the packs the user does not have offered rather
 * than hidden.
 *
 * Split off the appearance screen rather than expanded in place. Nineteen faces is
 * a browsing task, and browsing wants its own surface: room for group names, and a
 * back gesture that returns you where you were instead of leaving you scrolled
 * halfway down a settings page.
 *
 * Each group is its own lazy item, so a group's four animated previews compose when
 * it scrolls into view. The flat grid this replaced lived in one item, which meant
 * all nineteen previews — and their nineteen infinite animations — composed the
 * moment the screen opened.
 *
 * Selecting a face returns immediately: the tap answers the question the screen
 * asks, and the value still lands through the appearance screen's normal
 * unsaved-changes flow, so nothing is committed behind the user's back. Adding or
 * removing a pack does *not* return — it changes what is on this screen, so the
 * result should be visible here.
 */
@Composable
internal fun ClockFaceGalleryScreen(
    selected: ClockStyle,
    availablePacks: Set<ClockFaceGroup>,
    onSelect: (ClockStyle) -> Unit,
    onInstallPack: (ClockFaceGroup) -> Unit,
    onRemovePack: (ClockFaceGroup) -> Unit,
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
            val installed = group in availablePacks
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClockFaceGroupHeader(
                    group = group,
                    installed = installed,
                    canRemove = ClockFacePacks.canRemove(group, selected),
                    onInstall = { onInstallPack(group) },
                    onRemove = { onRemovePack(group) },
                )
                if (installed) {
                    ClockFaceGrid(
                        faces = group.faces,
                        selected = selected,
                        onSelect = {
                            onSelect(it)
                            onBack()
                        },
                    )
                } else {
                    ClockFacePackTeaser(group)
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun ClockFaceGroupHeader(
    group: ClockFaceGroup,
    installed: Boolean,
    canRemove: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
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
        when {
            // The bundled pack gets no action at all rather than a disabled one:
            // there is nothing the user could do with it and a greyed control only
            // invites the question.
            group.isBundled -> Unit
            !installed -> TextButton(
                onClick = onInstall,
                modifier = Modifier.padding(start = Spacing.s8),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(Spacing.s18))
                Spacer(Modifier.size(Spacing.s4))
                Text(stringResource(R.string.settings_pack_add), fontWeight = FontWeight.SemiBold)
            }
            canRemove -> TextButton(
                onClick = onRemove,
                modifier = Modifier.padding(start = Spacing.s8),
            ) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.s18),
                )
                Spacer(Modifier.size(Spacing.s4))
                Text(stringResource(R.string.settings_pack_remove))
            }
            // Installed and holding the selected face. Says why instead of
            // offering a button that would have to fail.
            else -> Text(
                stringResource(R.string.settings_pack_in_use),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.s8),
            )
        }
    }
}

/**
 * Stands in for a pack the user does not have.
 *
 * Names the faces rather than showing locked previews. A row of greyed-out tiles
 * would cost four animated canvases to render something the user cannot pick, and
 * the names are what actually help them decide.
 */
@Composable
private fun ClockFacePackTeaser(group: ClockFaceGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // map, then join: joinToString's transform is a stored function type, so
            // a composable cannot be called from it. map is inline and can.
            val names = group.faces.map { clockStyleDisplayName(it) }
            Text(
                names.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                stringResource(R.string.settings_pack_not_added, group.faces.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
