package com.thor.feature.settings.page

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.CustomTheme
import com.thor.core.model.MotionStyle
import com.thor.core.model.SurfaceStyle
import com.thor.core.model.ThemeRecipe
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.ThemePreviewPanel
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.ColorPickerRow
import com.thor.feature.settings.component.row.FilePickerRow
import com.thor.feature.settings.component.row.FileSaverRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.row.IntSliderRow
import com.thor.feature.settings.component.row.SliderRow
import com.thor.feature.settings.component.row.TextFieldRow
import kotlin.math.roundToInt

/**
 * Building a theme of your own.
 *
 * The Theme and Surfaces pages hand over the dials that sit *over* a recipe —
 * brightness, contrast, saturation, material — while the recipe itself, the thing
 * that makes Nocturne Nocturne, was written in Kotlin and reachable nowhere. This
 * is where it is reachable.
 *
 * The page has two states rather than one long list. Closed, it is a short list of
 * your themes and the two ways to get another. Open, it is one theme's decisions.
 * Both live in one [SettingsPage] because they are one subject, and because a
 * settings rail with "Themes" and "Editing a theme" as separate destinations would
 * describe the software's modes rather than the user's task.
 *
 * **Opening a theme applies it.** See [SettingsViewModel.editTheme] — a palette is
 * the entire interface across both panels, and the launcher wearing it is a truer
 * preview than any card. The panel at the top of the open state covers what that
 * cannot: the grid and the information panel, which are behind this page.
 */
@Composable
internal fun ThemeEditorPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    /** Which theme is open, from [SettingsViewModel.editingThemeId]. */
    editingId: String?,
    status: String?,
) {
    val personalization = settings.personalization
    val themes = personalization.customThemes
    val editing = themes.firstOrNull { it.id == editingId }

    if (editing == null) {
        ThemeListRows(settings, themes, focusedRow, viewModel, status)
    } else {
        ThemeParameterRows(settings, editing, focusedRow, viewModel, status)
    }
}

/**
 * The closed state: what you have, and the two ways to get another.
 *
 * "New theme from" is a choice of seed rather than a bare "New", because a theme
 * editor opened on default values is a wall of sliders with nothing to judge them
 * against. Starting from a theme that already works makes every change an edit.
 */
@Composable
private fun ThemeListRows(
    settings: ThorSettings,
    themes: List<CustomTheme>,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    status: String?,
) {
    InfoRow(
        "Theme editor",
        status ?: "Make a theme of your own from any of the built-in ones, and " +
            "share it as a file. Opening a theme applies it, so the whole " +
            "launcher is the preview.",
    )
    RowDivider()

    ChoiceRow(
        title = "New theme from",
        subtitle = "Copies a theme's colours and material, then opens it for editing",
        options = settings.personalization.galleryRecipes,
        // Nothing is "selected" here — the row is an action wearing a chooser, so
        // it stays on the applied theme rather than pretending to remember a
        // previous seed.
        selected = settings.personalization.activeRecipe,
        label = ThemeRecipe::displayName,
        focused = focusedRow == 0,
        onSelected = viewModel::createTheme,
    )
    RowDivider()

    FilePickerRow(
        title = "Import a theme",
        subtitle = "Pick a .json theme file somebody sent you",
        // Same reasoning as the extension importer: some providers report JSON as
        // plain text, and a picker that hides the file you are looking at is one
        // you cannot use.
        mimeTypes = arrayOf("application/json", "text/plain", "*/*"),
        focused = focusedRow == 1,
        onPicked = { uri, _ -> viewModel.importTheme(uri) },
    )

    if (themes.isEmpty()) return

    RowDivider()
    themes.forEachIndexed { index, theme ->
        val applied = settings.personalization.activeCustomThemeId == theme.id
        ActionRow(
            title = theme.name,
            subtitle = buildString {
                append(theme.family.label)
                append(" · ")
                // The colour as most people would name it to somebody else, rather
                // than as the perceptual angle the theme happens to store.
                append(theme.accentHex)
                if (applied) append("  ·  Applied")
            },
            focused = focusedRow == THEME_LIST_FIRST_ROW + index,
            trailingLabel = "Edit",
            onClick = { viewModel.editTheme(theme.id) },
        )
        if (index != themes.lastIndex) RowDivider()
    }
}

/**
 * The open state: one theme, and the decisions that actually change it.
 *
 * This was twenty-six rows. Every one of them was a real parameter and the page
 * was still the wrong thing, because a theme is not twenty-six decisions — it is
 * about seven, and the rest are consequences. Asking somebody to set a secondary
 * hue offset, an accent spread and a cursor offset is asking them to do the
 * palette generator's job by hand, on a page long enough that the colour they came
 * to change has scrolled off the top of it.
 *
 * So the rows here are the ones with an answer a person actually holds — what
 * colour, how strong, what the panels are made of, how round, how deep, how it
 * moves — and the rest are derived from those. Nothing was removed from the
 * *model*: an imported theme keeps every value it arrived with, an exported one
 * still carries them, and Randomise still rolls them. They are simply no longer
 * questions.
 */
@Composable
private fun ThemeParameterRows(
    settings: ThorSettings,
    theme: CustomTheme,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    status: String?,
) {
    val personalization = settings.personalization
    val applied = personalization.activeCustomThemeId == theme.id

    /*
     * Resolved through the user's own dials rather than at some notional default,
     * so the preview is a promise about what this theme looks like *for them* —
     * the same rule the gallery cards follow.
     *
     * Remembered per theme: resolving a palette is a few hundred gamut searches
     * and a binary search for readable text, which is nothing once and wasteful on
     * every frame of a cursor crossing the page.
     */
    val spec = remember(theme, personalization) {
        theme.toRecipe().resolve(personalization.themeOptions(systemDark = true))
    }

    ThemePreviewPanel(spec = spec, modifier = Modifier.padding(horizontal = 16.dp))
    RowDivider()

    InfoRow(
        theme.name,
        status ?: "On the ${theme.family.label.lowercase()} shelf. " +
            if (applied) "Applied — every change is live." else "Not applied.",
    )
    RowDivider()

    TextFieldRow(
        title = "Name",
        subtitle = "What the gallery calls it",
        value = theme.name,
        focused = focusedRow == 0,
        onValueChange = viewModel::renameEditedTheme,
    )
    RowDivider()

    // ---- Colour -------------------------------------------------------------

    ColorPickerRow(
        title = "Colour",
        subtitle = "Left and Right walk the spectrum, or tap it",
        hue = theme.accentHue,
        chroma = theme.accentChroma,
        focused = focusedRow == 1,
        onTakesHorizontalInput = { takes -> viewModel.setRowTakesHorizontal(1, takes) },
        onHueChange = { hue ->
            // The greys follow the accent, which is what every bundled theme does.
            // Left free to drift they read as a mistake rather than as a choice —
            // and it is one fewer question on a page whose point is having fewer.
            viewModel.updateEditedTheme { it.copy(accentHue = hue, neutralHue = hue) }
        },
    )
    RowDivider()

    SliderRow(
        title = "Strength",
        subtitle = "How colourful the theme is. Low is a grey launcher with a " +
            "coloured cursor; high leads with the colour.",
        value = theme.accentChroma,
        range = CustomTheme.ACCENT_CHROMA,
        focused = focusedRow == 2,
        stepOverride = CHROMA_STEP,
        valueLabel = ::strengthLabel,
        onValueChange = { chroma ->
            viewModel.updateEditedTheme {
                it.copy(
                    accentChroma = chroma,
                    // The surfaces take a fraction of the accent's own chroma
                    // rather than carrying a dial of their own. Both ways it can
                    // go wrong — grey panels under a loud accent, or panels tinted
                    // until they compete with it — are worse than any value
                    // somebody would have chosen deliberately.
                    neutralChroma = (chroma * SURFACE_TINT_RATIO)
                        .coerceIn(CustomTheme.NEUTRAL_CHROMA),
                )
            }
        },
    )
    RowDivider()

    // ---- Material -----------------------------------------------------------

    ChoiceRow(
        title = "Panels",
        subtitle = "What every panel, card and cell is made of",
        options = SurfaceStyle.entries,
        selected = theme.surfaceStyle,
        label = SurfaceStyle::label,
        optionDescription = ::surfaceStyleDescription,
        focused = focusedRow == 3,
        // Opacity and blur arrive with the material rather than beside it: they
        // are what "glass" and "flat" mean, and setting them separately is how a
        // theme ends up labelled glass and drawn opaque.
        onSelected = { style -> viewModel.updateEditedTheme { it.copy(surfaceStyle = style) } },
    )
    RowDivider()
    IntSliderRow(
        title = "Corners",
        subtitle = "How round every panel, card and cell is",
        value = theme.cornerRadiusDp,
        range = CustomTheme.CORNER_RADIUS_DP,
        focused = focusedRow == 4,
        suffix = "dp",
        onValueChange = { radius ->
            viewModel.updateEditedTheme { it.copy(cornerRadiusDp = radius) }
        },
    )
    RowDivider()
    SliderRow(
        title = "Depth",
        subtitle = "How far the background graduates toward the colour. Zero is a " +
            "flat field.",
        value = theme.backgroundDepth,
        range = CustomTheme.BACKGROUND_DEPTH,
        focused = focusedRow == 5,
        valueLabel = { percent(it, CustomTheme.BACKGROUND_DEPTH.endInclusive) },
        onValueChange = { depth ->
            viewModel.updateEditedTheme { it.copy(backgroundDepth = depth) }
        },
    )
    RowDivider()

    // ---- Character ----------------------------------------------------------

    ChoiceRow(
        title = "Motion",
        subtitle = "How fast this theme's animations run",
        options = MotionStyle.entries,
        selected = theme.motion,
        label = MotionStyle::label,
        focused = focusedRow == 6,
        onSelected = { motion -> viewModel.updateEditedTheme { it.copy(motion = motion) } },
    )
    RowDivider()
    ChoiceRow(
        title = "Wallpaper",
        subtitle = "Applied when this theme is picked. Changeable afterwards like " +
            "any other.",
        options = AnimatedWallpaper.entries,
        selected = theme.wallpaper,
        label = AnimatedWallpaper::label,
        focused = focusedRow == 7,
        onSelected = { paper -> viewModel.updateEditedTheme { it.copy(wallpaper = paper) } },
    )
    RowDivider()

    // ---- The theme itself ---------------------------------------------------

    ActionRow(
        title = "Randomise",
        subtitle = "Rolls a new colour and material. Everything stays editable.",
        focused = focusedRow == 8,
        trailingLabel = "Roll",
        onClick = viewModel::randomiseEditedTheme,
    )
    RowDivider()
    FileSaverRow(
        title = "Export to a file",
        subtitle = "Save this theme so it can be shared and imported anywhere",
        suggestedName = "${theme.name.lowercase().replace(NON_FILENAME, "-")}.json",
        focused = focusedRow == 9,
        onChosen = { uri -> viewModel.exportTheme(theme.id, uri) },
    )
    RowDivider()
    ActionRow(
        title = "Duplicate",
        subtitle = "Make a copy and edit that instead, leaving this one alone",
        focused = focusedRow == 10,
        trailingLabel = "Copy",
        onClick = { viewModel.duplicateTheme(theme.id) },
    )
    RowDivider()
    ActionRow(
        title = "Delete this theme",
        subtitle = "Cannot be undone. The launcher falls back to the built-in " +
            "theme underneath.",
        focused = focusedRow == 11,
        trailingLabel = "Delete",
        destructive = true,
        onClick = { viewModel.deleteTheme(theme.id) },
    )
    RowDivider()
    ActionRow(
        title = "Done",
        subtitle = "Back to the list. The theme stays applied.",
        focused = focusedRow == 12,
        trailingLabel = "Done",
        onClick = { viewModel.editTheme(null) },
    )
}

/** Seed chooser, importer, then one row per saved theme. */
private const val THEME_LIST_FIRST_ROW = 2

/** Name, colour, strength, three material, two character, five actions. */
internal const val THEME_EDITOR_OPEN_ROWS = 13

/**
 * How many rows the page has, which depends on whether a theme is open.
 *
 * Both branches are here rather than in `rowCountFor` so the count and the rows it
 * counts cannot drift apart — they are the two halves of one statement, and the
 * cursor walking off the end of a page is the failure when they disagree.
 */
internal fun themeEditorRows(customThemeCount: Int, editing: Boolean): Int =
    if (editing) THEME_EDITOR_OPEN_ROWS else THEME_LIST_FIRST_ROW + customThemeCount

/**
 * Strength as a word rather than a number.
 *
 * OKLCH chroma has no natural maximum, so a percentage would invent a ceiling the
 * colour space does not have — and "0.09" tells nobody anything. The one number
 * that matters is the neutral ceiling, which is where a theme stops being a
 * coloured one, so the labels name which side of it you are on.
 */
private fun strengthLabel(chroma: Float): String = when {
    chroma <= ThemeRecipe.NEUTRAL_CHROMA_CEILING * 0.4f -> "Barely there"
    chroma <= ThemeRecipe.NEUTRAL_CHROMA_CEILING -> "Quiet — a neutral theme"
    chroma <= ThemeRecipe.COLOURED_CHROMA_FLOOR -> "Gentle"
    chroma <= LOUD_CHROMA -> "Strong"
    else -> "Loud"
}

/** A percentage of the slider's own range, for the values that have no unit. */
private fun percent(value: Float, max: Float): String =
    "${(value / max * 100).roundToInt()}%"

/** What each material actually looks like, since the names are not self-evident. */
private fun surfaceStyleDescription(style: SurfaceStyle): String = when (style) {
    SurfaceStyle.FLAT -> "Opaque, hard-edged, no depth"
    SurfaceStyle.RAISED -> "Opaque cards with a shadow beneath them"
    SurfaceStyle.TINTED -> "Slightly translucent, tinted by elevation"
    SurfaceStyle.GLASS -> "Translucent and blurred, with a lit top edge"
}

/** Where a theme's name meets a file name. */
private val NON_FILENAME = Regex("[^a-z0-9]+")

/**
 * The step for Strength.
 *
 * The derived one is 0.05, which steps clean over the neutral ceiling at 0.085 —
 * so a theme could be quiet or loud with nothing in between. See the note on
 * [com.thor.feature.settings.component.row.SliderRow]'s override.
 */
private const val CHROMA_STEP = 0.01f

/** Past here a theme is leading with its colour rather than carrying one. */
private const val LOUD_CHROMA = 0.16f

/**
 * How much of the accent's chroma the greys pick up.
 *
 * Derived rather than asked. Every bundled theme sits near this ratio, and it is
 * the difference between surfaces that belong to the accent and surfaces that
 * argue with it.
 */
private const val SURFACE_TINT_RATIO = 0.16f
