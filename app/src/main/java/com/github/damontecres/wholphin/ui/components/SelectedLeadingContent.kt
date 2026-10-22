package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import com.github.damontecres.wholphin.ui.theme.Spacing

/**
 * Shows a dot indicator
 *
 * Intended for using within the `leadingContent` of a [androidx.tv.material3.ListItem]
 */
@Composable
fun BoxScope.SelectedLeadingContent(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Box(
            modifier =
                modifier
                    .padding(horizontal = Spacing.xs)
                    .clip(CircleShape)
                    .align(Alignment.Center)
                    .background(LocalContentColor.current)
                    .size(8.dp),
        )
    }
}
