package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.elmtrackr.app.ui.theme.Spacing

/**
 * Two headline figures, side by side when both fit and one above the other when
 * they do not.
 *
 * The month card and the report overview both lead with the hours on one side
 * and the estimate on the other. They were a `Row` whose first block had
 * `weight(1f)`, which hands that block whatever the second one leaves. At the
 * default font size that is plenty. At a larger one the estimate's label
 * ("ESTIMATED GROSS · BEFORE TAX") takes the row for itself: at 1.3× the hours
 * lost their unit, and at Android's largest setting the hours block was squeezed
 * to the width of one character — the figure vanished and its label ran down the
 * card a letter per line, underneath the other label.
 *
 * A weighted row cannot fix that from the inside, because the block that has to
 * give way is the one holding the number the screen exists to show. So the
 * decision is made here, before either block is measured: if their natural
 * widths and a gap fit, they share a row; otherwise each gets a full-width row
 * of its own, the first at the start edge and the second at the end edge — the
 * same sides they sit on when they share one.
 *
 * [end] is optional because a month with no pay rate has only the hours.
 */
@Composable
fun AuroraFigurePair(
    start: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    end: (@Composable () -> Unit)? = null,
    gap: Dp = Spacing.s12,
    stackedGap: Dp = Spacing.s8,
    verticalAlignment: Alignment.Vertical = Alignment.Bottom,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { start() }
            if (end != null) Box { end() }
        },
    ) { measurables, constraints ->
        val startBlock = measurables[0]
        val endBlock = measurables.getOrNull(1)
        val loose = constraints.copy(minWidth = 0, minHeight = 0)

        if (endBlock == null) {
            val placed = startBlock.measure(loose)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placed.width
            return@Layout layout(width, placed.height) { placed.placeRelative(0, 0) }
        }

        val gapPx = gap.roundToPx()
        val startNatural = startBlock.maxIntrinsicWidth(Constraints.Infinity)
        val endNatural = endBlock.maxIntrinsicWidth(Constraints.Infinity)
        val fitsOnOneRow =
            !constraints.hasBoundedWidth || startNatural + gapPx + endNatural <= constraints.maxWidth

        if (fitsOnOneRow) {
            val endPlaced = endBlock.measure(loose)
            val startPlaced = startBlock.measure(
                if (constraints.hasBoundedWidth) {
                    loose.copy(maxWidth = (constraints.maxWidth - gapPx - endPlaced.width).coerceAtLeast(0))
                } else {
                    loose
                },
            )
            val width = if (constraints.hasBoundedWidth) {
                constraints.maxWidth
            } else {
                startPlaced.width + gapPx + endPlaced.width
            }
            val height = maxOf(startPlaced.height, endPlaced.height)
            layout(width, height) {
                startPlaced.placeRelative(0, verticalAlignment.align(startPlaced.height, height))
                endPlaced.placeRelative(
                    width - endPlaced.width,
                    verticalAlignment.align(endPlaced.height, height),
                )
            }
        } else {
            val startPlaced = startBlock.measure(loose)
            val endPlaced = endBlock.measure(loose)
            val stackedGapPx = stackedGap.roundToPx()
            val width = constraints.maxWidth
            layout(width, startPlaced.height + stackedGapPx + endPlaced.height) {
                startPlaced.placeRelative(0, 0)
                endPlaced.placeRelative(width - endPlaced.width, startPlaced.height + stackedGapPx)
            }
        }
    }
}
