package com.elmtrackr.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.billing.BillingAvailability
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmOutlinedButton
import com.elmtrackr.app.ui.design.auroraAnimationSpec
import com.elmtrackr.app.ui.design.auroraHeading
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.layout.PhoneContentMaxWidth
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Layout
import com.elmtrackr.app.ui.theme.Spacing

/**
 * The full-screen look: one face, as close to the dashboard's presentation as
 * the store can get without applying it — the face on its plate at the
 * dashboard's proportions, live, with its name and pitch beneath.
 *
 * Opened by tapping a hero on a pack card. This is a detail view, not a step
 * in a funnel: closing it returns to the pack card at whatever page was being
 * looked at, and the only action it offers is the same purchase the card
 * offers, restated with the face at full size making the argument.
 *
 * The entrance is the reveal: the face arrives blurred to suggestion and
 * sharpens over one short beat. Under reduced motion it simply appears sharp.
 *
 * Drawn as an overlay inside the store rather than as a dialog window. The
 * store is itself drawn edge to edge, so an overlay inherits that for free,
 * where a dialog window stopped at the system bars and let the screen beneath
 * show through them. The pack card underneath keeps its state, and the system
 * back gesture closes the look like any other transient surface.
 */
@Composable
internal fun ClockFaceLookScreen(
    group: ClockFaceGroup,
    initialPage: Int,
    price: String?,
    owned: Boolean,
    availability: BillingAvailability,
    onBuy: () -> Unit,
    onClose: (settledAt: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, group.faces.lastIndex),
    ) { group.faces.size }
    val packName = clockFaceGroupName(group)
    BackHandler { onClose(pagerState.settledPage) }

    // The reveal: blur and scale settle over one short beat once the look is
    // up. auroraAnimationSpec collapses both to a snap when motion is reduced.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val revealBlur by animateDpAsState(
        targetValue = if (revealed) 0.dp else Layout.packVeilBlur,
        animationSpec = auroraAnimationSpec(REVEAL_MILLIS),
        label = "face-look-reveal-blur",
    )
    val revealScale by animateFloatAsState(
        targetValue = if (revealed) 1f else REVEAL_START_SCALE,
        animationSpec = auroraAnimationSpec(REVEAL_MILLIS),
        label = "face-look-reveal-scale",
    )
    val heroShape = RoundedCornerShape(CornerRadius.Large)

    // A Surface, so touches stop here instead of reaching the shelf beneath.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = PhoneContentMaxWidth)
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = Layout.screenGutter),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.settings_face_look_position,
                            packName,
                            pagerState.settledPage + 1,
                            group.faces.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = MaterialTheme.typography.labelSmall.fontSize * EYEBROW_TRACKING,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onClose(pagerState.settledPage) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    pageSpacing = Layout.cardGap,
                ) { page ->
                    val face = group.faces[page]
                    val settled = pagerState.settledPage == page
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = revealScale
                                    scaleY = revealScale
                                },
                        ) {
                            if (settled) {
                                LookGlow(Modifier.matchParentSize())
                            }
                            WatchFacePreview(
                                style = face,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ClockFaceAspect)
                                    .blur(revealBlur)
                                    .clearAndSetSemantics { },
                                animate = settled,
                                shape = heroShape,
                                plate = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }

                        Text(
                            clockStyleDisplayName(face),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = Spacing.s24)
                                .auroraHeading(),
                        )
                        Text(
                            watchFaceDescription(face),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(top = Spacing.s10)
                                .widthIn(max = LOOK_DESCRIPTION_MAX_WIDTH),
                        )
                    }
                }

                LookPagerDots(
                    count = group.faces.size,
                    current = pagerState.settledPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = Spacing.s20),
                )

                LookAction(
                    packName = packName,
                    price = price,
                    owned = owned,
                    availability = availability,
                    onBuy = onBuy,
                )
                Text(
                    stringResource(R.string.settings_pack_unlock_note, group.faces.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.s12, bottom = Spacing.s24),
                )
            }
        }
    }
}

/**
 * The one action the look offers, mirroring the card's three-way state.
 *
 * The buy case restates the face pack and the price in the label — this screen
 * exists to justify one purchase, so the button says exactly what it commits
 * to. Everything else behaves as on the card: owned adds, unavailable explains.
 */
@Composable
private fun LookAction(
    packName: String,
    price: String?,
    owned: Boolean,
    availability: BillingAvailability,
    onBuy: () -> Unit,
) {
    when {
        owned -> ElmOutlinedButton(onClick = onBuy) {
            Text(stringResource(R.string.settings_pack_add), fontWeight = FontWeight.SemiBold)
        }
        availability == BillingAvailability.UNAVAILABLE -> Text(
            stringResource(R.string.settings_pack_unavailable_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> ElmGradientButton(
            onClick = onBuy,
            enabled = availability == BillingAvailability.AVAILABLE,
        ) {
            Text(
                if (price != null) {
                    stringResource(R.string.settings_pack_unlock, packName, price)
                } else {
                    stringResource(R.string.settings_pack_unlock_unpriced, packName)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The plum halo behind the looked-at face — the hero glow, scaled up. */
@Composable
private fun LookGlow(modifier: Modifier = Modifier) {
    val breathe = if (auroraMotionEnabled()) {
        val transition = rememberInfiniteTransition(label = "face-look-glow")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(GLOW_BREATHE_MILLIS),
                RepeatMode.Reverse,
            ),
            label = "face-look-glow-breathe",
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
                    listOf(AuroraPlum.copy(alpha = .36f), Color.Transparent),
                ),
            ),
    )
}

/** The pager's position, as dots under the face. */
@Composable
private fun LookPagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Layout.rowGap),
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) DOT_ACTIVE_WIDTH else DOT_SIZE,
                animationSpec = auroraAnimationSpec(DOT_SETTLE_MILLIS),
                label = "face-look-dot",
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

private const val REVEAL_MILLIS = 300
private const val REVEAL_START_SCALE = 0.94f

/** Matches the CSS spec's 0.16em eyebrow tracking. */
private const val EYEBROW_TRACKING = 0.16f

private const val GLOW_BREATHE_MILLIS = 4_500
private const val GLOW_MIN_SCALE = 0.9f
private const val GLOW_MAX_SCALE = 1.12f
private const val GLOW_MIN_ALPHA = 0.55f
private const val GLOW_MAX_ALPHA = 1f

private const val DOT_SETTLE_MILLIS = 200
private val DOT_SIZE = 5.dp
private val DOT_ACTIVE_WIDTH = 22.dp
private val LOOK_DESCRIPTION_MAX_WIDTH = 280.dp
