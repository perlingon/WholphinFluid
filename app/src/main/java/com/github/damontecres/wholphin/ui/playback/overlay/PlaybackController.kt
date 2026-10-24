package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.MediaSegmentDto
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlaybackController(
    item: BaseItem?,
    nextState: OverlayViewState?,
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    currentSegment: MediaSegmentDto?,
    onChangeState: (OverlayViewState) -> Unit,
    modifier: Modifier = Modifier,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onTitleHeightChange: (Dp) -> Unit = {},
    queueHeadline: String? = null,
    chapters: List<Chapter> = emptyList(),
    playFocusRequester: FocusRequester = remember { FocusRequester() },
    restartFocusRequester: FocusRequester? = null,
    subtitleLabel: String = "",
    audioLabel: String = "",
) {
    // Tuning values for the "Chapters"/"Queue" hint next to the controls, kept local for Live Edit
    val nextHeadlineStyle = MaterialTheme.typography.labelLarge
    val nextHeadlineColor = Color.Unspecified

    // The hint sits at the right end of the button row under the seek bar (see PlaybackControls)
    val nextHint: (@Composable () -> Unit)? =
        when (nextState) {
            OverlayViewState.CHAPTERS -> {
                {
                    // "Chapter 5 of 23", following playback
                    val chapterNumber by produceState(
                        currentChapterNumber(chapters, player.currentPosition),
                        chapters,
                    ) {
                        while (true) {
                            value = currentChapterNumber(chapters, player.currentPosition)
                            delay(500)
                        }
                    }
                    Text(
                        text =
                            if (chapters.isNotEmpty()) {
                                stringResource(R.string.chapter_of, chapterNumber, chapters.size)
                            } else {
                                stringResource(R.string.chapters)
                            },
                        style = nextHeadlineStyle,
                        color = nextHeadlineColor,
                    )
                }
            }

            OverlayViewState.QUEUE -> {
                {
                    Text(
                        text = queueHeadline ?: stringResource(R.string.queue),
                        style = nextHeadlineStyle,
                        color = nextHeadlineColor,
                    )
                }
            }

            else -> {
                null
            }
        }

    Controller(
        player = player,
        controllerViewState = controllerViewState,
        showPlay = showPlay,
        previousEnabled = previousEnabled,
        nextEnabled = nextEnabled,
        seekEnabled = seekEnabled,
        seekBack = seekBack,
        skipBackOnResume = skipBackOnResume,
        seekForward = seekForward,
        onPlaybackActionClick = onPlaybackActionClick,
        onClickPlaybackDialogType = onClickPlaybackDialogType,
        onSeekProgress = onSeekBarChange,
        seekBarInteractionSource = seekBarInteractionSource,
        currentSegment = currentSegment,
        onTitleHeightChange = onTitleHeightChange,
        playFocusRequester = playFocusRequester,
        restartFocusRequester = restartFocusRequester,
        nextHint = nextHint,
        onDownFromRow = nextState?.let { { onChangeState.invoke(it) } },
        subtitleLabel = subtitleLabel,
        audioLabel = audioLabel,
        modifier = modifier,
    )
}

/**
 * A wrapper for the playback controls to show title and other information, plus the actual controls
 *
 * @see PlaybackControls
 */
@Composable
fun Controller(
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onTitleHeightChange: (Dp) -> Unit = {},
    playFocusRequester: FocusRequester = remember { FocusRequester() },
    restartFocusRequester: FocusRequester? = null,
    nextHint: (@Composable () -> Unit)? = null,
    onDownFromRow: (() -> Unit)? = null,
    subtitleLabel: String = "",
    audioLabel: String = "",
) {
    // Tuning values for the title block above the seek bar, kept local so Live Edit picks up changes.
    // The trickplay preview measures this block, so it follows any size here.
    val endsAtStyle = MaterialTheme.typography.labelLarge
    val endsAtColor = MaterialTheme.colorScheme.onSurface
    // Gap between the title and the subtitle row, and between the title block and the seek bar
    val titleGap = Spacing.sm
    val blockGap = Spacing.sm
    // Indent of the title block (used until the seek bar has been measured; after that the title
    // starts where the seek bar starts), and the gap after "Ends at"
    val startInset = Spacing.xxl
    // Extra nudge on top of lining up with the seek bar, e.g. 0.dp or Spacing.xs
    val titleNudge = 0.dp
    val endInset = Spacing.xl
    // How far the title block moves up while the seek bar is focused (makes room for trickplay)
    val bumpOffset = (0).dp
    // How far the title block sits below its place, closer to the seek bar
    val titleDrop = Spacing.xs

    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    val density = LocalDensity.current
    val verticalOffset by animateDpAsState(
        targetValue = if (seekBarFocused) bumpOffset else 0.dp,
        label = "TitleBumpOffset",
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = Dp.VisibilityThreshold,
            ),
    )

    // Where this block and the seek bar start on screen, so the title lines up with the bar
    var blockStart by remember { mutableStateOf(0.dp) }
    var barStart by remember { mutableStateOf<Dp?>(null) }
    val titleStart = barStart?.let { (it - blockStart + titleNudge).coerceAtLeast(0.dp) } ?: startInset

    Column(
        verticalArrangement = Arrangement.spacedBy(blockGap),
        modifier =
            modifier.onGloballyPositioned {
                blockStart = with(density) { it.positionInRoot().x.toDp() }
            },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(titleGap),
            modifier =
                Modifier
                    .padding(start = titleStart)
                    .offset(y = verticalOffset + titleDrop)
                    // The seek preview is placed from this measured height (see PlaybackOverlay)
                    .onSizeChanged { onTitleHeightChange(with(density) { it.height.toDp() }) },
        ) {
            // "Ends at" on the right; the title sits in the top end corner (see PlaybackOverlay)
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val context = LocalContext.current
                var endTimeStr by remember { mutableStateOf("...") }
                LaunchedEffect(player, context) {
                    while (isActive) {
                        val remaining =
                            (player.duration - player.currentPosition)
                                .div(player.playbackParameters.speed)
                                .toLong()
                                .milliseconds
                        val endTime = LocalTime.now().plusSeconds(remaining.inWholeSeconds)
                        endTimeStr = formatTime(context, endTime)
                        delay(1.seconds)
                    }
                }
                Text(
                    text = stringResource(R.string.ends_at, endTimeStr),
                    color = endsAtColor,
                    style = endsAtStyle,
                    modifier =
                        Modifier
                            .padding(end = endInset),
                )
            }
        }
        PlaybackControls(
            modifier = Modifier.fillMaxWidth(),
            onBarStartChange = { barStart = it },
            nextHint = nextHint,
            onDownFromRow = onDownFromRow,
            subtitleLabel = subtitleLabel,
            audioLabel = audioLabel,
            player = player,
            onPlaybackActionClick = onPlaybackActionClick,
            controllerViewState = controllerViewState,
            onSeekProgress = {
                onSeekProgress(it)
            },
            showPlay = showPlay,
            previousEnabled = previousEnabled,
            nextEnabled = nextEnabled,
            seekEnabled = seekEnabled,
            seekBarInteractionSource = seekBarInteractionSource,
            seekBarIntervals = 16,
            seekBack = seekBack,
            seekForward = seekForward,
            skipBackOnResume = skipBackOnResume,
            currentSegment = currentSegment,
            onClickPlaybackDialogType = onClickPlaybackDialogType,
            initialFocusRequester = playFocusRequester,
            upFocusRequester = restartFocusRequester,
        )
    }
}

/**
 * The two title lines in the top end corner: the series or movie name, then the lighter line
 * ("S1 E3 · Children of the Comet" for an episode, the year for a movie)
 */
fun playerTitleLines(item: BaseItem?): Pair<String?, String?> {
    val seriesName = item?.data?.seriesName
    val seasonNumber = item?.data?.parentIndexNumber
    val episodeNumber = item?.data?.indexNumber
    val episodeName = item?.data?.name
    val rowTitle = item?.title
    val rowSubtitle =
        if (seriesName != null) {
            // Episode: "S1 E3 · Children of the Comet", shown after the series name. Missing parts are left out
            val seasonEpisode =
                listOfNotNull(seasonNumber?.let { "S$it" }, episodeNumber?.let { "E$it" })
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
            listOfNotNull(seasonEpisode, episodeName).joinToString(" · ").takeIf { it.isNotBlank() }
            // Other formats to try, swap one in for the line above:
            // episodeName                                  → Star Trek: Strange New Worlds  Children of the Comet
            // "Episode $episodeNumber (Season $seasonNumber) $episodeName"
            //     → Star Trek: Strange New Worlds  Episode 3 (Season 1) Children of the Comet
            // "$episodeName ($seasonEpisode)"              → Star Trek: Strange New Worlds  Children of the Comet (S1 E3)
        } else {
            // Movie: "Dune  2021", the year in the lighter part. Alternative: "($it)" → Dune  (2021)
            item?.data?.productionYear?.let { "$it" }
        }

    return rowTitle to rowSubtitle
}

/**
 * 1-based number of the chapter playing at [positionMs]
 */
private fun currentChapterNumber(
    chapters: List<Chapter>,
    positionMs: Long,
): Int = chapters.indexOfLast { it.position.inWholeMilliseconds <= positionMs }.coerceAtLeast(0) + 1
