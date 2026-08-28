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
import androidx.compose.material.icons.filled.DeleteOutline
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
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.auroraHeading
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
            // A pack the user has is a picker: a header and four faces to choose
            // between. A pack they do not is a product, and gets a product's
            // shape instead — the two are different jobs and looked identical
            // when both were a header over a grey box.
            if (group in availablePacks) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    InstalledPackHeader(
                        group = group,
                        canRemove = ClockFacePacks.canRemove(group, selected),
                        onRemove = { onRemovePack(group) },
                    )
                    ClockFaceGrid(
                        faces = group.faces,
                        selected = selected,
                        onSelect = {
                            onSelect(it)
                            onBack()
                        },
                    )
                }
            } else {
                ClockFacePackOfferCard(
                    group = group,
                    price = storefront.priceOf(group),
                    owned = storefront.isOwned(group),
                    sellable = storefront.availability == BillingAvailability.AVAILABLE,
                    onBuy = { onBuyPack(group) },
                    onInstall = { onInstallPack(group) },
                )
            }
        }
        // Offered after the packs, not before them: someone who has just seen what
        // four packs look like is in a position to judge whether all of them is a
        // better deal, and someone who only wanted one should not have to scroll
        // past a bigger price to reach it.
        if (storefront.offerAllPacks) {
            item {
                AllClockFacePacksCard(storefront = storefront, onBuy = onBuyAllPacks)
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

/**
 * The name and description above an installed pack's faces, with the one action
 * that still applies to it.
 *
 * Buy, add and unavailable all left with the offer card: a pack that is on the
 * screen as a grid of choosable faces is past every one of those states, and
 * carrying them here meant a five-branch `when` where four branches described a
 * pack this header was never shown for.
 */
@Composable
private fun InstalledPackHeader(
    group: ClockFaceGroup,
    canRemove: Boolean,
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
