package com.github.damontecres.wholphin.util

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi

/**
 * Removes formatting embedded in text subtitles (such as `<font color>` in SRT or SSA/ASS styles
 * rendered by ExoPlayer) so that the user's subtitle style is used instead.
 *
 * Positioning and line breaks are kept.
 */
@OptIn(UnstableApi::class)
fun List<Cue>.stripEmbeddedFormatting(
    colors: Boolean,
    styles: Boolean,
): List<Cue> {
    if (!colors && !styles) return this
    return map { it.stripEmbeddedFormatting(colors, styles) }
}

@OptIn(UnstableApi::class)
private fun Cue.stripEmbeddedFormatting(
    colors: Boolean,
    styles: Boolean,
): Cue {
    val text = text
    if (bitmap != null || text == null) return this
    val builder = buildUpon()
    if (colors) {
        builder.clearWindowColor()
    }
    if (text is Spanned) {
        val stripped = SpannableString(text)
        stripped.getSpans(0, stripped.length, Any::class.java).forEach { span ->
            val remove =
                when (span) {
                    is ForegroundColorSpan, is BackgroundColorSpan -> colors
                    is StyleSpan, is UnderlineSpan, is StrikethroughSpan, is TypefaceSpan -> styles
                    else -> false
                }
            if (remove) stripped.removeSpan(span)
        }
        builder.setText(stripped)
    }
    return builder.build()
}
