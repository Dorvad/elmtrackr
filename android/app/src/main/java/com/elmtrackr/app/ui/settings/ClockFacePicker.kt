package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraDarkBg
import com.elmtrackr.app.ui.theme.AuroraDarkSurfaceRaised
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
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
 * The way into the face shop: a lit window with the merchandise in it, and a
 * button that says where it leads.
 *
 * What stood here before was an outlined "Browse all 8 faces", which is the
 * shape of a list expander: it promised more of the same rows and said nothing
 * about a shop. So the card shows the shop instead of describing it. It
 * borrows the store's own dark surface and glow, which on this light screen
 * reads as a doorway to somewhere else, and it leads with what is *not* the
 * user's yet — "16 faces in 4 packs" — over one real, live-rendered lead face
 * per locked pack, so the pitch is the drawings rather than a sentence about
 * them. The call to action at the foot is drawn as the primary button, even
 * though the whole card is the control: the previous card asked people to
 * infer that a dark rectangle with a chevron was tappable, and a shop door
 * should not need inferring.
 *
 * A pack shipped in the last two releases raises the New pill here, so the
 * shop advertises itself on the screen people actually open. Once everything
 * is owned there is nothing to sell, and the card stops selling: it names the
 * collection and stays as the way in to browse, re-pick and remove packs.
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
    // they lead the shelf.
    val teaser = (if (lockedPacks.isEmpty()) ClockFaceGroup.entries.filter { it in availablePacks } else lockedPacks)
        .sortedByDescending { it.isNewIn(appVersion) }
        .map { it.faces.first() }
        .take(TEASER_FACES)
    val locked = lockedPacks.isNotEmpty()
    val shape = RoundedCornerShape(CornerRadius.Large)
    val tileShape = RoundedCornerShape(CornerRadius.Small)
    val title = stringResource(R.string.settings_face_shop_title)
    val headline = if (locked) {
        pluralStringResource(
            R.plurals.settings_face_shop_waiting,
            lockedPacks.size,
            lockedFaces,
            lockedPacks.size,
        )
    } else {
        stringResource(R.string.settings_face_store_count_all_owned, ownedFaces)
    }
    val open = stringResource(R.string.settings_face_shop_open)

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
                // One button: what it is, what is in it, where it goes.
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = listOf(title, headline, open).joinToString(", ")
                },
        ) {
            ShopWindowGlow(Modifier.matchParentSize())
            Column(Modifier.padding(Layout.cardPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.uppercase(),
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
                }
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.s6),
                )
                if (locked) {
                    Text(
                        stringResource(R.string.settings_face_shop_pitch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s2),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.s14),
                    horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
                ) {
                    teaser.forEach { face ->
                        // Equal tiles at the thumbnails' proportions: the real
                        // face on its plate, as a still.
                        WatchFacePreview(
                            style = face,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(THUMB_ASPECT)
                                .border(Spacing.s1, Color.White.copy(alpha = WINDOW_HAIRLINE), tileShape)
                                .clearAndSetSemantics { },
                            animate = false,
                            shape = tileShape,
                            plate = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                ShopCallToAction(label = open, modifier = Modifier.padding(top = Spacing.s14))
            }
        }
    }
}

/**
 * The card's call to action, drawn as the primary button. Decorative on
 * purpose: the card is the control, and a second clickable inside it would be
 * a second button for TalkBack that does the same thing.
 */
@Composable
private fun ShopCallToAction(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.s48)
            .background(ShopButtonGradient, RoundedCornerShape(CornerRadius.Button))
            .padding(horizontal = Spacing.s16, vertical = Spacing.s12)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .padding(start = Spacing.s4)
                .size(Spacing.s20)
                .mirrorInRtl(),
        )
    }
}

/** The brand gradient the primary button wears. */
private val ShopButtonGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
)

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
                WatchFacePreview(face, Modifier.height(Layout.facePreviewHeight))
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
        ClockFaceGroup.NERDS -> R.string.clock_group_nerds
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
        ClockFaceGroup.NERDS -> R.string.clock_group_nerds_desc
    },
)

private const val COLUMNS = 2

/** Lead faces on the shop card — one per pack, so the strip reads as breadth. */
private const val TEASER_FACES = 4


private const val WINDOW_HAIRLINE = 0.08f

/** The 0.16em eyebrow tracking the store uses. */
private const val EYEBROW_TRACKING = 0.16f
