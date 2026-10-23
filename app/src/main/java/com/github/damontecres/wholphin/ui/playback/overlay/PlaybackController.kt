package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
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
) {
    // Tuning values for the "Chapters"/"Queue" hint under the controls, kept local for Live Edit
    val nextHeadlineStyle = MaterialTheme.typography.titleLarge
    val nextHeadlineColor = Color.Unspecified
    val nextHeadlineInset = Spacing.md

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Controller(
            title = item?.title,
            subtitle = item?.subtitleLong,
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
            modifier = Modifier,
        )
        when (nextState) {
            OverlayViewState.CHAPTERS -> {
                Text(
                    text = stringResource(R.string.chapters),
                    style = nextHeadlineStyle,
                    color = nextHeadlineColor,
                    modifier =
                        Modifier
                            .padding(start = nextHeadlineInset, top = 0.dp)
                            .onFocusChanged {
                                if (it.isFocused) onChangeState.invoke(nextState)
                            }.focusable(),
                )
            }

            OverlayViewState.QUEUE -> {
                Text(
                    text = stringResource(R.string.queue),
                    style = nextHeadlineStyle,
                    color = nextHeadlineColor,
                    modifier =
                        Modifier
                            .padding(start = nextHeadlineInset, top = 0.dp)
                            .onFocusChanged {
                                if (it.isFocused) onChangeState.invoke(nextState)
                            }.focusable(),
                )
            }

            else -> {
                Unit
            }
        }
    }
}

internal val titleTextSize = 28.sp
internal val subtitleTextSize = 18.sp

/**
 * A wrapper for the playback controls to show title and other information, plus the actual controls
 *
 * @see PlaybackControls
 */
@Composable
fun Controller(
    title: String?,
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
    subtitle: String? = null,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // Tuning values for the title block above the seek bar, kept local so Live Edit picks up changes.
    // The trickplay preview is placed with titleTextSize/subtitleTextSize, so copy the sizes back
    // there when done.
    val titleStyle = MaterialTheme.typography.titleLarge
    val titleSize = titleTextSize
    // Color.Unspecified = the theme's text color
    val titleColor = Color.Unspecified
    val subtitleStyle = MaterialTheme.typography.titleMedium
    val subtitleSize = subtitleTextSize
    val subtitleColor = Color.Unspecified
    val endsAtStyle = MaterialTheme.typography.labelLarge
    val endsAtColor = MaterialTheme.colorScheme.onSurface
    // How much of the width the title and subtitle may use before they are cut off
    val textWidthFraction = .75f
    // Gap between the title and the subtitle row, and between the title block and the seek bar
    val titleGap = Spacing.sm
    val blockGap = Spacing.sm
    // Indent of the title block, and the gap after "Ends at"
    val startInset = Spacing.md
    val endInset = Spacing.xl
    // How far the title block moves up while the seek bar is focused (makes room for trickplay)
    val bumpOffset = (-32).dp

    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    val verticalOffset by animateDpAsState(
        targetValue = if (seekBarFocused) bumpOffset else 0.dp,
        label = "TitleBumpOffset",
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = Dp.VisibilityThreshold,
            ),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(blockGap),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(titleGap),
            modifier =
                Modifier
                    .padding(start = startInset)
                    .offset(y = verticalOffset),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = titleStyle,
                    fontSize = titleSize,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.fillMaxWidth(textWidthFraction),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
            ) {
                subtitle?.let {
                    Text(
                        text = it,
                        style = subtitleStyle,
                        fontSize = subtitleSize,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.fillMaxWidth(textWidthFraction),
                    )
                }

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
        )
    }
}
