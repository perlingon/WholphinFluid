package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls a row so the focused card starts at a fixed point (the pivot), like the TV default, but
 * a number of card slots earlier than it.
 *
 * The TV default puts the focused card's start at [parentFraction] of the row's width. Moving it
 * [slotsEarlier] slots earlier takes off the focused card's width plus the gap between cards for
 * each slot. The card never goes further left than [minLeadingPx], usually where the first card starts
 *
 * @param parentFraction where the TV default pivot is, as a share of the row's width
 * @param slotsEarlier how many card slots before the default pivot the focused card goes
 * @param gapPx gap between cards, in pixels
 * @param minLeadingPx the furthest left the focused card goes, in pixels
 */
class RowPivotBringIntoViewSpec(
    private val parentFraction: Float = 0.3f,
    private val slotsEarlier: Int = 1,
    private val gapPx: Float = 0f,
    private val minLeadingPx: Float = 0f,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val target =
            (parentFraction * containerSize - slotsEarlier * (size + gapPx))
                .coerceAtLeast(minLeadingPx)
        return offset - target
    }
}
