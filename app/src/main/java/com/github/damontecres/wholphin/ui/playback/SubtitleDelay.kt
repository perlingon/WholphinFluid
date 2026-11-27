package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.SliderBar
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How far the subtitle delay slider reaches each way */
val MAX_SUBTITLE_DELAY = 2.minutes

/**
 * How far one press moves the slider: fine steps first, then bigger ones the longer the key is held
 */
private fun delayStep(repeatCount: Int): Duration =
    when {
        repeatCount < 8 -> 50.milliseconds
        repeatCount < 24 -> 250.milliseconds
        repeatCount < 48 -> 1.seconds
        else -> 5.seconds
    }

/** "+1250 ms", "−300 ms" or "0 ms" */
fun formatSubtitleDelay(delay: Duration): String {
    val ms = delay.inWholeMilliseconds
    return when {
        ms > 0 -> "+$ms ms"
        ms < 0 -> "\u2212${-ms} ms"
        else -> "0 ms"
    }
}

/**
 * A slider for the subtitle delay, from -[MAX_SUBTITLE_DELAY] to +[MAX_SUBTITLE_DELAY]. Left shows
 * subtitles earlier, right later. Holding left or right speeds it up.
 */
@Composable
fun SubtitleDelay(
    delay: Duration,
    onChangeDelay: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    // Held keys repeat faster than the new delay comes back, so steps build on this copy
    var shown by remember { mutableStateOf(delay) }
    LaunchedEffect(delay) { shown = delay }
    val maxMs = MAX_SUBTITLE_DELAY.inWholeMilliseconds
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.width(420.dp),
    ) {
        Text(
            text = stringResource(R.string.subtitle_delay) + ": " + formatSubtitleDelay(shown),
            color = MaterialTheme.colorScheme.onSurface,
        )
        SliderBar(
            value = shown.inWholeMilliseconds,
            min = -maxMs,
            max = maxMs,
            onChange = {},
            enableWrapAround = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        val direction =
                            when (event.key) {
                                Key.DirectionLeft -> -1
                                Key.DirectionRight -> 1
                                else -> return@onPreviewKeyEvent false
                            }
                        if (event.type == KeyEventType.KeyDown) {
                            val step = delayStep(event.nativeKeyEvent.repeatCount)
                            val newDelay =
                                (shown + step * direction)
                                    .coerceIn(-MAX_SUBTITLE_DELAY, MAX_SUBTITLE_DELAY)
                            if (newDelay != shown) {
                                onChangeDelay(newDelay - shown)
                                shown = newDelay
                            }
                        }
                        true
                    },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.subtitle_delay_earlier),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.subtitle_delay_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = {
                onChangeDelay.invoke(-shown)
                shown = Duration.ZERO
            },
            enabled = shown != Duration.ZERO,
        ) {
            Text(text = stringResource(R.string.reset))
        }
    }
}

@Composable
fun SubtitleDelayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.width(64.dp),
        shape =
            ClickableSurfaceDefaults.shape(
                shape = RoundedCornerShape(33),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTvSpec
@Composable
private fun SubtitleDelayPreview() {
    WholphinTheme {
        SubtitleDelay(
            delay = 1.5.seconds,
            onChangeDelay = {},
            modifier = Modifier,
        )
    }
}
