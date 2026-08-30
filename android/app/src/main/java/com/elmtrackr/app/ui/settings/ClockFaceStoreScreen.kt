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
 * The screen is two labelled zones under one title. "Your faces" holds every
 * pack the user can pick from, bare on the background with an eyebrow label
 * per pack; "Face packs" holds the merchandise, each pack a raised product
 * card. A card, on this screen, always means something for sale — that one
 * rule is what makes ownership readable at a glance. Both zones keep
 * [ClockFaceGroup.entries] order, each pack its own lazy item so only visible
 * packs compose. Below the first product card, further packs collapse to a
 * single row and expand in place — the store stays scannable at five packs
 * and beyond.
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

    // One item body for a pack wherever it sits. The item is keyed on the
    // group, so when a purchase moves a pack from the store zone into the
    // owned zone the same item glides up and its AnimatedContent morphs the
    // product card into the picker — the transform reads in place even though
    // the pack crosses the section rule.
    fun androidx.compose.foundation.lazy.LazyListScope.packStoreItem(
        group: ClockFaceGroup,
        kind: PackCardKind,
        staggerIndex: Int?,
    ) {
        item(key = group.name) {
            AnimatedContent(
                targetState = kind,
                modifier = Modifier
                    .animateItem()
                    .then(
                        if (staggerIndex != null) {
                            Modifier.auroraEnter(staggerIndex)
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
                    PackCardKind.Picker -> OwnedPackSection(
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

                // The screen's one structural claim: everything above the
                // "Face packs" rule is yours to use, everything below it is
                // for sale. Ownership is a place on the screen, not a badge
                // the eye has to find and interpret.
                item(key = "yours-header") {
                    StoreSectionHeader(
                        label = stringResource(R.string.settings_face_store_yours_header),
                        modifier = Modifier
                            .animateItem()
                            .auroraEnter(1),
                    )
                }
                installed.forEachIndexed { index, group ->
                    packStoreItem(
                        group = group,
                        kind = PackCardKind.Picker,
                        staggerIndex = if (index == 0) 2 else null,
                    )
                }

                if (products.isNotEmpty()) {
                    item(key = "packs-header") {
                        StoreSectionHeader(
                            label = stringResource(R.string.settings_face_store_packs_header),
                            modifier = Modifier
                                .animateItem()
                                .padding(top = Spacing.s12),
                        )
                    }
                }
                products.forEachIndexed { index, group ->
                    packStoreItem(
                        group = group,
                        // The first product opens expanded — it is the zone's
                        // argument; the rest wait as collapsed rows until asked.
                        kind = if (index == 0 || group.name in expandedPacks) {
                            PackCardKind.Product
                        } else {
                            PackCardKind.Collapsed
                        },
                        staggerIndex = null,
                    )
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
 * yours · 20 in 5 packs" — and collapses to a single count once everything is
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
 * A section rule: a quiet tracked label with a hairline running to the edge.
 *
 * The store's clarity rests on these two rules. Everything under "Your faces"
 * is usable now; everything under "Face packs" is merchandise. Ownership is a
 * place on the screen rather than a badge, which is the difference between a
 * screen the user reads and a screen the user decodes.
 */
@Composable
private fun StoreSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = MaterialTheme.typography.labelSmall.fontSize * EYEBROW_TRACKING,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.auroraHeading(),
        )
        Spacer(Modifier.size(Spacing.s12))
        Box(
            Modifier
                .weight(1f)
                .height(Spacing.s1)
                .background(Color.White.copy(alpha = SectionRuleAlpha)),
        )
    }
}

/**
 * A pack the user has: a labelled group of faces to choose between, sitting
 * directly on the store background.
 *
 * Deliberately bare. On this screen a raised card means "product for sale",
 * and holding that rule is what makes the owned zone legible — the pack label
 * is an eyebrow over the tiles it owns, "Included" is part of that label
 * rather than a badge competing with product ribbons, and the only trailing
 * controls are the two that still apply: the In-use mark, or Remove.
 *
 * Selecting a face returns immediately: the tap answers the question the
 * screen asks, and the value still lands through the appearance screen's
 * normal unsaved-changes flow, so nothing is committed behind the user's back.
 */
@Composable
private fun OwnedPackSection(
    group: ClockFaceGroup,
    selected: ClockStyle,
    onSelect: (ClockStyle) -> Unit,
    onRemovePack: (ClockFaceGroup) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val holdsSelected = ClockFaceGroup.of(selected) == group
    val canRemove = ClockFacePacks.canRemove(group, selected)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val eyebrow = if (group.isBundled) {
                "${clockFaceGroupName(group)} · ${stringResource(R.string.settings_pack_included)}"
            } else {
                clockFaceGroupName(group)
            }
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = MaterialTheme.typography.labelSmall.fontSize * EYEBROW_TRACKING,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .weight(1f)
                    .auroraHeading(),
            )
            when {
                // Holding the face on the dashboard right now. Says so instead
                // of offering a Remove that would have to fail.
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
                // Remove is a text control, never a button — housekeeping, not
                // the decision this screen is built around.
                canRemove -> TextButton(
                    onClick = { onRemovePack(group) },
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

/** Matches the CSS spec's 0.16em eyebrow tracking. */
private const val EYEBROW_TRACKING = 0.16f

/** The hairline that runs from a section label to the screen edge. */
private const val SectionRuleAlpha = 0.10f

/** The spec's purchase-landing curve — cubic-bezier(.3,.8,.3,1). */
private val ThunkEasing = CubicBezierEasing(0.3f, 0.8f, 0.3f, 1f)
private const val TRANSFORM_FADE_MILLIS = 300
private const val TRANSFORM_SETTLE_MILLIS = 1_050
private const val TRANSFORM_START_SCALE = 0.96f
