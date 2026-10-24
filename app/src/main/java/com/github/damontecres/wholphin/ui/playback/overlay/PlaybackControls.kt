@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.github.damontecres.wholphin.ui.playback.overlay

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.playback.insetFromSide
import com.github.damontecres.wholphin.ui.playback.rememberPictureSideInsetPx
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Possible actions the user can take during playback
 */
sealed interface PlaybackAction {
    data object ShowDebug : PlaybackAction

    data object ShowPlaylist : PlaybackAction

    data object ShowVideoFilterDialog : PlaybackAction

    data object SearchCaptions : PlaybackAction

    data class ToggleCaptions(
        val index: Int,
    ) : PlaybackAction

    data class ToggleAudio(
        val index: Int,
    ) : PlaybackAction

    data class PlaybackSpeed(
        val value: Float,
    ) : PlaybackAction

    data class Scale(
        val scale: ContentScale,
    ) : PlaybackAction

    data object Previous : PlaybackAction

    data object Next : PlaybackAction
}

/**
 * UI for actual playback controls such as seek bar, play/pause and seek buttons
 */
@OptIn(UnstableApi::class)
@Composable
fun PlaybackControls(
    player: Player,
    controllerViewState: ControllerViewState,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBarIntervals: Int,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val scope = rememberCoroutineScope()

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val onControllerInteraction = {
        scope.launch(ExceptionHandler()) {
            bringIntoViewRequester.bringIntoView()
        }
        controllerViewState.pulseControls()
    }
    LaunchedEffect(controllerViewState.controlsVisible) {
        if (controllerViewState.controlsVisible) {
            initialFocusRequester.tryRequestFocus()
        }
    }
    // Leave the seek bar (and its trickplay preview) after this long without a seek press, by moving
    // focus to play/pause. Tuning value, kept local so Live Edit picks up changes.
    val seekIdleMs = 3000L
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    var seekPresses by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekBarFocused, seekPresses) {
        if (seekBarFocused) {
            delay(seekIdleMs)
            initialFocusRequester.tryRequestFocus()
        }
    }
    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Gap between the play/pause button and the seek bar, kept local so Live Edit picks up changes
        val playButtonGap = Spacing.md
        SeekBar(
            player = player,
            controllerViewState = controllerViewState,
            onSeekProgress = {
                seekPresses++
                onSeekProgress(it)
            },
            interactionSource = seekBarInteractionSource,
            isEnabled = seekEnabled,
            intervals = seekBarIntervals,
            seekBack = seekBack,
            seekForward = seekForward,
            leadingGap = playButtonGap,
            leading = {
                // Play/pause sits to the left of the seek bar, centered on it
                PlaybackButton(
                    modifier = Modifier.focusRequester(initialFocusRequester),
                    iconRes = if (showPlay) R.drawable.baseline_play_arrow_24 else R.drawable.baseline_pause_24,
                    onClick = {
                        onControllerInteraction.invoke()
                        if (showPlay) {
                            player.play()
                            skipBackOnResume?.let {
                                player.seekBack(it)
                            }
                        } else {
                            player.pause()
                        }
                    },
                    onControllerInteraction = onControllerInteraction,
                )
            },
            modifier =
                Modifier
                    .padding(vertical = 0.dp)
                    .fillMaxWidth(.95f),
        )
        Box(
            modifier =
                Modifier
                    // Same width as the seek bar, so the row starts under the play/pause button
                    .fillMaxWidth(.95f),
        ) {
            // Tuning values, kept local so Live Edit picks up changes.
            // Gap between the settings, media and track groups, e.g. Spacing.md (16) / lg (24) / xl (32)
            val groupGap = Spacing.lg

            // All controls sit in one row on the left, under the play/pause button
            Row(
                horizontalArrangement = Arrangement.spacedBy(groupGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                LeftPlaybackButtons(
                    onControllerInteraction = onControllerInteraction,
                    onClickPlaybackDialogType = onClickPlaybackDialogType,
                )

                PlaybackButtons(
                    player = player,
                    initialFocusRequester = initialFocusRequester,
                    onControllerInteraction = onControllerInteraction,
                    onPlaybackActionClick = onPlaybackActionClick,
                    showPlay = showPlay,
                    previousEnabled = previousEnabled,
                    nextEnabled = nextEnabled,
                    seekBack = seekBack,
                    seekForward = seekForward,
                    skipBackOnResume = skipBackOnResume,
                )

                RightPlaybackButtons(
                    onControllerInteraction = onControllerInteraction,
                    onClickPlaybackDialogType = onClickPlaybackDialogType,
                )
            }

            currentSegment?.let { segment ->
                TextButton(
                    stringRes = segment.type.skipStringRes,
                    onClick = {
                        player.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                    },
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = Spacing.xl),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    player: Player,
    isEnabled: Boolean,
    intervals: Int,
    controllerViewState: ControllerViewState,
    onSeekProgress: (Long) -> Unit,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    leadingGap: Dp = 0.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    // Width of the leading content, so the timecodes line up with the bar
    var leadingWidth by remember { mutableStateOf(0.dp) }
    // While the leading content (the play button) has focus, the bar shows its focused look too
    var leadingFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var bufferedProgress by remember(player) { mutableFloatStateOf(player.bufferedPosition.toFloat() / player.duration) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var progress by remember(player) { mutableFloatStateOf(player.currentPosition.toFloat() / player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            bufferedProgress = player.bufferedPosition.toFloat() / player.duration
            position = player.currentPosition
            progress = player.currentPosition.toFloat() / player.duration
            delay(250L)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(leadingGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (leading != null) {
                Box(
                    modifier =
                        Modifier
                            .onSizeChanged { leadingWidth = with(density) { it.width.toDp() } }
                            .onFocusChanged { leadingFocused = it.hasFocus },
                ) {
                    leading()
                }
            }
            IntervalSeekBarImpl(
                progress = progress,
                bufferedProgress = bufferedProgress,
                onSeek = {
                    onSeekProgress(it)
                },
                controllerViewState = controllerViewState,
//                intervals = intervals,
                modifier = Modifier.weight(1f),
                interactionSource = interactionSource,
                enabled = isEnabled,
                durationMs = player.duration,
                seekBack = seekBack,
                seekForward = seekForward,
                highlighted = leadingFocused,
            )
        }
        SeekTimecodes(
            positionMs = position,
            durationMs = player.duration,
            modifier =
                Modifier
                    .padding(start = if (leading != null) leadingWidth + leadingGap else 0.dp)
                    .fillMaxWidth(),
        )
    }
}

@Composable
fun SeekTimecodes(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    // Tuning values for the times under the seek bar, kept local so Live Edit picks up changes.
    // To change only size or weight: MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
    val timeStyle = MaterialTheme.typography.labelLarge
    val timeColor = MaterialTheme.colorScheme.onSurface
    // Space around each time; the top value is the gap to the seek bar
    val timePadding = Spacing.sm

    val currentLayoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val resources = LocalResources.current
            val positionSec = positionMs / 1000
            val remainingSec = (durationMs - positionMs) / 1000
            val positionText =
                remember(positionSec) { resources.formatDuration(positionSec.seconds) }
            val remainingText =
                remember(remainingSec) { "-${resources.formatDuration(remainingSec.seconds)}" }
            CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                Text(
                    text = positionText,
                    color = timeColor,
                    style = timeStyle,
                    modifier =
                        Modifier
                            .padding(timePadding),
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                Text(
                    text = remainingText,
                    color = timeColor,
                    style = timeStyle,
                    modifier =
                        Modifier
                            .padding(timePadding),
                )
            }
        }
    }
}

/** Gap between buttons in the music Now Playing controls */
val buttonSpacing = Spacing.sm

@Composable
fun LeftPlaybackButtons(
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gap between buttons in this group, kept local so Live Edit picks up changes
    val buttonGap = Spacing.sm
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonGap),
    ) {
        PlaybackButton(
            iconRes = R.drawable.vector_settings,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.SETTINGS)
            },
            enabled = true,
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier,
        )
    }
}

@Composable
fun RightPlaybackButtons(
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gap between buttons in this group, kept local so Live Edit picks up changes
    val buttonGap = Spacing.sm
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonGap),
    ) {
        // Captions
        PlaybackButton(
            enabled = true,
            iconRes = R.drawable.ic_subtitles,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.CAPTIONS)
            },
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier,
        )
        // Audio
        PlaybackFaButton(
            enabled = true,
            iconRes = R.string.fa_volume_high,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.AUDIO)
            },
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackButtons(
    player: Player,
    initialFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Gap between the media buttons, kept local so Live Edit picks up changes
        val buttonGap = Spacing.sm
        Row(
            modifier = modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(buttonGap),
        ) {
            // Play/pause sits next to the seek bar and restart in the top corner (PlaybackOverlay);
            // there are no rewind/fast forward buttons, the seek bar does that
            PlaybackButton(
                iconRes = R.drawable.baseline_skip_next_24,
                onClick = {
                    onControllerInteraction.invoke()
                    onPlaybackActionClick.invoke(PlaybackAction.Next)
                },
                enabled = nextEnabled,
                onControllerInteraction = onControllerInteraction,
            )
        }
    }
}

@Composable
fun PlaybackButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    // Tuning values, kept local so Live Edit picks up changes. Keep PlaybackButton and
    // PlaybackFaButton in step.
    // Icon scale: 1f = the original 36 dp buttons, 0.8f = 20% smaller
    val iconScale = 0.8f
    val buttonSize = 36.dp * iconScale
    // Space between the button edge and the icon
    val iconPadding = Spacing.xs * iconScale
    val selectedColor = MaterialTheme.colorScheme.border
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(iconPadding),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(buttonSize)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(iconRes),
            contentDescription = "",
            tint =
                if (LocalTheme.current == AppThemeColors.OLED_BLACK) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
fun PlaybackFaButton(
    @StringRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    textColor: Color = Color.Unspecified,
) {
    // Tuning values, kept local so Live Edit picks up changes. Keep PlaybackButton and
    // PlaybackFaButton in step.
    // Icon scale: 1f = the original 36 dp buttons, 0.8f = 20% smaller
    val iconScale = 0.8f
    val buttonSize = 36.dp * iconScale
    // Space between the button edge and the icon
    val iconPadding = Spacing.xs * iconScale
    // Size of the Font Awesome glyph
    val glyphSize = 18.sp * iconScale
    val selectedColor = MaterialTheme.colorScheme.border
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(iconPadding),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(buttonSize)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Text(
            text = stringResource(iconRes),
            fontSize = glyphSize,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            color =
                if (textColor.isSpecified) {
                    textColor
                } else if (LocalTheme.current == AppThemeColors.OLED_BLACK) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically),
        )
    }
}

/**
 * Show a dialog aligned to the bottom of the screen instead of centered.
 *
 * @param gravity The [Gravity] to align the dialog to left or right
 */
@Composable
fun <T> BottomDialog(
    choices: List<BottomDialogItem<T>>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, BottomDialogItem<T>) -> Unit,
    gravity: Int,
    currentChoice: BottomDialogItem<T>? = null,
) {
    val focusRequesters = remember(choices.size) { List(choices.size) { FocusRequester() } }
    if (currentChoice != null) {
        LaunchedEffect(Unit) {
            choices.indexOfFirstOrNull { it == currentChoice }?.let {
                focusRequesters.getOrNull(it)?.tryRequestFocus()
            }
        }
    }
    // TODO enforcing a width ends up ignore the gravity
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        val sideInsetPx = rememberPictureSideInsetPx()
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.BOTTOM or gravity) // Move down, by default dialogs are in the centre
            window.insetFromSide(sideInsetPx) // Over the picture on ultrawide screens
            window.setDimAmount(0f) // Remove dimmed background of ongoing playback
        }

        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .padding(Spacing.sm)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
//                        .widthIn(max = 240.dp)
                        .wrapContentWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(choices) { index, choice ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ListItem(
                        selected = choice == currentChoice,
                        enabled = choice.enabled,
                        onClick = {
                            onDismissRequest()
                            onSelectChoice(index, choice)
                        },
                        leadingContent = {
                            SelectedLeadingContent(choice == currentChoice)
                        },
                        headlineContent = {
                            Text(
                                text = choice.headline,
                            )
                        },
                        supportingContent = {
                            choice.supporting?.let {
                                Text(
                                    text = it,
                                )
                            }
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier.focusRequester(focusRequesters[index]),
                    )
                }
            }
        }
    }
}

data class BottomDialogItem<T>(
    val data: T,
    val headline: String,
    val supporting: String?,
    val enabled: Boolean = true,
)

@PreviewTvSpec
@Composable
private fun ButtonPreview() {
    WholphinTheme {
        Row(Modifier.background(Color.Red)) {
            PlaybackButton(
                iconRes = R.drawable.baseline_play_arrow_24,
                onClick = {},
                onControllerInteraction = {},
            )
            PlaybackFaButton(
                iconRes = R.string.fa_volume_high,
                onClick = {},
                onControllerInteraction = {},
                textColor = Color.Green,
            )
        }
    }
}
