package com.github.damontecres.wholphin.services

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.BackdropStyle
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Stores state for the backdrop of the app shown on non-full screen pages
 *
 * This is usually the backdrop of the currently focused media
 */
@Singleton
@OptIn(FlowPreview::class)
class BackdropService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val imageUrlService: ImageUrlService,
        private val preferences: DataStore<AppPreferences>,
    ) {
        // How long focus has to rest on an item before its backdrop starts loading
        private val BACKDROP_FOCUS_DELAY_MS = 250L

        private val extractedColorCache = LruCache<String, ExtractedColors>(400)

        private val _backdropFlow = MutableStateFlow<BackdropResult>(BackdropResult.NONE)
        val backdropFlow = _backdropFlow

        /**
         * Update the backdrop to use the specified item
         */
        suspend fun submit(item: BaseItem) =
            withContext(WholphinDispatchers.IO) {
                submit(item.id.toString(), imageUrlService.getPageBackdropUrl(item))
            }

        /**
         * Update the backdrop to use the specified discovered item
         */
        suspend fun submit(item: DiscoverItem) = submit("discover_${item.id}", item.backDropUrl)

        /**
         * Update the backdrop to use the specified URL
         */
        suspend fun submit(
            itemId: String,
            imageUrl: String?,
        ) = withContext(WholphinDispatchers.IO) {
            if (backdropFlow.firstOrNull()?.imageUrl != imageUrl || imageUrl == null) {
                _backdropFlow.update {
                    it.copy(
                        itemId = itemId,
                        imageUrl = null,
                        // A new backdrop is on its way
                        colorsPending = true,
                    )
                }
                extractColors(itemId, imageUrl)
            }
        }

        /**
         * Remove the backdrop, such as when switching pages
         */
        suspend fun clearBackdrop() {
            _backdropFlow.update {
                BackdropResult.NONE
            }
        }

        private suspend fun extractColors(
            itemId: String,
            imageUrl: String?,
        ) {
            // Wait a moment before loading anything, so nothing is fetched for items that are
            // only passed over while scrolling
            delay(BACKDROP_FOCUS_DELAY_MS)
            // Announce the image first, so it can start loading while the colours are worked out
            _backdropFlow.update {
                if (it.itemId == itemId) it.copy(imageUrl = imageUrl, colorsPending = true) else it
            }
            val backdropStyle =
                preferences.data
                    .firstOrNull()
                    ?.interfacePreferences
                    ?.backdropStyle
            val dynamicEnabled =
                backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR ||
                    backdropStyle == BackdropStyle.UNRECOGNIZED
            val colors =
                if (dynamicEnabled) {
                    extractColorsFromBackdrop(imageUrl)
                } else {
                    ExtractedColors.DEFAULT
                }
            _backdropFlow.update {
                if (it.itemId == itemId) {
                    BackdropResult(
                        itemId = itemId,
                        imageUrl = imageUrl,
                        primaryColor = colors?.primary ?: it.primaryColor,
                        secondaryColor = colors?.secondary ?: it.secondaryColor,
                        tertiaryColor = colors?.tertiary ?: it.tertiaryColor,
                    )
                } else {
                    it
                }
            }
        }

        suspend fun extractColorsFromBackdrop(imageUrl: String?): ExtractedColors? =
            withContext(WholphinDispatchers.IO) {
                if (imageUrl.isNullOrBlank()) {
                    return@withContext null
                }
                extractedColorCache.get(imageUrl)?.let {
                    return@withContext it
                }

                try {
                    val loader = context.imageLoader
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            // A small version is plenty for picking colours, and far quicker to
                            // decode than the full image
                            .size(320, 180)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()

                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val drawable = result.image.asDrawable(context.resources)
                        val bitmap = drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                        extractColorsFromBitmap(bitmap).also {
                            extractedColorCache.put(imageUrl, it)
                        }
                    } else {
                        ExtractedColors.DEFAULT
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting colors from URL: $imageUrl")
                    ExtractedColors.DEFAULT
                }
            }

        /**
         * Helper function to determine if a color is "cool" (blue/purple/green) vs "warm" (red/orange/yellow)
         *
         * Cool colors have more blue/green than red
         */
        private val Palette.Swatch.coolColor: Boolean
            get() {
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                return b > r && (b + g) > (r * 1.5f)
            }

        // The vivid colour has to cover more than this share of the image to be used for the glows
        private val VIVID_SHARE_THRESHOLD = 0.35f

        // Swatches within this many degrees of hue count as the same colour as the vivid one
        private val VIVID_HUE_RANGE = 30f

        // Swatches less saturated than this (0-1) are not counted as vivid
        private val VIVID_MIN_SATURATION = 0.3f

        // Swatches lighter than this (0-1) and less saturated than WHITE_MAX_SATURATION count as
        // white. Images with lots of white (lab coats, office ceilings) would otherwise give
        // white or grey glows, so the colours are picked from the rest of the image instead
        private val WHITE_MIN_LIGHTNESS = 0.75f
        private val WHITE_MAX_SATURATION = 0.25f

        // When picking the second most common colour, prefer one whose hue is at least this many
        // degrees away from the most common one, so the glows are not all the same colour
        private val DISTINCT_HUE_RANGE = 40f

        // When white covers more than this share of the image, its most vivid colour is used as
        // an accent for the top-left glow, so the gradient gets some real colour
        private val WHITE_HEAVY_SHARE = 0.3f

        private val Palette.Swatch.isWhitish: Boolean
            get() = hsl[2] >= WHITE_MIN_LIGHTNESS && hsl[1] <= WHITE_MAX_SATURATION

        private fun hueDistance(
            a: Palette.Swatch,
            b: Palette.Swatch,
        ): Float = abs(a.hsl[0] - b.hsl[0]).let { minOf(it, 360f - it) }

        private fun toColor(
            swatch: Palette.Swatch?,
            alpha: Float,
        ): Color = swatch?.rgb?.let(::Color)?.copy(alpha = alpha) ?: Color.Transparent

        /**
         * Extracts colors from a bitmap using Android's Palette API.
         *
         * If the most vivid colour covers more than [VIVID_SHARE_THRESHOLD] of the image, the vivid
         * colours are used:
         * - Primary (Bottom-Right): darkVibrant -> darkMuted -> default
         * - Secondary (Top-Left): Smart selection based on color temperature (prefers cool colors)
         * - Tertiary (Top-Right): vibrant -> lightVibrant -> default
         *
         * Otherwise the most common colours are used:
         * - Primary (Bottom-Right) and Tertiary (Top-Right): most common colour
         * - Secondary (Top-Left): second most common colour
         *
         * @param bitmap The bitmap to extract colors from
         * @return ExtractedColors containing primary, secondary, and tertiary colors
         */
        private suspend fun extractColorsFromBitmap(bitmap: Bitmap): ExtractedColors =
            try {
                val palette = Palette.from(bitmap).generate()

                val vibrant = palette.vibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val lightVibrant = palette.lightVibrantSwatch
                val muted = palette.mutedSwatch
                val darkMuted = palette.darkMutedSwatch
                val lightMuted = palette.lightMutedSwatch
                val dominant = palette.dominantSwatch

                // The colours the image is mostly made of. White is left out when there are other
                // colours to use, so images with lots of white still get coloured glows
                val byCoverage =
                    palette.swatches
                        .sortedByDescending { it.population }
                        .let { all -> all.filterNot { it.isWhitish }.ifEmpty { all } }
                val mostCommon = (dominant?.takeIf { it in byCoverage }) ?: byCoverage.getOrNull(0)
                // Preferably a clearly different colour, for a more varied gradient
                val secondMostCommon =
                    byCoverage.firstOrNull { it != mostCommon && mostCommon != null && hueDistance(it, mostCommon) >= DISTINCT_HUE_RANGE }
                        ?: byCoverage.firstOrNull { it != mostCommon }

                // How much of the image the most vivid colour covers, counting every swatch of a
                // similar hue that is not washed out
                val mostVivid = vibrant ?: lightVibrant ?: darkVibrant
                val totalPopulation = palette.swatches.sumOf { it.population }
                val vividShare =
                    if (mostVivid == null || totalPopulation == 0) {
                        0f
                    } else {
                        val vividHue = mostVivid.hsl[0]
                        palette.swatches
                            .filter {
                                val hueDistance = abs(it.hsl[0] - vividHue).let { d -> minOf(d, 360f - d) }
                                hueDistance <= VIVID_HUE_RANGE && it.hsl[1] >= VIVID_MIN_SATURATION
                            }.sumOf { it.population }
                            .toFloat() / totalPopulation
                    }
                val useVivid = vividShare > VIVID_SHARE_THRESHOLD
                val whiteShare =
                    if (totalPopulation == 0) {
                        0f
                    } else {
                        palette.swatches.filter { it.isWhitish }.sumOf { it.population }.toFloat() / totalPopulation
                    }
                // An accent: the most vivid colour, even when it covers only a small part
                val accent = (mostVivid ?: muted)?.takeIf { whiteShare > WHITE_HEAVY_SHARE && !it.isWhitish }

                // Primary (Bottom-Right)
                val primaryColor =
                    if (useVivid) {
                        toColor(darkVibrant ?: darkMuted, .4f)
                    } else {
                        toColor(mostCommon ?: darkVibrant ?: darkMuted, .4f)
                    }

                // Secondary (Top-Left)
                val secondaryColor =
                    if (useVivid) {
                        // If Vibrant is cool (blue/purple), use it. If Vibrant is warm (yellow/orange)
                        // and Muted is cool, use Muted
                        when {
                            vibrant != null && vibrant.coolColor -> vibrant
                            muted != null && muted.coolColor -> muted
                            vibrant != null -> vibrant
                            muted != null -> muted
                            else -> null
                        }.let { toColor(it, .4f) }
                    } else {
                        toColor(accent ?: secondMostCommon ?: mostCommon ?: vibrant ?: muted, .4f)
                    }

                // Tertiary (Top-Right under image)
                val tertiaryColor =
                    if (useVivid) {
                        toColor(vibrant ?: lightVibrant, .35f)
                    } else {
                        toColor(mostCommon ?: vibrant ?: lightVibrant, .35f)
                    }

                Timber.v(
                    "Vivid colour covers %s of the image, useVivid=%s, white covers %s, accent=%s",
                    vividShare,
                    useVivid,
                    whiteShare,
                    accent != null,
                )
                Timber.v(
                    "Colors extracted: primary=%s, secondary=%s, tertiary=%s",
                    primaryColor,
                    secondaryColor,
                    tertiaryColor,
                )
                ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
            } catch (e: Exception) {
                Timber.e(e, "Error extracting palette colors")
                ExtractedColors.DEFAULT
            }

        fun clearColorCache() {
            extractedColorCache.evictAll()
        }
    }

/**
 * The result from determining the backdrop URL and extracted colors for the dynamic backdrop
 */
data class BackdropResult(
    val itemId: String?,
    val imageUrl: String?,
    val primaryColor: Color = Color.Unspecified,
    val secondaryColor: Color = Color.Unspecified,
    val tertiaryColor: Color = Color.Unspecified,
    // True from the moment focus moves until the new backdrop is complete: first while its image
    // is not known yet (imageUrl is null), then while its colours are being worked out. Until it
    // is false again, the colours here are still the previous backdrop's
    val colorsPending: Boolean = false,
) {
    val hasColors: Boolean =
        primaryColor.isSpecified ||
            secondaryColor.isSpecified ||
            tertiaryColor.isSpecified

    companion object {
        val NONE =
            BackdropResult(
                null,
                null,
            )
    }
}

/**
 * The colors extracted from an image
 */
data class ExtractedColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
) {
    companion object {
        val DEFAULT = ExtractedColors(Color.Unspecified, Color.Unspecified, Color.Unspecified)
    }
}
