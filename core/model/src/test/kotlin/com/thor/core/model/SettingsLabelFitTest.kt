package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Labels shown in a settings row's value button have to fit it.
 *
 * That button is a fixed-width pill holding one line, ellipsised rather than
 * wrapped, so a label that outgrows it is silently cut. What that looked like in
 * practice was worse than a missing word: the layout row's caption carried its
 * dimensions, and the part that survived the cut was a bare column count, which
 * reads as a stray number sitting in the middle of a sentence.
 *
 * The budget is in characters rather than measured width because a unit test has
 * no font. It is deliberately conservative — the real constraint is `200.dp`
 * minus the chevron and padding, at a bold 12sp — and it is the *label* that is
 * bounded, never the description, which is shown in the menu where there is
 * room for two lines.
 */
class SettingsLabelFitTest {

    @Test
    fun `every option label offered in settings fits its button`() {
        val labels = buildMap<String, List<String>> {
            put("CornerStyle", CornerStyle.entries.map { it.label })
            put("MouseAction", MouseAction.entries.map { it.label })
            put("AnimatedWallpaper", AnimatedWallpaper.entries.map { it.label })
            put("CouchWallpaperStyle", CouchWallpaperStyle.entries.map { it.label })
            put("IconShape", IconShape.entries.map { it.label })
            put("DockStyle", DockStyle.entries.map { it.label })
            put("CursorStyle", CursorStyle.entries.map { it.label })
            put("CursorAnimation", CursorAnimation.entries.map { it.label })
            put("ClockStyle", ClockStyle.entries.map { it.label })
            put("FolderStyle", FolderStyle.entries.map { it.label })
            put("SortOrder", SortOrder.entries.map { it.label })
            put("DualScreenMode", DualScreenMode.entries.map { it.label })
            put("ColorBlindMode", ColorBlindMode.entries.map { it.label })
            put("StreamCodec", StreamCodec.entries.map { it.label })
            put("StreamNetwork", StreamNetwork.entries.map { it.label })
            put("StreamAudio", StreamAudio.entries.map { it.label })
            put("GridPreset", GridSpec.PRESETS.map { it.label })
        }

        val tooLong = labels.flatMap { (type, values) ->
            values.filter { it.length > MAX_VALUE_LABEL }.map { "$type: \"$it\" (${it.length})" }
        }

        assertThat(tooLong).isEmpty()
    }

    @Test
    fun `the descriptions are free to be long`() {
        // Stated as an expectation so the budget above is not mistaken for a
        // house style and applied to the detail lines too. They are the reason
        // the labels can afford to be terse.
        assertThat(StreamCodec.AUTO.detail.length).isGreaterThan(MAX_VALUE_LABEL)
        assertThat(StreamNetwork.REMOTE.detail.length).isGreaterThan(MAX_VALUE_LABEL)
    }

    private companion object {
        const val MAX_VALUE_LABEL = 22
    }
}
