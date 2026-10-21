package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.math.roundToInt

/**
 * Kodi-style manual video adjustment, applied on top of the normal content scale.
 *
 * Everything is a layout change of the player surface (no re-encoding, no GPU effects),
 * so it works for any codec and keeps HDR / passthrough intact.
 *
 * @param zoom 1.0 = unchanged; scales both axes
 * @param pixelRatio 1.0 = unchanged; >1 stretches horizontally, <1 vertically
 * @param shiftX horizontal offset as a fraction of the fitted video width (+ = right)
 * @param shiftY vertical offset as a fraction of the fitted video height (+ = down)
 */
data class VideoTransform(
    val zoom: Float = 1f,
    val pixelRatio: Float = 1f,
    val shiftX: Float = 0f,
    val shiftY: Float = 0f,
) {
    val isIdentity: Boolean
        get() = this == IDENTITY

    companion object {
        val IDENTITY = VideoTransform()

        const val ZOOM_STEP = 0.01f
        const val RATIO_STEP = 0.01f
        const val SHIFT_STEP = 0.0025f // ~2.7 px on a 1080-line picture

        val ZOOM_RANGE = 0.5f..3f
        val RATIO_RANGE = 0.5f..2f
        val SHIFT_RANGE = -0.5f..0.5f
    }
}

/**
 * Apply [transform] to the already-fitted surface. Chain it AFTER `resizeWithContentScale`,
 * which hands this modifier the fitted size as fixed constraints.
 *
 * The surface is measured larger/smaller than the fitted box and placed with an offset;
 * the reported size stays the fitted size so nothing else in the layout moves.
 * Anything that spills past the screen edge is simply off-screen.
 */
fun Modifier.videoTransform(transform: VideoTransform): Modifier =
    if (transform.isIdentity) {
        this
    } else {
        layout { measurable, constraints ->
            val fitW = constraints.maxWidth
            val fitH = constraints.maxHeight
            if (fitW == Constraints.Infinity || fitH == Constraints.Infinity || fitW == 0 || fitH == 0) {
                val p = measurable.measure(constraints)
                return@layout layout(p.width, p.height) { p.place(0, 0) }
            }
            val w = (fitW * transform.zoom * transform.pixelRatio).roundToInt().coerceAtLeast(1)
            val h = (fitH * transform.zoom).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(fitW, fitH) {
                val x = (fitW - w) / 2 + (transform.shiftX * fitW).roundToInt()
                val y = (fitH - h) / 2 + (transform.shiftY * fitH).roundToInt()
                placeable.place(x, y)
            }
        }
    }

/**
 * Persists a [VideoTransform] per Jellyfin item id, so a movie always opens with its own
 * crop/zoom. Plain SharedPreferences: tiny data, read once per item.
 */
class VideoTransformStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences("video_transforms", Context.MODE_PRIVATE)

    fun get(itemId: String?): VideoTransform {
        if (itemId.isNullOrBlank()) return VideoTransform.IDENTITY
        val raw = prefs.getString(itemId, null) ?: return VideoTransform.IDENTITY
        val parts = raw.split(';').mapNotNull { it.toFloatOrNull() }
        if (parts.size != 4) return VideoTransform.IDENTITY
        return VideoTransform(parts[0], parts[1], parts[2], parts[3])
    }

    fun put(
        itemId: String?,
        transform: VideoTransform,
    ) {
        if (itemId.isNullOrBlank()) return
        prefs.edit().apply {
            if (transform.isIdentity) {
                remove(itemId)
            } else {
                putString(
                    itemId,
                    "${transform.zoom};${transform.pixelRatio};${transform.shiftX};${transform.shiftY}",
                )
            }
        }.apply()
    }
}

/** The media id (Jellyfin item id) of the player's current item, updated on next-up etc. */
@Composable
fun rememberCurrentMediaId(player: Player): String? {
    var mediaId by remember(player) { mutableStateOf(player.currentMediaItem?.mediaId) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    mediaId = mediaItem?.mediaId
                }
            }
        player.addListener(listener)
        mediaId = player.currentMediaItem?.mediaId
        onDispose { player.removeListener(listener) }
    }
    return mediaId?.takeIf { it.isNotBlank() }
}

private fun Float.step(
    delta: Float,
    range: ClosedFloatingPointRange<Float>,
): Float = ((this + delta) * 10000f).roundToInt().div(10000f).coerceIn(range)

fun VideoTransform.adjustZoom(steps: Int) = copy(zoom = zoom.step(steps * VideoTransform.ZOOM_STEP, VideoTransform.ZOOM_RANGE))

fun VideoTransform.adjustRatio(steps: Int) =
    copy(pixelRatio = pixelRatio.step(steps * VideoTransform.RATIO_STEP, VideoTransform.RATIO_RANGE))

fun VideoTransform.adjustShiftX(steps: Int) =
    copy(shiftX = shiftX.step(steps * VideoTransform.SHIFT_STEP, VideoTransform.SHIFT_RANGE))

fun VideoTransform.adjustShiftY(steps: Int) =
    copy(shiftY = shiftY.step(steps * VideoTransform.SHIFT_STEP, VideoTransform.SHIFT_RANGE))
