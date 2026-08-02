package com.elmtrackr.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.elmtrackr.app.R

/**
 * Accessibility semantics the app was missing entirely.
 *
 * Before this there was no `stateDescription`, no `heading()` and no
 * `onClickLabel` anywhere in the codebase. The practical effect: a collapsible
 * card told a screen-reader user nothing about whether it was open, and screens
 * over two thousand lines long offered no heading navigation at all.
 */

/**
 * Marks a collapsible surface with its open/closed state and the action a tap
 * performs.
 *
 * The chevron alone does not communicate this — it is decorative to TalkBack —
 * so the state has to be attached to the row that owns the click.
 */
@Composable
fun Modifier.auroraExpandable(expanded: Boolean): Modifier {
    val state = stringResource(if (expanded) R.string.a11y_expanded else R.string.a11y_collapsed)
    return semantics { stateDescription = state }
}

/** The label for the tap action on a collapsible surface. */
@Composable
fun auroraExpandActionLabel(expanded: Boolean): String =
    stringResource(if (expanded) R.string.a11y_collapse else R.string.a11y_expand)

/**
 * Marks text as a heading so TalkBack can jump between sections.
 *
 * Applied by [ElmSectionHeader] and the screen headers, which is the practical
 * argument for having consolidated those components: one line here covers every
 * section title in the app.
 */
fun Modifier.auroraHeading(): Modifier = semantics { heading() }
