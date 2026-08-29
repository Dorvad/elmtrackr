package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmOutlinedButton
import com.elmtrackr.app.ui.design.auroraAnimationSpec
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.design.mirrorInRtl
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
 * A pack the user does not have, sold by showing it.
 *
 * The previous card was a static product shot: one lead face with three
 * thumbnails under it. The store redesign turns the hero into a
 * [HorizontalPager] — swiping changes which face is crisp, and the veiled row
 * beneath re-ranks so it always shows the other three. The set is still what is
 * for sale; the pager just lets every face take a turn being the argument.
 *
 * Three rules carry the card:
 *
 * **Only the settled page animates.** Twenty live canvases was the problem the
 * grouped gallery was built to solve, and a pager would quietly reintroduce it.
 * The canvas animation flag is bound to [PagerState.settledPage].
 *
 * **The veil is decoration.** Blur plus alpha says "locked" to sighted users;
 * the face names under the veil stay at full contrast, every veiled tile is
 * tappable (it opens the full-screen look), and the card's merged description
 * still names all four faces plus the price.
 *
 * **The price sits beside the button, not in it.** It is the other half of the
 * decision, and the button is left to say one verb. Play restates the price on
 * its own sheet before any money moves.
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

    val pagerState = rememberPagerState { group.faces.size }
    val scope = rememberCoroutineScope()
    // -1 = closed. The look is a detail view of this card, so it lives here:
    // closing it can then return the hero pager to whichever face was looked at.
    var lookPage by rememberSaveable { mutableIntStateOf(LOOK_CLOSED) }

    PackCardSurface(modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                // One node for what the card says. Read as four separate
                // previews, a name, a price, a count and a description, this is
                // seven stops for something the user makes one decision about.
                // The veiled tiles keep their own tap actions beneath this.
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(
                        name,
                        description,
                        countLabel,
                        faceNames,
                        if (owned) ownedLabel else price,
                    ).joinToString(", ")
                },
            verticalArrangement = Arrangement.spacedBy(Layout.rowGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .auroraHeading(),
                )
                if (isNew) {
                    PackBadge(
                        text = stringResource(R.string.settings_pack_new),
                        container = auroraSemantics.infoContainer,
                        contentColor = auroraSemantics.infoInk,
                    )
                } else if (owned) {
                    PackBadge(ownedLabel)
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            PackHero(
                faces = group.faces,
                pagerState = pagerState,
                onOpenLook = { lookPage = it },
            )
            // The crisp face's name, under the hero where the veiled tiles
            // carry theirs. The drawing shows its own sample reading; the name
            // is the one thing it cannot say for itself.
            Text(
                clockStyleDisplayName(group.faces[pagerState.settledPage]),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            PagerDots(
                count = group.faces.size,
                current = pagerState.settledPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            VeiledFaceRow(
                faces = group.faces,
                leadingIndex = pagerState.settledPage,
                onOpenLook = { lookPage = it },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Owned packs show that instead of a price. A price beside a
                // pack the user has already paid for reads as being asked twice.
                if (!owned && price != null) {
                    Text(
                        price,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = Spacing.s12),
                    )
                }
                Box(Modifier.weight(1f)) {
                    PackAction(
                        owned = owned,
                        availability = availability,
                        onBuy = onBuy,
                        onInstall = onInstall,
                    )
                }
            }
        }
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
                scope.launch { pagerState.scrollToPage(settledAt) }
            },
        )
    }
}

/**
 * A pack below the fold, folded.
 *
 * One face, the name, the price and a chevron in a single row. Tapping expands
 * it in place to the full product card; nothing is sold from the collapsed
 * shape. This is what keeps the store scannable at five packs — the first
 * unowned pack opens expanded and makes the argument, the rest wait their turn.
 */
@Composable
internal fun ClockFacePackCollapsedRow(
    group: ClockFaceGroup,
    price: String?,
    owned: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = clockFaceGroupName(group)
    val description = clockFaceGroupDescription(group)
    val ownedLabel = stringResource(R.string.settings_pack_owned)
    val shape = RoundedCornerShape(CornerRadius.Large)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = CollapsedRowLift))
            .border(Spacing.s1, Color.White.copy(alpha = HairlineAlpha), shape)
            .clickable(onClick = onExpand)
            .heightIn(min = Layout.minTouchTarget)
            .padding(horizontal = Layout.cardPadding, vertical = Spacing.s12)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    name,
                    description,
                    if (owned) ownedLabel else price,
                ).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Box(Modifier.width(Spacing.s48).clearAndSetSemantics { }) {
            WatchFacePreview(
                style = group.faces.first(),
                selected = false,
                height = Spacing.s48,
                animate = false,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val trailing = if (owned) ownedLabel else price
                if (trailing != null) {
                    Text(
                        trailing,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.s8),
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Spacing.s20).mirrorInRtl(),
        )
    }
}

/**
 * The everything-at-once offer, last on the screen.
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
 *
 * Shared by the product cards and the installed-pack pickers so a purchase can
 * transform one into the other without the container appearing to change — the
 * contents crossfade, the card stays.
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
 * pager keeps its own scroll semantics, so TalkBack can still move between
 * pages; tapping the crisp face opens the full-screen look.
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
                    // unlabelled button nor the drawing: the veiled tiles are
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
 * Page indicators. The settled dot stretches into a pill; the others stay dots.
 *
 * Indicators, not controls: at this size a tap target would be a lie, and the
 * pager beneath them is the actual control. The width change is the only thing
 * that should move on a swipe.
 */
@Composable
private fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) DOT_ACTIVE_WIDTH else DOT_SIZE,
                animationSpec = auroraAnimationSpec(DOT_SETTLE_MILLIS),
                label = "pack-pager-dot",
            )
            Box(
                Modifier
                    .width(width)
                    .height(DOT_SIZE)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.White.copy(alpha = .22f)
                        },
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * The three faces that are not currently leading, veiled.
 *
 * Re-ranks against [leadingIndex] so the row always shows the *other* three.
 * The veil is blur plus alpha on the drawing only — the names beneath stay at
 * full contrast, because the veil says "locked" to sighted users and must
 * never be the only signal. Every tile is tappable and opens the full-screen
 * look at that face.
 */
@Composable
private fun VeiledFaceRow(
    faces: List<ClockStyle>,
    leadingIndex: Int,
    onOpenLook: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
    ) {
        faces.forEachIndexed { index, face ->
            if (index == leadingIndex) return@forEachIndexed
            val name = clockStyleDisplayName(face)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .clickable { onOpenLook(index) }
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
 * Buy, add, or say why neither is on offer.
 *
 * One verb, no price: the price is already the largest thing beside it, and
 * Play states it again on its own sheet before any money moves.
 */
@Composable
internal fun PackAction(
    owned: Boolean,
    availability: BillingAvailability,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
) {
    when {
        // Owned already — including every pack the user had when packs became
        // paid. Outlined rather than the gradient: adding something you own is
        // not the decision this screen is built around.
        owned -> ElmOutlinedButton(onClick = onInstall) {
            Text(stringResource(R.string.settings_pack_add), fontWeight = FontWeight.SemiBold)
        }

        // No Play billing here: a sideload, an emulator, a disabled Store, or a
        // country the product is not sold in. Says so instead of offering a
        // button that could only fail.
        availability == BillingAvailability.UNAVAILABLE -> Text(
            stringResource(R.string.settings_pack_unavailable_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // AVAILABLE, or still connecting. A disabled Buy while Play is being
        // asked, rather than the unavailable line: connecting takes a moment on
        // every cold start, and telling someone purchases are unavailable for
        // that moment is a sentence the app cannot yet know to be true.
        else -> ElmGradientButton(
            onClick = onBuy,
            enabled = availability == BillingAvailability.AVAILABLE,
        ) {
            Text(stringResource(R.string.settings_pack_buy), fontWeight = FontWeight.SemiBold)
        }
    }
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

/** The veil over a locked face: 30% alpha paired with [Layout.packVeilBlur]. */
private const val VEIL_ALPHA = 0.3f

/** The hairline border every store card wears. */
private const val HairlineAlpha = 0.07f

/** The lift that separates a collapsed pack row from the store background. */
private const val CollapsedRowLift = 0.04f

private const val GLOW_BREATHE_MILLIS = 4_500
private const val GLOW_MIN_SCALE = 0.85f
private const val GLOW_MAX_SCALE = 1.1f
private const val GLOW_MIN_ALPHA = 0.55f
private const val GLOW_MAX_ALPHA = 1f

private const val DOT_SETTLE_MILLIS = 200
private val DOT_SIZE = 5.dp
private val DOT_ACTIVE_WIDTH = 22.dp
