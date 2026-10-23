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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    onTitleHeightChange: (Dp) -> Unit = {},
    queueHeadline: String? = null,
) {
    // Tuning values for the "Chapters"/"Queue" hint under the controls, kept local for Live Edit
    val nextHeadlineStyle = MaterialTheme.typography.labelLarge
    val nextHeadlineColor = Color.Unspecified
    val nextHeadlineInset = Spacing.xl

    // Text of the row above the timeline: the series or movie name, then this lighter part (styled in Controller)
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

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        Controller(
            title = rowTitle,
            subtitle = rowSubtitle,
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
                    text = queueHeadline ?: stringResource(R.string.queue),
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
    onTitleHeightChange: (Dp) -> Unit = {},
) {
    // Tuning values for the title block above the seek bar, kept local so Live Edit picks up changes.
    // The trickplay preview measures this block, so it follows any size here.
    val titleStyle = MaterialTheme.typography.labelLarge
    val titleSize = 14.sp
    // titleTextSize
    // Color.Unspecified = the theme's text color
    val titleColor = Color.Unspecified
    val subtitleStyle = MaterialTheme.typography.labelLarge
    val subtitleSize = 14.sp
    // subtitleTextSize
    val subtitleColor = Color.Unspecified
    // Two-tone row: the name uses the title values above, the part after it (" S1 E3 · Children of the Comet")
    // is drawn in the same line with its own weight and colour
    // Weight of the name. Example: FontWeight.Normal, FontWeight.SemiBold, FontWeight.Bold
    val titleWeight = FontWeight.SemiBold
    // Weight of the part after the name. Example: FontWeight.Light, FontWeight.Normal, FontWeight.Medium
    val subtitleWeight = FontWeight.Normal
    // How dim the part after the name is, as text opacity. Example: 0.5f (dim), 0.7f, 1f (same as the name)
    val subtitleOpacity = 0.7f
    // Text between the name and the rest. Example: " | ", "  |  ", " · ", " – "
    val nameSeparator = " · "
    // Colour of the separator; Color.Unspecified = the same colour as the title. Example: Color.White.copy(alpha = 0.5f)
    val separatorColor = Color.Unspecified
    val endsAtStyle = MaterialTheme.typography.labelLarge
    val endsAtColor = MaterialTheme.colorScheme.onSurface
    // How much of the width the title and subtitle may use before they are cut off
    val textWidthFraction = .75f
    // Gap between the title and the subtitle row, and between the title block and the seek bar
    val titleGap = Spacing.sm
    val blockGap = Spacing.sm
    // Indent of the title block, and the gap after "Ends at"
    val startInset = Spacing.xl
    val endInset = Spacing.xl
    // How far the title block moves up while the seek bar is focused (makes room for trickplay)
    val bumpOffset = (0).dp

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

    Column(
        verticalArrangement = Arrangement.spacedBy(blockGap),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(titleGap),
            modifier =
                Modifier
                    .padding(start = startInset)
                    .offset(y = verticalOffset)
                    // The seek preview is placed from this measured height (see PlaybackOverlay)
                    .onSizeChanged { onTitleHeightChange(with(density) { it.height.toDp() }) },
        ) {
            // Title and subtitle side by side on one row, "Ends at" on the right
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val baseColor =
                    if (titleColor.isSpecified) titleColor else MaterialTheme.colorScheme.onSurface
                val restColor =
                    (if (subtitleColor.isSpecified) subtitleColor else baseColor).copy(alpha = subtitleOpacity)
                val rowText =
                    buildAnnotatedString {
                        title?.let {
                            withStyle(SpanStyle(fontWeight = titleWeight, color = baseColor)) { append(it) }
                        }
                        subtitle?.let {
                            if (title != null) {
                                withStyle(
                                    SpanStyle(
                                        color = if (separatorColor.isSpecified) separatorColor else baseColor,
                                        fontWeight = subtitleWeight,
                                    ),
                                ) {
                                    append(nameSeparator)
                                }
                            }
                            withStyle(
                                SpanStyle(
                                    fontSize = subtitleSize,
                                    fontWeight = subtitleWeight,
                                    color = restColor,
                                    fontFamily = subtitleStyle.fontFamily,
                                ),
                            ) { append(it) }
                        }
                    }
                Text(
                    text = rowText,
                    style = titleStyle,
                    fontSize = titleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

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
