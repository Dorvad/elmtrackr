package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.ElmOutlinedButton
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * The four faces offered on the appearance screen, plus a way to see the rest.
 *
 * Progressive disclosure, and the reason is arithmetic: twenty faces in a flat
 * grid is twenty animated previews composed at once and a wall of labels to
 * read before the first decision. Four covers the people this screen is actually
 * for — someone who rotates between a handful of faces never leaves this row —
 * and everyone else is one tap from the full set.
 */
@Composable
internal fun ClockFaceQuickPicker(
    selected: ClockStyle,
    recents: List<ClockStyle>,
    availableFaces: List<ClockStyle>,
    onSelect: (ClockStyle) -> Unit,
    onBrowseAll: () -> Unit,
) {
    val picks = clockFaceQuickPicks(
        current = selected,
        recents = recents,
        available = availableFaces,
    )
    Column {
        Text(
            stringResource(R.string.settings_watch_face),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.settings_watch_face_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        ClockFaceGrid(faces = picks, selected = selected, onSelect = onSelect)
        Spacer(Modifier.height(Spacing.sm))
        ElmOutlinedButton(onClick = onBrowseAll) {
            // Counts what the user has, not what exists. Offering "browse all 19"
            // while only eight are available would be a promise the next screen
            // breaks.
            Text(
                stringResource(R.string.settings_browse_all_faces, availableFaces.size),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(Spacing.xs))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp).mirrorInRtl(),
            )
        }
    }
}

/**
 * Lays faces out two to a row.
 *
 * Two columns rather than four because the tile carries a preview, a name and a
 * one-line description; at four across the description truncates to nothing and
 * the tile stops being a preview and becomes a swatch. Four *faces* per group,
 * two per row — that is the 2×2 block the gallery repeats.
 */
@Composable
internal fun ClockFaceGrid(
    faces: List<ClockStyle>,
    selected: ClockStyle,
    onSelect: (ClockStyle) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        faces.chunked(COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                row.forEach { face ->
                    ClockFaceTile(
                        face = face,
                        isSelected = face == selected,
                        onSelect = { onSelect(face) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short final row's tiles the same width as every other
                // row's, instead of stretching them across the full width.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ClockFaceTile(
    face: ClockStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = clockStyleDisplayName(face)
    val description = watchFaceDescription(face)
    val selectedLabel = stringResource(R.string.settings_selected)
    Box(modifier = modifier) {
        Card(
            onClick = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(CornerRadius.Medium),
                        )
                    } else {
                        Modifier
                    },
                )
                // One node for the whole tile: the name and description are two
                // separate reads otherwise, and the selected state was carried
                // only by a decorative check badge. Merging rather than clearing —
                // clearAndSetSemantics would take the Card's own click action with
                // it and leave a tile TalkBack cannot activate.
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isSelected) {
                        "$name, $description, $selectedLabel"
                    } else {
                        "$name, $description"
                    }
                    selected = isSelected
                },
            shape = RoundedCornerShape(CornerRadius.Medium),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WatchFacePreview(face, isSelected)
                Spacer(Modifier.height(6.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
internal fun clockFaceGroupName(group: ClockFaceGroup): String = stringResource(
    when (group) {
        ClockFaceGroup.ESSENTIALS -> R.string.clock_group_essentials
        ClockFaceGroup.PROGRESS -> R.string.clock_group_progress
        ClockFaceGroup.ATMOSPHERE -> R.string.clock_group_atmosphere
        ClockFaceGroup.NATURE -> R.string.clock_group_nature
        ClockFaceGroup.JOURNEYS -> R.string.clock_group_journeys
        ClockFaceGroup.PAYDAY -> R.string.clock_group_payday
    },
)

@Composable
internal fun clockFaceGroupDescription(group: ClockFaceGroup): String = stringResource(
    when (group) {
        ClockFaceGroup.ESSENTIALS -> R.string.clock_group_essentials_desc
        ClockFaceGroup.PROGRESS -> R.string.clock_group_progress_desc
        ClockFaceGroup.ATMOSPHERE -> R.string.clock_group_atmosphere_desc
        ClockFaceGroup.NATURE -> R.string.clock_group_nature_desc
        ClockFaceGroup.JOURNEYS -> R.string.clock_group_journeys_desc
        ClockFaceGroup.PAYDAY -> R.string.clock_group_payday_desc
    },
)

private const val COLUMNS = 2
