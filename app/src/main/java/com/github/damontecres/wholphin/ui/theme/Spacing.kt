package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing system: an 8 dp grid, with 4 dp only for fine detail.
 *
 * Margins, padding and gaps use these steps rather than their own numbers, so the same kind of
 * gap looks the same on every screen. Pick a role below when one fits; otherwise pick a step.
 */
object Spacing {
    // Steps
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp

    // Roles

    /** Between lines of text that belong together, such as a title and its subtitle */
    val text = xs

    /** Between items in a group: buttons, chips, metadata, icon and label, a headline and its cards */
    val item = sm

    /** Between cards in a row or grid, and the usual padding inside a page or dialog */
    val card = md

    /** Between blocks on a page, such as one card row and the next */
    val block = lg

    /** Between the larger sections of a page */
    val section = xl
}
