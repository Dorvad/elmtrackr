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
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.theme.AuroraDarkBg
import com.elmtrackr.app.ui.theme.AuroraDarkSurfaceRaised
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics

/**
 * The four faces offered on the appearance screen, plus the way into the shop.
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
    availablePacks: Set<ClockFaceGroup>,
    appVersion: String,
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
        Spacer(Modifier.height(Spacing.md))
        ClockFaceShopCard(
            availablePacks = availablePacks,
            appVersion = appVersion,
            onOpenShop = onBrowseAll,
        )
    }
}

/**
 * The way into the face shop — a lit window rather than a button.
 *
 * What stood here was an outlined "Browse all 8 faces", which is the shape of a
 * list expander: it promised more of the same rows, said nothing about a shop,
 * and its number counted what the user already owned — the least interesting
 * fact available. Nobody taps a control to see what they already have.
 *
 * So the card shows the shop instead of describing it. It borrows the store's
 * own dark surface and glow, which on this light screen reads as a doorway to
 * somewhere else and lands the user somewhere that looks like where they
 * tapped. It leads with what is *not* theirs yet — "16 faces in 4 packs" — and
 * carries the merchandise itself: one lead face per locked pack, drawn crisp,
 * so the pitch is the drawings rather than a sentence about them. The shelf
 * veils a locked face because that is where the buy decision is made; a shop
 * window that hid its goods would only be a worse button. A pack shipped in
 * the last two releases raises the New pill here, so the shop advertises
 * itself on the screen people actually open.
 *
 * Once everything is owned there is nothing to sell, and the card stops
 * selling: it names the collection and stays as the way in to browse, re-pick
 * and remove packs.
 */
@Composable
internal fun ClockFaceShopCard(
    availablePacks: Set<ClockFaceGroup>,
    appVersion: String,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lockedPacks = ClockFaceGroup.entries.filterNot { it in availablePacks }
    val lockedFaces = lockedPacks.sumOf { it.faces.size }
    val ownedFaces = ClockFaceGroup.entries.filter { it in availablePacks }.sumOf { it.faces.size }
    // One lead face per pack, so the strip shows breadth — four different packs
    // — rather than four faces from the same one. New arrivals lead, exactly as
    // they lead the shelf: a card that raises the New pill for Payday and then
    // shows four older packs advertises one thing and displays another.
    val teaser = (if (lockedPacks.isEmpty()) ClockFaceGroup.entries.filter { it in availablePacks } else lockedPacks)
        .sortedByDescending { it.isNewIn(appVersion) }
        .map { it.faces.first() }
        .take(TEASER_FACES)
    val locked = lockedPacks.isNotEmpty()
    val shape = RoundedCornerShape(CornerRadius.Large)

    // The store forces the dark arm whatever the app is set to; this window
    // into it does the same, so the two surfaces are the same place.
    ElmTrackrTheme(darkTheme = true) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(AuroraDarkSurfaceRaised, AuroraDarkBg)))
                .border(Spacing.s1, Color.White.copy(alpha = WINDOW_HAIRLINE), shape)
                .auroraRowClickable(onClick = onOpenShop)
                .semantics(mergeDescendants = true) { role = Role.Button },
        ) {
            ShopWindowGlow(Modifier.matchParentSize())
            Column(Modifier.padding(Layout.cardPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_face_shop_title).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = MaterialTheme.typography.labelSmall.fontSize * EYEBROW_TRACKING,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (locked && lockedPacks.any { it.isNewIn(appVersion) }) {
                        PackBadge(
                            text = stringResource(R.string.settings_pack_new),
                            container = auroraSemantics.infoContainer,
                            contentColor = auroraSemantics.infoInk,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.s24).mirrorInRtl(),
                    )
                }
                Text(
                    if (locked) {
                        pluralStringResource(
                            R.plurals.settings_face_shop_waiting,
                            lockedPacks.size,
                            lockedFaces,
                            lockedPacks.size,
                        )
                    } else {
                        stringResource(R.string.settings_face_store_count_all_owned, ownedFaces)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.s2),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.s12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s16),
                ) {
                    teaser.forEach { face ->
                        // Weighted rather than fixed: four fixed thumbnails plus
                        // their gaps overran the card on a narrow screen and the
                        // last face was clipped. Sharing the row cannot overflow,
                        // and the canvases centre their drawing in whatever width
                        // they are given.
                        Box(
                            Modifier
                                .weight(1f)
                                .height(Layout.packPreviewHeight)
                                .clearAndSetSemantics { },
                        ) {
                            WatchFacePreview(
                                style = face,
                                selected = false,
                                height = Layout.packPreviewHeight,
                                animate = false,
                                showBackground = false,
                                showReading = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The store's own glow, scaled to a card — the window is lit from inside. */
@Composable
private fun ShopWindowGlow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width * .16f, size.height * .1f)
        val radius = size.width * .55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuroraIndigo.copy(alpha = .34f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
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

/** Lead faces on the shop card — one per pack, so the strip reads as breadth. */
private const val TEASER_FACES = 4


private const val WINDOW_HAIRLINE = 0.08f

/** The 0.16em eyebrow tracking the store uses. */
private const val EYEBROW_TRACKING = 0.16f
