package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The theme editor's model.
 *
 * Two things are worth holding here that nothing else can. A custom theme is the
 * only [ThemeRecipe] built from numbers the launcher did not write — every other
 * one is a literal in `Theme.kt` and is correct by inspection — so the clamping is
 * load-bearing rather than defensive. And a custom theme is the only one that can
 * be *deleted* while selected, which is a state the palette generator has to
 * survive because the alternative is a launcher that cannot draw.
 */
class CustomThemeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `a theme seeded from a bundled one carries every parameter across`() {
        /*
         * The editor opens on a copy of something that already works, and this is
         * the promise that makes that worth doing: the copy has to *be* the
         * original, or the first thing the user sees on opening the editor is a
         * theme that has silently changed.
         */
        ThemeRecipe.ALL.forEach { recipe ->
            val copy = CustomTheme.seededFrom(recipe, id = "custom-1", name = "Mine").toRecipe()
            val message = "seeded from ${recipe.id}"

            assertWithMessage("$message accent hue").that(copy.accentHue).isEqualTo(recipe.accentHue)
            assertWithMessage("$message accent chroma")
                .that(copy.accentChroma).isEqualTo(recipe.accentChroma)
            assertWithMessage("$message secondary")
                .that(copy.secondaryHueShift).isEqualTo(recipe.secondaryHueShift)
            assertWithMessage("$message spread")
                .that(copy.accentSpread).isEqualTo(recipe.accentSpread)
            assertWithMessage("$message cursor")
                .that(copy.cursorHueShift).isEqualTo(recipe.cursorHueShift)
            assertWithMessage("$message grey hue")
                .that(copy.neutralHue).isEqualTo(recipe.neutralHue)
            assertWithMessage("$message grey chroma")
                .that(copy.neutralChroma).isEqualTo(recipe.neutralChroma)
            assertWithMessage("$message ground shift")
                .that(copy.groundShift).isEqualTo(recipe.groundShift)
            assertWithMessage("$message corner radius")
                .that(copy.material.cornerRadiusDp).isEqualTo(recipe.material.cornerRadiusDp)
            assertWithMessage("$message alpha")
                .that(copy.material.surfaceAlpha).isEqualTo(recipe.material.surfaceAlpha)
            assertWithMessage("$message blur")
                .that(copy.material.blurRadiusDp).isEqualTo(recipe.material.blurRadiusDp)
            assertWithMessage("$message grain")
                .that(copy.material.grain).isEqualTo(recipe.material.grain)
            assertWithMessage("$message depth")
                .that(copy.material.backgroundDepth).isEqualTo(recipe.material.backgroundDepth)
            assertWithMessage("$message motion").that(copy.motion).isEqualTo(recipe.motion)
            assertWithMessage("$message wallpaper")
                .that(copy.defaultWallpaper).isEqualTo(recipe.defaultWallpaper)
        }
    }

    @Test
    fun `a seeded theme keeps the material of the style it came from`() {
        // The surface *style* survives the round trip even though the treatment is
        // rebuilt from the preset rather than copied — which is the same rule the
        // Surfaces override follows: a style is taken whole, not adjusted.
        ThemeRecipe.ALL.forEach { recipe ->
            val copy = CustomTheme.seededFrom(recipe, id = "custom-1", name = "Mine").toRecipe()
            assertWithMessage("seeded from ${recipe.id}")
                .that(copy.material.surface.style)
                .isEqualTo(recipe.material.surface.style)
        }
    }

    @Test
    fun `every parameter is clamped on the way out`() {
        /*
         * The numbers below are what a hand-edited export can contain. None of
         * them is reachable from a slider, and the point of the clamp is that it
         * does not matter whether anyone should have written them.
         */
        val absurd = CustomTheme(
            id = "custom-1",
            name = "Absurd",
            accentHue = 900f,
            accentChroma = 40f,
            secondaryHueShift = -4_000f,
            accentSpread = 700f,
            cursorHueShift = 5_000f,
            neutralHue = -720f,
            neutralChroma = -3f,
            groundShift = 99f,
            cornerRadiusDp = -50,
            surfaceAlpha = 12f,
            blurRadiusDp = 5_000,
            grain = 88f,
            backgroundDepth = -12f,
        )

        val recipe = absurd.toRecipe()

        recipe.accentHue.assertWithin("accent hue", 0f..360f)
        recipe.neutralHue.assertWithin("grey hue", 0f..360f)
        recipe.accentChroma.assertWithin("accent chroma", CustomTheme.ACCENT_CHROMA)
        recipe.secondaryHueShift.assertWithin("secondary", CustomTheme.HUE_OFFSET)
        recipe.accentSpread.assertWithin("spread", CustomTheme.ACCENT_SPREAD)
        recipe.cursorHueShift.assertWithin("cursor", CustomTheme.HUE_OFFSET)
        recipe.neutralChroma.assertWithin("grey chroma", CustomTheme.NEUTRAL_CHROMA)
        recipe.groundShift.assertWithin("ground shift", CustomTheme.GROUND_SHIFT)
        recipe.material.surfaceAlpha.assertWithin("alpha", CustomTheme.SURFACE_ALPHA)
        recipe.material.grain.assertWithin("grain", CustomTheme.GRAIN)
        recipe.material.backgroundDepth.assertWithin("depth", CustomTheme.BACKGROUND_DEPTH)

        assertWithMessage("corner radius")
            .that(recipe.material.cornerRadiusDp)
            .isIn(CustomTheme.CORNER_RADIUS_DP.toList())
        assertWithMessage("blur radius")
            .that(recipe.material.blurRadiusDp)
            .isIn(CustomTheme.BLUR_RADIUS_DP.toList())
    }

    /** Truth has no assertion over a Kotlin range, and two bounds read fine. */
    private fun Float.assertWithin(what: String, range: ClosedFloatingPointRange<Float>) {
        assertWithMessage("$what is at least ${range.start}").that(this).isAtLeast(range.start)
        assertWithMessage("$what is at most ${range.endInclusive}")
            .that(this).isAtMost(range.endInclusive)
    }

    @Test
    fun `a theme built from absurd values still resolves to a drawable palette`() {
        // The clamp exists so this cannot throw and cannot produce a transparent
        // interface. Both polarities, because the ramp is built differently either way.
        val absurd = CustomTheme(
            id = "custom-1",
            name = "Absurd",
            accentChroma = 40f,
            neutralChroma = -3f,
            surfaceAlpha = 12f,
            grain = 88f,
        ).toRecipe()

        listOf(true, false).forEach { dark ->
            val spec = absurd.resolve(ThemeOptions(dark = dark))
            spec.surfaceAlpha.assertWithin("dark=$dark surface alpha", 0f..1f)
            spec.grain.assertWithin("dark=$dark grain", 0f..1f)
            spec.backgroundDepth.assertWithin("dark=$dark depth", 0f..1f)
            // Every colour is fully opaque ARGB; a transparent surface would mean
            // the launcher drawing nothing at all.
            assertWithMessage("dark=$dark background alpha")
                .that((spec.backgroundArgb ushr 24).toInt())
                .isEqualTo(0xFF)
        }
    }

    @Test
    fun `a colourless theme lands on the neutral shelf whatever hue it claims`() {
        // A hue is only a direction; with no chroma to travel along it, it points
        // nowhere. Checked at the ceiling itself, which is the boundary case.
        listOf(0f, 29f, 145f, 264f, 350f).forEach { hue ->
            val theme = CustomTheme(
                id = "custom-1",
                name = "Quiet",
                accentHue = hue,
                accentChroma = ThemeRecipe.NEUTRAL_CHROMA_CEILING,
            )
            assertWithMessage("hue $hue at the ceiling")
                .that(theme.family)
                .isEqualTo(ThemeFamily.NEUTRAL)
        }
    }

    @Test
    fun `a coloured theme is shelved by where its hue sits`() {
        fun familyAt(hue: Float) =
            CustomTheme(id = "custom-1", name = "Loud", accentHue = hue, accentChroma = 0.15f).family

        // Reds through yellows are warm, wrapping past magenta; the rest is cool.
        // The same two landmarks ThemeSpecTest holds the bundled themes to.
        assertThat(familyAt(29f)).isEqualTo(ThemeFamily.WARM)
        assertThat(familyAt(70f)).isEqualTo(ThemeFamily.WARM)
        assertThat(familyAt(350f)).isEqualTo(ThemeFamily.WARM)
        assertThat(familyAt(145f)).isEqualTo(ThemeFamily.COOL)
        assertThat(familyAt(264f)).isEqualTo(ThemeFamily.COOL)
    }

    @Test
    fun `the family is never the editor shelf`() {
        // That shelf is about where a palette came from, which is a fact about
        // history rather than about numbers. Nothing built here has one.
        listOf(0f, 0.05f, 0.12f, 0.24f).forEach { chroma ->
            (0..350 step 10).forEach { hue ->
                assertWithMessage("chroma $chroma at hue $hue")
                    .that(CustomTheme.familyFor(chroma, hue.toFloat()))
                    .isNotEqualTo(ThemeFamily.EDITOR)
            }
        }
    }

    @Test
    fun `a custom key can never collide with a bundled one`() {
        /*
         * The gallery keys its cards on this and the settings file stores it, so a
         * collision would mean two cards the row cannot tell apart — and the wrong
         * one being applied. Generated ids carry a prefix no enum constant has.
         */
        val bundled = ThemeId.entries.map { it.name }.toSet()
        val generated = (1..50).map { CustomTheme.freshId(emptyList()) } +
            CustomTheme.freshId(listOf("custom-1", "custom-2"))

        generated.forEach { id ->
            assertWithMessage("generated id $id").that(id).isNotIn(bundled)
            assertWithMessage("generated id $id").that(CustomTheme.isValidId(id)).isTrue()
        }
    }

    @Test
    fun `a fresh id skips the ones already taken`() {
        assertThat(CustomTheme.freshId(emptyList())).isEqualTo("custom-1")
        assertThat(CustomTheme.freshId(listOf("custom-1"))).isEqualTo("custom-2")
        assertThat(CustomTheme.freshId(listOf("custom-1", "custom-2", "custom-4")))
            .isEqualTo("custom-3")
    }

    @Test
    fun `a bundled recipe reports itself as bundled and keeps its own name`() {
        ThemeRecipe.ALL.forEach { recipe ->
            assertWithMessage("${recipe.id} is bundled").that(recipe.isCustom).isFalse()
            assertThat(recipe.key).isEqualTo(recipe.id.name)
            assertThat(recipe.displayName).isEqualTo(recipe.id.displayName)
        }
    }

    @Test
    fun `a custom recipe reports its own identity`() {
        val recipe = CustomTheme(id = "custom-7", name = "Mine").toRecipe()

        assertThat(recipe.isCustom).isTrue()
        assertThat(recipe.key).isEqualTo("custom-7")
        assertThat(recipe.displayName).isEqualTo("Mine")
        // And it survives being resolved, which is what the gallery reads.
        val spec = recipe.resolve()
        assertThat(spec.key).isEqualTo("custom-7")
        assertThat(spec.displayName).isEqualTo("Mine")
    }


    // ---- Selection ---------------------------------------------------------

    @Test
    fun `the applied custom theme is what resolves`() {
        val mine = CustomTheme(id = "custom-1", name = "Mine", accentHue = 12f, accentChroma = 0.2f)
        val personalization = PersonalizationSettings(
            themeId = ThemeId.TERMINAL,
            customThemes = listOf(mine),
            activeCustomThemeId = "custom-1",
        )

        assertThat(personalization.activeRecipe.customId).isEqualTo("custom-1")
        assertThat(personalization.activeThemeKey).isEqualTo("custom-1")
    }

    @Test
    fun `deleting the applied theme falls back to the bundled one underneath`() {
        /*
         * The failure this exists to prevent is not a wrong colour, it is a
         * launcher that cannot build a palette at all. `activeCustomThemeId` can
         * outlive the theme it names in two ordinary ways: deleting the applied
         * theme, and restoring a settings file from a device that had it.
         */
        val personalization = PersonalizationSettings(
            themeId = ThemeId.TERMINAL,
            customThemes = emptyList(),
            activeCustomThemeId = "custom-1",
        )

        assertThat(personalization.activeRecipe.id).isEqualTo(ThemeId.TERMINAL)
        assertThat(personalization.activeThemeKey).isEqualTo(ThemeId.TERMINAL.name)
        // And it still produces a palette rather than throwing.
        assertThat(personalization.resolveTheme().backgroundArgb).isNotEqualTo(0L)
    }

    @Test
    fun `the gallery is the bundled shelves and then the user's own`() {
        val personalization = PersonalizationSettings(
            customThemes = listOf(
                CustomTheme(id = "custom-1", name = "One"),
                CustomTheme(id = "custom-2", name = "Two"),
            ),
        )

        val gallery = personalization.galleryRecipes

        assertThat(gallery).hasSize(ThemeRecipe.ALL.size + 2)
        assertThat(gallery.take(ThemeRecipe.ALL.size).map(ThemeRecipe::key))
            .isEqualTo(ThemeRecipe.ALL.map(ThemeRecipe::key))
        assertThat(gallery.map(ThemeRecipe::key)).containsNoDuplicates()
        assertThat(gallery.takeLast(2).map(ThemeRecipe::displayName))
            .containsExactly("One", "Two").inOrder()
    }

    // ---- Sharing -----------------------------------------------------------

    @Test
    fun `a theme survives a round trip through a file`() {
        val mine = CustomTheme(
            id = "custom-3",
            name = "Warm Paper",
            accentHue = 64f,
            accentChroma = 0.13f,
            surfaceStyle = SurfaceStyle.GLASS,
            cornerRadiusDp = 4,
            motion = MotionStyle.MECHANICAL,
            wallpaper = AnimatedWallpaper.STARFIELD,
        )

        val text = json.encodeToString(ThemeFile.serializer(), ThemeFile(theme = mine))
        val read = json.decodeFromString(ThemeFile.serializer(), text)

        assertThat(read.isValid).isTrue()
        assertThat(read.theme).isEqualTo(mine)
    }

    @Test
    fun `a file that is not a theme is rejected rather than half read`() {
        // An extension manifest is the JSON most likely to be picked by mistake,
        // since it is the other file Loki asks people to import.
        val notATheme = """{"kind":"loki.extension","version":1,"extension":"movies"}"""

        val read = runCatching {
            json.decodeFromString(ThemeFile.serializer(), notATheme)
        }.getOrNull()

        // Either it fails to parse, or it parses and says it is not one. Both are
        // fine; silently importing it is not.
        assertThat(read?.isValid ?: false).isFalse()
    }

    @Test
    fun `a theme file from a later version is refused`() {
        val fromTheFuture = ThemeFile(
            version = ThemeFile.VERSION + 1,
            theme = CustomTheme(id = "custom-1", name = "Mine"),
        )
        assertThat(fromTheFuture.isValid).isFalse()
    }

    // ---- Names -------------------------------------------------------------

    @Test
    fun `a blank name falls back rather than leaving an unlabelled card`() {
        assertThat(sanitizeThemeName("   ")).isEqualTo("My theme")
        assertThat(sanitizeThemeName("  Dusk  ")).isEqualTo("Dusk")
    }

    @Test
    fun `a name is trimmed to something a card can hold`() {
        val long = "x".repeat(CustomTheme.MAX_NAME_LENGTH * 2)
        assertThat(sanitizeThemeName(long)).hasLength(CustomTheme.MAX_NAME_LENGTH)
    }

    @Test
    fun `a duplicate name is counted rather than allowed to collide`() {
        // Two identically labelled cards are indistinguishable to whoever is
        // choosing between them, even though the ids behind them differ.
        assertThat(uniqueThemeName("Dusk", listOf("Dusk"))).isEqualTo("Dusk 2")
        assertThat(uniqueThemeName("Dusk", listOf("Dusk", "Dusk 2"))).isEqualTo("Dusk 3")
        assertThat(uniqueThemeName("Dusk", listOf("dusk"))).isEqualTo("Dusk 2")
        assertThat(uniqueThemeName("Dusk", emptyList())).isEqualTo("Dusk")
    }
}
