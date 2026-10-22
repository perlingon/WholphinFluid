package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

/**
 * The app's interface font
 */
val PlusJakartaSans =
    FontFamily(
        Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.plus_jakarta_sans_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.plus_jakarta_sans_bold_italic, FontWeight.Bold, FontStyle.Italic),
        Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
    )

private fun TextStyle.withAppFont() = copy(fontFamily = PlusJakartaSans)

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
