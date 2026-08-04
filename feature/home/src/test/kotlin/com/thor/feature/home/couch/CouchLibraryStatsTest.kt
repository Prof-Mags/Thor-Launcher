package com.thor.feature.home.couch

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.thor.core.model.AppEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.Platform
import com.thor.core.model.PlayStats
import org.junit.Test

class CouchLibraryStatsTest {

    @Test
    fun `scopes every figure to the focused platform`() {
        val stats = couchLibraryStats(
            entries = listOf(
                game("a", platform = "gba", favorite = true, launches = 3, hours = 2),
                game("b", platform = "gba", launches = 1, hours = 5),
                game("c", platform = "gba"),
                game("d", platform = "snes", favorite = true, launches = 9, hours = 40),
            ),
            platform = platform("gba", short = "GBA"),
        )

        assertThat(stats.title).isEqualTo("GBA")
        assertThat(stats.games).isEqualTo(3)
        assertThat(stats.favorites).isEqualTo(1)
        assertThat(stats.played).isEqualTo(2)
        assertThat(stats.trailing).isEqualTo(7)
        assertThat(stats.trailingLabel).isEqualTo("Hours")
    }

    @Test
    fun `falls back to library totals when nothing platform-bound is focused`() {
        val stats = couchLibraryStats(
            entries = listOf(
                game("a", platform = "gba", launches = 1),
                game("b", platform = "snes", favorite = true),
                AppEntry(
                    id = "app",
                    title = "Browser",
                    sortTitle = "browser",
                    packageName = "com.example",
                    activityName = "com.example.Main",
                ),
            ),
            platform = null,
        )

        assertThat(stats.title).isEqualTo("YOUR LIBRARY")
        assertThat(stats.games).isEqualTo(2)
        assertThat(stats.favorites).isEqualTo(1)
        assertThat(stats.played).isEqualTo(1)
        assertThat(stats.trailing).isEqualTo(2)
        assertThat(stats.trailingLabel).isEqualTo("Platforms")
    }

    @Test
    fun `a platform with nothing in it reads zero rather than borrowing the library`() {
        val stats = couchLibraryStats(
            entries = listOf(game("a", platform = "gba", launches = 4, hours = 12)),
            platform = platform("n64", short = ""),
        )

        // Falls back to the full name when there is no short one, uppercased
        // like every other heading on the shelf.
        assertThat(stats.title).isEqualTo("NINTENDO 64")
        assertThat(stats.games).isEqualTo(0)
        assertThat(stats.played).isEqualTo(0)
        assertThat(stats.trailing).isEqualTo(0)
    }

    @Test
    fun `part-finished hours round down`() {
        val entry = game("a", platform = "gba").let {
            it.copy(stats = it.stats.copy(totalPlayMillis = 3_599_999L))
        }

        assertThat(couchLibraryStats(listOf(entry), platform("gba")).trailing).isEqualTo(0)
    }

    @Test
    fun `card size takes the preferred edge when the slot has room`() {
        assertThat(couchCardSize(400.dp)).isEqualTo(173.dp)
    }

    @Test
    fun `card size shrinks to fit a short slot instead of overflowing it`() {
        // 150 less the 26dp header and the 22dp rail inset leaves 102, below the
        // floor, so the floor takes it.
        assertThat(couchCardSize(150.dp)).isEqualTo(104.dp)
        // 200 leaves 152: under the preferred edge and over the floor, taken as is.
        assertThat(couchCardSize(200.dp)).isEqualTo(152.dp)
    }

    @Test
    fun `card size never collapses on an absurdly short slot`() {
        assertThat(couchCardSize(0.dp)).isEqualTo(104.dp)
    }

    @Test
    fun `the dashboard regions take their share of a roomy screen`() {
        // 600 tall: a third to the shelf, a sixth to the bottom bar, and the
        // panels above keep what is left rather than being squeezed by two
        // blocks that were sized for a different screen.
        assertThat(couchShelfHeight(600.dp)).isEqualTo(204.dp)
        assertThat(couchDashboardHeight(600.dp)).isEqualTo(96.dp)
    }

    @Test
    fun `the dashboard regions stop growing on a very tall screen`() {
        assertThat(couchShelfHeight(1200.dp)).isEqualTo(228.dp)
        assertThat(couchDashboardHeight(1200.dp)).isEqualTo(104.dp)
    }

    @Test
    fun `the dashboard regions stop shrinking on a very short one`() {
        assertThat(couchShelfHeight(200.dp)).isEqualTo(148.dp)
        assertThat(couchDashboardHeight(200.dp)).isEqualTo(78.dp)
    }

    @Test
    fun `shelf cards take what the block has left after its title`() {
        // 204 less the 16dp clearance, the 18dp title, the 8dp under it and the
        // 12dp a focused card grows into leaves 150.
        assertThat(couchShelfCardSize(204.dp)).isEqualTo(150.dp)
        // And neither end runs away: the cap holds a tall block back, the floor
        // holds a short one up.
        assertThat(couchShelfCardSize(400.dp)).isEqualTo(152.dp)
        assertThat(couchShelfCardSize(100.dp)).isEqualTo(96.dp)
    }

    @Test
    fun `the counts panel narrows with the screen rather than crowding it`() {
        assertThat(couchLibraryPanelWidth(1000.dp)).isEqualTo(260.dp)
        assertThat(couchLibraryPanelWidth(1600.dp)).isEqualTo(320.dp)
        assertThat(couchLibraryPanelWidth(600.dp)).isEqualTo(220.dp)
    }

    @Test
    fun `every rail destination stays on the screen`() {
        // Six tiles and five gaps inside a 400dp column, less the 20dp inset at
        // each end: 310 to share, which is more than the preferred 40.
        assertThat(couchRailItemSize(400.dp, 6)).isEqualTo(40.dp)
        // 260 leaves 170 for six, so they give ground rather than overflowing.
        assertThat(couchRailItemSize(260.dp, 6)).isEqualTo(170.dp / 6)
        // And they stop giving it before they stop being targets.
        assertThat(couchRailItemSize(120.dp, 6)).isEqualTo(28.dp)
    }

    @Test
    fun `an empty rail asks for nothing absurd`() {
        assertThat(couchRailItemSize(400.dp, 0)).isEqualTo(40.dp)
    }

    private fun platform(id: String, short: String = id.uppercase()) = Platform(
        id = id,
        name = "Nintendo 64",
        shortName = short,
        manufacturer = "Nintendo",
        releaseYear = 1996,
        accentArgb = 0xFF000000,
        romExtensions = setOf("z64"),
    )

    private fun game(
        id: String,
        platform: String,
        favorite: Boolean = false,
        launches: Int = 0,
        hours: Long = 0L,
    ) = GameEntry(
        id = id,
        title = id,
        sortTitle = id,
        platformId = platform,
        contentUri = "content://$id",
        fileName = "$id.rom",
        fileSizeBytes = 1L,
        stats = PlayStats(
            totalPlayMillis = hours * 3_600_000L,
            launchCount = launches,
        ),
        isFavorite = favorite,
    )
}
