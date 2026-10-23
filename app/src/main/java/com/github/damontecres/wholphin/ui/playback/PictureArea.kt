package com.github.damontecres.wholphin.ui.playback

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gap between each side of the screen and the picture area of the playing video, see
 * [com.github.damontecres.wholphin.ui.theme.FluidLayout.playingVideoWidth]. Zero outside playback
 * or when the picture fills the screen
 */
val LocalPictureSideInset = compositionLocalOf { 0.dp }

@Composable
fun rememberPictureSideInsetPx(): Int = with(LocalDensity.current) { LocalPictureSideInset.current.roundToPx() }

/**
 * Moves a dialog window that opens at the left or right edge of the screen in by [px], so it sits
 * over the picture. With START/END gravity the window's x is the offset from that edge
 */
fun Window.insetFromSide(px: Int) {
    if (attributes.x != px) {
        attributes = attributes.apply { x = px }
    }
}

fun pictureSideInset(
    screenWidth: Dp,
    pictureWidth: Dp,
): Dp = ((screenWidth - pictureWidth) / 2).coerceAtLeast(0.dp)
