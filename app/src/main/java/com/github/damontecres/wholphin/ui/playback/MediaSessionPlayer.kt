package com.github.damontecres.wholphin.ui.playback

import android.content.Intent
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.content.IntentCompat
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaSession
import com.github.damontecres.wholphin.preferences.PlaybackPreferences
import com.github.damontecres.wholphin.preferences.skipBackOnResume
import com.github.damontecres.wholphin.ui.seekBack
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber

@OptIn(UnstableApi::class)
class MediaSessionPlayer(
    player: Player,
    private val playbackPreferences: PlaybackPreferences,
) : ForwardingSimpleBasePlayer(player) {
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        Timber.v("handleSetPlayWhenReady: playWhenReady=$playWhenReady")
        if (playWhenReady) {
            playbackPreferences.skipBackOnResume?.let {
                player.seekBack(it)
            }
        }
        return super.handleSetPlayWhenReady(playWhenReady)
    }
}

/**
 * Handles play/pause media buttons sent straight to the session (e.g. by Button Mapper), which
 * otherwise go through the legacy session path and may be dropped.
 */
@OptIn(UnstableApi::class)
object MediaButtonCallback : MediaSession.Callback {
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent,
    ): Boolean {
        val keyEvent =
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                ?: return false
        val player = session.player
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> Unit

            else -> return false
        }
        if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
            Timber.v("onMediaButtonEvent: %s", keyEvent)
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> Util.handlePlayButtonAction(player)
                KeyEvent.KEYCODE_MEDIA_PAUSE -> Util.handlePauseButtonAction(player)
                else -> Util.handlePlayPauseButtonAction(player)
            }
        }
        return true
    }
}
