package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The shape of a widget's grid id.
 *
 * Worth pinning because it is duplicated in SQL that cannot reference it.
 * `GridDao.pruneOrphans` deletes every placement whose entry exists in none of
 * the tables it knows about, and it recognises a widget's placement by rebuilding
 * this id as `'widget:' || app_widget_id`. If the prefix here ever changes, that
 * query silently stops matching and the scan that runs at startup deletes the
 * cell of every widget on the grid — which is exactly the bug this guards, and
 * it presents as widgets not being saved rather than as anything to do with ids.
 */
class WidgetEntryTest {

    @Test
    fun `the id is the prefix and the host's own number`() {
        assertThat(WidgetEntry.ID_PREFIX).isEqualTo("widget:")
        assertThat(WidgetEntry.idFor(7)).isEqualTo("widget:7")
    }

    @Test
    fun `a built-in's negative id survives the round trip`() {
        // The launcher's own widgets take ids from below zero, where the
        // platform's allocator never goes. String concatenation in SQLite
        // renders those with the sign, and so must this.
        assertThat(WidgetEntry.idFor(-1)).isEqualTo("widget:-1")
    }

    @Test
    fun `a widget knows whether the host is involved in it`() {
        val hosted = WidgetEntry(
            id = WidgetEntry.idFor(7),
            title = "Clock",
            sortTitle = "clock",
            appWidgetId = 7,
            providerComponent = "com.example/.ClockProvider",
        )
        assertThat(hosted.isBuiltIn).isFalse()

        val own = hosted.copy(builtIn = LauncherWidget.CONTINUE_PLAYING)
        assertThat(own.isBuiltIn).isTrue()
    }

    @Test
    fun `every launcher widget asks for a size the grid can hold`() {
        LauncherWidget.entries.forEach { widget ->
            assertThat(widget.defaultColumns)
                .isIn(WidgetEntry.MIN_SPAN..WidgetEntry.MAX_SPAN)
            assertThat(widget.defaultRows)
                .isIn(WidgetEntry.MIN_SPAN..WidgetEntry.MAX_SPAN)
            // The smallest matrix pinch can reach is 3x2; a default larger than
            // that is one the user cannot place without resizing the grid first.
            assertThat(widget.defaultColumns).isAtMost(GridSpec.MIN_COLUMNS)
            assertThat(widget.defaultRows).isAtMost(GridSpec.MIN_ROWS)
        }
    }

    @Test
    fun `a launcher widget is recovered from the name the database stored`() {
        LauncherWidget.entries.forEach { widget ->
            assertThat(LauncherWidget.from(widget.name)).isEqualTo(widget)
        }
        // A widget dropped in a later build resolves to nothing rather than
        // throwing, which is what lets its row be drawn as unavailable.
        assertThat(LauncherWidget.from("A_WIDGET_THAT_WAS_REMOVED")).isNull()
        assertThat(LauncherWidget.from(null)).isNull()
    }
}
