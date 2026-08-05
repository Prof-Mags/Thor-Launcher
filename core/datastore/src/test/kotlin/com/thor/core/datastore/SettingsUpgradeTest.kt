package com.thor.core.datastore

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.ClockStyle
import com.thor.core.model.DockStyle
import com.thor.core.model.ControllerCommand
import com.thor.core.model.GridSpec
import com.thor.core.model.LauncherAction
import com.thor.core.model.ThemeId
import com.thor.core.model.ThemeRecipe
import com.thor.core.model.ThorSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Loading a settings document written by an older build.
 *
 * This is the upgrade path, and it is the one that cannot be exercised by
 * installing a fresh APK: an existing install carries a JSON document containing
 * fields that have since been renamed, removed or re-defaulted. If that document
 * fails to decode, the launcher throws before it can draw anything — a crash on
 * open that never reproduces on a clean device.
 */
class SettingsUpgradeTest {

    private val serializer = SettingsSerializer()

    @Test
    fun `default settings round trip`() = runTest {
        val written = ByteArrayOutputStream()
        serializer.writeTo(ThorSettings.DEFAULT, written)

        val read = serializer.readFrom(ByteArrayInputStream(written.toByteArray()))
        assertThat(read).isEqualTo(ThorSettings.DEFAULT)
    }

    @Test
    fun `a document from before the dock and audio changes still loads`() = runTest {
        // Deliberately hand-written rather than generated: the point is fields
        // this build no longer has (`dock.showLabels`) and fields it has gained
        // (`dock.style`, `audio.soundEffectsEnabled`) being absent.
        val legacy = """
            {
              "personalization": {
                "themeId": "ORCHID",
                "animatedWallpaper": "AURORA",
                "cursorStyle": "RING",
                "clockStyle": "DIGITAL_24",
                "folderStyle": "STACK"
              },
              "grid": {
                "columns": 5,
                "rows": 3,
                "iconScale": 1.0,
                "spacingDp": 18,
                "paddingDp": 14,
                "showLabels": true,
                "labelLines": 1,
                "iconShape": "SQUIRCLE",
                "cellStyle": "BOX_ART"
              },
              "dock": {
                "visible": true,
                "backgroundAlpha": 0.55,
                "blurEnabled": true,
                "scale": 1.0,
                "autoHide": false,
                "showLabels": false
              },
              "audio": {
                "uiVolume": 0.6,
                "navigationSounds": true,
                "launchSounds": true
              },
              "schemaVersion": 1
            }
        """.trimIndent()

        val read = serializer.readFrom(ByteArrayInputStream(legacy.toByteArray()))

        // Retired field ignored, new fields defaulted, retained values preserved.
        assertThat(read.dock.style).isEqualTo(DockStyle.PILL)
        assertThat(read.audio.soundEffectsEnabled).isTrue()
        assertThat(read.personalization.themeId).isEqualTo(ThemeId.ORCHID)
        // `cellStyle` was retired; the document still carries it and is ignored.
        assertThat(read.grid.columns).isEqualTo(5)
    }

    @Test
    fun `a retired enum constant costs only its own field`() = runTest {
        /*
         * A theme or wallpaper retired between builds leaves a name in the
         * document that no longer resolves, and what happens next used to be far
         * more expensive than the stale field deserved: the read failed, which is
         * reported as corruption, which has DataStore replace the whole document
         * with defaults. Retiring one theme therefore reset every unrelated
         * setting the user had — their grid, their ROM folders, their API keys.
         *
         * `coerceInputValues` makes the unresolvable value take its default and
         * leaves the rest of the document alone, which is what this asserts:
         * the theme falls back, and the setting beside it survives.
         */
        val document = """
            {
              "personalization": {
                "themeId": "A_THEME_THAT_WAS_REMOVED",
                "clockStyle": "DIGITAL_24"
              },
              "schemaVersion": 1
            }
        """.trimIndent()

        val read = serializer.readFrom(ByteArrayInputStream(document.toByteArray()))

        assertThat(read.personalization.themeId).isEqualTo(ThemeRecipe.DEFAULT)
        assertThat(read.personalization.clockStyle).isEqualTo(ClockStyle.DIGITAL_24)
    }

    /**
     * A retired command inside a *binding map*, which coercion does not reach.
     *
     * `coerceInputValues` substitutes the default for a class property that
     * declares one. A [ControllerCommand] stored as a map *value* is neither — so
     * when the notification command was deleted, every settings file belonging to
     * a user who had bound a button to it became undecodable, and undecodable is
     * reported as corruption, which resets everything they had ever configured.
     *
     * The binding is dropped instead: that button becomes unbound, which is the
     * honest outcome, and the rest of the profile survives.
     */
    @Test
    fun `a binding to a retired command drops the binding, not the document`() = runTest {
        val document = """
            {
              "controls": {
                "activeProfileId": "custom",
                "customProfiles": [
                  {
                    "id": "custom",
                    "name": "Mine",
                    "bindings": {
                      "96": "CONFIRM",
                      "97": "OPEN_NOTIFICATIONS",
                      "99": "GO_HOME"
                    }
                  }
                ],
                "hapticIntensity": 0.4
              },
              "schemaVersion": 1
            }
        """.trimIndent()

        val read = serializer.readFrom(ByteArrayInputStream(document.toByteArray()))

        val profile = read.controls.customProfiles.single()
        assertThat(profile.bindings[96]).isEqualTo(ControllerCommand.CONFIRM)
        assertThat(profile.bindings[99]).isEqualTo(ControllerCommand.GO_HOME)
        assertThat(profile.bindings).doesNotContainKey(97)
        // And nothing else in the document was lost.
        assertThat(read.controls.hapticIntensity).isEqualTo(0.4f)
    }

    /**
     * A retired dock action, which is the same failure through a sealed hierarchy.
     *
     * An unknown class discriminator is not coercible either — kotlinx has no
     * default branch for a sealed type — so a dock holding the deleted
     * notifications action took the whole document down with it.
     *
     * Built by rewriting the *current* document rather than hand-written, so the
     * discriminator and its format come from the encoder instead of from my
     * memory of it.
     */
    @Test
    fun `a dock slot holding a retired action falls back to that slot alone`() = runTest {
        val current = ByteArrayOutputStream()
            .also { serializer.writeTo(ThorSettings.DEFAULT, it) }
            .toByteArray()
            .decodeToString()

        val legacy = current.replaceFirst("LauncherAction.OpenSettings", "LauncherAction.OpenNotifications")
        // The rewrite has to have actually happened, or this test proves nothing.
        assertThat(legacy).isNotEqualTo(current)

        val read = serializer.readFrom(ByteArrayInputStream(legacy.toByteArray()))

        // Replaced, not removed: the dock is five fixed positions, and dropping
        // one slides every later slot left — costing a binding that was fine.
        assertThat(read.dock.slots).hasSize(ThorSettings.DEFAULT.dock.slots.size)
        assertThat(read.dock.slots.first()).isEqualTo(LauncherAction.GoHome)
        // Every other slot is untouched.
        assertThat(read.dock.slots.drop(1))
            .isEqualTo(ThorSettings.DEFAULT.dock.slots.drop(1))
    }

    @Test
    fun `malformed json is reported as corruption`() = runTest {
        val thrown = runCatching {
            serializer.readFrom(ByteArrayInputStream("{ not json".toByteArray()))
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(
            androidx.datastore.core.CorruptionException::class.java,
        )
    }

    @Test
    fun `every startup singleton initialises`() {
        // Touching these in one test catches a class-initialisation cycle between
        // the model's companion objects, which would surface as an
        // ExceptionInInitializerError the moment the first screen composed.
        assertThat(ThemeRecipe.ALL).isNotEmpty()
        assertThat(ThemeRecipe.of(ThemeId.NOCTURNE).id).isEqualTo(ThemeId.NOCTURNE)
        assertThat(GridSpec.PRESETS).isNotEmpty()
        assertThat(GridSpec.DEFAULT.preset).isIn(GridSpec.PRESETS)
        assertThat(ThorSettings.DEFAULT.personalization.animatedWallpaper)
            .isIn(AnimatedWallpaper.entries)
        // Every theme's paired wallpaper must be a real mode.
        ThemeRecipe.ALL.forEach { recipe ->
            assertThat(recipe.defaultWallpaper).isIn(AnimatedWallpaper.entries)
        }
        // And the default settings must resolve to a palette without a device, a
        // composition or a system dark-mode answer to hand — this is what the
        // pointer service and any headless caller get.
        assertThat(ThorSettings.DEFAULT.personalization.resolveTheme().id)
            .isEqualTo(ThemeRecipe.DEFAULT)
    }
}
