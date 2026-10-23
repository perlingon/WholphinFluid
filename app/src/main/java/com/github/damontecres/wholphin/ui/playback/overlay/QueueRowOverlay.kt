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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.cards.rememberImageUrl
import com.github.damontecres.wholphin.ui.components.HiddenFocusBox
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.isBackKey
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
 * Heading for one season's run of episodes: "Season 2 · 4 episodes left" for the season being
 * watched, "Season 3 · 10 Episodes" for a later one. Null for a group without a season
 */
@Composable
fun seasonHeadline(
    group: QueueGroup,
    currentItem: BaseItem?,
): String? {
    val season = group.seasonNumber ?: return null
    // With every episode listed, only the ones after the playing episode are left
    val playingIndex = group.items.indexOfFirst { it.id == currentItem?.id }
    val count = if (playingIndex >= 0) group.items.size - playingIndex - 1 else group.items.size
    val isCurrentSeason =
        currentItem?.data?.seriesId == group.seriesId && currentItem?.data?.parentIndexNumber == season
    val episodes =
        if (isCurrentSeason) {
            pluralStringResource(R.plurals.episodes_left, count, count)
        } else {
            pluralStringResource(R.plurals.episodes_count, count, count)
        }
    return formatSeasonNumber(season) + " · " + episodes
}

/**
 * Hint under the controls, about where the playing episode sits in its show:
 * "First Season · 4 episodes left", "Second Season · First of 10 episodes",
 * "Last Season · Final episode" (only for ended shows). Uses the queue, which holds the rest of the series. Queues
 * that don't follow an episode show "Queue · 8 Items"
 */
@Composable
fun queueHeadline(
    groups: List<QueueGroup>,
    currentItem: BaseItem?,
    seriesEnded: Boolean?,
): String {
    val current = currentItem?.data
    val season = current?.parentIndexNumber
    if (currentItem?.type != BaseItemKind.EPISODE || season == null) {
        val count = groups.sumOf { it.items.size }
        return stringResource(R.string.queue) + " · " + pluralStringResource(R.plurals.items_count, count, count)
    }
    val seriesGroups = groups.filter { it.seriesId == current?.seriesId && it.seasonNumber != null }
    val left = seriesGroups.filter { it.seasonNumber == season }.sumOf { it.items.size }
    // "Last Season" only for shows that have ended (running ones keep the ordinal). A full queue
    // may stop before the later seasons, so only trust it when it isn't full
    val queueFull = groups.sumOf { it.items.size } >= Playlist.MAX_SIZE - 1
    val laterSeasons = seriesGroups.any { it.seasonNumber!! > season }
    val seasonLabel =
        if (seriesEnded == true && season > 1 && !laterSeasons && !queueFull) {
            stringResource(R.string.last_season)
        } else {
            stringArrayResource(R.array.season_ordinals).getOrNull(season - 1)
                ?: formatSeasonNumber(season)
        }
    val episodeLabel =
        when {
            left == 0 -> stringResource(R.string.final_episode)
            current?.indexNumber == 1 -> pluralStringResource(R.plurals.first_of_episodes, left + 1, left + 1)
            else -> pluralStringResource(R.plurals.episodes_left, left, left)
        }
    return "$seasonLabel · $episodeLabel"
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
    currentItem: BaseItem?,
    controllerViewState: ControllerViewState,
    nextState: OverlayViewState,
    onChangeState: (OverlayViewState) -> Unit,
    onClickPlaylist: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    seriesEpisodes: List<BaseItem> = emptyList(),
    onClickEpisode: (BaseItem) -> Unit = onClickPlaylist,
) {
    val focusRequester = remember { FocusRequester() }
    val hiddenFocusRequester = remember { FocusRequester() }
    // While an episode plays, the row lists every episode of the series, not just the queue
    val showAllEpisodes = seriesEpisodes.isNotEmpty() && currentItem?.type == BaseItemKind.EPISODE
    val groups =
        remember(queue, seriesEpisodes, showAllEpisodes) {
            if (showAllEpisodes) {
                groupQueueBySeason(seriesEpisodes.map { PlaylistItem.Media(it) })
            } else {
                groupQueueBySeason(queue)
            }
        }
    // The season of the playing episode, where the row opens
    val playingGroupIndex =
        remember(groups, currentItem) {
            groups.indexOfFirst { group -> group.items.any { it.id == currentItem?.id } }.coerceAtLeast(0)
        }
    // One season is shown at a time, down/up moves to the next/previous season
    var groupIndex by remember(groups) { mutableIntStateOf(playingGroupIndex) }
    val group = groups.getOrNull(groupIndex) ?: return
    // The card that takes focus when a season opens: the playing episode in its own season, else the first
    val focusIndex =
        if (groupIndex == playingGroupIndex) {
            group.items.indexOfFirst { it.id == currentItem?.id }.coerceAtLeast(0)
        } else {
            0
        }
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
                seasonHeadline(group, currentItem) ?: (
                    stringResource(R.string.queue) + " · " +
                        pluralStringResource(R.plurals.items_count, group.items.size, group.items.size)
                ),
            style = headlineStyle,
            color = headlineColor,
            modifier = Modifier.padding(start = headlineInset),
        )
        // A fresh row per season, so it starts scrolled to the season's first episode
        key(groupIndex) {
            LazyRow(
                state = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex),
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
                            // Back returns to the controls instead of hiding them
                            if (isBackKey(it)) {
                                if (it.type == KeyEventType.KeyUp) onChangeState.invoke(OverlayViewState.CONTROLLER)
                                return@onPreviewKeyEvent true
                            }
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

                                // Up from the first season goes back to the controls, from a later one to the previous season
                                Key.DirectionUp -> {
                                    if (groupIndex > 0) {
                                        groupIndex--
                                        focusFirst = true
                                    } else {
                                        onChangeState.invoke(OverlayViewState.CONTROLLER)
                                    }
                                    true
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
                    if (index == focusIndex) {
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
                            if (showAllEpisodes) onClickEpisode.invoke(item.item) else onClickPlaylist.invoke(item.item)
                            controllerViewState.hideControls()
                        },
                        onLongClick = {},
                        imageHeight = 140.dp,
                        interactionSource = interactionSource,
                        modifier =
                            Modifier.ifElse(
                                index == focusIndex,
                                Modifier.focusRequester(focusRequester),
                            ),
                    )
                }
            }
        }
    }
}
