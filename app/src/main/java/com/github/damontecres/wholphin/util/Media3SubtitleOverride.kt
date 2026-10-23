package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import timber.log.Timber
import java.lang.reflect.Field

/**
 * Utility class to override private fields in media3's [SubtitleView]
 */
@OptIn(UnstableApi::class)
class Media3SubtitleOverride(
    val outlineThicknessPx: Float,
    val shadowOffsetPx: Float,
    val shadowRadiusPx: Float,
) {
    companion object {
        private lateinit var outputField: Field
        private lateinit var classCanvasSubtitleOutput: Class<*>
        private lateinit var paintersField: Field
        private lateinit var classSubtitlePainter: Class<*>
        private lateinit var outlineWidthField: Field
        private lateinit var shadowOffsetField: Field
        private lateinit var shadowRadiusField: Field

        var initialized: Boolean
            private set

        init {
            try {
                // This is bad times
                outputField = SubtitleView::class.java.getDeclaredField("output")
                classCanvasSubtitleOutput = Class.forName("androidx.media3.ui.CanvasSubtitleOutput")
                paintersField = classCanvasSubtitleOutput.getDeclaredField("painters")
                classSubtitlePainter = Class.forName("androidx.media3.ui.SubtitlePainter")
                outlineWidthField = classSubtitlePainter.getDeclaredField("outlineWidth")
                shadowOffsetField = classSubtitlePainter.getDeclaredField("shadowOffset")
                shadowRadiusField = classSubtitlePainter.getDeclaredField("shadowRadius")
                outputField.isAccessible = true
                outlineWidthField.isAccessible = true
                shadowOffsetField.isAccessible = true
                shadowRadiusField.isAccessible = true
                paintersField.isAccessible = true
                initialized = true
            } catch (ex: Exception) {
                Timber.w(ex, "Error initializing")
                initialized = false
            }
        }
    }

    fun apply(subtitleView: SubtitleView) {
        try {
            if (initialized) {
                // Basically hijack the fields that control the subtitle outline and shadow
                val canvasSubtitleOutput = outputField.get(subtitleView)
                val painters = paintersField.get(canvasSubtitleOutput) as List<*>
                painters.forEach { painter ->
                    outlineWidthField.set(painter, outlineThicknessPx)
                    shadowOffsetField.set(painter, shadowOffsetPx)
                    shadowRadiusField.set(painter, shadowRadiusPx)
                }
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Failed to apply")
        }
    }
}
