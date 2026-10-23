package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.cards.rememberImageUrl
import com.github.damontecres.wholphin.ui.components.HiddenFocusBox
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.theme.rowHeadline
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/**
 * A run of queue items from one season of one series, in queue order. Items that aren't episodes
 * (movies, intros) get a group without a season.
 */
data class QueueGroup(
    val seriesId: UUID?,
    val seasonNumber: Int?,
    val items: List<PlaylistItem>,
)

fun groupQueueBySeason(queue: List<PlaylistItem>): List<QueueGroup> {
    val groups = mutableListOf<QueueGroup>()
    queue.forEach { item ->
        val data = item.item.data
        val isEpisode = data.type == BaseItemKind.EPISODE
        val seriesId = data.seriesId.takeIf { isEpisode }
        val seasonNumber = data.parentIndexNumber.takeIf { isEpisode }
        val last = groups.lastOrNull()
        if (last != null && last.seriesId == seriesId && last.seasonNumber == seasonNumber) {
            groups[groups.lastIndex] = last.copy(items = last.items + item)
        } else {
            groups.add(QueueGroup(seriesId, seasonNumber, listOf(item)))
        }
    }
    return groups
}

/**
 * "3 Seasons · 24 Episodes" for a queue of episodes, otherwise "Queue"
 */
@Composable
fun queueHeadline(groups: List<QueueGroup>): String {
    val seasonGroups = groups.filter { it.seasonNumber != null }
    if (seasonGroups.isEmpty()) return stringResource(R.string.queue)
    val seasons = seasonGroups.map { it.seriesId to it.seasonNumber }.distinct().size
    val episodes = seasonGroups.sumOf { it.items.size }
    return pluralStringResource(R.plurals.seasons_count, seasons, seasons) + " · " +
        pluralStringResource(R.plurals.episodes_count, episodes, episodes)
}

/**
 * "S1 E3 · Children of the Comet" for an episode, like the title row above the timeline. Other
 * items just show their name
 */
fun queueItemTitle(item: BaseItem): String? {
    val data = item.data
    if (item.type != BaseItemKind.EPISODE) return item.name
    val seasonEpisode =
        listOfNotNull(data.parentIndexNumber?.let { "S$it" }, data.indexNumber?.let { "E$it" })
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    return listOfNotNull(seasonEpisode, data.name).joinToString(" · ").takeIf { it.isNotBlank() }
}

@Composable
fun QueueRowOverlay(
    queue: List<PlaylistItem>,
    controllerViewState: ControllerViewState,
    nextState: OverlayViewState,
    onChangeState: (OverlayViewState) -> Unit,
    onClickPlaylist: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val hiddenFocusRequester = remember { FocusRequester() }
    val groups = remember(queue) { groupQueueBySeason(queue) }
    // One season is shown at a time, down/up moves to the next/previous season
    var groupIndex by remember(groups) { mutableIntStateOf(0) }
    val group = groups.getOrNull(groupIndex) ?: return
    // Set when a season's row opens, so its first episode takes focus once it's composed
    var focusFirst by remember { mutableStateOf(true) }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        HiddenFocusBox(hiddenFocusRequester) {
            onChangeState.invoke(nextState)
        }
        // Tuning values for the heading, kept local so Live Edit picks up changes. Keep in step with
        // the headings in ChapterRowOverlay
        val headlineStyle = MaterialTheme.typography.rowHeadline
        val headlineColor = Color.Unspecified // Color.Unspecified = the theme's text color
        val headlineInset = 0.dp
        Text(
            text =
                group.seasonNumber?.let {
                    formatSeasonNumber(it) + " · " +
                        pluralStringResource(R.plurals.episodes_count, group.items.size, group.items.size)
                } ?: stringResource(R.string.queue),
            style = headlineStyle,
            color = headlineColor,
            modifier = Modifier.padding(start = headlineInset),
        )
        // A fresh row per season, so it starts scrolled to the season's first episode
        key(groupIndex) {
            LazyRow(
                contentPadding =
                    PaddingValues(
                        horizontal = Spacing.md,
                        vertical = Spacing.sm,
                    ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRestorer(focusRequester)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                controllerViewState.pulseControls()
                            }
                        }.onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (it.key) {
                                // Past the last season there is nowhere to go, so stay put
                                Key.DirectionDown -> {
                                    if (groupIndex < groups.lastIndex) {
                                        groupIndex++
                                        focusFirst = true
                                    }
                                    true
                                }

                                // From the first season, up leaves the queue as before
                                Key.DirectionUp -> {
                                    if (groupIndex > 0) {
                                        groupIndex--
                                        focusFirst = true
                                        true
                                    } else {
                                        false
                                    }
                                }

                                else -> {
                                    false
                                }
                            }
                        },
            ) {
                itemsIndexed(group.items) { index, item ->
                    val interactionSource =
                        remember { MutableInteractionSource() }
                    val isFocused =
                        interactionSource.collectIsFocusedAsState().value
                    LaunchedEffect(isFocused) {
                        if (isFocused) controllerViewState.pulseControls()
                    }
                    if (index == 0) {
                        LaunchedEffect(Unit) {
                            if (focusFirst) {
                                focusFirst = false
                                focusRequester.tryRequestFocus()
                            }
                        }
                    }
                    SeasonCard(
                        title = queueItemTitle(item.item),
                        subtitle = null,
                        name = item.item.name,
                        imageUrl = rememberImageUrl(item.item, 140.dp),
                        isFavorite =
                            item.item.data.userData
                                ?.isFavorite ?: false,
                        isPlayed =
                            item.item.data.userData
                                ?.played ?: false,
                        unplayedItemCount =
                            item.item.data.userData
                                ?.unplayedItemCount ?: 0,
                        playedPercentage =
                            item.item.data.userData
                                ?.playedPercentage ?: 0.0,
                        numberOfVersions = item.item.data.mediaSourceCount ?: 0,
                        aspectRatio = item.item.aspectRatio ?: AspectRatios.TALL,
                        showSubtitle = false,
                        onClick = {
                            onClickPlaylist.invoke(item.item)
                            controllerViewState.hideControls()
                        },
                        onLongClick = {},
                        imageHeight = 140.dp,
                        interactionSource = interactionSource,
                        modifier =
                            Modifier.ifElse(
                                index == 0,
                                Modifier.focusRequester(focusRequester),
                            ),
                    )
                }
            }
        }
    }
}
