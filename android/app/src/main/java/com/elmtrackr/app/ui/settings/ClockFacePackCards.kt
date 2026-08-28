package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmOutlinedButton
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing

/**
 * A pack the user does not have, sold by showing it.
 *
 * This replaced a card that listed the four face names as text and nothing else.
 * That was the right call while packs were free — you added one and saw it — and
 * the wrong one the moment there was a price, because a name is not a product.
 * "Sand · Tide · Sprout · Luna" tells someone nothing about whether they want
 * them; the drawings tell them in about a second.
 *
 * Three decisions carry the card:
 *
 * **One face leads.** Four previews at equal size is a filmstrip, and a
 * filmstrip reads as a list of things to get through. A lead face with three
 * behind it reads as a set — which is what is actually for sale — and gives the
 * pack something a glance can remember it by.
 *
 * **The price sits with the name, not inside the button.** It is the other half
 * of the decision, and it was previously a caption inside a control the eye
 * skips until it has already decided. The button is left to say one verb.
 *
 * **The previews are the real drawings**, by the same canvas that draws the
 * picker tiles. Not stills: a screenshot of a face would drift the first time
 * someone edits the drawing code, and it would be the marketing that went stale
 * rather than the product.
 *
 * The cost the old card avoided — animated canvases for something the user
 * cannot pick — is still avoided by the gallery's own structure: each group is a
 * separate lazy item, so only packs on screen compose.
 */
@Composable
internal fun ClockFacePackOfferCard(
    group: ClockFaceGroup,
    price: String?,
    owned: Boolean,
    sellable: Boolean,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
) {
    val name = clockFaceGroupName(group)
    val description = clockFaceGroupDescription(group)
    val faceNames = group.faces.map { clockStyleDisplayName(it) }.joinToString(" · ")
    val countLabel = stringResource(R.string.settings_pack_faces_count, group.faces.size)
    val ownedLabel = stringResource(R.string.settings_pack_owned)

    ElmCard(cornerRadius = CornerRadius.Large) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Layout.cardPadding)
                // One node for the card. Read as four separate previews, a name,
                // a price, a count, a description and a face list, this is seven
                // stops for something the user makes one decision about.
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
            PackShowcase(group.faces)

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Owned packs show that instead of a price. A price beside a pack
                // the user has already paid for reads as being asked twice.
                when {
                    owned -> PackBadge(ownedLabel)
                    price != null -> Text(
                        price,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.s8),
                    )
                }
            }

            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    faceNames,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s8),
                )
            }

            PackAction(owned = owned, sellable = sellable, onBuy = onBuy, onInstall = onInstall)
        }
    }
}

/**
 * The everything-at-once offer.
 *
 * The one card on the screen making a different kind of claim: every other card
 * sells a pack, this one sells the decision not to choose. It gets the accent
 * container, the largest price on the screen and, when the arithmetic supports
 * one, a saving.
 *
 * Deliberately *not* given the brand gradient the primary button wears. A
 * gradient card would need white text and a non-gradient button to sit on it,
 * and neither survives the theme flip without contrast work that cannot be
 * checked here. Position, tint and size already make it the climax of the
 * screen; the extra risk buys nothing.
 *
 * Shown only when at least two packs are unowned — see
 * [ClockFacePackStorefront.offerAllPacks].
 */
@Composable
internal fun AllClockFacePacksCard(
    storefront: ClockFacePackStorefront,
    onBuy: () -> Unit,
) {
    val packNames = storefront.unownedPacks.map { clockFaceGroupName(it) }.joinToString(" · ")
    val faceCount = storefront.unownedPacks.sumOf { it.faces.size }
    val countLabel = stringResource(R.string.settings_pack_faces_count, faceCount)
    val saving = storefront.allPacksSavingPercent

    ElmCard(
        cornerRadius = CornerRadius.Large,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Layout.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Layout.rowGap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_pack_all_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Text(
                stringResource(R.string.settings_pack_all_desc, storefront.unownedPacks.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                packNames,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(verticalAlignment = Alignment.Bottom) {
                storefront.allPacksPrice?.let { price ->
                    Text(
                        price,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * The pack as a product shot: one face leading, the rest beneath it.
 *
 * The stage behind them is a brand-tinted wash rather than the card surface, so
 * the previews read as *contents* rather than as more of the card. At these
 * alphas it is a lift rather than a colour, which is what keeps it working in
 * both themes without a second palette.
 *
 * The hero is width-constrained instead of filling the row: the face canvases
 * size their drawing off the shorter side, so a full-width band this short would
 * centre a small dial in a lot of empty space.
 *
 * Silenced for screen readers. The drawings carry nothing a blind user can act
 * on, and four unlabelled canvases between a pack's name and its price is four
 * stops of nothing; the card's own description names the faces instead.
 */
@Composable
private fun PackShowcase(faces: List<ClockStyle>) {
    val stage = Brush.verticalGradient(
        listOf(AuroraIndigo.copy(alpha = .10f), AuroraAqua.copy(alpha = .06f)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Medium))
            .background(stage)
            .padding(Spacing.s12)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Layout.inlineGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        faces.firstOrNull()?.let { hero ->
            Box(Modifier.fillMaxWidth(HERO_WIDTH_FRACTION)) {
                WatchFacePreview(style = hero, selected = false, height = Layout.packHeroHeight)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
        ) {
            faces.drop(1).forEach { face ->
                Box(Modifier.weight(1f)) {
                    WatchFacePreview(
                        style = face,
                        selected = false,
                        height = Layout.packPreviewHeight,
                    )
                }
            }
        }
    }
}

/** A small count, saving or ownership pill. */
@Composable
private fun PackBadge(
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
 * A full-width button rather than the text link this replaced. The tap target
 * was previously a caption-sized label in the corner of a header row —
 * findable, but nothing about it suggested it was the point of the screen.
 *
 * One verb, no price: the price is already the largest thing beside the pack's
 * name, and Play states it again on its own sheet before any money moves.
 */
@Composable
private fun PackAction(
    owned: Boolean,
    sellable: Boolean,
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

        sellable -> ElmGradientButton(onClick = onBuy) {
            Text(stringResource(R.string.settings_pack_buy), fontWeight = FontWeight.SemiBold)
        }

        // No Play billing here: a sideload, an emulator, a disabled Store, or a
        // country the product is not sold in. Says so instead of offering a
        // button that could only fail.
        else -> Text(
            stringResource(R.string.settings_pack_unavailable_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How much of the card's width the lead face takes.
 *
 * Around this fraction the hero's box lands near the aspect the face canvases
 * were drawn for; much wider and the drawing strands itself in the middle.
 */
private const val HERO_WIDTH_FRACTION = 0.58f
