package com.thor.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.thor.core.model.FontChoice

/**
 * Type scale.
 *
 * The launcher only ships families that are guaranteed present on any Android
 * device, so a theme can never fail to render because a font failed to
 * download. Custom user fonts are layered on top by the personalization screen
 * via an explicit [FontFamily] override.
 */
object ThorTypography {

    fun familyFor(choice: FontChoice): FontFamily = when (choice) {
        FontChoice.SYSTEM -> FontFamily.SansSerif
        FontChoice.ROUNDED -> FontFamily.SansSerif
        FontChoice.MONO -> FontFamily.Monospace
        FontChoice.PIXEL -> FontFamily.Monospace
        FontChoice.SERIF -> FontFamily.Serif
    }

    /**
     * Builds the Material scale.
     *
     * [scale] folds together the user's font-size preference and the
     * "large text" accessibility switch.
     */
    fun build(family: FontFamily, scale: Float): Typography {
        val s = scale.coerceIn(0.75f, 1.6f)
        return Typography(
            displayLarge = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Bold,
                fontSize = (52 * s).sp, lineHeight = (58 * s).sp, letterSpacing = (-0.5).sp,
            ),
            displayMedium = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Bold,
                fontSize = (40 * s).sp, lineHeight = (46 * s).sp, letterSpacing = (-0.25).sp,
            ),
            displaySmall = TextStyle(
                fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = (32 * s).sp, lineHeight = (38 * s).sp,
            ),
            headlineLarge = TextStyle(
                fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = (28 * s).sp, lineHeight = (34 * s).sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = (23 * s).sp, lineHeight = (29 * s).sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Medium,
                fontSize = (20 * s).sp, lineHeight = (26 * s).sp,
            ),
            titleLarge = TextStyle(
                fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = (18 * s).sp, lineHeight = (24 * s).sp,
            ),
            titleMedium = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Medium,
                fontSize = (16 * s).sp, lineHeight = (22 * s).sp,
            ),
            titleSmall = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Medium,
                fontSize = (14 * s).sp, lineHeight = (20 * s).sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Normal,
                fontSize = (16 * s).sp, lineHeight = (24 * s).sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Normal,
                fontSize = (14 * s).sp, lineHeight = (20 * s).sp,
            ),
            bodySmall = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Normal,
                fontSize = (12 * s).sp, lineHeight = (17 * s).sp,
            ),
            labelLarge = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Medium,
                fontSize = (14 * s).sp, lineHeight = (18 * s).sp, letterSpacing = 0.1.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Medium,
                fontSize = (12 * s).sp, lineHeight = (16 * s).sp, letterSpacing = 0.4.sp,
            ),
            labelSmall = TextStyle(
                fontFamily = family, fontWeight = FontWeight.Medium,
                fontSize = (10 * s).sp, lineHeight = (14 * s).sp, letterSpacing = 0.5.sp,
            ),
        )
    }

    /**
     * Style for the label under a grid icon: tight, centred, and small enough
     * that a six-column page does not turn into a wall of text.
     */
    fun gridLabel(family: FontFamily, scale: Float): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = (11 * scale.coerceIn(0.75f, 1.6f)).sp,
        lineHeight = (14 * scale.coerceIn(0.75f, 1.6f)).sp,
        textAlign = TextAlign.Center,
    )
}
