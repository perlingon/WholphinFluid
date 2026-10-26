package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shows and hides [content] by sliding it in and out while it fades.
 *
 * Unlike AnimatedVisibility's slideIn/slideOut, which move content in whole pixels and so look
 * slightly shaky when the movement is slow, this moves it with a graphics layer translation, which
 * can stop between pixels and looks smooth.
 *
 * @param slideX how far the content sits away when hidden, as a share of its own width
 * (negative = to the left)
 * @param slideY the same as a share of its own height (negative = up)
 */
@Composable
fun SlideFadeVisibility(
    visible: Boolean,
    slideX: Float,
    slideY: Float,
    showSpec: AnimationSpec<Float>,
    hideSpec: AnimationSpec<Float>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) showSpec else hideSpec,
        label = "slideFade",
    )
    if (visible || progress > 0f) {
        Box(
            modifier =
                modifier.graphicsLayer {
                    val away = 1f - progress
                    alpha = progress
                    translationX = slideX * size.width * away
                    translationY = slideY * size.height * away
                },
        ) {
            content()
        }
    }
}
