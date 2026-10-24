package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.CornerFade
import com.github.damontecres.wholphin.ui.components.CornerLogo
import com.github.damontecres.wholphin.ui.components.SlideFadeVisibility
import com.github.damontecres.wholphin.ui.components.TimeDisplay
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playback.AnalyticsState
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.theme.FluidLayout
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration

/**
 * The overlay during playback showing controls, seek preview image, debug info, etc
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlaybackOverlay(
    item: BaseItem?,
    chapters: List<Chapter>,
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    showClock: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    showDebugInfo: Boolean,
    currentPlayback: CurrentPlayback?,
    currentSegment: MediaSegmentDto?,
    analyticsState: AnalyticsState,
    queue: List<PlaylistItem>,
    seriesEnded: Boolean?,
    seriesEpisodes: List<BaseItem>,
    onClickEpisode: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    trickplayInfo: TrickplayInfo? = null,
    trickplayUrlFor: (Int) -> String? = { null },
    onClickPlaylist: (BaseItem) -> Unit = {},
    videoAspectRatio: Float? = null,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onCoveredHeightChange: (Dp) -> Unit = {},
    // Leaves the player, from the back button in the top corner
    onBack: () -> Unit = {},
    // Labels shown next to the subtitle and audio buttons when focused
    subtitleLabel: String = "",
    audioLabel: String = "",
) {
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    // Will be used for preview/trick play images
    var seekProgressMs by remember(seekBarFocused) { mutableLongStateOf(player.currentPosition) }
    // Where seeking started, for the total jump in the preview's label
    val seekStartMs = remember(seekBarFocused) { player.currentPosition }
    var seekProgressPercent = (seekProgressMs.toDouble() / player.duration).toFloat()

    val density = LocalDensity.current
    // Focus targets for Up/Down between the back button (top corner) and the play button
    val playFocusRequester = remember { FocusRequester() }
    val restartFocusRequester = remember { FocusRequester() }

    // Seek preview (trickplay) placement, kept local so Live Edit picks up changes
    // true = the preview sits above the title row, false = between the title row and the seek bar
    val previewAboveTitle = true
    // Extra space between the preview and what is below it. Example: 0.dp, Spacing.sm (8.dp), 16.dp, -8.dp to overlap
    val previewGap = Spacing.md

    // Height of the title row above the seek bar, measured after composition
    var titleHeight by remember { mutableStateOf(0.dp) }

    // The seek bar and controls never get wider than a 16:9 video, so they stay over the picture on ultrawide screens
    val controlsMaxWidth = FluidLayout.videoWidth()

    // The logo, clock and the chapter & queue rows stay over the picture: a 16:9 area, or wider for an ultrawide video
    val pictureMaxWidth = FluidLayout.playingVideoWidth(videoAspectRatio)

    // This will be calculated after composition
    var controllerHeight by remember { mutableStateOf(0.dp) }
    // Heights of the shown overlay content (controls or chapter/queue row) and of the seek preview,
    // so the subtitles can sit just above whatever covers the bottom of the screen
    var overlayContentHeight by remember { mutableStateOf(0.dp) }
    var previewBlockHeight by remember { mutableStateOf(0.dp) }
    var state by remember(controllerViewState.controlsVisible) {
        mutableStateOf(if (controllerViewState.controlsVisible) OverlayViewState.CONTROLLER else OverlayViewState.HIDDEN)
    }
    // Chapters only when there's no episode queue: chapter navigation is rarely useful in an
    // episode, so the episode row takes its place
    // The episode row shows for every episode, also the last one, since it navigates the whole series
    val showQueue = nextEnabled || (seriesEpisodes.isNotEmpty() && item?.type == BaseItemKind.EPISODE)
    val showChapters = chapters.isNotEmpty() && !showQueue
    // Coming back up from the chapter or episode row
    var returningFromRow by remember(controllerViewState.controlsVisible) { mutableStateOf(false) }
    val onChangeState = { newState: OverlayViewState ->
        returningFromRow =
            newState == OverlayViewState.CONTROLLER &&
            (state == OverlayViewState.CHAPTERS || state == OverlayViewState.QUEUE)
        state = newState
    }

    // How the controls show up and go away. Tuning values, kept local so Live Edit picks up changes.
    // Durations in ms. Easings: FastOutSlowInEasing (standard), LinearOutSlowInEasing (soft landing),
    // FastOutLinearInEasing (quick exit), LinearEasing, or CubicBezierEasing(a, b, c, d)
    val showMs = 350
    val hideMs = 250
    // When coming back up from the chapter or episode row, the row's focused card goes away with
    // its exit animation and focus can land on the top corner buttons. Once the animation is
    // done, put focus back on play/pause.
    LaunchedEffect(state, returningFromRow) {
        if (state == OverlayViewState.CONTROLLER && returningFromRow) {
            playFocusRequester.tryRequestFocus()
            delay(maxOf(showMs, hideMs) + 50L)
            playFocusRequester.tryRequestFocus()
        }
    }
    val showEasing: Easing = FastOutSlowInEasing
    val hideEasing: Easing = FastOutLinearInEasing
    // How far the controls slide, as a share of their own height (0f = fade only)
    val slideFraction = 0.5f
    // How far the seek preview rises as it fades in, as a share of its block's height
    val previewRise = 0.05f
    // Which way the controls are moving between states (down = towards the rows below). Plain
    // fields: they are only read while the state change recomposes anyway
    val slideDirection = remember { SlideDirection(state) }
    if (slideDirection.previous != state) {
        slideDirection.down = state.ordinal > slideDirection.previous.ordinal
        slideDirection.previous = state
    }
    val showFade = tween<Float>(showMs, easing = showEasing)
    val hideFade = tween<Float>(hideMs, easing = hideEasing)

    // How much of the bottom of the screen the overlay covers: the controls (or row), or the seek
    // preview above them when it shows. Reported so the subtitles can move above it.
    val previewShowing = controllerViewState.controlsVisible && seekBarFocused
    val previewBottom =
        ((if (previewAboveTitle) controllerHeight else controllerHeight - titleHeight) + previewGap)
            .coerceAtLeast(0.dp)
    val coveredHeight =
        when {
            !controllerViewState.controlsVisible || showDebugInfo -> {
                0.dp
            }

            previewShowing && previewBlockHeight > 0.dp -> {
                maxOf(overlayContentHeight, previewBottom + previewBlockHeight)
            }

            else -> {
                overlayContentHeight
            }
        }
    LaunchedEffect(coveredHeight) { onCoveredHeightChange(coveredHeight) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = controllerViewState.controlsVisible && !showDebugInfo,
            enter = fadeIn(showFade),
            exit = fadeOut(hideFade),
            modifier = Modifier.matchParentSize(),
        ) {
            // Background fade behind the controls, for readability. Tuning values, kept local so
            // Live Edit picks up changes: each stop is (position from top 0..1, color)
            val scrimColor = Color.Black
            val scrimStops =
                arrayOf(
                    0f to Color.Transparent,
                    0.4f to scrimColor.copy(alpha = 0.5f),
                    1f to scrimColor.copy(alpha = 0.80f),
                )
            val scrimBrush = Brush.verticalGradient(*scrimStops)
            // The overlay sits inside the system bar insets, which can leave a bright line at the
            // very bottom of the screen; the darkest color continues this far past the bottom edge
            val scrimBleed = Spacing.xxl
            val bleedColor = scrimStops.last().second
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawRect(
                                color = bleedColor,
                                topLeft = Offset(0f, size.height),
                                size = Size(size.width, scrimBleed.toPx()),
                            )
                        }.background(scrimBrush),
            )
        }

        AnimatedContent(
            targetState = state,
            label = "controls transition",
            contentAlignment = Alignment.BottomCenter,
            // Only the fades here; the slide is done below with a graphics layer translation, which
            // can stop between pixels (slideInVertically moves in whole pixels and looks shaky)
            transitionSpec = { fadeIn(showFade).togetherWith(fadeOut(hideFade)) },
        ) { targetState ->
            // Moving down (e.g. the controls showing up) the new content comes up from below and the
            // old one leaves upwards; moving up it is the other way round
            val slide by transition.animateFloat(
                transitionSpec = {
                    // this.targetState: the segment's state, not the overlay state named targetState above
                    if (this.targetState == EnterExitState.Visible) {
                        tween(showMs, easing = showEasing)
                    } else {
                        tween(hideMs, easing = hideEasing)
                    }
                },
                label = "controls slide",
            ) { enterExit ->
                when (enterExit) {
                    EnterExitState.PreEnter -> if (slideDirection.down) slideFraction else -slideFraction
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> if (slideDirection.down) -slideFraction else slideFraction
                }
            }
            // Measure the settled content, not the animating container, so the subtitle lift
            // doesn't get a new height (and a recomposition of the whole page) every frame
            Box(
                modifier =
                    Modifier
                        .onSizeChanged {
                            if (targetState == state) {
                                overlayContentHeight = with(density) { it.height.toDp() }
                            }
                        }.graphicsLayer { translationY = slide * size.height },
            ) {
                when (targetState) {
                    OverlayViewState.HIDDEN -> {
                        // Necessary so the bounds for animation are full width
                        Box(Modifier.fillMaxWidth())
                    }

                    OverlayViewState.CONTROLLER -> {
                        if (seekBarFocused) {
                            LaunchedEffect(Unit) {
                                seekProgressPercent =
                                    (player.currentPosition.toFloat() / player.duration)
                            }
                        }
                        val nextState =
                            remember(showChapters, showQueue) {
                                if (showChapters) {
                                    OverlayViewState.CHAPTERS
                                } else if (showQueue) {
                                    OverlayViewState.QUEUE
                                } else {
                                    null
                                }
                            }
                        PlaybackController(
                            item = item,
                            nextState = nextState,
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
                            onSeekBarChange = {
                                onSeekBarChange(it)
                                seekProgressMs = it
                            },
                            currentSegment = currentSegment,
                            onChangeState = onChangeState,
                            modifier =
                                Modifier
                                    .widthIn(max = controlsMaxWidth)
                                    .padding(bottom = Spacing.sm)
                                    .onGloballyPositioned {
                                        controllerHeight = with(density) { it.size.height.toDp() }
                                    },
                            seekBarInteractionSource = seekBarInteractionSource,
                            onTitleHeightChange = { titleHeight = it },
                            chapters = chapters,
                            queueHeadline = queueHeadline(remember(queue) { groupQueueBySeason(queue) }, item, seriesEnded, seriesEpisodes),
                            playFocusRequester = playFocusRequester,
                            subtitleLabel = subtitleLabel,
                            audioLabel = audioLabel,
                            restartFocusRequester = restartFocusRequester.takeIf { !showDebugInfo },
                        )
                    }

                    OverlayViewState.CHAPTERS -> {
                        if (showChapters) {
                            ChapterRowOverlay(
                                player = player,
                                controllerViewState = controllerViewState,
                                chapters = chapters,
                                hasNext = showQueue,
                                onChangeState = onChangeState,
                                modifier =
                                    Modifier
                                        .widthIn(max = pictureMaxWidth)
                                        .padding(horizontal = Spacing.sm)
                                        .fillMaxWidth(),
                            )
                        }
                    }

                    OverlayViewState.QUEUE -> {
                        if (showQueue) {
                            QueueRowOverlay(
                                player = player,
                                queue = queue,
                                currentItem = item,
                                controllerViewState = controllerViewState,
                                nextState =
                                    remember(showChapters) {
                                        if (showChapters) {
                                            OverlayViewState.CHAPTERS
                                        } else {
                                            OverlayViewState.CONTROLLER
                                        }
                                    },
                                onChangeState = onChangeState,
                                onClickPlaylist = onClickPlaylist,
                                seriesEpisodes = seriesEpisodes,
                                onClickEpisode = onClickEpisode,
                                rowMaxWidth = pictureMaxWidth,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Trickplay: fades in while rising a little. (It used to expand vertically, which
            // re-measured it, and the subtitles placed above it, every frame.)
            SlideFadeVisibility(
                visible = controllerViewState.controlsVisible && seekProgressPercent >= 0 && seekBarFocused,
                slideX = 0f,
                slideY = previewRise,
                showSpec = showFade,
                hideSpec = hideFade,
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .widthIn(max = controlsMaxWidth)
                            .fillMaxWidth(.95f),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .offsetByPercent(
                                    xPercentage = seekProgressPercent.coerceIn(0f, 1f),
                                ).padding(bottom = previewBottom),
                    ) {
                        TrickplayPreview(
                            seekProgressMs = seekProgressMs,
                            trickplayInfo = trickplayInfo,
                            trickplayUrlFor = trickplayUrlFor,
                            totalJumpMs = seekProgressMs - seekStartMs,
                            modifier = Modifier.onSizeChanged { previewBlockHeight = with(density) { it.height.toDp() } },
                        )
                    }
                }
            }
        }

        // Logo in the bottom start corner of the picture while paused with the controls hidden
        val logoImageUrl = LocalImageUrlService.current.rememberImageUrl(item, ImageType.LOGO)
        if (logoImageUrl.isNotNullOrBlank()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = pictureMaxWidth)
                        .fillMaxWidth(),
            ) {
                // Slides in from the corner like the top corners; the fade behind it only fades
                CornerLogo(
                    logoUrl = logoImageUrl,
                    visible = !showDebugInfo && showPlay && !controllerViewState.controlsVisible,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // Top
        // Title in the top end corner while the controls show
        val showTitle = !showDebugInfo && controllerViewState.controlsVisible
        val (titleLine, detailLine) = remember(item) { playerTitleLines(item) }
        // While paused with the controls hidden: the logo in the bottom start corner (CornerLogo,
        // shared with the screensaver), the clock in the top end corner
        val pausedHidden = !showDebugInfo && showPlay && !controllerViewState.controlsVisible
        val showClockNow = pausedHidden && showClock
        // Fades in the top corners behind the logo and the clock, each shown only with its element.
        // Tuning values, kept local so Live Edit picks up changes: each stop is
        // (distance from the corner 0..1, color), and the size is how far the fade reaches.
        val topFadeColor = Color.Black
        val topFadeStops =
            arrayOf(
                0f to topFadeColor.copy(alpha = 0.7f),
                0.5f to topFadeColor.copy(alpha = 0.4f),
                1f to Color.Transparent,
            )
        val titleFadeSize = DpSize(560.dp, 280.dp)
        // Size of the dark fade in the top start corner, behind the back and restart buttons
        val topStartFadeSize = DpSize(560.dp, 280.dp)
        // Title lines in the top end corner: distance from the edges, styles, the gap between the
        // lines, how dim the second line is, and how wide the block may get before lines are cut off.
        // Sizes to try: titleLarge (about 20 sp) + bodyLarge (about 15 sp), or headlineSmall (about
        // 22 sp) + titleMedium (about 15 sp)
        val titleInsetEnd = Spacing.lg
        val titleInsetTop = Spacing.lg
        val titleStyle = MaterialTheme.typography.titleLarge
        val titleWeight = FontWeight.SemiBold
        val detailStyle = MaterialTheme.typography.bodyLarge
        val detailOpacity = 0.7f
        val titleLineGap = Spacing.xs
        val titleMaxWidth = 560.dp
        // Distance of the restart button from the top and start edge of the picture area
        val restartInsetStart = Spacing.lg
        val restartInsetTop = Spacing.lg
        // Gap between the back and restart buttons, and their background without focus (none:
        // the corner fade behind them gives the icons their contrast)
        val topButtonGap = Spacing.sm
        val topButtonBackground = Color.Transparent
        val clockFadeSize = DpSize(400.dp, 200.dp)
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = pictureMaxWidth)
                    .fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(showFade),
                exit = fadeOut(hideFade),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                CornerFade(stops = topFadeStops, fromEnd = true, modifier = Modifier.size(titleFadeSize))
            }
            AnimatedVisibility(
                visible = showClockNow,
                enter = fadeIn(showFade),
                exit = fadeOut(hideFade),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                CornerFade(stops = topFadeStops, fromEnd = true, modifier = Modifier.size(clockFadeSize))
            }
            // "Back" and "Restart" in the top start corner
            val showRestart = !showDebugInfo && controllerViewState.controlsVisible
            // Dark fade behind the back and restart buttons
            AnimatedVisibility(
                visible = showRestart,
                enter = fadeIn(showFade),
                exit = fadeOut(hideFade),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                CornerFade(stops = topFadeStops, fromEnd = false, modifier = Modifier.size(topStartFadeSize))
            }
            SlideFadeVisibility(
                visible = showRestart,
                slideX = -0.5f,
                slideY = -0.5f,
                showSpec = showFade,
                hideSpec = hideFade,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = restartInsetStart, top = restartInsetTop),
            ) {
                // Down from either button goes back to the play button
                val downToPlay =
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            playFocusRequester.tryRequestFocus()
                            true
                        } else {
                            false
                        }
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(topButtonGap)) {
                    PlayerLabelButton(
                        label = stringResource(R.string.back),
                        onClick = { onBack() },
                        onControllerInteraction = { controllerViewState.pulseControls() },
                        containerColor = topButtonBackground,
                        modifier =
                            Modifier
                                .focusRequester(restartFocusRequester)
                                .then(downToPlay),
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = it)
                    }
                    PlayerLabelButton(
                        label = stringResource(R.string.restart),
                        onClick = {
                            player.seekTo(0)
                            controllerViewState.pulseControls()
                        },
                        onControllerInteraction = { controllerViewState.pulseControls() },
                        containerColor = topButtonBackground,
                        modifier = downToPlay,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = it.graphicsLayer { scaleX = -1f },
                        )
                    }
                }
            }
            // Title in the top end corner, on two lines: the name, then the lighter line
            // ("S1 E4 · Episode title", or the year for a movie)
            SlideFadeVisibility(
                visible = showTitle,
                slideX = 0.5f,
                slideY = -0.5f,
                showSpec = showFade,
                hideSpec = hideFade,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = titleInsetEnd, top = titleInsetTop),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(titleLineGap),
                    modifier = Modifier.widthIn(max = titleMaxWidth),
                ) {
                    titleLine?.let {
                        Text(
                            text = it,
                            style = titleStyle,
                            fontWeight = titleWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                        )
                    }
                    detailLine?.let {
                        Text(
                            text = it,
                            style = detailStyle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = detailOpacity),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            SlideFadeVisibility(
                visible = showClockNow,
                slideX = 0.5f,
                slideY = -0.5f,
                showSpec = showFade,
                hideSpec = hideFade,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd),
            ) {
                TimeDisplay()
            }
        }

        // Debug overlay
        AnimatedVisibility(
            visible = showDebugInfo && controllerViewState.controlsVisible,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopStart),
        ) {
            val configuration = LocalConfiguration.current
            val height =
                remember(
                    configuration,
                    controllerHeight,
                ) { configuration.screenHeightDp.dp - controllerHeight }
            PlaybackDebugOverlay(
                analyticsState = analyticsState,
                currentPlayback = currentPlayback,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .heightIn(max = height)
                        .padding(bottom = Spacing.md)
                        .background(AppColors.TransparentBlack50)
                        .padding(Spacing.sm)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                // If Debug overlay gains focus, do not hide the controls
                                controllerViewState.pulseControls(Long.MAX_VALUE)
                            } else {
                                controllerViewState.pulseControls()
                            }
                        },
            )
        }
    }
}

/**
 * The view state of the overlay
 */
enum class OverlayViewState {
    HIDDEN,
    CONTROLLER,
    CHAPTERS,
    QUEUE,
}

/** Remembers the last overlay state, to tell which way the controls move on a change */
private class SlideDirection(
    var previous: OverlayViewState,
) {
    var down: Boolean = true
}
