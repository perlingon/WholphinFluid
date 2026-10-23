package com.github.damontecres.wholphin.ui.preferences.subtitle

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.mpv.MPVLib
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppClickablePreference
import com.github.damontecres.wholphin.preferences.AppDestinationPreference
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.BackgroundStyle
import com.github.damontecres.wholphin.preferences.EdgeStyle
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.preferences.SubtitlePreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.ConditionalPreferences
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.util.Media3SubtitleOverride
import com.github.damontecres.wholphin.util.mpv.setPropertyColor
import timber.log.Timber

object SubtitleSettings {
    val FontSize =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.font_size,
            defaultValue = 24,
            min = 8,
            max = 70,
            interval = 2,
            getter = {
                it.fontSize.toLong()
            },
            setter = { prefs, value ->
                prefs.update { fontSize = value.toInt() }
            },
            summarizer = { value -> value?.toString() },
        )

    private val colorList =
        listOf(
            Color.White,
            Color.Black,
            Color.LightGray,
            Color.DarkGray,
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
        )

    val FontColor =
        AppChoicePreference<SubtitlePreferences, Color>(
            title = R.string.font_color,
            defaultValue = Color.White,
            getter = { Color(it.fontColor) },
            setter = { prefs, value ->
                prefs.update { fontColor = value.toArgb().and(0x00FFFFFF) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.White },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val FontWeight =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.subtitle_font_weight,
            defaultValue = 400,
            min = 300,
            max = 700,
            interval = 10,
            getter = { it.effectiveFontWeight.toLong() },
            setter = { prefs, value ->
                prefs.update {
                    fontWeight = value.toInt()
                    fontBold = false
                }
            },
            summarizer = { value -> value?.toString() },
        )
    val FontItalic =
        AppSwitchPreference<SubtitlePreferences>(
            title = R.string.italic_font,
            defaultValue = false,
            getter = { it.fontItalic },
            setter = { prefs, value ->
                prefs.update { fontItalic = value }
            },
        )

    val FontOpacity =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.font_opacity,
            defaultValue = 100,
            min = 10,
            max = 100,
            interval = 10,
            getter = {
                it.fontOpacity
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.update { fontOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val EdgeStylePref =
        AppChoicePreference<SubtitlePreferences, EdgeStyle>(
            title =
                R.string.edge_style,
            defaultValue = EdgeStyle.EDGE_SOLID,
            getter = { it.edgeStyle },
            setter = { prefs, value ->
                prefs.update { edgeStyle = value }
            },
            displayValues = R.array.subtitle_edge,
            indexToValue = { EdgeStyle.forNumber(it) },
            valueToIndex = { it.number },
        )

    val EdgeColor =
        AppChoicePreference<SubtitlePreferences, Color>(
            title = R.string.edge_color,
            defaultValue = Color.Black,
            getter = { Color(it.edgeColor) },
            setter = { prefs, value ->
                prefs.update { edgeColor = value.toArgb().and(0x00FFFFFF) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.White },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val ShadowColor =
        AppChoicePreference<SubtitlePreferences, Color>(
            title = R.string.subtitle_shadow_color,
            defaultValue = Color.Black,
            getter = { Color(it.effectiveShadowColor) },
            setter = { prefs, value ->
                // Stored with full alpha, so 0 can mean "not set"
                prefs.update { shadowColor = value.toArgb().or(0xFF000000.toInt()) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.Black },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val EdgeThickness =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.edge_size,
            defaultValue = 4,
            min = 1,
            max = 32,
            interval = 1,
            getter = {
                it.edgeThickness
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.update { edgeThickness = value.toInt() }
            },
            summarizer = { value -> value?.let { "${it / 2.0}" } },
        )

    val OutlineOpacity =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.subtitle_outline_opacity,
            defaultValue = 100,
            min = 10,
            max = 100,
            interval = 10,
            getter = { it.effectiveOutlineOpacity.toLong() },
            setter = { prefs, value ->
                prefs.update { outlineOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val ShadowOffset =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.subtitle_shadow_distance,
            defaultValue = 4,
            min = 1,
            max = 16,
            interval = 1,
            getter = { it.effectiveShadowOffset.toLong() },
            setter = { prefs, value ->
                prefs.update { shadowOffset = value.toInt() }
            },
            summarizer = { value -> value?.let { "${it / 2.0}" } },
        )

    val ShadowBlur =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.subtitle_shadow_softness,
            defaultValue = 4,
            min = 1,
            max = 32,
            interval = 1,
            getter = { it.effectiveShadowBlur.toLong() },
            setter = { prefs, value ->
                prefs.update { shadowBlur = value.toInt() }
            },
            summarizer = { value -> value?.let { "${it / 2.0}" } },
        )

    val ShadowOpacity =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.subtitle_shadow_opacity,
            defaultValue = 100,
            min = 10,
            max = 100,
            interval = 10,
            getter = { it.effectiveShadowOpacity.toLong() },
            setter = { prefs, value ->
                prefs.update { shadowOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val BackgroundColor =
        AppChoicePreference<SubtitlePreferences, Color>(
            title = R.string.background_color,
            defaultValue = Color.Transparent,
            getter = { Color(it.backgroundColor) },
            setter = { prefs, value ->
                prefs.update { backgroundColor = value.toArgb().and(0x00FFFFFF) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.White },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val BackgroundOpacity =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.background_opacity,
            defaultValue = 50,
            min = 10,
            max = 100,
            interval = 10,
            getter = {
                it.backgroundOpacity
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.update { backgroundOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val BackgroundStylePref =
        AppChoicePreference<SubtitlePreferences, BackgroundStyle>(
            title =
                R.string.background_style,
            defaultValue = BackgroundStyle.BG_NONE,
            getter = { it.backgroundStyle },
            setter = { prefs, value ->
                prefs.update { backgroundStyle = value }
            },
            displayValues = R.array.background_style,
            indexToValue = { BackgroundStyle.forNumber(it) },
            valueToIndex = { it.number },
        )

    val Margin =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.subtitle_margin,
            defaultValue = 8,
            min = 0,
            max = 100,
            interval = 1,
            getter = {
                it.margin
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.update { margin = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val ImageOpacity =
        AppSliderPreference<SubtitlePreferences>(
            title = R.string.image_subtitle_opacity,
            defaultValue = 100,
            min = 10,
            max = 100,
            interval = 5,
            getter = {
                it.imageSubtitleOpacity.toLong()
            },
            setter = { prefs, value ->
                prefs.update { imageSubtitleOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val IgnoreEmbeddedColors =
        AppSwitchPreference<SubtitlePreferences>(
            title = R.string.ignore_embedded_subtitle_colors,
            defaultValue = false,
            getter = { it.ignoreEmbeddedColors },
            setter = { prefs, value ->
                prefs.update { ignoreEmbeddedColors = value }
            },
            summary = R.string.ignore_embedded_subtitle_colors_summary,
        )

    val IgnoreEmbeddedStyles =
        AppSwitchPreference<SubtitlePreferences>(
            title = R.string.ignore_embedded_subtitle_styles,
            defaultValue = false,
            getter = { it.ignoreEmbeddedStyles },
            setter = { prefs, value ->
                prefs.update { ignoreEmbeddedStyles = value }
            },
            summary = R.string.ignore_embedded_subtitle_styles_summary,
        )

    val Reset =
        AppClickablePreference<SubtitlePreferences>(
            title = R.string.reset,
            getter = { },
            setter = { prefs, _ -> prefs },
        )

    val SeparateHdr =
        AppSwitchPreference<SubtitlePreferences>(
            title = R.string.different_for_hdr,
            defaultValue = false,
            getter = { it.useSeparateHdr },
            setter = { prefs, value ->
                prefs.update { useSeparateHdr = value }
            },
        )

    val HdrSettings =
        AppDestinationPreference<SubtitlePreferences>(
            title = R.string.hdr_subtitle_style,
            destination = Destination.SubtitleSettings(true),
        )

    val preferences =
        listOf(
            PreferenceGroup(
                title = R.string.font,
                preferences =
                    listOf(
                        FontSize,
                        FontColor,
                        FontWeight,
                        FontItalic,
                        FontOpacity,
                    ),
            ),
            PreferenceGroup(
                title = R.string.edge_style,
                preferences =
                    listOf(
                        EdgeStylePref,
                    ),
                // With no edge only the style shows
                conditionalPreferences =
                    listOf(
                        ConditionalPreferences(
                            condition = { it.hasOutline },
                            preferences = listOf(EdgeColor, EdgeThickness, OutlineOpacity),
                        ),
                        ConditionalPreferences(
                            condition = { it.hasShadow },
                            preferences = listOf(ShadowColor, ShadowOffset, ShadowBlur, ShadowOpacity),
                        ),
                    ),
            ),
            PreferenceGroup(
                title = R.string.background,
                preferences =
                    listOf(
                        BackgroundStylePref,
                    ),
                // With no background only the style shows
                conditionalPreferences =
                    listOf(
                        ConditionalPreferences(
                            condition = { it.hasBackground },
                            preferences = listOf(BackgroundColor, BackgroundOpacity),
                        ),
                    ),
            ),
            PreferenceGroup(
                title = R.string.more,
                preferences =
                    listOf(
                        Margin,
                        ImageOpacity,
                        IgnoreEmbeddedColors,
                        IgnoreEmbeddedStyles,
                        Reset,
                    ),
            ),
        )

    val hdrPreferenceGroup =
        listOf(
            PreferenceGroup(
                title = R.string.hdr,
                preferences =
                    listOf(
                        SeparateHdr,
                    ),
                conditionalPreferences =
                    listOf(
                        ConditionalPreferences(
                            condition = { it.useSeparateHdr },
                            preferences = listOf(HdrSettings),
                        ),
                    ),
            ),
        )

    fun shouldShowHdr(display: Display): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && display.isHdr

    private fun combine(
        color: Int,
        opacity: Int,
    ) = ((opacity / 100.0 * 255).toInt().shl(24)).or(color.and(0x00FFFFFF))

    val SubtitlePreferences.hasOutline: Boolean
        get() = edgeStyle == EdgeStyle.EDGE_SOLID || edgeStyle == EdgeStyle.EDGE_OUTLINE_SHADOW

    val SubtitlePreferences.hasShadow: Boolean
        get() = edgeStyle == EdgeStyle.EDGE_SHADOW || edgeStyle == EdgeStyle.EDGE_OUTLINE_SHADOW

    val SubtitlePreferences.hasBackground: Boolean
        get() = backgroundStyle == BackgroundStyle.BG_WRAP || backgroundStyle == BackgroundStyle.BG_BOXED

    // Before there was a separate shadow color, the shadow used the edge color
    val SubtitlePreferences.effectiveShadowColor: Int
        get() = if (shadowColor != 0) shadowColor else edgeColor

    private val SubtitlePreferences.shadowLayerColor: Int
        get() = combine(effectiveShadowColor, fontOpacity * effectiveShadowOpacity / 100)

    private val SubtitlePreferences.outlineColor: Int
        get() = combine(edgeColor, fontOpacity * effectiveOutlineOpacity / 100)

    @OptIn(UnstableApi::class)
    fun SubtitlePreferences.toSubtitleStyle(context: Context): CaptionStyleCompat {
        val bg = combine(backgroundColor, backgroundOpacity)
        return CaptionStyleCompat(
            combine(fontColor, fontOpacity),
            if (backgroundStyle == BackgroundStyle.BG_WRAP) bg else 0,
            if (backgroundStyle == BackgroundStyle.BG_BOXED) bg else 0,
            when (edgeStyle) {
                EdgeStyle.EDGE_NONE, EdgeStyle.UNRECOGNIZED -> CaptionStyleCompat.EDGE_TYPE_NONE
                EdgeStyle.EDGE_SOLID, EdgeStyle.EDGE_OUTLINE_SHADOW -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                EdgeStyle.EDGE_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            },
            if (edgeStyle == EdgeStyle.EDGE_SHADOW) shadowLayerColor else outlineColor,
            subtitleTypeface(context),
        )
    }

    /**
     * Style for the shadow layer of Outline + shadow: the text in the edge color with a drop
     * shadow. The outlined text drawn on top covers the text itself, so only the shadow shows
     */
    @OptIn(UnstableApi::class)
    private fun SubtitlePreferences.toShadowLayerStyle(context: Context): CaptionStyleCompat =
        CaptionStyleCompat(
            combine(edgeColor, fontOpacity),
            0,
            0,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            shadowLayerColor,
            subtitleTypeface(context),
        )

    private const val SHADOW_LAYER_TAG = "subtitle-shadow-layer"

    /**
     * media3 draws one edge type per [SubtitleView], so Outline + shadow draws the shadow with a
     * second view inside the first, underneath its outlined text
     */
    @OptIn(UnstableApi::class)
    private val SubtitleView.shadowLayer: SubtitleView?
        get() = findViewWithTag(SHADOW_LAYER_TAG)

    /**
     * Sets the cues on a [SubtitleView] styled with [applyStyleTo] or [applyTo], including its
     * shadow layer
     */
    @OptIn(UnstableApi::class)
    fun SubtitleView.setSubtitleCues(cues: List<Cue>?) {
        setCues(cues)
        shadowLayer?.setCues(cues)
    }

    /**
     * The font used for text subtitles: DM Sans in the chosen weight and style. From Android 8 the
     * variable font is drawn at exactly that weight; before that, the nearest of the fixed weights
     * (300 to 700) is used. Nothing is synthesized
     */
    private fun SubtitlePreferences.subtitleTypeface(context: Context): Typeface {
        val weight = effectiveFontWeight
        val italic = fontItalic
        return subtitleTypefaces.getOrPut(weight to italic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val file = if (italic) "dm_sans_variable_italic.ttf" else "dm_sans_variable.ttf"
                    // opsz 14 matches the fixed weights Google Fonts serves, which the interface uses
                    return@getOrPut Typeface
                        .Builder(context.assets, "font-dm-sans/$file")
                        .setFontVariationSettings("'wght' $weight, 'opsz' 14")
                        .build()
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load variable subtitle font")
                }
            }
            staticSubtitleTypeface(context, (weight + 50) / 100 * 100, italic)
        }
    }

    private val subtitleTypefaces = mutableMapOf<Pair<Int, Boolean>, Typeface>()

    private fun staticSubtitleTypeface(
        context: Context,
        weight: Int,
        italic: Boolean,
    ): Typeface {
        val font =
            when (weight) {
                300 -> if (italic) R.font.dm_sans_light_italic else R.font.dm_sans_light
                500 -> if (italic) R.font.dm_sans_medium_italic else R.font.dm_sans_medium
                600 -> if (italic) R.font.dm_sans_semibold_italic else R.font.dm_sans_semibold
                700 -> if (italic) R.font.dm_sans_bold_italic else R.font.dm_sans_bold
                else -> if (italic) R.font.dm_sans_italic else R.font.dm_sans_regular
            }
        return try {
            ResourcesCompat.getFont(context, font)
        } catch (ex: Resources.NotFoundException) {
            Timber.w(ex, "Could not load subtitle font")
            null
        } ?: Typeface.DEFAULT
    }

    /**
     * The chosen weight, 300 to 700 in steps of 10. Settings saved before there was a choice of
     * weight only had a bold switch, so that counts as 700
     */
    val SubtitlePreferences.effectiveFontWeight: Int
        get() =
            when {
                fontWeight > 0 -> (fontWeight / 10 * 10).coerceIn(300, 700)
                fontBold -> 700
                else -> FontWeight.defaultValue.toInt()
            }

    // The shadow settings are 0 until changed, which means the default
    val SubtitlePreferences.effectiveShadowOffset: Int
        get() = shadowOffset.takeIf { it > 0 } ?: ShadowOffset.defaultValue.toInt()

    val SubtitlePreferences.effectiveShadowBlur: Int
        get() = shadowBlur.takeIf { it > 0 } ?: ShadowBlur.defaultValue.toInt()

    val SubtitlePreferences.effectiveShadowOpacity: Int
        get() = shadowOpacity.takeIf { it > 0 } ?: ShadowOpacity.defaultValue.toInt()

    val SubtitlePreferences.effectiveOutlineOpacity: Int
        get() = outlineOpacity.takeIf { it > 0 } ?: OutlineOpacity.defaultValue.toInt()

    /**
     * The values media3's subtitle painter draws its outline and shadow with, which it has no
     * public setting for, see [Media3SubtitleOverride]
     */
    fun SubtitlePreferences.media3Override(density: Density): Media3SubtitleOverride =
        with(density) {
            Media3SubtitleOverride(
                outlineThicknessPx = calculateEdgeSize(density),
                shadowOffsetPx = (effectiveShadowOffset / 2f).dp.toPx(),
                shadowRadiusPx = (effectiveShadowBlur / 2f).dp.toPx(),
            )
        }

    @OptIn(UnstableApi::class)
    fun SubtitlePreferences.applyTo(view: SubtitleView) {
        applyStyleTo(view)
        view.setBottomPaddingFraction(margin.toFloat() / 100f)
        view.shadowLayer?.setBottomPaddingFraction(margin.toFloat() / 100f)
    }

    /**
     * Applies everything but the margin, adding or removing the shadow layer for Outline + shadow
     */
    @OptIn(UnstableApi::class)
    fun SubtitlePreferences.applyStyleTo(view: SubtitleView) {
        view.setStyle(toSubtitleStyle(view.context))
        view.setFixedTextSize(Dimension.SP, fontSize.toFloat())
        var shadowLayer = view.shadowLayer
        if (edgeStyle == EdgeStyle.EDGE_OUTLINE_SHADOW) {
            if (shadowLayer == null) {
                shadowLayer =
                    SubtitleView(view.context).apply {
                        tag = SHADOW_LAYER_TAG
                        layoutParams =
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                    }
                // Index 0 draws it before, so underneath, the view's own subtitles
                view.addView(shadowLayer, 0)
            }
            shadowLayer.setStyle(toShadowLayerStyle(view.context))
            shadowLayer.setFixedTextSize(Dimension.SP, fontSize.toFloat())
        } else if (shadowLayer != null) {
            view.removeView(shadowLayer)
        }
    }

    fun SubtitlePreferences.calculateEdgeSize(density: Density): Float = with(density) { (edgeThickness / 2f).dp.toPx() }

    fun SubtitlePreferences.applyToMpv(
        configuration: Configuration,
        density: Density,
    ) {
        val fo = (fontOpacity / 100.0 * 255).toInt().shl(24)
        val fc = Color(combine(fontColor, fontOpacity))
        val bg = Color(combine(backgroundColor, backgroundOpacity))
        val edge = Color(outlineColor)

        // TODO weird, but seems to get the size to be very close to matching sizes between renderers
        val fontSizePx = with(density) { fontSize.sp.toPx() * .8 }.toInt()
        MPVLib.setPropertyInt("sub-font-size", fontSizePx)
        MPVLib.setPropertyColor("sub-color", fc)
        MPVLib.setPropertyColor("sub-outline-color", edge)

        val heightInPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val margin = (heightInPx * (margin.toFloat() / 100f) * .8).toInt()
        MPVLib.setPropertyInt("sub-margin-y", margin)
        Timber.d("MPV subtitles: fontSizePx=%s, margin=$margin", fontSizePx, margin)

        when (edgeStyle) {
            EdgeStyle.EDGE_NONE,
            EdgeStyle.UNRECOGNIZED,
            -> {
                MPVLib.setPropertyInt("sub-shadow-offset", 0)
                MPVLib.setPropertyDouble("sub-outline-size", 0.0)
            }

            EdgeStyle.EDGE_SOLID -> {
                MPVLib.setPropertyInt("sub-shadow-offset", 0)
                MPVLib.setPropertyDouble("sub-outline-size", 1.15)
            }

            EdgeStyle.EDGE_SHADOW,
            EdgeStyle.EDGE_OUTLINE_SHADOW,
            -> {
                val shadowOffsetPx = with(density) { (effectiveShadowOffset / 2f).dp.toPx() * .8 }
                MPVLib.setPropertyInt("sub-shadow-offset", shadowOffsetPx.toInt())
                MPVLib.setPropertyColor("sub-shadow-color", Color(shadowLayerColor))
                MPVLib.setPropertyDouble("sub-outline-size", 0.0)
            }
        }
        if (hasOutline) {
            val outlineSizePx = calculateEdgeSize(density) * .8
            MPVLib.setPropertyDouble("sub-outline-size", outlineSizePx)
        }

//        if (fontBold) {
//            MPVLib.setPropertyString("sub-font", "Roboto Bold")
//        } else {
//            MPVLib.setPropertyString("sub-font", "Roboto Regular")
//        }
        // MPV uses its own font file, so it can only show bold or not
        MPVLib.setPropertyBoolean("sub-bold", effectiveFontWeight >= 600)
        MPVLib.setPropertyBoolean("sub-italic", fontItalic)

        MPVLib.setPropertyColor("sub-back-color", bg)
        val borderStyle =
            when (backgroundStyle) {
                BackgroundStyle.UNRECOGNIZED,
                BackgroundStyle.BG_NONE,
                -> "outline-and-shadow"

                BackgroundStyle.BG_WRAP -> "opaque-box"

                BackgroundStyle.BG_BOXED -> "background-box"
            }
        MPVLib.setPropertyString("sub-border-style", borderStyle)

        // "strip" removes all ASS tags and styles (including colors), "force" replaces the
        // ASS styles with the sub-* options above so the configured colors are used
        val assOverride =
            when {
                ignoreEmbeddedStyles -> "strip"
                ignoreEmbeddedColors -> "force"
                else -> "scale"
            }
        MPVLib.setPropertyString("sub-ass-override", assOverride)
    }

    /**
     * Whether embedded subtitle formatting should be removed, which requires rendering SSA/ASS
     * subtitles with ExoPlayer instead of libass
     */
    val SubtitlePreferences.stripsEmbeddedFormatting: Boolean
        get() = ignoreEmbeddedColors || ignoreEmbeddedStyles
}

inline fun SubtitlePreferences.update(block: SubtitlePreferences.Builder.() -> Unit): SubtitlePreferences = toBuilder().apply(block).build()

fun InterfacePreferences.shouldEnableSeparateHdrToggle(): Boolean {
    // If already separate, leave it
    if (subtitlesPreferences.useSeparateHdr) return true
    val defaultStyle =
        SubtitlePreferences
            .newBuilder()
            .apply {
                resetSubtitles()
            }.build()
    // If SDR and HDR are the same, don't separate
    return if (subtitlesPreferences == hdrSubtitlesPreferences) {
        false
    } else {
        // If different, but HDR has not been changed from original settings, don't separate
        hdrSubtitlesPreferences != defaultStyle
    }
}
