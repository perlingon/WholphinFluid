package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.theme.Spacing
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration.Companion.seconds

@Composable
fun TrickplayPreview(
    seekProgressMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
    totalJumpMs: Long = 0L,
) {
    // How long the jump chip ("+1m 20s") stays after the latest press, and how long it takes to fade
    // out, leaving the bare image. Tuning values, kept local so Live Edit picks up changes.
    val chipShowMs = 1000L
    val chipFadeMs = 400

    val totalSec = totalJumpMs / 1000L
    val totalText = remember(totalSec) { if (totalSec != 0L) formatJump(totalSec) else null }
    val resources = LocalResources.current
    val seekProgressSec = seekProgressMs / 1000L
    val seekText = remember(seekProgressSec) { resources.formatDuration(seekProgressSec.seconds) }
    // After jumping away and coming back to the start (Back on the remote, or stepping back), show
    // "Back to 12:34" in the same spot instead of a jump
    var hasJumped by remember { mutableStateOf(false) }
    LaunchedEffect(totalSec) { if (totalSec != 0L) hasJumped = true }
    val chipText = totalText ?: if (hasJumped) resources.getString(R.string.seek_back_to, seekText) else null
    var chipVisible by remember { mutableStateOf(false) }
    LaunchedEffect(seekProgressMs) {
        chipVisible = true
        delay(chipShowMs)
        chipVisible = false
    }
    val chipAlpha by animateFloatAsState(
        targetValue = if (chipVisible) 1f else 0f,
        animationSpec = tween(if (chipVisible) 0 else chipFadeMs),
        label = "jump chip",
    )
    var imageShown = false
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (trickplayInfo != null) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val index =
                (seekProgressMs / trickplayInfo.interval).toInt() / tilesPerImage
            val imageUrl = remember(index) { trickplayUrlFor(index) }

            if (imageUrl != null) {
                SeekPreviewImage(
                    modifier = Modifier,
                    previewImageUrl = imageUrl,
                    seekProgressMs = seekProgressMs,
                    trickPlayInfo = trickplayInfo,
                    jumpText = chipText,
                    jumpAlpha = chipAlpha,
                )
                imageShown = true
            }
        }
        // Without a preview image, fall back to a label with the time and the total jump
        if (!imageShown) {
            Text(
                text =
                    when {
                        totalText != null -> "$seekText · $totalText"
                        chipText != null -> chipText
                        else -> seekText
                    },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp),
                        ).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

/**
 * Format how far the user has jumped from where seeking started, e.g. "+10s", "-1m 30s", "+1h 5m"
 */
internal fun formatJump(jumpSec: Long): String {
    val sign = if (jumpSec < 0) "-" else "+"
    val total = kotlin.math.abs(jumpSec)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val parts =
        buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0 && h == 0L) add("${s}s")
        }
    return sign + parts.joinToString(" ")
}
