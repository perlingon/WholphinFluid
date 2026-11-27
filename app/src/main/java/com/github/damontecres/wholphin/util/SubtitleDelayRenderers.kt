@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.util

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * The subtitle delay for ExoPlayer, set from the player screen. Positive shows subtitles later,
 * negative shows them earlier.
 */
object ExoSubtitleDelay {
    @Volatile
    var delayUs: Long = 0L
}

/**
 * Wraps every text renderer, both media3's own and libass's, in [SubtitleDelayRenderer]. Applied
 * outermost, so it also covers renderers that another factory adds.
 */
class SubtitleDelayRenderersFactory(
    private val delegate: RenderersFactory,
) : RenderersFactory {
    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> =
        delegate
            .createRenderers(
                eventHandler,
                videoRendererEventListener,
                audioRendererEventListener,
                textRendererOutput,
                metadataRendererOutput,
            ).map { it.withSubtitleDelay() }
            .toTypedArray()

    override fun createSecondaryRenderer(
        renderer: Renderer,
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Renderer? =
        delegate
            .createSecondaryRenderer(
                (renderer as? SubtitleDelayRenderer)?.wrapped ?: renderer,
                eventHandler,
                videoRendererEventListener,
                audioRendererEventListener,
                textRendererOutput,
                metadataRendererOutput,
            )?.withSubtitleDelay()

    private fun Renderer.withSubtitleDelay(): Renderer =
        if (trackType == C.TRACK_TYPE_TEXT) SubtitleDelayRenderer(this) else this
}

/**
 * Renders subtitles as if playback were [ExoSubtitleDelay.delayUs] earlier, so a positive delay
 * shows each subtitle later. Showing them earlier needs the samples to be loaded ahead, which they
 * are for external subtitle files and, within the player's buffer, for embedded ones.
 */
class SubtitleDelayRenderer(
    val wrapped: Renderer,
) : ForwardingRenderer(wrapped) {
    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        super.render(positionUs - ExoSubtitleDelay.delayUs, elapsedRealtimeUs)
    }
}
