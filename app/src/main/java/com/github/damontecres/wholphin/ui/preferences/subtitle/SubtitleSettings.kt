package com.github.damontecres.wholphin.ui.preferences.subtitle

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.view.Display
import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
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

    val FontBold =
        AppSwitchPreference<SubtitlePreferences>(
            title = R.string.bold_font,
            defaultValue = false,
            getter = { it.fontBold },
            setter = { prefs, value ->
                prefs.update { fontBold = value }
            },
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
                        FontBold,
                        FontItalic,
                        FontOpacity,
                    ),
            ),
            PreferenceGroup(
                title = R.string.edge_style,
                preferences =
                    listOf(
                        EdgeStylePref,
                        EdgeColor,
                        EdgeThickness,
                    ),
            ),
            PreferenceGroup(
                title = R.string.background,
                preferences =
                    listOf(
                        BackgroundStylePref,
                        BackgroundColor,
                        BackgroundOpacity,
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

    @OptIn(UnstableApi::class)
    fun SubtitlePreferences.toSubtitleStyle(context: Context): CaptionStyleCompat {
        val bg = combine(backgroundColor, backgroundOpacity)
        return CaptionStyleCompat(
            combine(fontColor, fontOpacity),
            if (backgroundStyle == BackgroundStyle.BG_WRAP) bg else 0,
            if (backgroundStyle == BackgroundStyle.BG_BOXED) bg else 0,
            when (edgeStyle) {
                EdgeStyle.EDGE_NONE, EdgeStyle.UNRECOGNIZED -> CaptionStyleCompat.EDGE_TYPE_NONE
                EdgeStyle.EDGE_SOLID -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                EdgeStyle.EDGE_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            },
            combine(edgeColor, fontOpacity),
            Typeface.create(
                subtitleTypeface(context),
                when {
                    fontBold && fontItalic -> Typeface.BOLD_ITALIC
                    fontBold -> Typeface.BOLD
                    fontItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                },
            ),
        )
    }

    /**
     * The font used for text subtitles, same as the interface font
     */
    private fun subtitleTypeface(context: Context): Typeface =
        try {
            ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
        } catch (ex: Resources.NotFoundException) {
            Timber.w(ex, "Could not load subtitle font")
            null
        } ?: Typeface.DEFAULT

    @OptIn(UnstableApi::class)
    fun SubtitlePreferences.applyTo(view: SubtitleView) {
        view.setStyle(toSubtitleStyle(view.context))
        view.setFixedTextSize(Dimension.SP, fontSize.toFloat())
        view.setBottomPaddingFraction(margin.toFloat() / 100f)
    }

    fun SubtitlePreferences.calculateEdgeSize(density: Density): Float = with(density) { (edgeThickness / 2f).dp.toPx() }

    fun SubtitlePreferences.applyToMpv(
        configuration: Configuration,
        density: Density,
    ) {
        val fo = (fontOpacity / 100.0 * 255).toInt().shl(24)
        val fc = Color(combine(fontColor, fontOpacity))
        val bg = Color(combine(backgroundColor, backgroundOpacity))
        val edge = Color(combine(edgeColor, fontOpacity))

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

            EdgeStyle.EDGE_SHADOW -> {
                MPVLib.setPropertyInt("sub-shadow-offset", 4)
                MPVLib.setPropertyDouble("sub-outline-size", 0.0)
            }
        }
        val outlineSizePx = calculateEdgeSize(density) * .8
        MPVLib.setPropertyDouble("sub-outline-size", outlineSizePx)

//        if (fontBold) {
//            MPVLib.setPropertyString("sub-font", "Roboto Bold")
//        } else {
//            MPVLib.setPropertyString("sub-font", "Roboto Regular")
//        }
        MPVLib.setPropertyBoolean("sub-bold", fontBold)
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
