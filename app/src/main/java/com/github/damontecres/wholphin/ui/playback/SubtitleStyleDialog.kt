package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.github.damontecres.wholphin.preferences.SubtitlePreferences
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.ui.preferences.LocalPanelDialogPlacement
import com.github.damontecres.wholphin.ui.preferences.PanelDialogPlacement
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitlePreferencesContent
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings
import kotlinx.coroutines.flow.first

/**
 * Subtitle style settings shown as a side panel over the playing video, so changes can be seen on
 * the real subtitles right away.
 *
 * @param isHdr edit the HDR subtitle style, i.e. the one in use for the current video
 * @param gravity [Gravity.START] for the left side, otherwise the panel opens on the right
 */
@Composable
fun SubtitleStyleDialog(
    isHdr: Boolean,
    onDismissRequest: () -> Unit,
    onChange: (SubtitlePreferences) -> Unit,
    gravity: Int,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    // The panel keeps its own copy, so every change shows at once and the next change builds on it
    // instead of on a value that hasn't been saved yet
    var prefs by remember { mutableStateOf<SubtitlePreferences?>(null) }
    LaunchedEffect(isHdr) {
        val interfacePreferences =
            viewModel.preferenceDataStore.data
                .first()
                .interfacePreferences
        prefs = if (isHdr) interfacePreferences.hdrSubtitlesPreferences else interfacePreferences.subtitlesPreferences
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        // Keep the video and its subtitles fully visible
        dialogWindowProvider?.window?.setDimAmount(0f)
        // The dialog window can fill the screen and center its content, so place the panel at the
        // side here, moved in to the edge of the picture on ultrawide screens
        val sideInset = LocalPictureSideInset.current
        val atStart = gravity == Gravity.START
        Box(
            contentAlignment = if (atStart) Alignment.CenterStart else Alignment.CenterEnd,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = if (atStart) sideInset else 0.dp, end = if (atStart) 0.dp else sideInset),
        ) {
            val panelWidth = LocalConfiguration.current.screenWidthDp.dp * .3f
            prefs?.let { current ->
                // Choice lists (colors etc.) open over this panel instead of in the middle of the video
                CompositionLocalProvider(
                    LocalPanelDialogPlacement provides
                        if (atStart) null else PanelDialogPlacement(width = panelWidth, endInset = sideInset),
                ) {
                    SubtitlePreferencesContent(
                        title = stringResource(if (isHdr) R.string.hdr_subtitle_style else R.string.subtitle_style),
                        preferences = current,
                        prefList = SubtitleSettings.preferences,
                        onPreferenceChange = { newSubtitlePrefs ->
                            prefs = newSubtitlePrefs
                            // Show it on the playing video right away
                            onChange(newSubtitlePrefs)
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
                                .width(panelWidth),
                    )
                }
            }
        }
    }
}
