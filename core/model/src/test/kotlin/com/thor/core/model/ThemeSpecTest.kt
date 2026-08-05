package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The guarantees the generated palettes make.
 *
 * These were checks before and they are guarantees now, which is the whole reason
 * the theme system was rebuilt around a seed. When every palette was thirty
 * hand-written hex values, a test like "body text is readable" could only report
 * that somebody had typed a bad one — and the fix was to type a better one, in a
 * file with four hundred more waiting to go wrong. The colours are computed now,
 * so the same assertions test the *generator*: pass them and every theme is
 * readable, in both polarities, at every contrast level, at any hue the user
 * rotates to. There is nowhere left for a bad value to hide.
 *
 * Which is why the sweep below is over the cross product rather than over the
 * twelve defaults. Twelve themes is the smallest interesting case; twelve themes
 * times two polarities times four contrast levels is what a user can actually
 * reach, and the polarity is the one that used to be un-testable because light and
 * dark were different themes with different authors' care behind them.
 */
class ThemeSpecTest {

    @Test
    fun `every theme id has exactly one recipe`() {
        val ids = ThemeRecipe.ALL.map(ThemeRecipe::id)

        assertThat(ids).containsNoDuplicates()
        assertThat(ids).containsExactlyElementsIn(ThemeId.entries)
    }

    @Test
    fun `there are twelve themes, four to a family`() {
        assertThat(ThemeRecipe.ALL).hasSize(THEME_COUNT)

        val byFamily = ThemeRecipe.ALL.groupBy(ThemeRecipe::family)
        assertThat(byFamily.keys).containsExactlyElementsIn(ThemeFamily.entries)
        byFamily.forEach { (family, recipes) ->
            assertWithMessage("themes in $family").that(recipes).hasSize(THEMES_PER_FAMILY)
        }
    }

    @Test
    fun `themes are grouped by family in gallery order`() {
        // The gallery renders ALL in order, so the ordering here *is* the grouping
        // the user sees; interleaving would scatter the shelves.
        val families = ThemeRecipe.ALL.map(ThemeRecipe::family)

        assertThat(families).isEqualTo(
            families.distinct().flatMap { family -> List(THEMES_PER_FAMILY) { family } },
        )
    }

    @Test
    fun `a family says something true about the colour in it`() {
        /*
         * The shelves are a claim, and an unenforced claim is a comment.
         *
         * Neutral is a claim about chroma — the accent stays quiet whatever hue it
         * nominally has — and warm and cool are claims about where on the wheel it
         * sits. Both are checked against the recipe rather than against the label,
         * so a theme cannot be filed under "Warm" and come out green.
         */
        ThemeRecipe.ALL.forEach { recipe ->
            val message = "${recipe.id} on the ${recipe.family} shelf"
            when (recipe.family) {
                ThemeFamily.NEUTRAL ->
                    assertWithMessage(message)
                        .that(recipe.accentChroma)
                        .isAtMost(ThemeRecipe.NEUTRAL_CHROMA_CEILING)

                ThemeFamily.WARM -> {
                    assertWithMessage(message)
                        .that(recipe.accentChroma)
                        .isAtLeast(ThemeRecipe.COLOURED_CHROMA_FLOOR)
                    assertWithMessage("$message is a warm hue")
                        .that(isWarmHue(recipe.accentHue))
                        .isTrue()
                }

                ThemeFamily.COOL -> {
                    assertWithMessage(message)
                        .that(recipe.accentChroma)
                        .isAtLeast(ThemeRecipe.COLOURED_CHROMA_FLOOR)
                    assertWithMessage("$message is a cool hue")
                        .that(isWarmHue(recipe.accentHue))
                        .isFalse()
                }
            }
        }
    }

    @Test
    fun `the default theme exists and is bundled`() {
        assertThat(ThemeRecipe.of(ThemeRecipe.DEFAULT).id).isEqualTo(ThemeRecipe.DEFAULT)
        assertThat(ThemeRecipe.DEFAULT).isEqualTo(ThemeId.MATERIAL)
    }

    @Test
    fun `of returns the matching recipe for every id`() {
        ThemeId.entries.forEach { id ->
            assertThat(ThemeRecipe.of(id).id).isEqualTo(id)
        }
    }

    // ------------------------------------------------ generated palettes

    @Test
    fun `the surface ramp never goes backwards`() {
        // A card on a panel on the background has to stay distinguishable, which it
        // cannot if an "elevated" surface is darker than the one beneath it. Higher
        // is lighter in both polarities — on a light theme that means a white card
        // over an off-white page, which is the conventional cue.
        forEveryPalette { spec, label ->
            val ramp = listOf(
                spec.backgroundArgb,
                spec.surfaceArgb,
                spec.surfaceElevatedArgb,
                spec.surfaceHighestArgb,
            ).map { Oklch.relativeLuminance(it) }

            assertWithMessage("surface ramp for $label").that(ramp).isInOrder()
        }
    }

    @Test
    fun `body text clears WCAG AA on every palette`() {
        forEveryPalette { spec, label ->
            assertWithMessage("body on surface, $label")
                .that(Oklch.contrastRatio(spec.onSurfaceArgb, spec.surfaceArgb))
                .isAtLeast(MIN_BODY_CONTRAST)
            assertWithMessage("body on background, $label")
                .that(Oklch.contrastRatio(spec.onBackgroundArgb, spec.backgroundArgb))
                .isAtLeast(MIN_BODY_CONTRAST)
        }
    }

    @Test
    fun `muted text clears the large-text bar on every palette`() {
        // Muted text is where contrast is usually lost: it is the metadata on the
        // information panel, so it has to clear the large-text bar at minimum.
        forEveryPalette { spec, label ->
            assertWithMessage("muted on surface, $label")
                .that(Oklch.contrastRatio(spec.onSurfaceVariantArgb, spec.surfaceArgb))
                .isAtLeast(MIN_MUTED_CONTRAST)
        }
    }

    @Test
    fun `each contrast level actually delivers what it promises`() {
        /*
         * The levels are sold to the user by their ratio — the settings row prints
         * "Body text at 7.0:1 or better" underneath each one — so the promise is
         * part of the interface and not an implementation note.
         */
        ThemeRecipe.ALL.forEach { recipe ->
            ContrastLevel.entries.forEach { level ->
                listOf(true, false).forEach { dark ->
                    val spec = recipe.resolve(ThemeOptions(dark = dark, contrast = level))
                    val label = "${recipe.id} ${polarity(dark)} at $level"

                    assertWithMessage("body, $label")
                        .that(Oklch.contrastRatio(spec.onSurfaceArgb, spec.surfaceArgb))
                        .isAtLeast(level.bodyRatio)
                    assertWithMessage("muted, $label")
                        .that(Oklch.contrastRatio(spec.onSurfaceVariantArgb, spec.surfaceArgb))
                        .isAtLeast(level.mutedRatio)
                }
            }
        }
    }

    @Test
    fun `raising the contrast never lowers it`() {
        // The levels are ordered, and an ordering the user can see in a menu has to
        // be an ordering in the palette: picking "High" after "Normal" and getting
        // softer text would make the control read as broken.
        ThemeRecipe.ALL.forEach { recipe ->
            listOf(true, false).forEach { dark ->
                val ratios = ContrastLevel.entries.map { level ->
                    val spec = recipe.resolve(ThemeOptions(dark = dark, contrast = level))
                    Oklch.contrastRatio(spec.onSurfaceArgb, spec.surfaceArgb)
                }
                assertWithMessage("${recipe.id} ${polarity(dark)} across levels")
                    .that(ratios)
                    .isInOrder()
            }
        }
    }

    @Test
    fun `the dark flag agrees with the ground it was built on`() {
        forEveryPalette { spec, label ->
            assertWithMessage("isDark for $label")
                .that(spec.isDark)
                .isEqualTo(Oklch.relativeLuminance(spec.backgroundArgb) < 0.5f)
        }
    }

    @Test
    fun `every colour is fully opaque except the glow`() {
        // A palette entry that lost its alpha byte renders as nothing rather than
        // as the wrong colour, which is correspondingly hard to spot by eye. The
        // glow is the one that is meant to be translucent — it is drawn *over* the
        // cursor's own surface — so it is checked for the opposite.
        forEveryPalette { spec, label ->
            listOf(
                spec.primaryArgb, spec.secondaryArgb, spec.accentEndArgb,
                spec.backgroundArgb, spec.surfaceArgb, spec.surfaceElevatedArgb,
                spec.surfaceHighestArgb, spec.onBackgroundArgb, spec.onSurfaceArgb,
                spec.onSurfaceVariantArgb, spec.cursorArgb, spec.outlineArgb,
                spec.errorArgb,
            ).forEach { argb ->
                assertWithMessage("alpha for $label").that(alpha(argb)).isEqualTo(FULLY_OPAQUE)
            }
            assertWithMessage("glow alpha for $label").that(alpha(spec.glowArgb))
                .isLessThan(FULLY_OPAQUE)
        }
    }

    @Test
    fun `the accent stands out from the surface it sits on`() {
        // An accent that fails this is not an accent. It is the specific failure a
        // hand-written light theme makes — a colour picked for how it looks on a
        // dark ground, reused on a pale one — and the generator's whole job is that
        // it cannot happen, because the accent's lightness comes from the polarity.
        forEveryPalette { spec, label ->
            assertWithMessage("accent on surface, $label")
                .that(Oklch.contrastRatio(spec.primaryArgb, spec.surfaceArgb))
                .isAtLeast(MIN_ACCENT_CONTRAST)
        }
    }

    @Test
    fun `material values stay in range`() {
        forEveryPalette { spec, label ->
            assertWithMessage(label).that(spec.surfaceAlpha).isIn(UNIT_RANGE)
            assertWithMessage(label).that(spec.grain).isIn(UNIT_RANGE)
            assertWithMessage(label).that(spec.backgroundDepth).isIn(UNIT_RANGE)
            assertWithMessage(label).that(spec.blurRadiusDp).isAtLeast(0)
            assertWithMessage(label).that(spec.cornerRadiusDp).isAtLeast(0)
        }
    }

    @Test
    fun `translucent themes ask for a blur to sit behind`() {
        // Translucency with no backdrop blur is just a washed-out panel; the theme
        // layer compensates at runtime, but a recipe that needs it should say so
        // rather than relying on that fallback.
        ThemeRecipe.ALL.filter { it.material.surfaceAlpha < 0.9f }.forEach { recipe ->
            assertWithMessage("blur for ${recipe.id}")
                .that(recipe.material.blurRadiusDp)
                .isGreaterThan(0)
        }
    }

    // ------------------------------------------------------- user dials

    @Test
    fun `pure black is black, and only in the dark`() {
        ThemeRecipe.ALL.forEach { recipe ->
            val dark = recipe.resolve(ThemeOptions(dark = true, pureBlack = true))
            assertWithMessage("pure black ground for ${recipe.id}")
                .that(dark.backgroundArgb)
                .isEqualTo(BLACK)
            // Grain and the accent wash both band visibly on #000, so asking for
            // pure black has to turn them off rather than draw them over it.
            assertWithMessage("grain over pure black, ${recipe.id}").that(dark.grain).isEqualTo(0f)
            assertWithMessage("depth over pure black, ${recipe.id}")
                .that(dark.backgroundDepth)
                .isEqualTo(0f)

            val light = recipe.resolve(ThemeOptions(dark = false, pureBlack = true))
            assertWithMessage("pure black ignored on a light palette, ${recipe.id}")
                .that(light.backgroundArgb)
                .isNotEqualTo(BLACK)
        }
    }

    @Test
    fun `a picked accent replaces the accent on every theme`() {
        /*
         * The failure this guards is the quiet one. An override used to be dropped
         * in as the primary verbatim, so a dark colour picked while running a dark
         * theme produced an accent that was technically applied and practically
         * invisible — and the surfaces went on being tinted toward the hue it had
         * replaced. Taking the hue and re-deriving the lightness is what makes an
         * arbitrary swatch usable, so that is what is asserted: the hue arrives,
         * and the result is still legible.
         */
        val picked = Oklch(l = 0.32f, c = 0.16f, h = 22f).toArgb()

        ThemeRecipe.ALL.forEach { recipe ->
            listOf(true, false).forEach { dark ->
                val spec = recipe.resolve(
                    ThemeOptions(dark = dark, accentOverrideArgb = picked),
                )
                val label = "${recipe.id} ${polarity(dark)}"

                assertWithMessage("accent hue follows the pick, $label")
                    .that(hueDistance(Oklch.fromArgb(spec.primaryArgb).h, 22f))
                    .isLessThan(HUE_TOLERANCE)
                assertWithMessage("picked accent stays legible, $label")
                    .that(Oklch.contrastRatio(spec.primaryArgb, spec.surfaceArgb))
                    .isAtLeast(MIN_ACCENT_CONTRAST)
            }
        }
    }

    @Test
    fun `zero colour intensity greys the palette without losing the text`() {
        // The bottom of the intensity slider is a real position, not an edge case:
        // it is how somebody asks for a monochrome launcher. It must not take the
        // contrast guarantees down with it.
        forEveryTheme { recipe, dark ->
            val spec = recipe.resolve(ThemeOptions(dark = dark, colorIntensity = 0f))
            val label = "${recipe.id} ${polarity(dark)} at zero intensity"

            assertWithMessage("accent is grey, $label")
                .that(Oklch.fromArgb(spec.primaryArgb).c)
                .isLessThan(GREY_CHROMA)
            assertWithMessage("body still readable, $label")
                .that(Oklch.contrastRatio(spec.onSurfaceArgb, spec.surfaceArgb))
                .isAtLeast(MIN_BODY_CONTRAST)
        }
    }

    @Test
    fun `a hue shift rotates the palette and keeps it readable`() {
        // Sampled all the way round rather than at one angle: the sRGB gamut is
        // lopsided, and the hues where it runs out soonest — the deep blues and the
        // saturated yellows — are exactly where a generator quietly stops meeting
        // its promises.
        forEveryTheme { recipe, dark ->
            HUE_SAMPLES.forEach { shift ->
                val spec = recipe.resolve(ThemeOptions(dark = dark, hueShift = shift))
                val label = "${recipe.id} ${polarity(dark)} shifted $shift°"

                assertWithMessage("body, $label")
                    .that(Oklch.contrastRatio(spec.onSurfaceArgb, spec.surfaceArgb))
                    .isAtLeast(MIN_BODY_CONTRAST)
                assertWithMessage("ramp, $label")
                    .that(
                        listOf(
                            spec.backgroundArgb,
                            spec.surfaceArgb,
                            spec.surfaceElevatedArgb,
                            spec.surfaceHighestArgb,
                        ).map { Oklch.relativeLuminance(it) },
                    )
                    .isInOrder()
            }
        }
    }

    @Test
    fun `an overridden material replaces the theme's own`() {
        SurfaceStyle.entries.forEach { style ->
            ThemeRecipe.ALL.forEach { recipe ->
                val spec = recipe.resolve(ThemeOptions(surfaceStyle = style))
                assertWithMessage("${recipe.id} forced to $style")
                    .that(spec.surface)
                    .isEqualTo(SurfaceTreatment.forStyle(style))
            }
        }
    }

    @Test
    fun `an untouched option set gives the theme exactly as designed`() {
        // The defaults have to be a no-op, or every theme is being previewed and
        // shipped through a transform nobody asked for.
        ThemeRecipe.ALL.forEach { recipe ->
            val spec = recipe.resolve()
            assertWithMessage("${recipe.id} material").that(spec.surface)
                .isEqualTo(recipe.material.surface)
            assertWithMessage("${recipe.id} grain").that(spec.grain)
                .isEqualTo(recipe.material.grain)
            assertWithMessage("${recipe.id} depth").that(spec.backgroundDepth)
                .isEqualTo(recipe.material.backgroundDepth)
            assertWithMessage("${recipe.id} radius").that(spec.cornerRadiusDp)
                .isEqualTo(recipe.material.cornerRadiusDp)
            assertWithMessage("${recipe.id} font").that(spec.fontFamily).isEqualTo(recipe.font)
            assertWithMessage("${recipe.id} motion").that(spec.motion).isEqualTo(recipe.motion)
        }
    }

    @Test
    fun `light and dark are the same theme`() {
        // Two resolutions of one recipe rather than two themes, so what identifies
        // the theme has to survive the switch. This is the assertion that would
        // have caught the old system's real problem, where "Daylight" and
        // "Midnight" were unrelated palettes that happened to sit in one list.
        ThemeRecipe.ALL.forEach { recipe ->
            val dark = recipe.resolve(ThemeOptions(dark = true))
            val light = recipe.resolve(ThemeOptions(dark = false))

            assertWithMessage("accent hue survives the polarity switch, ${recipe.id}")
                .that(
                    hueDistance(
                        Oklch.fromArgb(dark.primaryArgb).h,
                        Oklch.fromArgb(light.primaryArgb).h,
                    ),
                )
                .isLessThan(HUE_TOLERANCE)
            assertThat(dark.id).isEqualTo(light.id)
            assertThat(dark.fontFamily).isEqualTo(light.fontFamily)
            assertThat(dark.cornerRadiusDp).isEqualTo(light.cornerRadiusDp)
        }
    }

    // ------------------------------------------------------------- helpers

    /** Every theme in both polarities: the set a user can reach with one switch. */
    private fun forEveryTheme(body: (ThemeRecipe, Boolean) -> Unit) {
        ThemeRecipe.ALL.forEach { recipe ->
            listOf(true, false).forEach { dark -> body(recipe, dark) }
        }
    }

    /** Every theme, both polarities, every contrast level. */
    private fun forEveryPalette(body: (ThemeSpec, String) -> Unit) {
        forEveryTheme { recipe, dark ->
            ContrastLevel.entries.forEach { level ->
                body(
                    recipe.resolve(ThemeOptions(dark = dark, contrast = level)),
                    "${recipe.id} ${polarity(dark)} at $level",
                )
            }
        }
    }

    private fun polarity(dark: Boolean) = if (dark) "dark" else "light"

    private fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    /** Shortest angular distance between two hues, in degrees. */
    private fun hueDistance(a: Float, b: Float): Float {
        val raw = kotlin.math.abs(a - b).mod(360f)
        return minOf(raw, 360f - raw)
    }

    /** Reds through yellows, wrapping past magenta. See [Oklch] for the landmarks. */
    private fun isWarmHue(hue: Float): Boolean =
        hue < WARM_UPPER_BOUND || hue > WARM_LOWER_BOUND

    private companion object {
        const val FULLY_OPAQUE = 0xFF
        const val BLACK = 0xFF000000L

        const val THEME_COUNT = 12
        const val THEMES_PER_FAMILY = 4

        /** Above this hue a colour has turned green; below it, it is still warm. */
        const val WARM_UPPER_BOUND = 110f

        /** And past this it has come back round through magenta into the reds. */
        const val WARM_LOWER_BOUND = 320f

        /** WCAG AA for normal text. */
        const val MIN_BODY_CONTRAST = 4.5f

        /** WCAG AA for large/secondary text. */
        const val MIN_MUTED_CONTRAST = 3.0f

        /** WCAG AA for a non-text indicator, which is what an accent fill is. */
        const val MIN_ACCENT_CONTRAST = 3.0f

        /** Below this a colour is a grey by any reasonable reading. */
        const val GREY_CHROMA = 0.01f

        /** Rounding through OKLCH and back costs a fraction of a degree. */
        const val HUE_TOLERANCE = 2f

        val HUE_SAMPLES = listOf(-180f, -120f, -60f, -25f, 25f, 60f, 120f, 180f)

        val UNIT_RANGE = com.google.common.collect.Range.closed(0f, 1f)
    }
}
