package com.thor.feature.home.couch

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.PlatformFolders
import com.thor.core.model.PlayStats
import org.junit.Test

/**
 * The two figures on the dashboard that are arithmetic rather than layout.
 *
 * Everything else on that screen is either a placeholder or a direct read, but
 * these two are counted — and a count that is quietly wrong on a television is
 * read as fact, because there is nothing next to it to check it against.
 */
class CouchHomeTest {

    private fun game(
        id: String,
        favourite: Boolean = false,
        playedAt: Long? = null,
        platform: String = "snes",
    ) = GameEntry(
        id = id,
        title = id,
        sortTitle = id,
        platformId = platform,
        contentUri = "content://$id",
        fileName = "$id.sfc",
        fileSizeBytes = 1024L,
        isFavorite = favourite,
        stats = PlayStats(lastPlayedEpochMs = playedAt),
    )

    // ---- Library counts -----------------------------------------------------

    @Test
    fun `counts describe what is actually in the library`() {
        val entries: List<GridEntry> = listOf(
            game("a", favourite = true, playedAt = 10L),
            game("b", playedAt = 20L),
            game("c"),
            AppEntry(
                id = "app:one",
                title = "One",
                sortTitle = "one",
                packageName = "com.example.one",
                activityName = "com.example.one.Main",
            ),
        )

        val counts = couchLibraryCounts(entries)

        assertThat(counts.allGames).isEqualTo(3)
        assertThat(counts.favourites).isEqualTo(1)
        assertThat(counts.recentlyPlayed).isEqualTo(2)
        assertThat(counts.installed).isEqualTo(1)
    }

    /**
     * Platforms counts systems, not games and not folders.
     *
     * The shelf is built one rail per system, and this row is the way into them,
     * so the figure has to be the number of rails behind it — several games on
     * one system is one shelf to go and look at.
     */
    @Test
    fun `platforms counts the systems with something on them`() {
        val entries: List<GridEntry> = listOf(
            game("a", platform = "snes"),
            game("b", platform = "snes"),
            game("c", platform = "md"),
            FolderEntry(
                id = PlatformFolders.idFor("n64"),
                title = "N64",
                sortTitle = "n64",
            ),
        )

        // Two systems, though there are three games and a third system's folder:
        // a folder with nothing in it is not a shelf this row can reach.
        assertThat(couchLibraryCounts(entries).platforms).isEqualTo(2)
    }

    @Test
    fun `an empty library counts to nothing rather than failing`() {
        val counts = couchLibraryCounts(emptyList())

        assertThat(counts.allGames).isEqualTo(0)
        assertThat(counts.platforms).isEqualTo(0)
    }

    // ---- Storage ------------------------------------------------------------

    @Test
    fun `storage reports what is used rather than what is free`() {
        val storage = couchStorageOf(totalBytes = 476_000_000_000, freeBytes = 261_000_000_000)

        assertThat(storage.totalGb).isEqualTo(476)
        assertThat(storage.usedGb).isEqualTo(215)
        assertThat(storage.usedFraction).isWithin(0.01f).of(0.45f)
    }

    /**
     * A volume that cannot be read must not divide by zero, and must not draw a
     * full bar — which is what any "used = total - free" would give for 0 and 0.
     */
    @Test
    fun `an unreadable volume reports nothing rather than a full bar`() {
        val storage = couchStorageOf(totalBytes = 0, freeBytes = 0)

        assertThat(storage.totalGb).isEqualTo(0)
        assertThat(storage.usedFraction).isEqualTo(0f)
    }

    @Test
    fun `free space larger than the volume cannot push the bar past full`() {
        val storage = couchStorageOf(totalBytes = 100_000_000_000, freeBytes = 200_000_000_000)

        assertThat(storage.usedFraction).isAtMost(1f)
        assertThat(storage.usedFraction).isAtLeast(0f)
    }
}
