package com.thor.feature.home.couch

import com.google.common.truth.Truth.assertThat
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.PlayStats
import com.thor.core.model.GridEntry
import com.thor.core.model.PlatformFolders
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
    ) = GameEntry(
        id = id,
        title = id,
        sortTitle = id,
        platformId = "snes",
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
     * A platform folder is the scanner filing games, not a collection.
     *
     * Counting them would report a "Collections" figure that grows every time a
     * system is added, which is the library organising itself rather than
     * anything the user made.
     */
    @Test
    fun `only folders the user made count as collections`() {
        val entries: List<GridEntry> = listOf(
            FolderEntry(id = "folder:mine", title = "Shooters", sortTitle = "shooters"),
            FolderEntry(
                id = PlatformFolders.idFor("snes"),
                title = "SNES",
                sortTitle = "snes",
            ),
        )

        assertThat(couchLibraryCounts(entries).collections).isEqualTo(1)
    }

    @Test
    fun `an empty library counts to nothing rather than failing`() {
        val counts = couchLibraryCounts(emptyList())

        assertThat(counts.allGames).isEqualTo(0)
        assertThat(counts.collections).isEqualTo(0)
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
