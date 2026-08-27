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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ShoppingCart
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
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.billing.ClockFacePackProducts
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Every clock face, grouped, with the packs the user does not have offered rather
 * than hidden.
 *
 * Split off the appearance screen rather than expanded in place. Twenty faces is
 * a browsing task, and browsing wants its own surface: room for group names, and a
 * back gesture that returns you where you were instead of leaving you scrolled
 * halfway down a settings page.
 *
 * Each group is its own lazy item, so a group's four animated previews compose when
 * it scrolls into view. The flat grid this replaced lived in one item, which meant
 * every preview — and every one of their infinite animations — composed the
 * moment the screen opened.
 *
 * Selecting a face returns immediately: the tap answers the question the screen
 * asks, and the value still lands through the appearance screen's normal
 * unsaved-changes flow, so nothing is committed behind the user's back. Adding,
 * buying or removing a pack does *not* return — it changes what is on this screen,
 * so the result should be visible here.
 *
 * The screen shows three different things about a pack the user does not have —
 * buy it, add it, or it cannot be sold here — and takes all three from
 * [storefront] rather than working any of them out itself. Ownership is Play's
 * answer, and a screen that second-guessed it would be a screen that can hand out
 * a pack nobody paid for.
 */
@Composable
internal fun ClockFaceGalleryScreen(
    selected: ClockStyle,
    availablePacks: Set<ClockFaceGroup>,
    storefront: ClockFacePackStorefront,
    onSelect: (ClockStyle) -> Unit,
    onInstallPack: (ClockFaceGroup) -> Unit,
    onBuyPack: (ClockFaceGroup) -> Unit,
    onBuyAllPacks: () -> Unit,
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
            val owned = storefront.isOwned(group)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ClockFaceGroupHeader(
                    group = group,
                    installed = installed,
                    owned = owned,
                    price = storefront.priceOf(group),
                    sellable = storefront.availability == BillingAvailability.AVAILABLE,
                    canRemove = ClockFacePacks.canRemove(group, selected),
                    onInstall = { onInstallPack(group) },
                    onBuy = { onBuyPack(group) },
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
                    ClockFacePackTeaser(group = group, owned = owned)
                }
            }
        }
        // Offered after the packs, not before them: someone who has just read what
        // four packs contain is in a position to judge whether all of them is a
        // better deal, and someone who only wanted one should not have to scroll
        // past a bigger price to reach it.
        if (!storefront.everythingOwned) {
            item {
                AllPacksOffer(
                    price = storefront.allPacksPrice,
                    availability = storefront.availability,
                    onBuy = onBuyAllPacks,
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun ClockFaceGroupHeader(
    group: ClockFaceGroup,
    installed: Boolean,
    owned: Boolean,
    price: String?,
    sellable: Boolean,
    canRemove: Boolean,
    onInstall: () -> Unit,
    onBuy: () -> Unit,
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

            // Owned but not added — including every pack the user already had when
            // packs became paid. Nothing to charge for, so this is the same Add
            // button it has always been.
            !installed && owned -> TextButton(
                onClick = onInstall,
                modifier = Modifier.padding(start = Spacing.s8),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(Spacing.s18))
                Spacer(Modifier.size(Spacing.s4))
                Text(stringResource(R.string.settings_pack_add), fontWeight = FontWeight.SemiBold)
            }

            // Not owned, and Play can sell it. The price comes from Play so it is
            // the price actually charged, in the user's own currency; the button
            // still works before it arrives rather than sitting disabled, because
            // Play's own sheet states the price again before any money moves.
            !installed && sellable -> TextButton(
                onClick = onBuy,
                modifier = Modifier.padding(start = Spacing.s8),
            ) {
                Icon(
                    Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(Spacing.s18),
                )
                Spacer(Modifier.size(Spacing.s4))
                Text(
                    text = price?.let { stringResource(R.string.settings_pack_buy, it) }
                        ?: stringResource(R.string.settings_pack_buy_no_price),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // Not owned and not sellable here: no Play services, the Store
            // disabled, or the product not sold in this country. Says so plainly
            // instead of showing a Buy button that could only fail.
            !installed -> Text(
                stringResource(R.string.settings_pack_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.s8),
            )

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
 *
 * The line underneath is the only difference between an unowned pack and one that
 * is merely not added, so it carries the whole of "this costs money" — which is
 * why it says one-time purchase rather than anything vaguer.
 */
@Composable
private fun ClockFacePackTeaser(group: ClockFaceGroup, owned: Boolean) {
    ElmCard(
        cornerRadius = CornerRadius.Medium,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                text = if (owned) {
                    stringResource(R.string.settings_pack_not_added, group.faces.size)
                } else {
                    stringResource(R.string.settings_pack_locked, group.faces.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The everything-at-once offer.
 *
 * Sold as a product of its own rather than as a discount applied at checkout,
 * because Play has no concept of the latter for one-time products. It grants
 * packs added in later versions too — a promise the app has to keep in
 * [ClockFacePackProducts.packsGrantedBy], and the reason the wording says so
 * rather than naming a count of packs that will change.
 */
@Composable
private fun AllPacksOffer(
    price: String?,
    availability: BillingAvailability,
    onBuy: () -> Unit,
) {
    ElmCard(cornerRadius = CornerRadius.Medium) {
        Column(
            Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                stringResource(R.string.settings_pack_all_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.auroraHeading(),
            )
            Text(
                stringResource(
                    R.string.settings_pack_all_desc,
                    ClockFacePackProducts.purchasablePacks.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                // Priced and sellable. The price is Play's, never the app's.
                availability == BillingAvailability.AVAILABLE && price != null -> TextButton(
                    onClick = onBuy,
                ) {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.s18),
                    )
                    Spacer(Modifier.size(Spacing.s4))
                    Text(
                        stringResource(R.string.settings_pack_all_buy, price),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Play is reachable but has not answered yet. A button with no
                // price is better than a placeholder price, which would be a
                // number the app made up.
                availability == BillingAvailability.AVAILABLE -> TextButton(onClick = onBuy) {
                    Text(
                        stringResource(R.string.settings_pack_buy_no_price),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                availability == BillingAvailability.UNAVAILABLE -> Text(
                    stringResource(R.string.settings_pack_unavailable_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Still connecting. Nothing is claimed either way until it is known.
                else -> Unit
            }
        }
    }
}
