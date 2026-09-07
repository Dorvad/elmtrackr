package com.elmtrackr.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.ClockFacePackStorefront
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.ui.common.findActivity
import com.elmtrackr.app.ui.design.ElmSegmentedPillRow
import com.elmtrackr.app.ui.design.auroraEnter
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.design.mirrorInRtl
import com.elmtrackr.app.ui.layout.PhoneContentMaxWidth
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics
import kotlinx.coroutines.delay

/**
 * The clock face store: what you have on one tab, what is for sale on the
 * other.
 *
 * Ownership was a scroll position in the previous design and readers kept
 * losing it. It is now a *mode*, the way established stores separate a library
 * from a storefront: **Your faces** holds every pack the user can pick from —
 * bare grids with an eyebrow label per pack, selection returning through the
 * appearance screen's normal unsaved-changes flow — and **Shop** holds the
 * merchandise, every pack a product card whose price chip is always visible
 * and is itself the buy button. Nothing in the Shop is owned; nothing in Your
 * faces is for sale.
 *
 * The store forces the dark theme whatever the app is set to — the faces are
 * drawn glowing, and a showroom is lit for the merchandise — which is also why
 * its contrast pairs are asserted once in `DarkThemeContrastTest` rather than
 * per theme. It is also drawn edge to edge: the settings host hands it the
 * whole window (see `SettingsScreen`'s immersive flag), it paints its own
 * background under both system bars and pads for them itself, and it sets the
 * bars' icons light for as long as it is up. A dark shop with a strip of the
 * light app showing above it and the bottom bar below was a shop with the
 * street visible through the roof.
 *
 * Every claim about ownership, price or availability is read from
 * [storefront]. The store never decides a purchase happened — Play is the
 * record, and a screen that second-guessed it would be a screen that can hand
 * out a pack nobody paid for. When a purchase lands, the pack leaves the shelf
 * and appears under Your faces; the success strip on the Shop tab names what
 * arrived and offers the way over.
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
    isRestoring: Boolean = false,
    onDismissUnlocked: () -> Unit,
    onBack: () -> Unit,
    // The screenshot suite opens straight onto the shelf; users always land
    // on their own faces first.
    startInShop: Boolean = false,
) {
    val installed = ClockFaceGroup.entries.filter { it in availablePacks }
    // New arrivals lead the shelf — the one merchandising reorder the store
    // allows itself. The sort is stable, so everything else keeps catalog
    // order, and once the ribbon expires the shelf settles back on its own.
    val products = ClockFaceGroup.entries
        .filter { it !in availablePacks }
        .sortedByDescending { it.isNewIn(appVersion) }
    var inShop by rememberSaveable { mutableStateOf(startInShop) }
    // Read here, not in transitionSpec: spec lambdas run outside composition.
    val motionEnabled = auroraMotionEnabled()
    // The full-screen look, hoisted above the tabs so it can cover the whole
    // store, and the page each card's hero should return to when it closes.
    var look by remember { mutableStateOf<Pair<ClockFaceGroup, Int>?>(null) }
    val heroPages = remember { mutableStateMapOf<ClockFaceGroup, Int>() }
    // A pack bought from inside its own look has nothing left to look at.
    LaunchedEffect(availablePacks) {
        if (look?.first in availablePacks) look = null
    }
    StoreSystemBars()

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
            Column(
                Modifier
                    .widthIn(max = PhoneContentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Layout.screenGutter),
            ) {
                StoreHeader(
                    availablePacks = availablePacks,
                    onRestore = onRestore,
                    isRestoring = isRestoring,
                    onBack = onBack,
                    modifier = Modifier.auroraEnter(0),
                )
                ElmSegmentedPillRow(
                    options = listOf(
                        stringResource(R.string.settings_face_store_yours_header),
                        stringResource(R.string.settings_face_store_packs_header),
                    ),
                    selectedIndex = if (inShop) 1 else 0,
                    onSelect = { inShop = it == 1 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraEnter(1),
                )
                AnimatedContent(
                    targetState = inShop,
                    transitionSpec = {
                        if (motionEnabled) {
                            fadeIn(tween(TAB_FADE_MILLIS)) togetherWith fadeOut(tween(TAB_FADE_MILLIS))
                        } else {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        }
                    },
                    label = "store-tab",
                ) { shop ->
                    if (shop) {
                        ShopTab(
                            products = products,
                            storefront = storefront,
                            justUnlocked = justUnlocked,
                            appVersion = appVersion,
                            heroPages = heroPages,
                            onBuyPack = onBuyPack,
                            onInstallPack = onInstallPack,
                            onBuyAllPacks = onBuyAllPacks,
                            onOpenLook = { group, page -> look = group to page },
                            onOpenYourFaces = { inShop = false },
                        )
                    } else {
                        YourFacesTab(
                            installed = installed,
                            products = products,
                            selected = selected,
                            onSelect = onSelect,
                            onRemovePack = onRemovePack,
                            onOpenShop = { inShop = true },
                            onBack = onBack,
                        )
                    }
                }
            }
            look?.let { (group, page) ->
                ClockFaceLookScreen(
                    group = group,
                    initialPage = page,
                    price = storefront.priceOf(group),
                    owned = storefront.isOwned(group),
                    availability = storefront.availability,
                    onBuy = { onBuyPack(group) },
                    onInstall = { onInstallPack(group) },
                    onClose = { settledAt ->
                        heroPages[group] = settledAt
                        look = null
                    },
                )
            }
        }
    }
}

/**
 * Light status and navigation bar icons while the store is up, whatever the
 * app's theme: the store is always dark, and dark icons over it vanish. The
 * previous appearance goes back on the way out.
 */
@Composable
private fun StoreSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
            ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

/**
 * Everything the user can pick from, and one quiet pointer to the shelf.
 *
 * Selecting a face returns immediately: the tap answers the question the
 * screen asks, and the value still lands through the appearance screen's
 * normal unsaved-changes flow, so nothing is committed behind the user's back.
 */
@Composable
private fun YourFacesTab(
    installed: List<ClockFaceGroup>,
    products: List<ClockFaceGroup>,
    selected: ClockStyle,
    onSelect: (ClockStyle) -> Unit,
    onRemovePack: (ClockFaceGroup) -> Unit,
    onOpenShop: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Layout.cardGap),
    ) {
        item(key = "top-gap") { Spacer(Modifier.height(Spacing.s4)) }
        installed.forEach { group ->
            item(key = group.name) {
                OwnedPackSection(
                    group = group,
                    selected = selected,
                    onSelect = onSelect,
                    onRemovePack = onRemovePack,
                    onBack = onBack,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (products.isNotEmpty()) {
            item(key = "shop-teaser") {
                ShopTeaserRow(
                    moreFaces = products.sumOf { it.faces.size },
                    onOpenShop = onOpenShop,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        // Under the navigation bar, plus a breath: the store draws edge to edge.
        item(key = "inset") { Spacer(Modifier.navigationBarsPadding().height(Spacing.s24)) }
    }
}

/**
 * The shelf. Product cards in catalog order, the bundle last, and — right
 * after a purchase — the success strip leading back to Your faces. When
 * nothing is left to sell, the shelf says so instead of standing empty.
 *
 * [heroPages] carries, per pack, the page the full-screen look was closed on,
 * so a card's hero lands back on the face that was being looked at.
 */
@Composable
private fun ShopTab(
    products: List<ClockFaceGroup>,
    storefront: ClockFacePackStorefront,
    justUnlocked: Set<ClockFaceGroup>,
    appVersion: String,
    heroPages: Map<ClockFaceGroup, Int>,
    onBuyPack: (ClockFaceGroup) -> Unit,
    onInstallPack: (ClockFaceGroup) -> Unit,
    onBuyAllPacks: () -> Unit,
    onOpenLook: (ClockFaceGroup, Int) -> Unit,
    onOpenYourFaces: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Layout.cardGap),
    ) {
        item(key = "top-gap") { Spacer(Modifier.height(Spacing.s4)) }
        if (justUnlocked.isNotEmpty()) {
            item(key = "unlocked") {
                PackUnlockedStrip(
                    packs = justUnlocked,
                    onOpenYourFaces = onOpenYourFaces,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        products.forEach { group ->
            item(key = group.name) {
                ClockFacePackOfferCard(
                    group = group,
                    price = storefront.priceOf(group),
                    owned = storefront.isOwned(group),
                    availability = storefront.availability,
                    isNew = group.isNewIn(appVersion),
                    heroPage = heroPages[group],
                    onBuy = { onBuyPack(group) },
                    onInstall = { onInstallPack(group) },
                    onOpenLook = { page -> onOpenLook(group, page) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        // Offered after the packs, not before them: someone who has just
        // seen what the packs look like can judge whether all of them is a
        // better deal, and someone who only wanted one should not have to
        // scroll past a bigger price to reach it.
        if (storefront.offerAllPacks) {
            item(key = "bundle") {
                AllClockFacePacksCard(storefront = storefront, onBuy = onBuyAllPacks)
            }
        }
        if (products.isEmpty()) {
            item(key = "everything-owned") {
                Text(
                    stringResource(R.string.settings_face_store_everything_owned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s24),
                )
            }
        }
        // Under the navigation bar, plus a breath: the store draws edge to edge.
        item(key = "inset") { Spacer(Modifier.navigationBarsPadding().height(Spacing.s24)) }
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
 * file when they cannot find it. Anything it recovers is added straight away
 * and named on the unlock strip; when there was nothing missing it says so in
 * the snackbar, because a restore that reports nothing at all is a restore the
 * user has no reason to believe ran.
 */
@Composable
private fun StoreHeader(
    availablePacks: Set<ClockFaceGroup>,
    onRestore: () -> Unit,
    isRestoring: Boolean,
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
            // Disabled while it runs, and it says which it is. Play is given
            // twenty seconds to answer, and a control that shows nothing for
            // that long reads as broken — the user taps it again, and each tap
            // would queue another query.
            TextButton(onClick = onRestore, enabled = !isRestoring) {
                Text(
                    stringResource(
                        if (isRestoring) {
                            R.string.settings_face_store_restoring
                        } else {
                            R.string.settings_face_store_restore
                        },
                    ),
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
            modifier = Modifier.padding(top = Spacing.s4, bottom = Spacing.s12),
        )
    }
}

/**
 * A pack the user has: a labelled group of faces to choose between, sitting
 * directly on the store background.
 *
 * Deliberately bare — on this screen a raised card means "product for sale",
 * and the two never share a tab. The pack label is an eyebrow over the tiles
 * it owns, "Included" is part of that label, and the only trailing controls
 * are the two that still apply: the In-use mark, or Remove.
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
 * The one line on Your faces that admits a shop exists.
 *
 * Quiet on purpose: the picking tab is not a sales surface, but someone who
 * came to browse should not have to discover the second tab on their own.
 */
@Composable
private fun ShopTeaserRow(
    moreFaces: Int,
    onOpenShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(CornerRadius.Large)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = TeaserLift))
            .border(Spacing.s1, Color.White.copy(alpha = HairlineAlpha), shape)
            .clickable(onClick = onOpenShop)
            .heightIn(min = Layout.minTouchTarget)
            .padding(horizontal = Layout.cardPadding, vertical = Spacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.settings_face_store_teaser, moreFaces, moreFaces),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Spacing.s20).mirrorInRtl(),
        )
    }
}

/**
 * The inline confirmation a purchase leaves behind, on the shelf where the
 * purchase happened.
 *
 * The pack itself has already moved to Your faces — a sold pack does not stay
 * on sale — so the strip names what arrived and is itself the way over. It
 * never picks a face for the user: they bought four, and choosing one for
 * them would undo the choice they came to make.
 */
@Composable
private fun PackUnlockedStrip(
    packs: Set<ClockFaceGroup>,
    onOpenYourFaces: () -> Unit,
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
            .clip(shape)
            .background(auroraSemantics.successContainer)
            .border(Spacing.s1, auroraSemantics.successInk.copy(alpha = .28f), shape)
            .clickable(onClick = onOpenYourFaces)
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = auroraSemantics.successInk,
            modifier = Modifier.size(Spacing.s20).mirrorInRtl(),
        )
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

/** How long the just-unlocked strip stays before dismissing itself. */
private const val UNLOCK_STRIP_MILLIS = 6_000L

/** Matches the CSS spec's 0.16em eyebrow tracking. */
private const val EYEBROW_TRACKING = 0.16f

/** The hairline border the teaser row wears. */
private const val HairlineAlpha = 0.07f

/** The lift that separates the teaser row from the store background. */
private const val TeaserLift = 0.04f

private const val TAB_FADE_MILLIS = 180
