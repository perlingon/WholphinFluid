package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.util.LocalClock

/**
 * Displays the [LocalClock] in the upper right corner of the parent [androidx.compose.foundation.layout.Box]
 */
@Composable
fun BoxScope.TimeDisplay(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val timeString by LocalClock.current.timeString
    Text(
        text = timeString,
        fontSize = 18.sp,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(vertical = Spacing.md, horizontal = Spacing.lg),
    )
}
