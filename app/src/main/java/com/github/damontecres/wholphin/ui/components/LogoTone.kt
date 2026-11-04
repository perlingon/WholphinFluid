package com.github.damontecres.wholphin.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import coil3.size.Size
import coil3.transform.Transformation

/**
 * How a logo is coloured, worked out from its own pixels
 *
 * @param monochrome true when the logo is only greys (white, black or anything in between);
 * coloured logos are never recoloured
 * @param luminance average brightness of the logo's solid pixels, 0 (black) to 1 (white)
 */
data class LogoTone(
    val monochrome: Boolean,
    val luminance: Float,
)

object LogoTones {
    // A logo brighter than this (0-1) counts as light. Logos that are already dark stay as they are
    val lightLogoLevel = 0.5f

    // A pixel counts as grey when its red, green and blue differ by at most this much (0-255),
    // which leaves room for anti-aliasing and compression noise
    const val GREY_MAX_CHROMA = 36

    // A logo counts as monochrome when at least this share of its solid pixels is grey
    const val GREY_MIN_SHARE = 0.96f

    // How solid (0-255) a pixel has to be to count as part of the logo
    const val SOLID_ALPHA = 128

    private val cache = LruCache<String, LogoTone>(200)

    fun get(url: String?): LogoTone? = url?.let { cache.get(it) }

    fun put(
        url: String,
        tone: LogoTone,
    ) {
        cache.put(url, tone)
    }

    /**
     * Turns every colour into its opposite and keeps the alpha, so a white logo becomes black
     * and any grey shading inside it stays visible, just reversed
     */
    val invertFilter: ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

    /**
     * The filter to draw a logo with: inverted when the logo is monochrome and light and the
     * backdrop behind it is light (see BackdropContrast.headerIsLight), otherwise none
     */
    fun filterFor(
        tone: LogoTone?,
        backdropIsLight: Boolean,
    ): ColorFilter? =
        if (backdropIsLight &&
            tone != null &&
            tone.monochrome &&
            tone.luminance > lightLogoLevel
        ) {
            invertFilter
        } else {
            null
        }

    fun analyse(bitmap: Bitmap): LogoTone? {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return null
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var solid = 0
        var grey = 0
        var lumSum = 0.0
        for (pixel in pixels) {
            if ((pixel ushr 24) < SOLID_ALPHA) continue
            solid++
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (maxOf(r, g, b) - minOf(r, g, b) <= GREY_MAX_CHROMA) grey++
            lumSum += 0.299 * r + 0.587 * g + 0.114 * b
        }
        if (solid == 0) return null
        return LogoTone(
            monochrome = grey >= solid * GREY_MIN_SHARE,
            luminance = (lumSum / solid / 255.0).toFloat(),
        )
    }
}

/**
 * A Coil [Transformation] that leaves the image as it is, but works out its [LogoTone] and
 * stores it in [LogoTones] under [url]. Coil runs transformations on a software bitmap, so the
 * pixels can be read here, which they can't always be once the image is shown
 */
class LogoToneTransformation(
    private val url: String,
) : Transformation() {
    override val cacheKey: String
        get() = "LogoToneTransformation"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        if (LogoTones.get(url) == null) {
            LogoTones.analyse(input)?.let { LogoTones.put(url, it) }
        }
        return input
    }
}
