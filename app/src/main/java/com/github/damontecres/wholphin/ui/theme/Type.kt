package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

/**
 * The app's interface font. ABeeZee only has a regular weight, so bolder weights are synthesized.
 */
val ABeeZee =
    FontFamily(
        Font(R.font.abeezee_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.abeezee_italic, FontWeight.Normal, FontStyle.Italic),
    )

private fun TextStyle.withAppFont() = copy(fontFamily = ABeeZee)

val AppTypography =
    Typography().run {
        Typography(
            displayLarge = displayLarge.withAppFont(),
            displayMedium = displayMedium.withAppFont(),
            displaySmall = displaySmall.withAppFont(),
            headlineLarge = headlineLarge.withAppFont(),
            headlineMedium = headlineMedium.withAppFont(),
            headlineSmall = headlineSmall.withAppFont(),
            titleLarge = titleLarge.withAppFont(),
            titleMedium = titleMedium.withAppFont(),
            titleSmall = titleSmall.withAppFont(),
            bodyLarge = bodyLarge.withAppFont(),
            bodyMedium = bodyMedium.withAppFont(),
            bodySmall = bodySmall.withAppFont(),
            labelLarge = labelLarge.withAppFont(),
            labelMedium = labelMedium.withAppFont(),
            labelSmall = labelSmall.withAppFont(),
        )
    }

/**
 * Same as [AppTypography] for the androidx.compose.material3 components used in the app
 */
val AppMaterialTypography =
    androidx.compose.material3.Typography().run {
        androidx.compose.material3.Typography(
            displayLarge = displayLarge.withAppFont(),
            displayMedium = displayMedium.withAppFont(),
            displaySmall = displaySmall.withAppFont(),
            headlineLarge = headlineLarge.withAppFont(),
            headlineMedium = headlineMedium.withAppFont(),
            headlineSmall = headlineSmall.withAppFont(),
            titleLarge = titleLarge.withAppFont(),
            titleMedium = titleMedium.withAppFont(),
            titleSmall = titleSmall.withAppFont(),
            bodyLarge = bodyLarge.withAppFont(),
            bodyMedium = bodyMedium.withAppFont(),
            bodySmall = bodySmall.withAppFont(),
            labelLarge = labelLarge.withAppFont(),
            labelMedium = labelMedium.withAppFont(),
            labelSmall = labelSmall.withAppFont(),
        )
    }
