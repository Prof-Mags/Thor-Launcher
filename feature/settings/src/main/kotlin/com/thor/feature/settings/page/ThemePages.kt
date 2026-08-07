package com.thor.feature.settings.page

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.thor.core.model.ContrastLevel
import com.thor.core.model.CornerStyle
import com.thor.core.model.SurfaceStyle
import com.thor.core.model.ThemeMode
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.ColorRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SliderRow
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.ThemePreviewRow
import com.thor.feature.settings.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Colour: which theme, which polarity, and the four dials over the top of it.
 *
 * The dials are new and they are the point of the page. A theme used to be a fixed
 * set of colours with one accent swatch over it; every palette is now generated
 * from a seed — see [com.thor.core.model.ThemeRecipe] — so brightness, contrast,
 * saturation and hue are all inputs the user can hold rather than decisions the
 * theme made on their behalf.
 *
 * Ordered by how much each one changes: the gallery, then light or dark, then the
 * adjustments, smallest last. Somebody who only wants a different colour never has
 * to read past the first two rows.
 */
@Composable
internal fun ThemePage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization

    // A gallery rather than a dropdown of names: choosing from a list meant
    // leaving Settings to see each one.
    ThemePreviewRow(
        selected = personalization.activeThemeKey,
        // The bundled shelves and then the user's own, so a theme built in the
        // editor is chosen from the same row as everything else rather than from a
        // second list somewhere that would have to explain itself.
        recipes = personalization.galleryRecipes,
        // Previewed through the user's own dials rather than at each theme's
        // defaults, so a card is a promise about what selecting it would give.
        options = personalization.themeOptions(systemDark = isSystemInDarkTheme()),
        focused = focusedRow == 0,
        onSelected = viewModel::selectTheme,
        onTakesHorizontalInput = { takes -> viewModel.setRowTakesHorizontal(0, takes) },
    )
    RowDivider()
    ChoiceRow(
        title = "Light or dark",
        subtitle = "Every theme is built both ways",
        options = ThemeMode.entries,
        selected = personalization.themeMode,
        label = ThemeMode::label,
        focused = focusedRow == 1,
        onSelected = { mode ->
            viewModel.updatePersonalization { it.copy(themeMode = mode) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Pure black",
        subtitle = "Draw the darkest surface as true black. Saves power on an OLED " +
            "panel, and turns off the grain and the background wash with it.",
        checked = personalization.pureBlack,
        focused = focusedRow == 2,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(pureBlack = on) }
        },
    )
    RowDivider()
    ColorRow(
        title = "Accent colour",
        subtitle = "Replaces the theme's own, and re-tints the surfaces to match",
        colorsToPick = ACCENT_SWATCHES,
        selected = personalization.accentOverrideArgb?.let(::Color),
        focused = focusedRow == 3,
        onSelected = { color ->
            // Stored as an unsigned 32-bit ARGB value in a Long, matching the
            // representation every other colour in the model uses.
            val argb = color?.toArgb()?.toLong()?.and(0xFFFFFFFFL)
            viewModel.updatePersonalization { it.copy(accentOverrideArgb = argb) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Hue",
        subtitle = "Rotates the whole palette, accent and surfaces together",
        value = personalization.accentHueShift,
        range = -180f..180f,
        focused = focusedRow == 4,
        valueLabel = { shift ->
            if (shift.roundToInt() == 0) "Theme" else "${shift.roundToInt()}°"
        },
        onValueChange = { shift ->
            viewModel.updatePersonalization { it.copy(accentHueShift = shift) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Colour intensity",
        subtitle = "0% is greyscale with a coloured cursor; above 100% saturates",
        value = personalization.colorIntensity,
        range = 0f..1.6f,
        focused = focusedRow == 5,
        valueLabel = { "${(it * 100).roundToInt()}%" },
        onValueChange = { intensity ->
            viewModel.updatePersonalization { it.copy(colorIntensity = intensity) }
        },
    )
    RowDivider()
    ChoiceRow(
        title = "Contrast",
        subtitle = "How far text is pushed from the surface behind it",
        options = ContrastLevel.entries,
        selected = personalization.contrastLevel,
        label = ContrastLevel::label,
        optionDescription = { level ->
            "Body text at ${"%.1f".format(level.bodyRatio)}:1 or better"
        },
        focused = focusedRow == 6,
        onSelected = { level ->
            viewModel.updatePersonalization { it.copy(contrastLevel = level) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Dynamic colour",
        subtitle = "Derive Material components' palette from the system wallpaper " +
            "(Android 12+)",
        checked = personalization.useDynamicColor,
        focused = focusedRow == 7,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(useDynamicColor = on) }
        },
    )
    RowDivider()
    ActionRow(
        title = "Reset colour adjustments",
        subtitle = "Puts the accent, hue, intensity and contrast back to the theme's own",
        focused = focusedRow == 8,
        trailingLabel = "Reset",
        onClick = {
            viewModel.updatePersonalization {
                it.copy(
                    accentOverrideArgb = null,
                    accentHueShift = 0f,
                    colorIntensity = 1f,
                    contrastLevel = ContrastLevel.NORMAL,
                )
            }
        },
    )
}

/** Gallery, mode, pure black, accent, hue, intensity, contrast, dynamic, reset. */
internal const val THEME_ROWS = 9

/**
 * What panels are made of, and how much texture sits under them.
 *
 * These were a theme's private business until now: the material, the corner radius,
 * the wash under the background and the film grain over it were all declared by
 * whichever palette was chosen, so liking Terminal's green but not its hard-edged
 * rectangles meant liking a different theme. The theme still supplies the defaults
 * — every row here reads "Theme default" until it is touched — but nothing is
 * locked to it.
 */
@Composable
internal fun SurfacesPage(settings: ThorSettings, focusedRow: Int, viewModel: SettingsViewModel) {
    val personalization = settings.personalization

    ChoiceRow(
        title = "Panel material",
        subtitle = "How every panel, card and sheet is built",
        options = SURFACE_STYLE_OPTIONS,
        selected = personalization.surfaceStyleOverride,
        label = { it?.label ?: THEME_DEFAULT },
        optionDescription = { style -> style?.let(::surfaceStyleDescription) },
        focused = focusedRow == 0,
        onSelected = { style ->
            viewModel.updatePersonalization { it.copy(surfaceStyleOverride = style) }
        },
    )
    RowDivider()
    // One answer for every corner in the launcher, next to the material it is
    // shaping. The two are only comprehensible together.
    ChoiceRow(
        title = "Corner style",
        subtitle = "Shape of panels, cards, dialogs and tabs",
        options = CornerStyle.entries,
        selected = personalization.cornerStyle,
        label = CornerStyle::label,
        focused = focusedRow == 1,
        onSelected = { style ->
            viewModel.updatePersonalization { it.copy(cornerStyle = style) }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Glass effects",
        subtitle = "Translucent panels with a blurred backdrop. Off makes every " +
            "surface opaque, whatever material is chosen.",
        checked = personalization.glassEffects,
        focused = focusedRow == 2,
        onCheckedChange = { on ->
            viewModel.updatePersonalization { it.copy(glassEffects = on) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Background depth",
        subtitle = "How far the ground graduates toward the accent. 0% is a flat field.",
        value = personalization.surfaceDepth,
        range = 0f..2f,
        focused = focusedRow == 3,
        valueLabel = { "${(it * 100).roundToInt()}%" },
        onValueChange = { depth ->
            viewModel.updatePersonalization { it.copy(surfaceDepth = depth) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Film grain",
        subtitle = "Fine noise over the background. Hides the banding a large " +
            "gradient shows on an OLED panel.",
        value = personalization.grainAmount,
        range = 0f..2f,
        focused = focusedRow == 4,
        valueLabel = { "${(it * 100).roundToInt()}%" },
        onValueChange = { grain ->
            viewModel.updatePersonalization { it.copy(grainAmount = grain) }
        },
    )
    RowDivider()
    ActionRow(
        title = "Reset surfaces",
        subtitle = "Back to the material, depth and grain the theme declares",
        focused = focusedRow == 5,
        trailingLabel = "Reset",
        onClick = {
            viewModel.updatePersonalization {
                it.copy(
                    surfaceStyleOverride = null,
                    surfaceDepth = 1f,
                    grainAmount = 1f,
                )
            }
        },
    )
}

/** Material, corners, glass, depth, grain, reset. */
internal const val SURFACES_ROWS = 6

/**
 * "Theme default" first, then the four materials.
 *
 * Null leads because it is the value every install starts on, and a list whose
 * default sits at the end asks the reader to walk past four options to find where
 * they already are.
 */
private val SURFACE_STYLE_OPTIONS: List<SurfaceStyle?> = listOf(null) + SurfaceStyle.entries

/** What each material actually looks like, since the names are not self-evident. */
private fun surfaceStyleDescription(style: SurfaceStyle): String = when (style) {
    SurfaceStyle.FLAT -> "Opaque, hard-edged, no depth"
    SurfaceStyle.RAISED -> "Opaque cards with a shadow beneath them"
    SurfaceStyle.TINTED -> "Slightly translucent, tinted by elevation"
    SurfaceStyle.GLASS -> "Translucent and blurred, with a lit top edge"
}

/** Shown wherever an override is unset, so "off" reads as deference, not absence. */
internal const val THEME_DEFAULT = "Theme default"

private val ACCENT_SWATCHES = listOf(
    Color(0xFF4F8CFF), Color(0xFF8B5CF6), Color(0xFF00E5FF), Color(0xFF39FF14),
    Color(0xFFFF2E88), Color(0xFFF57C00), Color(0xFFE53935), Color(0xFF00C3E3),
)
