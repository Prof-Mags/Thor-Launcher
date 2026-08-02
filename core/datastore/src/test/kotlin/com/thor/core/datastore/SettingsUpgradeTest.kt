package com.thor.core.datastore

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.AnimatedWallpaper
import com.thor.core.model.ClockStyle
import com.thor.core.model.DockStyle
import com.thor.core.model.GridSpec
import com.thor.core.model.ThemeId
import com.thor.core.model.ThemeSpec
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
                "themeId": "STEAM",
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
        assertThat(read.personalization.themeId).isEqualTo(ThemeId.STEAM)
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

        assertThat(read.personalization.themeId).isEqualTo(ThemeSpec.DEFAULT)
        assertThat(read.personalization.clockStyle).isEqualTo(ClockStyle.DIGITAL_24)
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
        assertThat(ThemeSpec.ALL).isNotEmpty()
        assertThat(ThemeSpec.of(ThemeId.DARK).id).isEqualTo(ThemeId.DARK)
        assertThat(GridSpec.PRESETS).isNotEmpty()
        assertThat(GridSpec.DEFAULT.preset).isIn(GridSpec.PRESETS)
        assertThat(ThorSettings.DEFAULT.personalization.animatedWallpaper)
            .isIn(AnimatedWallpaper.entries)
        // Every theme's paired wallpaper must be a real mode.
        ThemeSpec.ALL.forEach { spec ->
            assertThat(spec.defaultWallpaper).isIn(AnimatedWallpaper.entries)
        }
    }
}
