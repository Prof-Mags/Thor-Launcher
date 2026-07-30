package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Integrity of the bundled theme set.
 *
 * Twenty hand-written palettes are twenty chances to typo a hex value, and most
 * mistakes there are invisible until someone selects that one theme on a device
 * — a surface ramp that goes backwards, unreadable body text, or an id with no
 * spec at all. These are the checks that catch those at build time.
 */
class ThemeSpecTest {

    @Test
    fun `every theme id has exactly one spec`() {
        val ids = ThemeSpec.ALL.map(ThemeSpec::id)

        assertThat(ids).containsNoDuplicates()
        assertThat(ids).containsExactlyElementsIn(ThemeId.entries)
    }

    @Test
    fun `of returns the matching spec for every id`() {
        ThemeId.entries.forEach { id ->
            assertThat(ThemeSpec.of(id).id).isEqualTo(id)
        }
    }

    @Test
    fun `surface ramp never goes backwards`() {
        // A card on a panel on the background has to stay distinguishable, which
        // it cannot if an "elevated" surface is darker than the one beneath it.
        // Higher is lighter in both polarities — on a light theme that means a
        // white card over an off-white page, which is the conventional cue.
        //
        // Checked on opaque themes only: the translucent ones deliberately use
        // low-alpha whites whose composited luminance depends on what is behind.
        ThemeSpec.ALL.filter { it.surfaceAlpha >= OPAQUE_THRESHOLD }.forEach { spec ->
            val ramp = listOf(
                spec.backgroundArgb,
                spec.surfaceArgb,
                spec.surfaceElevatedArgb,
                spec.surfaceHighestArgb,
            ).map(::relativeLuminance)

            assertWithMessage("surface ramp for ${spec.id}").that(ramp).isInOrder()
        }
    }

    @Test
    fun `body text has usable contrast against its surface`() {
        ThemeSpec.ALL.filter { it.surfaceAlpha >= OPAQUE_THRESHOLD }.forEach { spec ->
            val ratio = contrastRatio(spec.onSurfaceArgb, spec.surfaceArgb)
            assertThat(ratio).isAtLeast(MIN_BODY_CONTRAST)
        }
    }

    @Test
    fun `muted text stays readable`() {
        // Muted text is where contrast is usually lost: it is the metadata on the
        // information panel, so it has to clear the large-text bar at minimum.
        ThemeSpec.ALL.filter { it.surfaceAlpha >= OPAQUE_THRESHOLD }.forEach { spec ->
            val ratio = contrastRatio(spec.onSurfaceVariantArgb, spec.surfaceArgb)
            assertThat(ratio).isAtLeast(MIN_MUTED_CONTRAST)
        }
    }

    @Test
    fun `background text has usable contrast`() {
        ThemeSpec.ALL.forEach { spec ->
            val ratio = contrastRatio(spec.onBackgroundArgb, spec.backgroundArgb)
            assertThat(ratio).isAtLeast(MIN_BODY_CONTRAST)
        }
    }

    @Test
    fun `dark flag agrees with the background`() {
        ThemeSpec.ALL.forEach { spec ->
            val luminance = relativeLuminance(spec.backgroundArgb)
            assertThat(spec.isDark).isEqualTo(luminance < 0.5)
        }
    }

    @Test
    fun `every colour is fully specified`() {
        // A palette entry written as 0x4F8CFF rather than 0xFF4F8CFF is
        // transparent, which renders as nothing rather than as the wrong colour
        // and is correspondingly hard to spot by eye.
        ThemeSpec.ALL.forEach { spec ->
            listOf(
                spec.primaryArgb, spec.secondaryArgb, spec.accentEndArgb,
                spec.backgroundArgb, spec.onBackgroundArgb, spec.onSurfaceArgb,
                spec.onSurfaceVariantArgb, spec.cursorArgb, spec.errorArgb,
            ).forEach { argb ->
                assertThat(alpha(argb)).isEqualTo(FULLY_OPAQUE)
            }
        }
    }

    @Test
    fun `material values stay in range`() {
        ThemeSpec.ALL.forEach { spec ->
            assertThat(spec.surfaceAlpha).isAtLeast(0f)
            assertThat(spec.surfaceAlpha).isAtMost(1f)
            assertThat(spec.grain).isAtLeast(0f)
            assertThat(spec.grain).isAtMost(1f)
            assertThat(spec.blurRadiusDp).isAtLeast(0)
            assertThat(spec.cornerRadiusDp).isAtLeast(0)
        }
    }

    @Test
    fun `translucent themes ask for a blur to sit behind`() {
        // Translucency with no backdrop blur is just a washed-out panel; the
        // theme layer compensates at runtime, but a spec that needs it should
        // say so rather than relying on that fallback.
        ThemeSpec.ALL.filter { it.surfaceAlpha < 0.6f }.forEach { spec ->
            assertThat(spec.blurRadiusDp).isGreaterThan(0)
        }
    }

    // ------------------------------------------------------------- helpers

    private fun alpha(argb: Long): Int = ((argb shr 24) and 0xFF).toInt()

    /**
     * WCAG relative luminance, gamma-corrected.
     *
     * The naive average of the channels would rate a saturated blue and a
     * mid-grey as equally light, which is exactly the mistake that lets an
     * unreadable palette through.
     */
    private fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val raw = ((argb shr shift) and 0xFF).toDouble() / 255.0
            return if (raw <= 0.03928) raw / 12.92 else Math.pow((raw + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrastRatio(foreground: Long, background: Long): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private companion object {
        const val FULLY_OPAQUE = 0xFF

        /** Above this, a surface composites close enough to opaque to measure. */
        const val OPAQUE_THRESHOLD = 0.85f

        /** WCAG AA for normal text. */
        const val MIN_BODY_CONTRAST = 4.5

        /** WCAG AA for large/secondary text. */
        const val MIN_MUTED_CONTRAST = 3.0
    }
}
