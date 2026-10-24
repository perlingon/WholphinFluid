package com.github.damontecres.wholphin.ui.playback

import android.content.res.Resources
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatSubtitleCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * A slimmer [MediaStream] with minimal data for UI purposes
 *
 * @see SimpleVideoStream
 */
data class SimpleMediaStream(
    val index: Int,
    val streamTitle: String?,
    val displayTitle: String,
    // Language name only, e.g. "English", for short labels; null when the stream has none
    val language: String? = null,
    // Three-letter language code in capitals, e.g. "ENG"; null when the stream has none
    val languageCode: String? = null,
    // Format details for short labels: codec and channel layout for audio ("Dolby Digital+ 5.1"),
    // codec and SDH for subtitles ("SRT SDH")
    val formatDetail: String? = null,
) {
    companion object {
        fun from(
            resources: Resources,
            mediaStream: MediaStream,
            includeFlags: Boolean = true,
        ): SimpleMediaStream =
            SimpleMediaStream(
                index = mediaStream.index,
                streamTitle = mediaStream.title?.takeIf { it.isNotNullOrBlank() },
                displayTitle = mediaStreamDisplayTitle(resources, mediaStream, includeFlags),
                language = mediaStream.language?.takeIf { it.isNotNullOrBlank() }?.let { languageName(it) },
                languageCode =
                    mediaStream.language
                        ?.takeIf { it.isNotNullOrBlank() && it != "und" }
                        ?.uppercase(),
                formatDetail =
                    when (mediaStream.type) {
                        MediaStreamType.AUDIO -> {
                            listOfNotNull(
                                formatAudioCodec(resources, mediaStream.codec, mediaStream.profile),
                                mediaStream.channelLayout,
                            )
                        }

                        MediaStreamType.SUBTITLE -> {
                            listOfNotNull(
                                formatSubtitleCodec(mediaStream.codec),
                                "SDH".takeIf { mediaStream.isHearingImpaired },
                            )
                        }

                        else -> {
                            emptyList()
                        }
                    }.joinToString(" ").takeIf { it.isNotBlank() },
            )
    }
}

data class SimpleVideoStream(
    val index: Int,
    val hdr: Boolean,
    val is4k: Boolean,
)
