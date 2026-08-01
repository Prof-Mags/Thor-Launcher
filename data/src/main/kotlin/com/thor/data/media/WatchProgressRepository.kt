package com.thor.data.media

import com.thor.core.common.dispatchers.Dispatcher
import com.thor.core.common.dispatchers.ThorDispatcher
import com.thor.core.database.dao.WatchProgressDao
import com.thor.core.database.model.WatchProgressEntity
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRow
import com.thor.core.model.WatchProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the viewer got to, and what to offer them next.
 *
 * Persisted rather than held in memory, which is the whole point of it. The
 * position lived in a map on the view model with a note saying it would be
 * migrated once — and a launcher's process does not outlive the thing it
 * launched, so the resume point was reliably gone by the moment it was wanted.
 * A continue-watching shelf built on that would have been empty every time the
 * launcher had been away.
 */
@Singleton
class WatchProgressRepository @Inject constructor(
    private val dao: WatchProgressDao,
    @Dispatcher(ThorDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    /** Serializes periodic saves, terminal deletion, and manual removal. */
    private val writeMutex = Mutex()

    /**
     * The continue-watching shelf, kept current.
     *
     * A flow rather than a fetch: the row has to change the moment a film is
     * stopped, and the viewer is looking straight at it when that happens.
     *
     * Landscape artwork, because these are resumptions rather than
     * recommendations — a still from the thing you were watching reads as
     * "carry on" where a poster reads as "start this".
     */
    fun observeContinueWatching(): Flow<MediaRow> = dao.observeInProgress().map { rows ->
        /* Convert both values together so skipped malformed ids cannot shift alignment. */
        val entries = rows.mapNotNull { row ->
            val item = row.toItem() ?: return@mapNotNull null
            val progress = row.toProgress() ?: return@mapNotNull null
            item to progress
        }
        MediaRow(
            id = CONTINUE_ROW_ID,
            title = "Continue watching",
            items = entries.map { it.first },
            landscape = true,
            progress = entries.map { it.second },
        )
    }

    /** Everything recorded for one title, so a series can resume its own episode. */
    suspend fun progressFor(id: MediaId): List<WatchProgress> = withContext(ioDispatcher) {
        dao.forMedia(id.key).mapNotNull(WatchProgressEntity::toProgress)
    }

    suspend fun progressFor(id: MediaId, season: Int?, episode: Int?): WatchProgress? =
        withContext(ioDispatcher) {
            dao.find(keyOf(id, season, episode))?.toProgress()
        }

    /**
     * Records a position.
     *
     * Denormalises the title and artwork alongside it so the shelf can be drawn
     * from one query — the alternative is a catalogue round trip per row, on the
     * screen that has to appear fastest.
     */
    suspend fun record(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        positionMs: Long,
        durationMs: Long,
        nowEpochMs: Long,
    ) = withContext(ioDispatcher) {
        writeMutex.withLock {
            if (durationMs <= 0L) return@withLock

            val safePosition = positionMs.coerceAtLeast(0L)
            val progress = WatchProgress(
                mediaId = item.id,
                seasonNumber = season,
                episodeNumber = episode,
                positionMs = safePosition,
                durationMs = durationMs,
                updatedAtEpochMs = nowEpochMs,
            )
            val progressKey = keyOf(item.id, season, episode)

            // A completed title is no longer a resume point. Removing it also keeps
            // the continue shelf and database from accumulating finished entries.
            if (progress.isFinished) {
                dao.clear(progressKey)
                return@withLock
            }

            dao.upsert(
                WatchProgressEntity(
                    id = progressKey,
                    mediaKey = item.id.key,
                    seasonNumber = season,
                    episodeNumber = episode,
                    positionMillis = safePosition,
                    durationMillis = durationMs,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
        }
    }

    /** Forgets one entry — "remove from continue watching". */
    suspend fun forget(id: MediaId, season: Int? = null, episode: Int? = null) =
        withContext(ioDispatcher) {
            writeMutex.withLock { dao.clear(keyOf(id, season, episode)) }
        }

    suspend fun forgetTitle(id: MediaId) = withContext(ioDispatcher) {
        writeMutex.withLock { dao.clearMedia(id.key) }
    }

    companion object {
        const val CONTINUE_ROW_ID = "continue"

        /**
         * `movie:tt0133093`, or `series:tt0903747:2:7`.
         *
         * Built rather than composite so a row can be upserted without being read
         * first: the player writes one of these several times a minute for the
         * whole length of a film.
         */
        fun keyOf(id: MediaId, season: Int?, episode: Int?): String =
            if (season != null && episode != null) "${id.key}:$season:$episode" else id.key
    }
}

/**
 * Enough of a title to draw a shelf cell and resume it.
 *
 * Deliberately not a full [MediaItem] pretending to be complete: what is stored
 * is a title, its artwork and where the viewer got to, and selecting the cell
 * fetches the rest. Inventing empty genres and an empty cast here would make a
 * partial record indistinguishable from a title the catalogue knows nothing
 * about.
 */
private fun WatchProgressEntity.toItem(): MediaItem? {
    val id = MediaId.parse(mediaKey) ?: return null
    return MediaItem(
        id = id,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
    )
}

private fun WatchProgressEntity.toProgress(): WatchProgress? {
    val id = MediaId.parse(mediaKey) ?: return null
    return WatchProgress(
        mediaId = id,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        positionMs = positionMillis,
        durationMs = durationMillis,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
