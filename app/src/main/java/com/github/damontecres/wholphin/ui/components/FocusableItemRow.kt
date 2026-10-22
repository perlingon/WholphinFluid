package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.Spacing
import com.github.damontecres.wholphin.ui.theme.rowHeadline

/**
 * Placeholder for [com.github.damontecres.wholphin.ui.cards.ItemRow]. It is [focusable] so it can be scrolled.
 */
@Composable
@NonRestartableComposable
fun FocusableItemRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) = FocusableItemRow(
    titleContent = {
        Text(
            text = title,
            style = MaterialTheme.typography.rowHeadline,
            color = MaterialTheme.colorScheme.onBackground,
        )
    },
    subtitleContent = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    },
    modifier = modifier,
)

@Composable
fun FocusableItemRow(
    titleContent: @Composable () -> Unit,
    subtitleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val background by animateColorAsState(
        if (focused) {
            MaterialTheme.colorScheme.border.copy(alpha = .25f)
        } else {
            Color.Transparent
        },
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier =
            modifier
                .padding(start = Spacing.sm)
                .focusable(interactionSource = interactionSource)
                .background(background, shape = RoundedCornerShape(8.dp))
                .padding(Spacing.sm),
    ) {
        titleContent.invoke()
        subtitleContent.invoke()
    }
}
