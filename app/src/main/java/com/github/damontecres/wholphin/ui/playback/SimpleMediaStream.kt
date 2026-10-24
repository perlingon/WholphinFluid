package com.github.damontecres.wholphin.ui.playback

import android.content.res.Resources
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaStream

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
            )
    }
}

data class SimpleVideoStream(
    val index: Int,
    val hdr: Boolean,
    val is4k: Boolean,
)
