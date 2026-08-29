package com.elmtrackr.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics
import kotlinx.coroutines.delay

/**
 * The clock face store: every face the user has, and every pack they could
 * have, on one dark surface.
 *
 * Replaces the gallery as the browse surface. The store forces the dark theme
 * whatever the app is set to — the faces are drawn glowing, and a showroom is
 * lit for the merchandise — which is also why its contrast pairs are asserted
 * once in `DarkThemeContrastTest` rather than per theme.
 *
 * Owned packs come first, as pickers; packs the user does not have follow as
 * product cards, in [ClockFaceGroup.entries] order, each its own lazy item so
 * only visible packs compose. Below the first product card, further packs
 * collapse to a single row and expand in place — the store stays scannable at
 * five packs and beyond.
 *
 * Two rules the flow must not break, inherited from the gallery it replaces:
 * selecting a face returns (the tap answers the question the screen asks, and
 * the value lands through the appearance screen's normal unsaved-changes
 * flow), while buying does not — a purchase changes what is on this screen, so
 * the result must be visible here: the card transforms in place into a picker,
 * and the success strip above the list says what arrived.
 *
 * Every claim about ownership, price or availability is read from
 * [storefront]. The store never decides a purchase happened — Play is the
 * record, and a screen that second-guessed it would be a screen that can hand
 * out a pack nobody paid for.
 */
@Composable
internal fun ClockFaceStoreScreen(
    selected: ClockStyle,
    availablePacks: Set<ClockFaceGroup>,
    storefront: ClockFacePackStorefront,
    justUnlocked: Set<ClockFaceGroup>,
    appVersion: String,
    onSelect: (ClockStyle) -> Unit,
    onInstallPack: (ClockFaceGroup) -> Unit,
    onBuyPack: (ClockFaceGroup) -> Unit,
    onBuyAllPacks: () -> Unit,
    onRemovePack: (ClockFaceGroup) -> Unit,
    onRestore: () -> Unit,
    onDismissUnlocked: () -> Unit,
    onBack: () -> Unit,
) {
    val installed = ClockFaceGroup.entries.filter { it in availablePacks }
    val products = ClockFaceGroup.entries.filter { it !in availablePacks }
    // Which below-the-fold packs the user has opened. The first product is
    // always expanded — it is the screen's argument — and a purchased pack
    // stops being a product, so the list never needs pruning.
    var expandedPacks by rememberSaveable { mutableStateOf(listOf<String>()) }
    val motionEnabled = auroraMotionEnabled()

    // The success strip says what a purchase just added, then leaves on its
    // own. Six seconds is enough to read; anything longer is a banner.
    LaunchedEffect(justUnlocked) {
        if (justUnlocked.isNotEmpty()) {
            delay(UNLOCK_STRIP_MILLIS)
            onDismissUnlocked()
        }
    }

    ElmTrackrTheme(darkTheme = true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            StoreGlow(Modifier.fillMaxSize())
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Layout.screenGutter),
                verticalArrangement = Arrangement.spacedBy(Layout.cardGap),
            ) {
                item(key = "header") {
                    StoreHeader(
                        availablePacks = availablePacks,
                        onRestore = onRestore,
                        onBack = onBack,
                        modifier = Modifier.auroraEnter(0),
                    )
                }

                if (justUnlocked.isNotEmpty()) {
                    item(key = "unlocked") {
                        PackUnlockedStrip(packs = justUnlocked)
                    }
                }

                // One pass over every group, owned first. Each item renders
                // whichever of the three card shapes its state calls for, and
                // AnimatedContent is what turns a purchase into the in-place
                // transform: the product card's pager crossfades into the
                // picker grid with a small settle, and nothing navigates away.
                (installed + products).forEachIndexed { index, group ->
                    item(key = group.name) {
                        val kind = when {
                            group in availablePacks -> PackCardKind.Picker
                            products.firstOrNull() == group ||
                                group.name in expandedPacks -> PackCardKind.Product
                            else -> PackCardKind.Collapsed
                        }
                        AnimatedContent(
                            targetState = kind,
                            // The stagger ladder covers the first cards on
                            // screen; everything below the fold enters plain.
                            // animateItem keeps a card that changes rank after
                            // a purchase gliding rather than jumping.
                            modifier = Modifier
                                .animateItem()
                                .then(
                                    if (index < ENTER_STAGGER_COUNT) {
                                        Modifier.auroraEnter(index + 1)
                                    } else {
                                        Modifier
                                    },
                                ),
                            transitionSpec = {
                                if (motionEnabled) {
                                    (
                                        fadeIn(tween(TRANSFORM_FADE_MILLIS)) +
                                            scaleIn(
                                                initialScale = TRANSFORM_START_SCALE,
                                                animationSpec = tween(
                                                    TRANSFORM_SETTLE_MILLIS,
                                                    easing = ThunkEasing,
                                                ),
                                            )
                                        ) togetherWith fadeOut(tween(TRANSFORM_FADE_MILLIS))
                                } else {
                                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                }
                            },
                            label = "pack-card-${group.name}",
                        ) { shape ->
                            when (shape) {
                                PackCardKind.Picker -> InstalledPackSection(
                                    group = group,
                                    selected = selected,
                                    onSelect = onSelect,
                                    onRemovePack = onRemovePack,
                                    onBack = onBack,
                                )
                                PackCardKind.Product -> ClockFacePackOfferCard(
                                    group = group,
                                    price = storefront.priceOf(group),
                                    owned = storefront.isOwned(group),
                                    availability = storefront.availability,
                                    isNew = group.isNewIn(appVersion),
                                    onBuy = { onBuyPack(group) },
                                    onInstall = { onInstallPack(group) },
                                )
                                PackCardKind.Collapsed -> ClockFacePackCollapsedRow(
                                    group = group,
                                    price = storefront.priceOf(group),
                                    owned = storefront.isOwned(group),
                                    onExpand = { expandedPacks = expandedPacks + group.name },
                                )
                            }
                        }
                    }
                }

                // Offered after the packs, not before them: someone who has just
                // seen what four packs look like can judge whether all of them is
                // a better deal, and someone who only wanted one should not have
                // to scroll past a bigger price to reach it.
                if (storefront.offerAllPacks) {
                    item(key = "bundle") {
                        AllClockFacePacksCard(storefront = storefront, onBuy = onBuyAllPacks)
                    }
                }

                item(key = "inset") { Spacer(Modifier.height(Layout.bottomNavInset)) }
            }
        }
    }
}

/**
 * Back, restore, the store's name and what it holds.
 *
 * The subtitle counts what the user has against what is left to want — "4
 * yours · 16 in 4 packs" — and collapses to a single count once everything is
 * theirs, because "0 in 0 packs" is a store admitting it has nothing to sell.
 *
 * Restore re-reads ownership from Play. The refresh also runs on every
 * foreground, so the button is mostly reassurance — but reassurance is what a
 * user who just reinstalled is looking for, and a support ticket is what they
 * file when they cannot find it.
 */
@Composable
private fun StoreHeader(
    availablePacks: Set<ClockFaceGroup>,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yoursCount = availablePacks.sumOf { it.faces.size }
    val lockedGroups = ClockFaceGroup.entries.filterNot { it in availablePacks }
    val lockedFaces = lockedGroups.sumOf { it.faces.size }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.s8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRestore) {
                Text(
                    stringResource(R.string.settings_face_store_restore),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = MaterialTheme.typography.labelSmall.fontSize * EYEBROW_TRACKING,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            stringResource(R.string.settings_face_store_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.auroraHeading(),
        )
        Text(
            if (lockedGroups.isEmpty()) {
                stringResource(R.string.settings_face_store_count_all_owned, yoursCount)
            } else {
                pluralStringResource(
                    R.plurals.settings_face_store_count,
                    lockedGroups.size,
                    yoursCount,
                    lockedFaces,
                    lockedGroups.size,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = Spacing.s4, bottom = Spacing.s8),
        )
    }
}

/**
 * A pack the user has: a header and four faces to choose between.
 *
 * The bundled pack sits directly on the store background — it is the floor,
 * not merchandise — while purchased packs keep the card surface their product
 * shot arrived on, so buying visibly turns the product into the same kind of
 * picker the user already knows.
 *
 * Selecting a face returns immediately: the tap answers the question the
 * screen asks, and the value still lands through the appearance screen's
 * normal unsaved-changes flow, so nothing is committed behind the user's back.
 */
@Composable
private fun InstalledPackSection(
    group: ClockFaceGroup,
    selected: ClockStyle,
    onSelect: (ClockStyle) -> Unit,
    onRemovePack: (ClockFaceGroup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            InstalledPackHeader(
                group = group,
                holdsSelected = ClockFaceGroup.of(selected) == group,
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
    }
    if (group.isBundled) {
        Box(modifier) { content() }
    } else {
        PackCardSurface(modifier) { content() }
    }
}

/**
 * The name and description above an installed pack's faces, with the one thing
 * that still applies to it.
 *
 * Remove is a text control, never a button — removing is housekeeping, not the
 * decision this screen is built around. It disappears when the pack holds the
 * selected face; the header says why ("In use") instead of offering a control
 * that would have to fail. The bundled pack says "Included" and offers nothing:
 * there is nothing the user could do with it, and a greyed control only
 * invites the question.
 */
@Composable
private fun InstalledPackHeader(
    group: ClockFaceGroup,
    holdsSelected: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clockFaceGroupName(group),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.auroraHeading(),
                )
                if (group.isBundled) {
                    PackBadge(stringResource(R.string.settings_pack_included))
                }
            }
            Text(
                clockFaceGroupDescription(group),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        when {
            // Holding the face on the dashboard right now. Says so instead of
            // offering a Remove that would have to fail.
            holdsSelected -> Text(
                stringResource(R.string.settings_pack_in_use),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = auroraSemantics.successInk,
                modifier = Modifier
                    .padding(start = Spacing.s8)
                    .background(
                        auroraSemantics.successContainer,
                        RoundedCornerShape(CornerRadius.Chip),
                    )
                    .padding(horizontal = Spacing.s8, vertical = Spacing.s4),
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
            else -> Unit
        }
    }
}

/**
 * The inline confirmation a purchase leaves behind.
 *
 * A strip in the list rather than a snackbar, so it survives the scroll the
 * new grid invites. It never picks a face for the user — they bought four, and
 * choosing one for them would undo the choice they came to make.
 */
@Composable
private fun PackUnlockedStrip(
    packs: Set<ClockFaceGroup>,
    modifier: Modifier = Modifier,
) {
    val names = ClockFaceGroup.entries
        .filter { it in packs }
        .map { clockFaceGroupName(it) }
        .joinToString(" · ")
    val faceCount = packs.sumOf { it.faces.size }
    val shape = RoundedCornerShape(CornerRadius.Medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(auroraSemantics.successContainer, shape)
            .border(Spacing.s1, auroraSemantics.successInk.copy(alpha = .28f), shape)
            .padding(horizontal = Layout.cardPadding, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s12),
    ) {
        Box(
            Modifier
                .size(Spacing.s24)
                .background(auroraSemantics.successInk, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(Spacing.s16),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_pack_added, names),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = auroraSemantics.successInk,
            )
            Text(
                pluralStringResource(R.plurals.settings_pack_added_hint, faceCount, faceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The two soft glows that keep the store from being a flat black sheet.
 *
 * Static, deliberately: the background is the one place the store draws no
 * attention, and every moving thing on this screen is merchandise.
 */
@Composable
private fun StoreGlow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuroraIndigo.copy(alpha = .30f), Color.Transparent),
                center = Offset(size.width * .12f, size.height * .02f),
                radius = size.width * .72f,
            ),
            radius = size.width * .72f,
            center = Offset(size.width * .12f, size.height * .02f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AuroraPlum.copy(alpha = .18f), Color.Transparent),
                center = Offset(size.width * .98f, size.height * .32f),
                radius = size.width * .6f,
            ),
            radius = size.width * .6f,
            center = Offset(size.width * .98f, size.height * .32f),
        )
    }
}

/** The three shapes a pack takes on this screen. */
private enum class PackCardKind { Picker, Product, Collapsed }

/** How long the just-unlocked strip stays before dismissing itself. */
private const val UNLOCK_STRIP_MILLIS = 6_000L

/** How many list items ride the entrance stagger ladder. */
private const val ENTER_STAGGER_COUNT = 2

/** Matches the CSS spec's 0.16em eyebrow tracking. */
private const val EYEBROW_TRACKING = 0.16f

/** The spec's purchase-landing curve — cubic-bezier(.3,.8,.3,1). */
private val ThunkEasing = CubicBezierEasing(0.3f, 0.8f, 0.3f, 1f)
private const val TRANSFORM_FADE_MILLIS = 300
private const val TRANSFORM_SETTLE_MILLIS = 1_050
private const val TRANSFORM_START_SCALE = 0.96f
