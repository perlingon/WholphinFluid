package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitlePreferencesContent
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings

/**
 * Subtitle style settings shown as a side panel over the playing video, so changes can be seen on
 * the real subtitles right away.
 *
 * @param isHdr edit the HDR subtitle style, i.e. the one in use for the current video
 * @param gravity The [Gravity] to align the panel to left or right
 */
@Composable
fun SubtitleStyleDialog(
    isHdr: Boolean,
    onDismissRequest: () -> Unit,
    gravity: Int,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val appPreferences by viewModel.preferenceDataStore.data.collectAsState(null)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(gravity)
            window.setDimAmount(0f) // Keep the video and its subtitles fully visible
        }
        val prefs =
            appPreferences?.interfacePreferences?.let {
                if (isHdr) it.hdrSubtitlesPreferences else it.subtitlesPreferences
            }
        if (prefs != null) {
            SubtitlePreferencesContent(
                title = stringResource(if (isHdr) R.string.hdr_subtitle_style else R.string.subtitle_style),
                preferences = prefs,
                prefList = SubtitleSettings.preferences,
                onPreferenceChange = { newSubtitlePrefs ->
                    viewModel.preferenceDataStore.updateData {
                        it.updateInterfacePreferences {
                            if (isHdr) {
                                hdrSubtitlesPreferences = newSubtitlePrefs
                            } else {
                                subtitlesPreferences = newSubtitlePrefs
                            }
                        }
                    }
                },
                viewModel = viewModel,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(LocalConfiguration.current.screenWidthDp.dp * .3f),
            )
        }
    }
}
