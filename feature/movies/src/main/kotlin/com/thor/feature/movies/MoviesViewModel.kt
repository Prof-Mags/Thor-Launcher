package com.thor.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thor.core.common.coroutines.launchSafely
import com.thor.core.datastore.SettingsRepository
import com.thor.core.model.MediaId
import com.thor.core.model.MediaItem
import com.thor.core.model.MediaRow
import com.thor.core.model.MediaSettings
import com.thor.core.model.MediaType
import com.thor.core.model.Season
import com.thor.core.model.SourceRanking
import com.thor.core.model.StreamSource
import com.thor.core.model.WatchProgress
import com.thor.data.media.MediaRepository
import com.thor.data.media.ResolvedStream
import com.thor.data.media.SourceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Where the cursor is in the browse grid: which shelf, and how far along it. */
data class BrowseCursor(val row: Int = 0, val column: Int = 0)

/** What the section is doing, as one value the two panels both read. */
data class MoviesUiState(
    val type: MediaType = MediaType.MOVIE,
    val rows: List<MediaRow> = emptyList(),
    val cursor: BrowseCursor = BrowseCursor(),
    val loading: Boolean = false,
    /** Set when the section cannot work at all, e.g. no TMDb key. */
    val setupMessage: String? = null,
) {
    val highlighted: MediaItem?
        get() = rows.getOrNull(cursor.row)?.items?.getOrNull(cursor.column)
}

/** The detail panel's own state, which lags the cursor by one fetch. */
data class DetailState(
    val item: MediaItem? = null,
    val loading: Boolean = false,
    val season: Season? = null,
    val selectedSeason: Int = 1,
    val similar: List<MediaItem> = emptyList(),
)

/** Everything about picking and opening a source. */
data class SourceState(
    val result: SourceResult? = null,
    val searching: Boolean = false,
    val chosen: StreamSource? = null,
    val resolving: Boolean = false,
    val resolveError: String? = null,
    /** Progress while the debrid service fetches an uncached torrent. */
    val downloadProgress: Float? = null,
)

/** A stream that is ready to play, with what is playing it. */
data class Playback(
    val url: String,
    val item: MediaItem,
    val source: StreamSource,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val resumeFromMs: Long = 0L,
) {
    val title: String
        get() = if (seasonNumber != null && episodeNumber != null) {
            "${item.title} · S%02dE%02d".format(seasonNumber, episodeNumber)
        } else {
            item.title
        }
}

/**
 * The Movies section.
 *
 * One view model for both panels, deliberately. The top screen browses and the
 * bottom screen describes what is highlighted — they are two views of a single
 * cursor, and splitting them would mean synchronising two sources of truth
 * across a window boundary for no gain.
 */
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoviesUiState())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private val _detail = MutableStateFlow(DetailState())
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _sources = MutableStateFlow(SourceState())
    val sources: StateFlow<SourceState> = _sources.asStateFlow()

    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback.asStateFlow()

    val settings: StateFlow<MediaSettings> = settingsRepository.media.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaSettings(),
    )

    /**
     * Progress per title, in memory for now.
     *
     * Deliberately not persisted yet: the schema wants to arrive with episode
     * tracking rather than be migrated twice, and losing resume points between
     * runs is a smaller cost than a database column that has to change shape.
     */
    private val progress = mutableMapOf<String, WatchProgress>()

    /** Cancelled when the cursor moves, so a slow fetch cannot land on a later title. */
    private var detailJob: Job? = null
    private var sourceJob: Job? = null

    init {
        load(MediaType.MOVIE)
    }

    fun load(type: MediaType) {
        _uiState.update { it.copy(type = type, loading = true, setupMessage = null) }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _uiState.update {
                    it.copy(loading = false, setupMessage = error.message ?: "Could not load")
                }
            },
        ) {
            val media = settingsRepository.media.first()
            if (!media.isMetadataConfigured) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        rows = emptyList(),
                        setupMessage = "Add a TMDb API key in Settings to browse films and shows.",
                    )
                }
                return@launchSafely
            }

            val rows = repository.browseRows(type)
            _uiState.update {
                it.copy(
                    rows = rows,
                    loading = false,
                    cursor = BrowseCursor(),
                    setupMessage = if (rows.isEmpty()) "Nothing came back from TMDb." else null,
                )
            }
            refreshDetail()
        }
    }

    fun switchType(type: MediaType) {
        if (type == _uiState.value.type) return
        load(type)
    }

    /**
     * Moves the browse cursor.
     *
     * Clamped rather than wrapped in both axes: a shelf that wraps means pressing
     * Right on the last item lands on the first, which reads as the cursor
     * jumping rather than as running out of shelf.
     */
    fun move(deltaRow: Int, deltaColumn: Int) {
        val state = _uiState.value
        if (state.rows.isEmpty()) return

        val row = (state.cursor.row + deltaRow).coerceIn(0, state.rows.lastIndex)
        val width = state.rows[row].items.size
        if (width == 0) return

        // Column is clamped against the *new* row, so moving between shelves of
        // different lengths lands somewhere real rather than off the end.
        val column = (state.cursor.column + deltaColumn).coerceIn(0, width - 1)
        if (row == state.cursor.row && column == state.cursor.column) return

        _uiState.update { it.copy(cursor = BrowseCursor(row, column)) }
        refreshDetail()
    }

    private fun refreshDetail() {
        val item = _uiState.value.highlighted
        detailJob?.cancel()
        sourceJob?.cancel()
        _sources.value = SourceState()

        if (item == null) {
            _detail.value = DetailState()
            return
        }

        // The summary is shown immediately and replaced when the full record
        // arrives, so moving along a shelf never shows an empty panel.
        _detail.value = DetailState(item = item, loading = true)

        detailJob = viewModelScope.launchSafely(TAG) {
            val full = repository.details(item.id) ?: item
            val firstSeason = full.orderedSeasons.firstOrNull { !it.isSpecials }?.number ?: 1

            _detail.update {
                it.copy(item = full, loading = false, selectedSeason = firstSeason)
            }

            if (full.isSeries) selectSeason(firstSeason)
            _detail.update { it.copy(similar = repository.similar(full.id)) }
        }
    }

    fun selectSeason(number: Int) {
        val item = _detail.value.item ?: return
        if (!item.isSeries) return

        _detail.update { it.copy(selectedSeason = number) }
        viewModelScope.launchSafely(TAG) {
            val season = repository.season(item.id.tmdbId, number)
            // Guarded: the user can change season while this is in flight.
            if (_detail.value.selectedSeason == number) {
                _detail.update { it.copy(season = season) }
            }
        }
    }

    /**
     * Finds sources for what is highlighted.
     *
     * Separate from the detail fetch and never automatic. A search hits every
     * configured indexer and then the debrid service; doing that for each title
     * the cursor passes over would hammer both and be discarded almost every
     * time.
     */
    fun findSources(seasonNumber: Int? = null, episodeNumber: Int? = null) {
        val item = _detail.value.item ?: return
        sourceJob?.cancel()
        _sources.value = SourceState(searching = true)

        sourceJob = viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _sources.value = SourceState(resolveError = error.message ?: "Search failed")
            },
        ) {
            val result = repository.sourcesFor(item, seasonNumber, episodeNumber)
            _sources.update { it.copy(result = result, searching = false) }
        }
    }

    /**
     * Plays [source], resolving it first.
     *
     * @param seasonNumber and [episodeNumber] carried through so the player can
     *   title itself and so progress is recorded against the right episode.
     */
    fun play(
        source: StreamSource,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) {
        val item = _detail.value.item ?: return
        _sources.update { it.copy(chosen = source, resolving = true, resolveError = null) }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _sources.update {
                    it.copy(resolving = false, resolveError = error.message ?: "Could not open")
                }
            },
        ) {
            when (val resolved = repository.resolve(source)) {
                is ResolvedStream.Ready -> {
                    _sources.update { it.copy(resolving = false, downloadProgress = null) }
                    _playback.value = Playback(
                        url = resolved.url,
                        item = item,
                        source = source,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        resumeFromMs = resumePoint(item.id, seasonNumber, episodeNumber),
                    )
                }

                is ResolvedStream.Downloading -> _sources.update {
                    it.copy(
                        resolving = false,
                        downloadProgress = resolved.progress,
                        resolveError = "Real-Debrid is still fetching this source. " +
                            "Pick a cached one, or try again shortly.",
                    )
                }

                is ResolvedStream.Failed -> _sources.update {
                    it.copy(resolving = false, resolveError = resolved.reason)
                }
            }
        }
    }

    /** Plays the best source without showing the list, when settings allow it. */
    fun playBest(seasonNumber: Int? = null, episodeNumber: Int? = null) {
        val item = _detail.value.item ?: return
        _sources.value = SourceState(searching = true)

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                _sources.value = SourceState(resolveError = error.message ?: "Search failed")
            },
        ) {
            val result = repository.sourcesFor(item, seasonNumber, episodeNumber)
            _sources.update { it.copy(result = result, searching = false) }

            val media = settingsRepository.media.first()
            val best = (result as? SourceResult.Found)
                ?.let { SourceRanking.best(it.all, media) }

            if (best != null && media.autoSelectSource) {
                play(best, seasonNumber, episodeNumber)
            }
        }
    }

    fun stopPlayback() {
        _playback.value = null
    }

    fun clearSourceError() {
        _sources.update { it.copy(resolveError = null) }
    }

    /** Records where the viewer got to, so the title can be resumed. */
    fun onProgress(positionMs: Long, durationMs: Long) {
        val playing = _playback.value ?: return
        val key = progressKey(playing.item.id, playing.seasonNumber, playing.episodeNumber)
        progress[key] = WatchProgress(
            mediaId = playing.item.id,
            seasonNumber = playing.seasonNumber,
            episodeNumber = playing.episodeNumber,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    fun progressFor(id: MediaId, season: Int? = null, episode: Int? = null): WatchProgress? =
        progress[progressKey(id, season, episode)]

    private fun resumePoint(id: MediaId, season: Int?, episode: Int?): Long =
        progressFor(id, season, episode)?.takeIf { it.isResumable }?.positionMs ?: 0L

    /** The episode after the one playing, or null at the end of a season. */
    fun nextEpisode(): Pair<Int, Int>? {
        val playing = _playback.value ?: return null
        val season = playing.seasonNumber ?: return null
        val episode = playing.episodeNumber ?: return null
        val episodes = _detail.value.season?.takeIf { it.number == season }?.episodes ?: return null

        val index = episodes.indexOfFirst { it.number == episode }
        val next = episodes.getOrNull(index + 1) ?: return null
        return season to next.number
    }

    private companion object {
        const val TAG = "Movies"

        fun progressKey(id: MediaId, season: Int?, episode: Int?): String =
            if (season != null && episode != null) "${id.key}:$season:$episode" else id.key
    }
}
