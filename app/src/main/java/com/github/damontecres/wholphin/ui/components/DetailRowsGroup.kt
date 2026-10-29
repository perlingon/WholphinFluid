package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
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

/**
 * The backdrop's colour glows as drawn right now (see Backdrop), so the row panel can draw a darker
 * copy of them. Transparent when there are no glows
 */
object BackdropGlows {
    var primary by mutableStateOf(Color.Transparent)
        internal set
    var secondary by mutableStateOf(Color.Transparent)
        internal set
    var tertiary by mutableStateOf(Color.Transparent)
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
 * The rows below the buttons on a movie or series page, together on a see-through background
 * made from a darker copy of the backdrop's colours, the row panel. While focus is in them, the sharp backdrop image fades out and moves up.
 * It runs the full width of the screen when its list uses [bleedToScreenEdges]
 */
@Composable
fun DetailRowsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Tuning values, kept local so Live Edit picks them up
    // How much of the backdrop shows through the panel: 0 = none (solid), 1 = all of it
    val seeThrough = 0.35f
    // How much darker than the backdrop's colours the panel is: 0 = as bright, 1 = black
    val darken = 0.6f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val baseColor = MaterialTheme.colorScheme.background
    // Where the panel is on the screen, so its glows line up with the backdrop's
    var positionOnScreen by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) { onDispose { BackdropImageAway.away = false } }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // Reaches the bottom of the screen even when there are only a few rows
                .defaultMinSize(minHeight = screenHeight)
                .onGloballyPositioned { positionOnScreen = it.positionInRoot() },
    ) {
        // The backdrop's background, darker: the theme background and the three colour glows,
        // placed as on the screen (see glowPainter in Backdrop)
        Spacer(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - seeThrough }
                    .drawBehind {
                        val screenWidth = (size.width).coerceAtLeast(1f)
                        val screenHeightPx = screenHeight.toPx()
                        val origin = -positionOnScreen
                        fun Color.darker() = copy(red = red * (1f - darken), green = green * (1f - darken), blue = blue * (1f - darken))
                        drawRect(baseColor.darker())
                        listOf(
                            BackdropGlows.secondary to Offset(0f, 0f),
                            BackdropGlows.primary to Offset(screenWidth, screenHeightPx),
                            BackdropGlows.tertiary to Offset(screenWidth, 0f),
                        ).forEach { (color, corner) ->
                            if (color.alpha > 0f) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors = listOf(color.darker(), Color.Transparent),
                                            center = origin + corner,
                                            radius = screenWidth * 0.8f,
                                        ),
                                )
                            }
                        }
                    },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { BackdropImageAway.away = it.hasFocus }
                    .padding(top = Spacing.lg, bottom = Spacing.xxl)
                    // The background runs the full width; the rows keep the page's side padding
                    .pageSidePadding(),
            content = content,
        )
    }
}
