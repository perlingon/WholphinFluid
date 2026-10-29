package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.nav.CollapsedDrawerItemWidth
import com.github.damontecres.wholphin.ui.theme.Spacing

/**
 * Whether the sharp backdrop image should be out of the way, faded out and moved up, because
 * focus is in the rows below a details page's buttons (see [DetailRowsGroup])
 */
object BackdropImageAway {
    var away by mutableStateOf(false)
        internal set
}

// The padding the nav drawer gives every page (see NavDrawer), which the row panel reaches past
private val PageStartPadding = CollapsedDrawerItemWidth + Spacing.sm
private val PageEndPadding = Spacing.md

/**
 * Lets a details page's list reach past the page's side padding to the edges of the screen, so the
 * row panel (see [DetailRowsGroup]) can run the full width. What sits in the list keeps its place
 * by using [pageSidePadding]
 */
fun Modifier.bleedToScreenEdges(): Modifier =
    layout { measurable, constraints ->
        val start = PageStartPadding.roundToPx()
        val extra = start + PageEndPadding.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-start, 0) }
    }

/**
 * Puts back the page's side padding inside a list made with [bleedToScreenEdges]
 */
fun Modifier.pageSidePadding(): Modifier = padding(start = PageStartPadding, end = PageEndPadding)

/**
 * The rows below the buttons on a movie or series page, together on a dark see-through
 * background, the row panel. While focus is in them, the sharp backdrop image fades out and moves up.
 * It runs the full width of the screen when its list uses [bleedToScreenEdges]
 */
@Composable
fun DetailRowsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Tuning values, kept local so Live Edit picks them up
    // How dark the background behind the rows is: 0 = none, 1 = black
    val backgroundAlpha = 0.5f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    DisposableEffect(Unit) { onDispose { BackdropImageAway.away = false } }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier
                .fillMaxWidth()
                // Reaches the bottom of the screen even when there are only a few rows
                .defaultMinSize(minHeight = screenHeight)
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .onFocusChanged { BackdropImageAway.away = it.hasFocus }
                .padding(top = Spacing.lg, bottom = Spacing.xxl)
                // The background runs the full width; the rows keep the page's side padding
                .pageSidePadding(),
        content = content,
    )
}
