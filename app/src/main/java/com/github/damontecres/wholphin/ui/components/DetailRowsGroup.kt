package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.theme.Spacing

/**
 * Whether the sharp backdrop image should be out of the way, faded out and moved up, because
 * focus is in the rows below a details page's buttons (see [DetailRowsGroup])
 */
object BackdropImageAway {
    var away by mutableStateOf(false)
        internal set
}

/**
 * The rows below the buttons on a movie or series page, together on a dark see-through
 * background. While focus is in them, the sharp backdrop image fades out and moves up
 */
@Composable
fun DetailRowsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Tuning values, kept local so Live Edit picks them up
    // How dark the background behind the rows is: 0 = none, 1 = black
    val backgroundAlpha = 0.5f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    DisposableEffect(Unit) { onDispose { BackdropImageAway.away = false } }
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier =
            modifier
                .fillMaxWidth()
                // Reaches the bottom of the screen even when there are only a few rows
                .defaultMinSize(minHeight = screenHeight)
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .onFocusChanged { BackdropImageAway.away = it.hasFocus }
                .padding(top = Spacing.lg, bottom = Spacing.xxl),
        content = content,
    )
}
