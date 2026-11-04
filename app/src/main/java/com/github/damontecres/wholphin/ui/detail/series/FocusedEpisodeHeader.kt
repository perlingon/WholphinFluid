package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.nav.OnBackdropHeader
import com.github.damontecres.wholphin.ui.theme.Spacing

@Composable
fun FocusedEpisodeHeader(
    preferences: UserPreferences,
    ep: BaseItem?,
    chosenStreams: ChosenStreams?,
    overviewOnClick: () -> Unit,
    overviewOnFocus: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnBackdropHeader {
        val context = LocalContext.current
        val dto = ep?.data
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = modifier,
        ) {
            EpisodeName(dto, modifier = Modifier.padding(start = HeaderUtils.startPadding))

            ep?.ui?.quickDetails?.let {
                QuickDetails(
                    it,
                    ep.timeRemainingOrRuntime,
                    Modifier.padding(start = HeaderUtils.startPadding),
                )
            }

            if (dto != null) {
                VideoStreamDetails(
                    chosenStreams = chosenStreams,
                    numberOfVersions = dto.mediaSourceCount ?: 0,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }
            OverviewText(
                overview = dto?.overview ?: "",
                maxLines = 3,
                onClick = overviewOnClick,
                // The text (not its focus background) lines up with the title
                modifier =
                    Modifier
                        .padding(start = HeaderUtils.startPadding - Spacing.sm)
                        .onFocusChanged(overviewOnFocus),
            )
        }
    }
}
