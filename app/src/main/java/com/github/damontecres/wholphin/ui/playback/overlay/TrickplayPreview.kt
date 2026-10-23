package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.theme.Spacing
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration.Companion.seconds

@Composable
fun TrickplayPreview(
    seekProgressMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
    jumpMs: Long = 0L,
) {
    val jumpSec = jumpMs / 1000L
    val jumpText = remember(jumpSec) { if (jumpSec != 0L) formatJump(jumpSec) else null }
    var jumpShown = false
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
                    jumpText = jumpText,
                )
                jumpShown = true
            }
        }
        val resources = LocalResources.current
        val seekProgressSec = seekProgressMs / 1000L
        val seekText = remember(seekProgressSec) { resources.formatDuration(seekProgressSec.seconds) }
        // Without a preview image, the jump goes next to the timestamp instead
        val labelText = if (!jumpShown && jumpText != null) "$seekText  $jumpText" else seekText
        Text(
            text = labelText,
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
