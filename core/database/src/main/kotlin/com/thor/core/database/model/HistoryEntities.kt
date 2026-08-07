package com.thor.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One play session. Kept as individual rows rather than a running total so the
 * detail panel can show a play history and so totals can be recomputed if a
 * session is ever recorded twice.
 */
@Entity(
    tableName = "play_sessions",
    indices = [Index(value = ["entry_id"]), Index(value = ["started_at"])],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0L,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMs: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
)


/**
 * What the user wrote about a game.
 *
 * A table of its own rather than a column on `games`, and rather than a field in
 * `GameMetadata`, because it is the one thing in the library that no scraper may
 * ever touch. Metadata is merged from five sources and overwritten on every
 * rescrape — a note living there would need the locked-field machinery to survive,
 * and a note that can be lost to a background scrape is not worth writing.
 *
 * Keyed by entry rather than by game, so a note can be attached to anything the
 * grid holds. In practice that is games; the type does not need to care.
 *
 * No foreign key to `games`, deliberately. A rescan that loses a ROM removes its
 * row, and the note about where you got to in it is exactly what should outlive
 * a moved file — see [com.thor.data.library.NoteRepository], which sweeps notes
 * only when a game is deleted on purpose.
 */
@Entity(
    tableName = "game_notes",
    // Ordered by when it was written wherever notes are listed rather than
    // looked up, which is every screen that shows more than one.
    indices = [Index(value = ["updated_at"])],
)
data class GameNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)

/** A single RetroAchievements achievement and this user's progress on it. */
@Entity(
    tableName = "achievements",
    indices = [Index(value = ["entry_id"])],
)
data class AchievementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "points") val points: Int,
    @ColumnInfo(name = "badge_uri") val badgeUri: String? = null,
    @ColumnInfo(name = "earned_at") val earnedEpochMs: Long? = null,
    @ColumnInfo(name = "is_hardcore") val isHardcore: Boolean = false,
)


/**
 * How far through a film or an episode the viewer got.
 *
 * Keyed on the title *and* the episode, because "continue watching" has to
 * resume the right one and a series-level position cannot say which episode it
 * belonged to. The key is built rather than composite so a row can be upserted
 * without reading it first — the player writes one of these several times a
 * minute for the whole length of a film.
 *
 * Persisted, unlike the in-memory map it replaces. A resume point that does not
 * survive the launcher being killed is one that is gone every time it is
 * actually wanted: a launcher's process does not outlive the game it started.
 */
@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["updated_at"]), Index(value = ["media_key"])],
)
data class WatchProgressEntity(
    /** `movie:tt0133093`, or `series:tt0903747:2:7`. */
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    /** The title alone, so a series' episodes can be found together. */
    @ColumnInfo(name = "media_key") val mediaKey: String,
    @ColumnInfo(name = "season_number") val seasonNumber: Int? = null,
    @ColumnInfo(name = "episode_number") val episodeNumber: Int? = null,
    @ColumnInfo(name = "position_millis") val positionMillis: Long,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    /** Denormalised so the shelf can be drawn without a catalogue round trip. */
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "poster_url") val posterUrl: String? = null,
    @ColumnInfo(name = "backdrop_url") val backdropUrl: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMs: Long,
)
