package com.elmtrackr.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.auroraAnimationSpec
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraDarkBg
import com.elmtrackr.app.ui.theme.AuroraDarkSurface
import com.elmtrackr.app.ui.theme.AuroraDarkSurfaceRaised
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics
import kotlinx.coroutines.launch

/**
 * A pack on the shop shelf.
 *
 * Shaped the way established stores shape a product row: the name, the pitch
 * and the **price are always visible**, and the price chip is the buy button —
 * a shopper never has to open anything to learn what something costs. Beneath
 * the header, a strip of the pack's four faces is the product photography:
 * always on show, veiled because they are locked, tappable to open the
 * full-screen look.
 *
 * The card expands — and collapses again — from the header or the chevron,
 * revealing the showroom: a swipeable hero pager at product-shot size, its
 * position mirrored by a ring on the thumbnail strip. Only the settled page
 * animates; collapsed cards run no canvases at all.
 *
 * Ownership never renders here: an owned-and-installed pack belongs to the
 * "Your faces" tab, and the one owned state this card can show is the
 * grandfathered "owned but not added" pack, whose chip says Add instead of a
 * price.
 */
@Composable
internal fun ClockFacePackOfferCard(
    group: ClockFaceGroup,
    price: String?,
    owned: Boolean,
    availability: BillingAvailability,
    isNew: Boolean,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = clockFaceGroupName(group)
    val description = clockFaceGroupDescription(group)
    val faceNames = group.faces.map { clockStyleDisplayName(it) }.joinToString(" · ")
    val countLabel = stringResource(R.string.settings_pack_faces_count, group.faces.size)
    val ownedLabel = stringResource(R.string.settings_pack_owned)

    var expanded by rememberSaveable(group.name) { mutableStateOf(false) }
    val pagerState = rememberPagerState { group.faces.size }
    val scope = rememberCoroutineScope()
    // -1 = closed. The look is a detail view of this card, so it lives here:
    // closing it can then return the hero pager to whichever face was looked at.
    var lookPage by rememberSaveable { mutableIntStateOf(LOOK_CLOSED) }

    PackCardSurface(modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CornerRadius.Small))
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) R.string.settings_pack_hide_faces else R.string.settings_pack_show_faces,
                    ),
                ) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    // One node for what the row says — name, pitch, contents
                    // and ownership — so a screen reader hears the product,
                    // not five fragments. The chip and thumbnails keep their
                    // own actions beside it.
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(
                            name,
                            description,
                            countLabel,
                            faceNames,
                            if (owned) ownedLabel else price,
                        ).joinToString(", ")
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.auroraHeading(),
                    )
                    if (isNew) {
                        PackBadge(
                            text = stringResource(R.string.settings_pack_new),
                            container = auroraSemantics.infoContainer,
                            contentColor = auroraSemantics.infoInk,
                        )
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            PackPriceChip(
                packName = name,
                price = price,
                owned = owned,
                availability = availability,
                onBuy = onBuy,
                onInstall = onInstall,
            )
            val chevronTurn by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = auroraAnimationSpec(CHEVRON_TURN_MILLIS),
                label = "pack-chevron",
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .padding(start = Spacing.s6)
                    .size(Spacing.s20)
                    .graphicsLayer { rotationZ = chevronTurn },
            )
        }

        // Billing being down is said in words, not with a button that can
        // only fail — and it is said whether or not the card is open.
        if (!owned && availability == BillingAvailability.UNAVAILABLE) {
            Text(
                stringResource(R.string.settings_pack_unavailable_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s6),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(auroraAnimationSpec(SHOWROOM_MILLIS)) +
                fadeIn(auroraAnimationSpec(SHOWROOM_MILLIS)),
            exit = shrinkVertically(auroraAnimationSpec(SHOWROOM_MILLIS)) +
                fadeOut(auroraAnimationSpec(SHOWROOM_MILLIS)),
        ) {
            Column(Modifier.padding(top = Spacing.s8)) {
                PackHero(
                    faces = group.faces,
                    pagerState = pagerState,
                    onOpenLook = { lookPage = it },
                )
                // The crisp face's name, under the hero where the thumbnails
                // carry theirs. The drawing shows its own sample reading; the
                // name is the one thing it cannot say for itself.
                Text(
                    clockStyleDisplayName(group.faces[pagerState.settledPage]),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = Spacing.s4),
                )
            }
        }

        FaceThumbRow(
            faces = group.faces,
            ringIndex = if (expanded) pagerState.settledPage else null,
            onOpenLook = { lookPage = it },
            modifier = Modifier.padding(top = Spacing.s8),
        )
    }

    if (lookPage != LOOK_CLOSED) {
        ClockFaceLookScreen(
            group = group,
            initialPage = lookPage,
            price = price,
            owned = owned,
            availability = availability,
            onBuy = onBuy,
            onClose = { settledAt ->
                lookPage = LOOK_CLOSED
                // Back from the look returns to this card at the same pager
                // index — the look is a detail view, not a step in a funnel.
                expanded = true
                scope.launch { pagerState.scrollToPage(settledAt) }
            },
        )
    }
}

/**
 * The price, as the buy button — the store convention shoppers already know.
 *
 * One chip, three shapes: a gradient chip carrying the price when the pack is
 * for sale (disabled while Play is still connecting, because "unavailable"
 * is a claim the app cannot yet make), an outlined Add for a pack the user
 * owns but has not installed, and nothing at all when billing is genuinely
 * unavailable — the card explains that in words instead.
 */
@Composable
private fun PackPriceChip(
    packName: String,
    price: String?,
    owned: Boolean,
    availability: BillingAvailability,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
) {
    when {
        owned -> Box(
            Modifier
                .padding(start = Spacing.s8)
                .clip(RoundedCornerShape(CornerRadius.Button))
                .border(
                    Spacing.s1,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(CornerRadius.Button),
                )
                .clickable(role = Role.Button, onClick = onInstall)
                .heightIn(min = Spacing.s32 + Spacing.s4)
                .padding(horizontal = Spacing.s14, vertical = Spacing.s8),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.settings_pack_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        availability == BillingAvailability.UNAVAILABLE -> Unit

        else -> ElmGradientButton(
            onClick = onBuy,
            compact = true,
            enabled = availability == BillingAvailability.AVAILABLE && price != null,
            accessibilityLabel = if (price != null) {
                stringResource(R.string.settings_pack_unlock, packName, price)
            } else {
                stringResource(R.string.settings_pack_buy)
            },
            modifier = Modifier.padding(start = Spacing.s8),
        ) {
            Text(
                price ?: stringResource(R.string.settings_pack_buy),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * The everything-at-once offer, last on the shelf.
 *
 * The one card making a different kind of claim: every other card sells a pack,
 * this one sells the decision not to choose. It wears a low-alpha wash of the
 * brand gradient — a tint, not the gradient itself, which stays reserved for
 * the button — and the largest price on the screen.
 *
 * The saving badge is absent unless [ClockFacePackStorefront.allPacksSavingPercent]
 * says the bundle is genuinely cheaper for *this* user. A pricing mistake in
 * Play Console removes the claim rather than inventing one, which is also why
 * there is no struck-through "was" price: the app never formats currency
 * itself, so the only honest numbers are the ones Play hands over.
 *
 * Shown only when at least two packs are unowned — see
 * [ClockFacePackStorefront.offerAllPacks].
 */
@Composable
internal fun AllClockFacePacksCard(
    storefront: ClockFacePackStorefront,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val packNames = storefront.unownedPacks.map { clockFaceGroupName(it) }.joinToString(" · ")
    val faceCount = storefront.unownedPacks.sumOf { it.faces.size }
    val countLabel = stringResource(R.string.settings_pack_faces_count, faceCount)
    val saving = storefront.allPacksSavingPercent
    val shape = RoundedCornerShape(CornerRadius.Large)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        AuroraIndigo.copy(alpha = .22f),
                        AuroraPlum.copy(alpha = .16f),
                        AuroraAqua.copy(alpha = .14f),
                    ),
                ),
            )
            .border(Spacing.s1, Color.White.copy(alpha = .10f), shape)
            .padding(Layout.cardPadding),
        verticalArrangement = Arrangement.spacedBy(Layout.rowGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_pack_all_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .auroraHeading(),
            )
            // Absent unless the arithmetic supports it. A pricing mistake in
            // Play Console removes the badge rather than making the app claim
            // a discount nobody is getting.
            if (saving != null) {
                PackBadge(
                    text = stringResource(R.string.settings_pack_saving, saving),
                    container = AuroraAqua,
                    contentColor = AuroraDarkBg,
                )
            }
        }

        Text(
            stringResource(R.string.settings_pack_all_desc, storefront.unownedPacks.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            packNames,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.Bottom) {
            storefront.allPacksPrice?.let { price ->
                Text(
                    price,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.s8),
            )
        }

        when (storefront.availability) {
            BillingAvailability.AVAILABLE -> ElmGradientButton(onClick = onBuy) {
                Text(
                    stringResource(R.string.settings_pack_all_buy),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // Still connecting. Nothing is claimed either way until it is known.
            BillingAvailability.LOADING -> Unit
            BillingAvailability.UNAVAILABLE -> Text(
                stringResource(R.string.settings_pack_unavailable_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The store's card surface: a raised near-navy gradient with a hairline border.
 */
@Composable
internal fun PackCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(AuroraDarkSurfaceRaised, AuroraDarkSurface)))
            .border(Spacing.s1, Color.White.copy(alpha = HairlineAlpha), shape)
            .padding(Layout.cardPadding),
        content = content,
    )
}

/**
 * The hero pager: one face at product-shot size, a breathing glow behind it.
 *
 * Pages compose their canvases static except the settled one — swiping between
 * four live infinite animations is exactly the cost the store exists to avoid.
 * The glow, too, breathes only behind the settled page, and holds its middle
 * value when motion is reduced.
 *
 * Silenced for screen readers: the drawing carries nothing a blind user can
 * act on, and the card's merged description already names every face. The
 * pager keeps its own scroll semantics, and the thumbnail strip is the
 * labelled, tappable route to each face's full-screen look.
 */
@Composable
private fun PackHero(
    faces: List<ClockStyle>,
    pagerState: PagerState,
    onOpenLook: (Int) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        val face = faces[page]
        val settled = pagerState.settledPage == page
        Box(
            Modifier
                .fillMaxWidth()
                .height(Layout.packHeroHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (settled) {
                BreathingGlow(Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .fillMaxWidth(HERO_WIDTH_FRACTION)
                    // Silenced before the click so TalkBack sees neither an
                    // unlabelled button nor the drawing: the thumbnails are
                    // the accessible route to the look, and the card's merged
                    // description already names this face.
                    .clearAndSetSemantics { }
                    .clickable { onOpenLook(page) },
            ) {
                WatchFacePreview(
                    style = face,
                    selected = false,
                    height = Layout.packHeroHeight,
                    animate = settled,
                    showBackground = false,
                    readingStyle = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}

/**
 * The soft indigo halo behind a settled hero face.
 *
 * 4.5s ease-in-out, alternating — the same breathe the dashboard clock wears.
 * Under reduced motion it holds the midpoint of its range rather than
 * disappearing: the glow is part of the composition, only its movement is
 * optional.
 */
@Composable
private fun BreathingGlow(modifier: Modifier = Modifier) {
    val breathe = if (auroraMotionEnabled()) {
        val transition = rememberInfiniteTransition(label = "pack-hero-glow")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(GLOW_BREATHE_MILLIS),
                RepeatMode.Reverse,
            ),
            label = "pack-hero-glow-breathe",
        )
        value
    } else {
        0.5f
    }
    Box(
        modifier
            .graphicsLayer {
                val scale = GLOW_MIN_SCALE + breathe * (GLOW_MAX_SCALE - GLOW_MIN_SCALE)
                scaleX = scale
                scaleY = scale
                alpha = GLOW_MIN_ALPHA + breathe * (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA)
            }
            .background(
                Brush.radialGradient(
                    listOf(AuroraIndigo.copy(alpha = .42f), Color.Transparent),
                ),
            ),
    )
}

/**
 * The pack's contents as product photography: all four faces, always visible,
 * veiled because they are locked, each tappable to open the full-screen look.
 *
 * When the showroom above is open, a ring marks the face the hero pager is
 * settled on — the thumbnail strip doubles as the pager's indicator, which is
 * why the card has no separate dots. The veil is blur plus alpha on the
 * drawing only; the names beneath stay at full contrast, because the veil
 * says "locked" to sighted users and must never be the only signal.
 */
@Composable
private fun FaceThumbRow(
    faces: List<ClockStyle>,
    ringIndex: Int?,
    onOpenLook: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
    ) {
        faces.forEachIndexed { index, face ->
            val name = clockStyleDisplayName(face)
            val ringed = index == ringIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .then(
                        if (ringed) {
                            Modifier.border(
                                Spacing.s1,
                                MaterialTheme.colorScheme.primary.copy(alpha = .55f),
                                RoundedCornerShape(CornerRadius.Medium),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.Button) { onOpenLook(index) }
                    .heightIn(min = Layout.minTouchTarget)
                    .padding(vertical = Spacing.s4)
                    .semantics(mergeDescendants = true) { contentDescription = name },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Layout.inlineGap),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .blur(Layout.packVeilBlur)
                        .alpha(VEIL_ALPHA)
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
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A small count, saving or ownership pill. */
@Composable
internal fun PackBadge(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        modifier = Modifier
            .padding(start = Spacing.s8)
            .background(container, RoundedCornerShape(CornerRadius.Chip))
            .padding(horizontal = Spacing.s8, vertical = Spacing.s4),
    )
}

/**
 * How much of the card's width the hero face takes.
 *
 * Around this fraction the hero's box lands near the aspect the face canvases
 * were drawn for; much wider and the drawing strands itself in the middle.
 */
private const val HERO_WIDTH_FRACTION = 0.58f

/** Sentinel for "the full-screen look is closed". */
private const val LOOK_CLOSED = -1

/**
 * The veil over a locked face, paired with [Layout.packVeilBlur].
 *
 * Higher than the reference's 30%: the mock's stand-in faces were solid
 * conic fills, while the real drawings are thin strokes that a blur thins
 * further. At this alpha the drawing still reads as behind glass without
 * disappearing into the card.
 */
private const val VEIL_ALPHA = 0.45f

/** The hairline border every store card wears. */
private const val HairlineAlpha = 0.07f

private const val GLOW_BREATHE_MILLIS = 4_500
private const val GLOW_MIN_SCALE = 0.85f
private const val GLOW_MAX_SCALE = 1.1f
private const val GLOW_MIN_ALPHA = 0.55f
private const val GLOW_MAX_ALPHA = 1f

private const val CHEVRON_TURN_MILLIS = 200
private const val SHOWROOM_MILLIS = 250
