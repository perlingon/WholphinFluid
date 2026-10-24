@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.github.damontecres.wholphin.ui.playback.overlay

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    // Where Up from the play button or seek bar goes (the back button), null when it isn't shown
    upFocusRequester: FocusRequester? = null,
    // Where the seek bar starts on screen, so the title above can line up with it
    onBarStartChange: (Dp) -> Unit = {},
    // The "Season 1 · 4 episodes left" / "Chapter 5 of 23" hint, at the right end of the button row
    nextHint: (@Composable () -> Unit)? = null,
    // Down from the button row, to open the chapter or episode row; null when there is none
    onDownFromRow: (() -> Unit)? = null,
    subtitleLabel: String = "",
    audioLabel: String = "",
) {
    // Down from the play button or seek bar goes to subtitles, the first button in the row below
    val rowFocusRequester = remember { FocusRequester() }
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
            onBarStartChange = onBarStartChange,
            leading = {
                // Play/pause sits to the left of the seek bar, centered on it
                // Same size as the other player buttons (see PlaybackButton)
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
                    .fillMaxWidth(.95f)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                upFocusRequester?.let {
                                    it.tryRequestFocus()
                                    true
                                } ?: false
                            }

                            Key.DirectionDown -> {
                                rowFocusRequester.tryRequestFocus()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        )
        Box(
            modifier =
                Modifier
                    // Same width as the seek bar, so the row starts under the play/pause button
                    .fillMaxWidth(.95f)
                    // Down from the row opens the chapter or episode row
                    .onPreviewKeyEvent { event ->
                        if (onDownFromRow != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onDownFromRow()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            // Tuning values, kept local so Live Edit picks up changes.
            // Gap between the settings, media and track groups, e.g. Spacing.md (16) / lg (24) / xl (32)
            val groupGap = Spacing.lg
            // Buttons in this row: smaller than the play button and without a background (the
            // dark fade behind the controls gives the icons their contrast)
            val rowButtonSize = 32.dp
            val rowIconSize = 24.dp
            val rowButtonBackground = Color.Transparent

            // All controls sit in one row on the left, under the play/pause button
            Row(
                horizontalArrangement = Arrangement.spacedBy(groupGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                // Subtitles and audio first, then settings
                RightPlaybackButtons(
                    onControllerInteraction = onControllerInteraction,
                    onClickPlaybackDialogType = onClickPlaybackDialogType,
                    subtitlesModifier = Modifier.focusRequester(rowFocusRequester),
                    subtitleLabel = subtitleLabel,
                    audioLabel = audioLabel,
                    buttonSize = rowButtonSize,
                    iconSize = rowIconSize,
                    containerColor = rowButtonBackground,
                )

                LeftPlaybackButtons(
                    onControllerInteraction = onControllerInteraction,
                    onClickPlaybackDialogType = onClickPlaybackDialogType,
                    buttonSize = rowButtonSize,
                    iconSize = rowIconSize,
                    containerColor = rowButtonBackground,
                )
            }

            // On the right: the skip button when there is a segment, then the chapter/episode hint
            Row(
                horizontalArrangement = Arrangement.spacedBy(groupGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                currentSegment?.let { segment ->
                    TextButton(
                        stringRes = segment.type.skipStringRes,
                        onClick = {
                            player.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                        },
                    )
                }
                nextHint?.invoke()
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
    onBarStartChange: (Dp) -> Unit = {},
) {
    // Width of the leading content, so the timecodes line up with the bar
    var leadingWidth by remember { mutableStateOf(0.dp) }
    // Heights of the leading content and of the bar, so the times can sit close under the bar
    // even though the play button makes the row taller
    var leadingHeight by remember { mutableStateOf(0.dp) }
    var barBoxHeight by remember { mutableStateOf(0.dp) }
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
    // How far the times move up from below the row: the empty space under the bar, plus a nudge
    // (positive = closer to the bar), kept local so Live Edit picks up changes
    val timecodeNudge = 0.dp
    val timecodeLift = ((leadingHeight - barBoxHeight) / 2).coerceAtLeast(0.dp) + timecodeNudge
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
                            .onSizeChanged {
                                leadingWidth = with(density) { it.width.toDp() }
                                leadingHeight = with(density) { it.height.toDp() }
                            }.onFocusChanged { leadingFocused = it.hasFocus },
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
                modifier =
                    Modifier
                        .weight(1f)
                        .onSizeChanged { barBoxHeight = with(density) { it.height.toDp() } }
                        .onGloballyPositioned {
                            // The bar itself is drawn inset by Spacing.xs inside this box
                            onBarStartChange(with(density) { it.positionInRoot().x.toDp() } + Spacing.xs)
                        },
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
                    .fillMaxWidth()
                    // Drawn up next to the bar, and the space under the bar is given back, so
                    // the button row below comes closer too
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val lift = timecodeLift.roundToPx()
                        layout(placeable.width, (placeable.height - lift).coerceAtLeast(0)) {
                            placeable.place(0, -lift)
                        }
                    },
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
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 28.dp,
    containerColor: Color = AppColors.TransparentBlack25,
) {
    // Gap between buttons in this group, kept local so Live Edit picks up changes
    val buttonGap = Spacing.sm
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonGap),
    ) {
        PlayerLabelButton(
            label = stringResource(R.string.adjust_video),
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.SETTINGS)
            },
            onControllerInteraction = onControllerInteraction,
            buttonSize = buttonSize,
            iconSize = iconSize,
            containerColor = containerColor,
        ) {
            Icon(painter = painterResource(R.drawable.ic_video_settings), contentDescription = null, modifier = it)
        }
    }
}

@Composable
fun RightPlaybackButtons(
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    modifier: Modifier = Modifier,
    subtitlesModifier: Modifier = Modifier,
    subtitleLabel: String = "",
    audioLabel: String = "",
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 28.dp,
    containerColor: Color = AppColors.TransparentBlack25,
) {
    // Gap between buttons in this group, kept local so Live Edit picks up changes
    val buttonGap = Spacing.sm
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonGap),
    ) {
        // Captions, labelled with the chosen subtitle language
        PlayerLabelButton(
            label = subtitleLabel.ifBlank { stringResource(R.string.subtitles) },
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.CAPTIONS)
            },
            onControllerInteraction = onControllerInteraction,
            buttonSize = buttonSize,
            iconSize = iconSize,
            containerColor = containerColor,
            modifier = subtitlesModifier,
        ) {
            Icon(painter = painterResource(R.drawable.ic_subtitles), contentDescription = null, modifier = it)
        }
        // Audio, labelled with the chosen audio track
        PlayerLabelButton(
            label = audioLabel.ifBlank { stringResource(R.string.audio) },
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.AUDIO)
            },
            onControllerInteraction = onControllerInteraction,
            buttonSize = buttonSize,
            iconSize = iconSize,
            containerColor = containerColor,
        ) { iconModifier ->
            Box(modifier = iconModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.fa_volume_high),
                    // 18 sp at the 28 dp icon size, scaled with it
                    fontSize = (iconSize.value * 18f / 28f).sp,
                    fontFamily = FontAwesome,
                    textAlign = TextAlign.Center,
                    color = LocalContentColor.current,
                )
            }
        }
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

/**
 * A round player button that grows a label to the right of its icon while it has focus, like the
 * back and restart buttons. [icon] gets a modifier that gives it the icon's size.
 */
@Composable
fun PlayerLabelButton(
    label: String,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Circle size without focus and icon size; the row under the seek bar passes smaller ones
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 28.dp,
    // Background without focus
    containerColor: Color = AppColors.TransparentBlack25,
    icon: @Composable (Modifier) -> Unit,
) {
    // Tuning values, kept local so Live Edit picks up changes
    // Gap between the icon and the label, and after the label
    val labelGap = Spacing.sm
    // Longest label before it is cut off with "…"
    val labelMaxWidth = 260.dp
    // How dim the "1/4" track count at the end of a label is
    val countOpacity = 0.6f
    val labelStyle = MaterialTheme.typography.titleSmall
    // Dark and see-through without focus; white with dark icon and label with focus
    val contentColor = MaterialTheme.colorScheme.onSurface
    val focusedContainerColor = MaterialTheme.colorScheme.onSurface
    val focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Button(
        enabled = enabled,
        onClick = onClick,
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = containerColor,
                contentColor = contentColor,
                focusedContainerColor = focusedContainerColor,
                focusedContentColor = focusedContentColor,
                pressedContainerColor = focusedContainerColor,
                pressedContentColor = focusedContentColor,
            ),
        contentPadding = PaddingValues((buttonSize - iconSize) / 2),
        contentHeight = iconSize,
        interactionSource = interactionSource,
        modifier =
            modifier
                .height(buttonSize)
                .widthIn(min = buttonSize)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        icon(Modifier.size(iconSize))
        AnimatedVisibility(isFocused) {
            // A trailing "1/4" (which track of how many) is drawn dimmer
            val countStart = Regex(" \\d+/\\d+$").find(label)?.range?.first
            val labelText =
                buildAnnotatedString {
                    if (countStart == null) {
                        append(label)
                    } else {
                        append(label.substring(0, countStart))
                        withStyle(SpanStyle(color = LocalContentColor.current.copy(alpha = countOpacity))) {
                            append(label.substring(countStart))
                        }
                    }
                }
            Text(
                text = labelText,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(start = labelGap, end = labelGap)
                        .widthIn(max = labelMaxWidth),
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
    // Overrides the size below, e.g. for the play button next to the seek bar
    size: Dp? = null,
    padding: Dp? = null,
) {
    // Tuning values, kept local so Live Edit picks up changes. Keep PlaybackButton and
    // PlaybackFaButton in step.
    // Icon scale: 1f = the original 36 dp buttons, 0.8f = 20% smaller. 36 dp matches the back and
    // restart buttons in the top corner (topButtonSize in PlaybackOverlay)
    val iconScale = 1f
    val buttonSize = size ?: (36.dp * iconScale)
    // Space between the button edge and the icon
    val iconPadding = padding ?: (Spacing.xs * iconScale)
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
    // Icon scale: 1f = the original 36 dp buttons, 0.8f = 20% smaller. 36 dp matches the back and
    // restart buttons in the top corner (topButtonSize in PlaybackOverlay)
    val iconScale = 1f
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
