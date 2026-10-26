package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.nav.BackdropDebug
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.theme.FluidLayout
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.theme.rowHeadline
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.RowPivotBringIntoViewSpec

// Fading rows (fadeEnd = true), see ItemRow. Tune these to taste
// Where the focused card goes: the TV default pivot (a share of the row's width), so many slots earlier
private const val FADE_ROW_PIVOT = 0.3f
private const val FADE_ROW_SLOTS_EARLIER = 1
// The fades themselves are tuned in drawRowFades below

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = Spacing.card,
    showViewMore: Boolean = false,
    viewMoreCardContent: @Composable (Modifier) -> Unit = {},
    fadeEnd: Boolean = false,
) {
    val state = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt()

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)

    // Fading rows end at FluidLayout.rowEnd() with soft, curved fades at both ends, and the
    // focused card sits one slot before the TV default pivot
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val rowEndPx = with(density) { FluidLayout.rowEnd().toPx() }
    val rowStartPadding = FluidLayout.rowStart(horizontalPadding)
    // Where the row sits on screen, read when drawing (not state, so it never recomposes)
    val rowCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val bringIntoViewSpec =
        if (fadeEnd) {
            remember(density, horizontalPadding, rowStartPadding) {
                with(density) {
                    RowPivotBringIntoViewSpec(
                        parentFraction = FADE_ROW_PIVOT,
                        slotsEarlier = FADE_ROW_SLOTS_EARLIER,
                        gapPx = horizontalPadding.toPx(),
                        minLeadingPx = rowStartPadding.toPx(),
                    )
                }
            }
        } else {
            LocalBringIntoViewSpec.current
        }
    // Room after the last card so it can scroll all the way to the pivot, clear of the fade: the
    // pivot puts a card's end at (pivot * width - gap), so the rest of the row is left over
    val endPadding =
        if (fadeEnd) {
            screenWidth * (1f - FADE_ROW_PIVOT) + horizontalPadding
        } else {
            horizontalPadding
        }

    Column(
        // Headline to its cards
        verticalArrangement = Arrangement.spacedBy(Spacing.item),
        modifier =
            modifier
                // Development switch, see BackdropDebug
                .alpha(if (BackdropDebug.hideRows) 0f else 1f)
                .focusProperties {
                onEnter = {
                    focusRequester.tryRequestFocus()
                }
            },
    ) {
        ItemRowTitle(title)

        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyRow(
                state = state,
                horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
                contentPadding = PaddingValues(
                        // The first card lines up with the headlines; the rest scroll full width
                        start = rowStartPadding,
                        end = endPadding,
                        top = Spacing.sm,
                        bottom = Spacing.sm,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (fadeEnd) {
                                Modifier
                                    .onGloballyPositioned { rowCoordinates[0] = it }
                                    .drawWithContent {
                                        val rowLeft =
                                            rowCoordinates[0]
                                                ?.takeIf { it.isAttached }
                                                ?.positionInRoot()
                                                ?.x ?: 0f
                                        drawRowFades(
                                            endX = rowEndPx - rowLeft,
                                            startGapPx = rowStartPadding.toPx(),
                                            screenWidth = screenWidth.toPx(),
                                            // Focused cards grow past the row's top and bottom
                                            overflowPx = Spacing.xxl.toPx(),
                                        )
                                    }
                            } else {
                                Modifier
                            },
                        ).focusGroup()
                        .focusRestorer(firstFocus)
                        .focusRequester(focusRequester),
            ) {
                itemsIndexed(items) { index, item ->
                    val cardModifier =
                        remember(index, position) {
                            if (index == position) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            }
                        }

                    val onClick =
                        remember(index, item) {
                            {
                                position = index
                                if (item != null) currentOnClickItem(index, item)
                            }
                        }

                    val onLongClick =
                        remember(index, item) {
                            {
                                position = index
                                if (item != null) currentOnLongClickItem(index, item)
                            }
                        }

                    cardContent.invoke(
                        index,
                        item,
                        cardModifier,
                        onClick,
                        onLongClick,
                    )
                }
                if (showViewMore) {
                    item {
                        val cardModifier =
                            remember(items.size, position) {
                                if (position == items.size) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                }
                            }
                        viewMoreCardContent.invoke(cardModifier)
                    }
                }
            }
        }
    }
}

/**
 * Draws the row with a soft, rounded fade at each end, like the backdrop image's curved fades:
 * an ellipse whose centre sits inside the row and whose far side reaches the edge, so the edge
 * bends in at the top and bottom. Stops are "position to opacity", from the centre (0f) out to
 * the ellipse's rim (1f). Everything past [endX] is hidden
 *
 * @param endX where the row ends (fully faded out), in pixels from the row's left
 * @param startGapPx the gap before the first card, which the start fade is measured against
 * @param screenWidth the screen's width in pixels, which the end fade is measured against
 * @param overflowPx how far above and below the row content may draw, e.g. a focused card
 */
private fun ContentDrawScope.drawRowFades(
    endX: Float,
    startGapPx: Float,
    screenWidth: Float,
    overflowPx: Float,
) {
    // End fade, towards FluidLayout.rowEnd()
    val endFade =
        listOf(
            0f to 1f,
            0.55f to 1f,
            0.8f to 0.45f,
            1f to 0f,
        )
    // Centre of the curve, measured back from the row's end as a share of the screen width.
    // Further from the end = a wider, softer fade
    val endFadeReach = 0.3f
    // Height of the curve relative to the row's height: lower = rounder (corners cut away more),
    // higher = flatter (closer to a straight edge)
    val endFadeHeight = 1f

    // Start fade, at the row's left edge, where cards scroll away
    val startFade =
        listOf(
            0f to 1f,
            0.55f to 1f,
            0.8f to 0.45f,
            1f to 0f,
        )
    // Centre of the curve, measured from the row's left edge as a share of the gap before the
    // first card: 1f = the fade fits in that gap, so the first card is never touched. 0f = off
    val startFadeReach = 1f
    val startFadeHeight = 1f

    fun List<Pair<Float, Float>>.asMask() =
        map { (position, opacity) -> position to Color.Black.copy(alpha = opacity) }.toTypedArray()

    val top = -overflowPx
    val bottom = size.height + overflowPx
    val height = bottom - top
    val centerY = (top + bottom) / 2f

    drawContext.canvas.saveLayer(Rect(0f, top, size.width, bottom), Paint())
    drawContent()

    // Ellipse from its centre out to one edge; only the part between the centre and that edge
    // is touched
    fun curvedFade(
        stops: List<Pair<Float, Float>>,
        edgeX: Float,
        reach: Float,
        curveHeight: Float,
        towardsRight: Boolean,
    ) {
        if (reach < 1f) return
        val center = Offset(if (towardsRight) edgeX - reach else edgeX + reach, centerY)
        val stretch = (height * curveHeight / reach).coerceAtLeast(0.01f)
        scale(scaleX = 1f, scaleY = stretch, pivot = center) {
            val scaledTop = center.y - (center.y - top) / stretch
            val scaledBottom = center.y + (bottom - center.y) / stretch
            drawRect(
                brush =
                    Brush.radialGradient(
                        colorStops = stops.asMask(),
                        center = center,
                        radius = reach,
                    ),
                topLeft = Offset(if (towardsRight) center.x else edgeX, scaledTop),
                size = Size(reach, scaledBottom - scaledTop),
                blendMode = BlendMode.DstIn,
            )
        }
    }

    curvedFade(endFade, endX, screenWidth * endFadeReach, endFadeHeight, towardsRight = true)
    curvedFade(startFade, 0f, startGapPx * startFadeReach, startFadeHeight, towardsRight = false)
    // Nothing shows past the row's end
    if (endX < size.width) {
        drawRect(
            color = Color.Black,
            topLeft = Offset(endX, top),
            size = Size(size.width - endX, height),
            blendMode = BlendMode.Clear,
        )
    }
    drawContext.canvas.restore()
}

@Composable
@NonRestartableComposable
fun ItemRowTitle(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.rowHeadline,
    color = MaterialTheme.colorScheme.onBackground,
    // Row titles line up with the page's title, at the left margin
    modifier = modifier.padding(start = FluidLayout.headlineInset()),
)
