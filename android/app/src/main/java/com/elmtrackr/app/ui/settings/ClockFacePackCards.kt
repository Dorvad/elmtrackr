package com.elmtrackr.app.ui.settings

import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextOverflow
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmOutlinedButton
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
import com.elmtrackr.app.domain.text.BidiText
import kotlinx.coroutines.launch

/**
 * A pack on the shop shelf, shaped like a product card and nothing else.
 *
 * Every card has the same four parts in the same order, so the shelf lines up:
 * the **showroom** on top — one face at the dashboard's own proportions,
 * swipeable, live — with the pack's four faces as a thumbnail strip beneath
 * it; the **name and pitch**; and the **price, as a full-width button** at the
 * bottom. The previous card put the price in a chip beside the title and hid
 * the hero behind an expander, which left five cards whose buy buttons sat at
 * five different heights and whose product shots were mostly closed. A shop
 * shows its goods.
 *
 * Only the settled hero page animates; the thumbnails are stills. Tapping a
 * thumbnail brings that face into the hero; tapping the hero opens the
 * full-screen look. [heroPage] is how the look hands the pager back the page
 * it was closed on.
 *
 * Ownership never renders here: an owned-and-installed pack belongs to the
 * "Your faces" tab, and the one owned state this card can show is the
 * grandfathered "owned but not added" pack, whose button says Add instead of
 * carrying a price.
 */
@Composable
internal fun ClockFacePackOfferCard(
    group: ClockFaceGroup,
    price: String?,
    owned: Boolean,
    availability: BillingAvailability,
    isNew: Boolean,
    heroPage: Int?,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
    onOpenLook: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = clockFaceGroupName(group)
    val description = clockFaceGroupDescription(group)
    val countLabel = stringResource(R.string.settings_pack_faces_count, group.faces.size)
    val ownedLabel = stringResource(R.string.settings_pack_owned)

    val pagerState = rememberPagerState { group.faces.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(heroPage) {
        if (heroPage != null && heroPage != pagerState.currentPage) pagerState.scrollToPage(heroPage)
    }

    PackCardSurface(modifier = modifier) {
        PackShowroom(
            faces = group.faces,
            pagerState = pagerState,
            onOpenLook = onOpenLook,
            onPickFace = { scope.launch { pagerState.animateScrollToPage(it) } },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = Layout.rowGap + Spacing.s4)
                // One node for what the card says — name, pitch, contents and
                // price — so a screen reader hears the product, not fragments.
                // The hero, the thumbnails and the button keep their own actions.
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(
                        name,
                        description,
                        countLabel,
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
                }
                PackBadge(text = countLabel)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s2),
            )
        }

        PackAction(
            packName = name,
            price = price,
            owned = owned,
            availability = availability,
            onBuy = onBuy,
            onInstall = onInstall,
            modifier = Modifier.padding(top = Layout.rowGap + Spacing.s6),
        )
    }
}

/**
 * The price, as the button — one full-width action at the foot of every card,
 * so the shelf's buttons form a single column.
 *
 * Three shapes: the gradient carrying the price when the pack is for sale
 * (disabled while Play is still connecting, because "unavailable" is a claim
 * the app cannot yet make), an outlined Add for a pack the user owns but has
 * not installed, and a sentence when billing is genuinely unavailable — a
 * button that can only fail is worse than an explanation.
 */
@Composable
private fun PackAction(
    packName: String,
    price: String?,
    owned: Boolean,
    availability: BillingAvailability,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        when {
            owned -> ElmOutlinedButton(onClick = onInstall) {
                Text(stringResource(R.string.settings_pack_add), fontWeight = FontWeight.SemiBold)
            }

            availability == BillingAvailability.UNAVAILABLE -> Text(
                stringResource(R.string.settings_pack_unavailable_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.s12),
            )

            else -> ElmGradientButton(
                onClick = onBuy,
                enabled = availability == BillingAvailability.AVAILABLE && price != null,
                accessibilityLabel = if (price != null) {
                    stringResource(
                        R.string.settings_pack_unlock,
                        *BidiText.isolateAll(packName, price),
                    )
                } else {
                    stringResource(R.string.settings_pack_unlock_unpriced, BidiText.isolate(packName))
                },
            ) {
                Text(
                    if (price != null) {
                        stringResource(R.string.settings_pack_unlock_price, BidiText.isolate(price))
                    } else {
                        stringResource(R.string.settings_pack_buy)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The everything-at-once offer, last on the shelf — built to the same plan as
 * the pack cards so it reads as the fifth product rather than a footnote: a
 * mosaic of the packs' lead faces where the showroom would be, the name and
 * pitch, the price in the button.
 *
 * The one card making a different kind of claim, so it wears a low-alpha wash
 * of the brand gradient — a tint, not the gradient itself, which stays
 * reserved for the button. The saving badge is absent unless
 * [ClockFacePackStorefront.allPacksSavingPercent] says the bundle is genuinely
 * cheaper for *this* user: a pricing mistake in Play Console removes the claim
 * rather than inventing one, and the app never formats currency itself, so
 * the only honest numbers are the ones Play hands over.
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
    val price = storefront.allPacksPrice

    PackCardSurface(modifier = modifier, tinted = true) {
        PackMosaic(faces = storefront.unownedPacks.map { it.faces.first() }.take(MOSAIC_FACES))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = Layout.rowGap + Spacing.s4)
                .semantics(mergeDescendants = true) {},
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
                if (saving != null) {
                    PackBadge(
                        text = stringResource(R.string.settings_pack_saving, saving),
                        container = AuroraAqua,
                        contentColor = AuroraDarkBg,
                    )
                }
                PackBadge(text = countLabel)
            }
            Text(
                stringResource(R.string.settings_pack_all_desc, storefront.unownedPacks.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s2),
            )
            Text(
                packNames,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = Spacing.s4),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = Layout.rowGap + Spacing.s6),
        ) {
            when (storefront.availability) {
                BillingAvailability.UNAVAILABLE -> Text(
                    stringResource(R.string.settings_pack_unavailable_note),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s12),
                )
                // Disabled while Play is still connecting: the button keeps
                // the card's shape, and claims nothing until the price is known.
                else -> ElmGradientButton(
                    onClick = onBuy,
                    enabled = storefront.availability == BillingAvailability.AVAILABLE && price != null,
                ) {
                    Text(
                        if (price != null) {
                            stringResource(R.string.settings_pack_all_buy_priced, price)
                        } else {
                            stringResource(R.string.settings_pack_all_buy)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * The store's card surface: a raised near-navy gradient with a hairline
 * border, or the brand-tinted wash the bundle wears.
 */
@Composable
internal fun PackCardSurface(
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    val fill = if (tinted) {
        Brush.linearGradient(
            listOf(
                AuroraIndigo.copy(alpha = .22f),
                AuroraPlum.copy(alpha = .16f),
                AuroraAqua.copy(alpha = .14f),
            ),
        )
    } else {
        Brush.verticalGradient(listOf(AuroraDarkSurfaceRaised, AuroraDarkSurface))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(Spacing.s1, Color.White.copy(alpha = HairlineAlpha), shape)
            .padding(Layout.cardPadding),
        content = content,
    )
}

/**
 * The showroom: a hero pager at the dashboard face box's proportions, and the
 * pack's faces as a strip of stills beneath it.
 *
 * The strip is also the pager's indicator — a ring marks the face the hero is
 * settled on — which is why the card has no separate dots. Tapping a
 * thumbnail brings its face into the hero; tapping the hero opens the
 * full-screen look. Only the settled hero page animates, and the glow
 * breathes only behind it.
 */
@Composable
private fun PackShowroom(
    faces: List<ClockStyle>,
    pagerState: PagerState,
    onOpenLook: (Int) -> Unit,
    onPickFace: (Int) -> Unit,
) {
    val heroShape = RoundedCornerShape(CornerRadius.Medium)
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
        pageSpacing = Layout.rowGap,
    ) { page ->
        val face = faces[page]
        val settled = pagerState.settledPage == page
        val previewLabel = stringResource(R.string.settings_pack_preview_face, clockStyleDisplayName(face))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ClockFaceAspect),
        ) {
            if (settled) {
                BreathingGlow(Modifier.fillMaxSize())
            }
            WatchFacePreview(
                style = face,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(heroShape)
                    .clickable(role = Role.Button, onClickLabel = previewLabel) { onOpenLook(page) }
                    // The drawing and its sample reading say nothing a screen
                    // reader can use; the hero is one button, named for its face.
                    .clearAndSetSemantics { contentDescription = previewLabel },
                animate = settled,
                shape = heroShape,
                plate = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
    FaceThumbRow(
        faces = faces,
        ringIndex = pagerState.settledPage,
        onPickFace = onPickFace,
        modifier = Modifier.padding(top = Layout.rowGap),
    )
}

/**
 * The bundle's stand-in for a showroom: the lead face of each unowned pack, two
 * to a row, every tile the same shape as a hero.
 */
@Composable
private fun PackMosaic(faces: List<ClockStyle>) {
    val tileShape = RoundedCornerShape(CornerRadius.Small)
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Layout.inlineGap),
    ) {
        faces.chunked(MOSAIC_COLUMNS).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
            ) {
                row.forEach { face ->
                    WatchFacePreview(
                        style = face,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(ClockFaceAspect),
                        animate = false,
                        shape = tileShape,
                        plate = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                repeat(MOSAIC_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
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
 * The pack's four faces as a strip of equal tiles, each a still of the real
 * face on its plate — sample reading included, because for Minimal and Retro
 * the reading *is* the face — named beneath. The ring marks the hero's
 * settled page.
 */
@Composable
private fun FaceThumbRow(
    faces: List<ClockStyle>,
    ringIndex: Int,
    onPickFace: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = RoundedCornerShape(CornerRadius.Small)
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
                    .clip(RoundedCornerShape(CornerRadius.Small))
                    .clickable(role = Role.Button) { onPickFace(index) }
                    .heightIn(min = Layout.minTouchTarget)
                    .semantics(mergeDescendants = true) { contentDescription = name },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
            ) {
                WatchFacePreview(
                    style = face,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(THUMB_ASPECT)
                        .border(
                            width = if (ringed) Spacing.s2 else Spacing.s1,
                            color = if (ringed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = HairlineAlpha)
                            },
                            shape = tileShape,
                        )
                        .clearAndSetSemantics { },
                    animate = false,
                    shape = tileShape,
                    plate = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (ringed) FontWeight.Bold else FontWeight.Medium,
                    color = if (ringed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
        maxLines = 1,
        modifier = Modifier
            .padding(start = Spacing.s8)
            .background(container, RoundedCornerShape(CornerRadius.Chip))
            .padding(horizontal = Spacing.s8, vertical = Spacing.s4),
    )
}

/**
 * The thumbnails' shape: squarer than a hero, because four across a card
 * leaves each about 70dp wide, and a face box that wide at the hero's 16:9
 * would be too short to read.
 */
internal const val THUMB_ASPECT = 1.45f

/** The hairline border every store card wears. */
private const val HairlineAlpha = 0.08f

private const val MOSAIC_FACES = 4
private const val MOSAIC_COLUMNS = 2

private const val GLOW_BREATHE_MILLIS = 4_500
private const val GLOW_MIN_SCALE = 0.85f
private const val GLOW_MAX_SCALE = 1.1f
private const val GLOW_MIN_ALPHA = 0.55f
private const val GLOW_MAX_ALPHA = 1f
