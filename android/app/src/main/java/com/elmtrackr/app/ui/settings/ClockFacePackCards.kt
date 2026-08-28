package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * them; four drawings tell them in about a second.
 *
 * The previews are the real ones, drawn by the same canvas that draws the picker
 * tiles, at [Layout.packPreviewHeight] rather than full size. Not mock-ups and
 * not screenshots: a still of a face would drift the first time someone edits
 * the drawing code, and it would be the marketing that went stale rather than
 * the product.
 *
 * The old card's concern — that previews cost animated canvases for something
 * the user cannot pick — still holds and is still handled, by the gallery's own
 * structure: each group is a separate lazy item, so only the packs actually on
 * screen compose. Four small canvases for a pack in view is the same cost as the
 * four large ones an owned pack already pays.
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

    ElmCard(cornerRadius = CornerRadius.Large) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(Layout.cardPadding)
                // One node for the card. Read as four separate previews, a name,
                // a count, a description and a face list, this is six stops for
                // something the user makes one decision about.
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(
                        name,
                        description,
                        countLabel,
                        faceNames,
                        price,
                    ).joinToString(", ")
                },
            verticalArrangement = Arrangement.spacedBy(Layout.rowGap),
        ) {
            PackPreviewStrip(group.faces)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                PackBadge(countLabel)
            }

            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                faceNames,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PackAction(
                owned = owned,
                sellable = sellable,
                price = price,
                onBuy = onBuy,
                onInstall = onInstall,
            )
        }
    }
}

/**
 * The everything-at-once offer.
 *
 * Given the accent container rather than the plain card surface, because it is
 * the one card on the screen making a different kind of claim: every other card
 * sells a pack, this one sells the decision not to choose. Shown only when at
 * least two packs are still unowned — see
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
                "$packNames — $countLabel",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            when {
                storefront.availability != BillingAvailability.AVAILABLE -> Text(
                    stringResource(R.string.settings_pack_unavailable_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                else -> ElmGradientButton(onClick = onBuy) {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.s18),
                    )
                    Spacer(Modifier.size(Spacing.s8))
                    Text(
                        text = storefront.allPacksPrice
                            ?.let { stringResource(R.string.settings_pack_all_buy, it) }
                            ?: stringResource(R.string.settings_pack_buy_no_price),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * The four faces, side by side, at pack-card scale.
 *
 * Silenced for screen readers: the drawings carry no information a blind user
 * can act on, and four unlabelled canvases between the pack's name and its price
 * is four stops of nothing. The card's own description names the faces instead.
 */
@Composable
private fun PackPreviewStrip(faces: List<ClockStyle>) {
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Layout.inlineGap),
    ) {
        faces.forEach { face ->
            Column(Modifier.weight(1f)) {
                WatchFacePreview(
                    style = face,
                    selected = false,
                    height = Layout.packPreviewHeight,
                )
            }
        }
    }
}

/** A small count or saving pill. */
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
 * was previously a caption-sized label in the corner of a header row — findable,
 * but nothing about it suggested it was the point of the screen.
 */
@Composable
private fun PackAction(
    owned: Boolean,
    sellable: Boolean,
    price: String?,
    onBuy: () -> Unit,
    onInstall: () -> Unit,
) {
    when {
        // Owned already — including every pack the user had when packs became
        // paid. Outlined rather than the gradient: adding something you own is
        // not the decision this screen is built around.
        owned -> ElmOutlinedButton(onClick = onInstall) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(Spacing.s18),
            )
            Spacer(Modifier.size(Spacing.s8))
            Text(stringResource(R.string.settings_pack_add), fontWeight = FontWeight.SemiBold)
        }

        sellable -> ElmGradientButton(onClick = onBuy) {
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(Spacing.s18),
            )
            Spacer(Modifier.size(Spacing.s8))
            Text(
                text = price?.let { stringResource(R.string.settings_pack_buy, it) }
                    ?: stringResource(R.string.settings_pack_buy_no_price),
                fontWeight = FontWeight.SemiBold,
            )
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
