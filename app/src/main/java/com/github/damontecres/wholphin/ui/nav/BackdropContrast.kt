package com.github.damontecres.wholphin.ui.nav

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import kotlin.math.pow

/**
 * Whether the backdrop behind the page's text is so light that white text is hard to read, for
 * each part of the screen that has text straight on the backdrop. Worked out by [Backdrop] from
 * the background it shows, which already has the colour glows and the flat edge colours in it.
 * Text in those parts then turns [DarkText]
 */
object BackdropContrast {
    /** Behind the title, details and overview at the top left */
    var headerIsLight by mutableStateOf(false)
        internal set

    /** Behind the row headlines, down the left side */
    var rowsIsLight by mutableStateOf(false)
        internal set

    /** Behind the clock in the top right corner */
    var clockIsLight by mutableStateOf(false)
        internal set

    /** The almost black used for text on a light backdrop */
    val DarkText = Color(0xFF141414)

    internal fun reset() {
        headerIsLight = false
        rowsIsLight = false
        clockIsLight = false
    }

    /** The colour for a row headline, [default] unless the backdrop behind the rows is light */
    @Composable
    @ReadOnlyComposable
    fun rowText(default: Color = MaterialTheme.colorScheme.onBackground): Color = if (rowsIsLight) DarkText else default

    /** The colour for the clock, [default] unless the backdrop behind it is light */
    @Composable
    @ReadOnlyComposable
    fun clockText(default: Color = MaterialTheme.colorScheme.onSurface): Color = if (clockIsLight) DarkText else default
}

/**
 * Puts [BackdropContrast] back to white text when the backdrop leaves the screen. Lives here rather
 * than in Backdrop.kt, because the object onDispose makes would stop Live Edit working in that file
 */
@Composable
internal fun ResetBackdropContrastOnLeave() {
    DisposableEffect(Unit) { onDispose { BackdropContrast.reset() } }
}

/**
 * The page's title, details and overview: on a light backdrop, its text colours are swapped for
 * [BackdropContrast.DarkText]
 */
@Composable
fun OnBackdropHeader(content: @Composable () -> Unit) {
    // Always the same structure, so nothing inside is reset (or loses focus) when it switches
    val light = BackdropContrast.headerIsLight
    val dark = BackdropContrast.DarkText
    val scheme = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme =
            if (light) {
                scheme.copy(
                    onSurface = dark,
                    onBackground = dark,
                    // Secondary text stays a little lighter than the main text, as it does on dark
                    onSurfaceVariant = dark.copy(alpha = .8f),
                )
            } else {
                scheme
            },
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (light) dark else LocalContentColor.current,
            content = content,
        )
    }
}

/**
 * How light an area of [bitmap] is, as relative luminance from 0 (black) to 1 (white): the level
 * that [percentile] of its pixels are at or below, so a bright patch counts even if the rest is dark.
 * The area is given as shares of the bitmap's width and height
 */
internal fun areaLuminance(
    bitmap: Bitmap,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    percentile: Float,
): Float {
    val x0 = (bitmap.width * left).toInt().coerceIn(0, bitmap.width - 1)
    val y0 = (bitmap.height * top).toInt().coerceIn(0, bitmap.height - 1)
    val x1 = (bitmap.width * right).toInt().coerceIn(x0 + 1, bitmap.width)
    val y1 = (bitmap.height * bottom).toInt().coerceIn(y0 + 1, bitmap.height)
    val w = x1 - x0
    val h = y1 - y0
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, x0, y0, w, h)

    fun linear(channel: Int): Float {
        val c = channel / 255f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }
    val levels =
        FloatArray(pixels.size) { i ->
            val p = pixels[i]
            0.2126f * linear((p shr 16) and 0xFF) + 0.7152f * linear((p shr 8) and 0xFF) + 0.0722f * linear(p and 0xFF)
        }
    levels.sort()
    return levels[((levels.size - 1) * percentile.coerceIn(0f, 1f)).toInt()]
}
