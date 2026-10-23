package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.theme.Spacing
import org.jellyfin.sdk.model.api.TrickplayInfo

fun Modifier.offsetByPercent(
    xPercentage: Float,
    yOffset: Int,
) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x =
                    ((constraints.maxWidth * xPercentage).toInt() - placeable.width / 2)
                        .coerceIn(0, constraints.maxWidth - placeable.width),
                y = constraints.maxHeight - yOffset, // (constraints.maxHeight * yPercentage).toInt() - (placeable.height / 1.33f).toInt(),
            )
        }
    },
)

/**
 * Offset the composable by a percentage of the available x direction
 *
 * This will account for the composable actual width so it won't be pushed off screen.
 * In other words, 0% means the left edge of the composable will be at the left end of the x-axis.
 *
 * @param xPercentage percent offset between 0 inclusive and 1 inclusive
 */
fun Modifier.offsetByPercent(
    @FloatRange(0.0, 1.0) xPercentage: Float,
) = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(
                x =
                    ((constraints.maxWidth * xPercentage).toInt() - placeable.width / 2)
                        .coerceIn(0, constraints.maxWidth - placeable.width),
                y = 0,
            )
        }
    },
)

/**
 * Show trickplay preview image. This composable assumes the provided URL is for the correct index.
 *
 * If no trickplay image is available, just the timestamp will be shown.
 */
@Composable
fun SeekPreviewImage(
    previewImageUrl: String,
    seekProgressMs: Long,
    trickPlayInfo: TrickplayInfo,
    modifier: Modifier = Modifier,
    jumpText: String? = null,
    jumpAlpha: Float = 1f,
) {
    val context = LocalContext.current

    // Tuning values, kept local so Live Edit picks up changes.
    // Height of the preview image; the width follows the video's shape (64 dp is about 114 dp wide at 16:9)
    val previewHeight = 72.dp
    val previewCornerRadius = 4.dp
    // Drop shadow under the preview: its size (elevation, e.g. 4.dp subtle … 16.dp strong) and colour.
    // The colour only applies on Android 9+; older boxes draw the system's grey shadow.
    val previewShadow = 12.dp
    val previewShadowColor = Color.Black
    val previewShape = RoundedCornerShape(previewCornerRadius)
    // The jump text ("+1m 20s" / "Back to 12:34") sits at the bottom of the image on a dark fade.
    // How much of the image height the fade covers, how dark it gets at the bottom, the text style
    // and its distance from the bottom edge
    val jumpFadeShare = 0.72f
    val jumpFadeAlpha = 0.8f
    val jumpTextStyle = MaterialTheme.typography.titleMedium
    val jumpTextBottomInset = Spacing.xs

    if (previewImageUrl.isNotNullOrBlank()) {
        val height = previewHeight
        val width = height * (trickPlayInfo.width.toFloat() / trickPlayInfo.height)
        val scale = with(LocalDensity.current) { width.toPx() / trickPlayInfo.width }

        val model =
            remember(previewImageUrl) {
                ImageRequest
                    .Builder(context)
                    .data(previewImageUrl)
                    .size(coil3.size.Size.ORIGINAL)
                    .build()
            }
        val painter =
            rememberAsyncImagePainter(
                model = model,
                contentScale = ContentScale.None,
            )
        val index =
            (seekProgressMs.toDouble() / trickPlayInfo.interval).toInt() // Index of tile across images
        val numberOfTilesPerImage = trickPlayInfo.tileHeight * trickPlayInfo.tileWidth
        val tileIndex =
            index % numberOfTilesPerImage // Index of tile within the current image
        val x = (tileIndex % trickPlayInfo.tileWidth) // x position within tile grid
        val y = (tileIndex / trickPlayInfo.tileWidth) // y position
        Box(
            modifier =
                modifier
                    .shadow(
                        elevation = previewShadow,
                        shape = previewShape,
                        clip = true,
                        ambientColor = previewShadowColor,
                        spotColor = previewShadowColor,
                    ).background(Color.Black)
                    .height(height)
                    .width(width),
        ) {
            Canvas(
                modifier =
                    Modifier
                        .height(height)
                        .width(width)
                        .clip(RectangleShape),
            ) {
                with(painter) {
                    // Scale and translate to the right position in the tile grid
                    scale(scale, scale, pivot = Offset.Zero) {
                        translate(
                            left = -x.toFloat() * trickPlayInfo.width,
                            top = -y.toFloat() * trickPlayInfo.height,
                        ) {
                            draw(
                                size =
                                    Size(
                                        trickPlayInfo.width * trickPlayInfo.tileWidth.toFloat(),
                                        trickPlayInfo.height * trickPlayInfo.tileHeight.toFloat(),
                                    ),
                            )
                        }
                    }
                }
            }
            jumpText?.let {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(jumpFadeShare)
                            .graphicsLayer { alpha = jumpAlpha }
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = jumpFadeAlpha),
                                ),
                            ),
                ) {
                    Text(
                        text = it,
                        color = Color.White,
                        style = jumpTextStyle,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = jumpTextBottomInset),
                    )
                }
            }
        }
    }
}

/**
 * The "+1m 20s" / "Back to 12:34" chip shown while seeking when there are no trickplay images (with an
 * image, the text sits at the bottom of the image instead)
 */
@Composable
internal fun SeekJumpChip(
    text: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier =
            modifier
                .graphicsLayer { this.alpha = alpha }
                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
