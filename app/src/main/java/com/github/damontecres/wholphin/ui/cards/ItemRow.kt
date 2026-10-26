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
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlin.math.sqrt

// Fading rows (fadeEnd = true), see ItemRow. Tune these to taste
// Where the focused card goes: the TV default pivot (a share of the row's width), so many slots earlier
private const val FADE_ROW_PIVOT = 0.3f
private const val FADE_ROW_SLOTS_EARLIER = 1

// Width of the fade at the row's end, as a share of the screen width. Higher = softer
private const val FADE_ROW_FEATHER = 0.16f

// How far the fade's edge bends in at the top and bottom, as a share of the row's height.
// 0 = straight edge
private const val FADE_ROW_CURVE = 0.08f

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

    // Fading rows end at FluidLayout.rowEnd() with a soft, slightly curved fade, and the focused
    // card sits one slot before the TV default pivot
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val rowEndPx = with(density) { FluidLayout.rowEnd().toPx() }
    val rowStartPadding = FluidLayout.rowStart(horizontalPadding)
    // Where the row sits on screen, to place the fade
    var rowLeftPx by remember { mutableFloatStateOf(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
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
        if (fadeEnd && rowWidthPx > 0f) {
            with(density) { (rowWidthPx * (1f - FADE_ROW_PIVOT)).toDp() } + horizontalPadding
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
                                    .onGloballyPositioned {
                                        rowLeftPx = it.positionInRoot().x
                                        rowWidthPx = it.size.width.toFloat()
                                    }.drawWithContent {
                                        drawRowEndFade(
                                            edgeX = rowEndPx - rowLeftPx,
                                            featherPx = screenWidthPx * FADE_ROW_FEATHER,
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
 * Draws the row, fading it out towards [edgeX] (fully gone there). The fade's edge is a curve: the
 * right half of a tall ellipse, so it bends in slightly at the top and bottom, see FADE_ROW_CURVE
 *
 * @param edgeX where the row is fully faded out, in pixels from the row's left
 * @param featherPx width of the fade
 * @param overflowPx how far above and below the row content may draw, e.g. a focused card
 */
private fun ContentDrawScope.drawRowEndFade(
    edgeX: Float,
    featherPx: Float,
    overflowPx: Float,
) {
    val top = -overflowPx
    val bottom = size.height + overflowPx
    val halfHeight = (bottom - top) / 2f
    val centerY = (top + bottom) / 2f
    // The ellipse's horizontal radius is twice the feather, so its inner, solid half is as wide
    // as the fade
    val radiusX = featherPx * 2f
    // Vertical radius that bends the edge in by the curve's depth at the top and bottom
    val depth = (size.height * FADE_ROW_CURVE).coerceIn(0.5f, radiusX * 0.13f)
    val along = 1f - depth / radiusX
    val radiusY = halfHeight / sqrt(1f - along * along)
    val center = Offset(edgeX - radiusX, centerY)
    val stretch = radiusY / radiusX

    drawContext.canvas.saveLayer(Rect(0f, top, size.width, bottom), Paint())
    drawContent()
    scale(scaleX = 1f, scaleY = stretch, pivot = center) {
        // Only what is right of the ellipse's centre is touched; in scaled space the rows from
        // top to bottom shrink by the stretch
        val scaledTop = center.y - (center.y - top) / stretch
        val scaledBottom = center.y + (bottom - center.y) / stretch
        drawRect(
            brush =
                Brush.radialGradient(
                    // Eased, so the fade starts gently and ends softly
                    0.5f to Color.Black,
                    0.65f to Color.Black.copy(alpha = 0.85f),
                    0.8f to Color.Black.copy(alpha = 0.5f),
                    0.92f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radiusX,
                ),
            topLeft = Offset(center.x.coerceAtLeast(0f), scaledTop),
            size =
                Size(
                    (size.width - center.x.coerceAtLeast(0f)).coerceAtLeast(0f),
                    scaledBottom - scaledTop,
                ),
            blendMode = BlendMode.DstIn,
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
