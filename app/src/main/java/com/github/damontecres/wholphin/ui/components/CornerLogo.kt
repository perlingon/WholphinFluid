package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.theme.Spacing
import kotlin.time.Duration.Companion.milliseconds

/**
 * The title logo in the bottom start corner, over a dark corner fade, as shown by the player while
 * paused with the controls hidden and by the screensaver. Place it with
 * `Modifier.align(Alignment.BottomStart)` in a box that covers the picture.
 *
 * The logo is fetched already resized by the server to the box's size in pixels, so it is drawn
 * about 1:1 instead of being shrunk on the device, which made thin edges look jagged. Any remaining
 * scaling is drawn with high quality filtering.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun CornerLogo(
    logoUrl: String?,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    // Tuning values, kept local so Live Edit picks up changes.
    // The box the logo is fitted into (it keeps its own shape inside it)
    val logoMaxSize = DpSize(200.dp, 90.dp)
    // Distance from the start, end and bottom edges
    val insetStart = Spacing.xxl
    val insetEnd = Spacing.xxl
    val insetBottom = Spacing.xxl
    // The fade behind it: how far it reaches, and each stop as (distance from the corner 0..1, color)
    val fadeSize = DpSize(720.dp, 360.dp)
    val fadeColor = Color.Black
    val fadeStops =
        arrayOf(
            0f to fadeColor.copy(alpha = 0.6f),
            0.5f to fadeColor.copy(alpha = 0.3f),
            1f to Color.Transparent,
        )

    val density = LocalDensity.current
    val sizedUrl =
        remember(logoUrl, logoMaxSize, density) {
            logoUrl?.let {
                val maxWidth = with(density) { logoMaxSize.width.roundToPx() }
                val maxHeight = with(density) { logoMaxSize.height.roundToPx() }
                val separator = if (it.contains('?')) '&' else '?'
                "$it${separator}maxWidth=$maxWidth&maxHeight=$maxHeight"
            }
        }

    Box(modifier = modifier) {
        CornerFade(
            stops = fadeStops,
            fromEnd = false,
            fromBottom = true,
            modifier = Modifier.size(fadeSize).align(Alignment.BottomStart),
        )
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(sizedUrl)
                    .transitionFactory(CrossFadeFactory(750.milliseconds))
                    .build(),
            contentDescription = "Logo",
            alignment = Alignment.BottomStart,
            filterQuality = FilterQuality.High,
            onError = { onError() },
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = insetStart, end = insetEnd, bottom = insetBottom)
                    .size(logoMaxSize),
        )
    }
}

/**
 * A soft elliptical fade from a corner, filling its size
 *
 * @param fromEnd true for an end corner, false for a start corner
 * @param fromBottom true for a bottom corner, false for a top corner
 */
@Composable
fun CornerFade(
    stops: Array<Pair<Float, Color>>,
    fromEnd: Boolean,
    modifier: Modifier = Modifier,
    fromBottom: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val atRight = fromEnd != rtl
    Box(
        modifier =
            modifier.drawBehind {
                val corner = Offset(if (atRight) size.width else 0f, if (fromBottom) size.height else 0f)
                val radius = size.width
                // Draw a circle around the corner, squashed to the box's height
                scale(scaleX = 1f, scaleY = size.height / size.width, pivot = corner) {
                    drawCircle(
                        brush = Brush.radialGradient(*stops, center = corner, radius = radius),
                        radius = radius,
                        center = corner,
                    )
                }
            },
    )
}
