package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.Locale

/**
 * Kodi-style video adjustment: zoom, pixel ratio and position.
 * Each row has coarse (×5) and fine (×1) steps; D-pad left/right moves between buttons.
 */
@Composable
fun VideoAdjust(
    transform: VideoTransform,
    onChange: (VideoTransform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        AdjustRow(
            label = stringResource(R.string.video_adjust_zoom),
            value = String.format(Locale.US, "%.0f%%", transform.zoom * 100),
            onStep = { onChange(transform.adjustZoom(it)) },
            fineMinusModifier = Modifier.focusRequester(focusRequester),
        )
        AdjustRow(
            label = stringResource(R.string.video_adjust_pixel_ratio),
            value = String.format(Locale.US, "%.2f", transform.pixelRatio),
            onStep = { onChange(transform.adjustRatio(it)) },
        )
        AdjustRow(
            label = stringResource(R.string.video_adjust_shift_vertical),
            value = String.format(Locale.US, "%+.2f%%", transform.shiftY * 100),
            onStep = { onChange(transform.adjustShiftY(it)) },
        )
        AdjustRow(
            label = stringResource(R.string.video_adjust_shift_horizontal),
            value = String.format(Locale.US, "%+.2f%%", transform.shiftX * 100),
            onStep = { onChange(transform.adjustShiftX(it)) },
        )
        SubtitleDelayButton(
            text = stringResource(R.string.reset),
            onClick = { onChange(VideoTransform.IDENTITY) },
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun AdjustRow(
    label: String,
    value: String,
    onStep: (Int) -> Unit,
    fineMinusModifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "$label: $value",
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(220.dp),
        )
        SubtitleDelayButton(text = "−−", onClick = { onStep(-5) })
        SubtitleDelayButton(text = "−", onClick = { onStep(-1) }, modifier = fineMinusModifier)
        SubtitleDelayButton(text = "+", onClick = { onStep(1) })
        SubtitleDelayButton(text = "++", onClick = { onStep(5) })
    }
}

@PreviewTvSpec
@Composable
private fun VideoAdjustPreview() {
    WholphinTheme {
        VideoAdjust(
            transform = VideoTransform(zoom = 1.03f, shiftY = 0.005f),
            onChange = {},
        )
    }
}
