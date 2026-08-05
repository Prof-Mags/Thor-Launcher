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
import com.thor.data.media.WatchProgressRepository
import com.thor.feature.movies.player.PlayerStatus
import com.thor.feature.movies.player.ThorPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job

/** Where the cursor is in the browse grid: which shelf, and how far along it. */
data class BrowseCursor(val row: Int = 0, val column: Int = 0) {

    /**
     * Kept inside a set of shelves that has changed underneath it.
     *
     * The continue-watching row appears and disappears as things are watched, so
     * the shelf the cursor was on can move or go away entirely while the viewer
     * is looking at it. Clamping beats resetting: resetting would throw them back
     * to the top of the screen every time a film was stopped.
     */
    fun clampedTo(rows: List<MediaRow>): BrowseCursor {
        if (rows.isEmpty()) return BrowseCursor()
        val safeRow = row.coerceIn(0, rows.lastIndex)
        val width = rows[safeRow].items.size
        return BrowseCursor(safeRow, column.coerceIn(0, (width - 1).coerceAtLeast(0)))
    }

    /**
     * Follows the same shelf entry when a dynamic row is inserted or removed.
     *
     * Continue-watching can appear above the cursor while progress is being
     * persisted. Index-only clamping would then silently select a different
     * title. Shelf id plus [MediaRow.entryKeyAt] keeps the actual selection.
     */
    fun remappedFrom(previousRows: List<MediaRow>, nextRows: List<MediaRow>): BrowseCursor {
        if (nextRows.isEmpty()) return BrowseCursor()
        val previousRow = previousRows.getOrNull(row) ?: return clampedTo(nextRows)
        val entryKey = previousRow.entryKeyAt(column)
        val nextRowIndex = nextRows.indexOfFirst { it.id == previousRow.id }

        if (nextRowIndex >= 0) {
            val nextRow = nextRows[nextRowIndex]
            val nextColumn = entryKey
                ?.let { key -> nextRow.items.indices.firstOrNull { nextRow.entryKeyAt(it) == key } }
                ?: column.coerceIn(0, (nextRow.items.lastIndex).coerceAtLeast(0))
            return BrowseCursor(nextRowIndex, nextColumn)
        }

        // If the shelf itself disappeared, retain the title when it is present
        // elsewhere before falling back to a nearby valid index.
        if (entryKey != null) {
            nextRows.forEachIndexed { candidateRow, mediaRow ->
                val candidateColumn = mediaRow.items.indices
                    .firstOrNull { mediaRow.entryKeyAt(it) == entryKey }
                if (candidateColumn != null) return BrowseCursor(candidateRow, candidateColumn)
            }
        }
        return clampedTo(nextRows)
    }
}

/** What the section is doing, as one value the two panels both read. */
data class MoviesUiState(
    val type: MediaType = MediaType.MOVIE,
    val rows: List<MediaRow> = emptyList(),
    val cursor: BrowseCursor = BrowseCursor(),
    val loading: Boolean = false,
    /** What is in the search box, empty when the viewer is browsing. */
    val query: String = "",
    /**
     * Results, or null when not searching at all.
     *
     * Null and empty mean different things and the screen says so: null is "you
     * have not searched", empty is "nothing matched" — and showing the shelves
     * for the second would look like the search never happened.
     */
    val searchResults: List<MediaItem>? = null,
    val searching: Boolean = false,
    /** Set when the section cannot work at all. */
    val setupMessage: String? = null,
) {
    val isSearching: Boolean get() = searchResults != null || query.isNotBlank()

    /**
     * What the cursor walks: results while searching, shelves otherwise.
     *
     * One list either way, so cursor movement, the detail panel and the source
     * search need no idea which mode the screen is in.
     */
    val visibleRows: List<MediaRow> by lazy(LazyThreadSafetyMode.NONE) {
        /*
         * Computed once per state, not once per read.
         *
         * As a plain `get()` this allocated a list and a `MediaRow` on every
         * access — and it is read during composition, by `highlighted`, and by
         * cursor movement, so it was allocating on every frame of a held
         * direction. `by lazy` costs one object per state instance instead, and
         * the instance is already replaced whenever any of this changes.
         */
        searchResults?.let { results ->
            /*
             * The shelf is titled with what was asked for.
             *
             * The couch catalogue has no search box - the keyboard is a
             * full-screen overlay raised by a button, and a box standing empty
             * underneath it earned no space on a television - so this heading is
             * the only place the query is written down while the results are
             * being read.
             */
            val heading = query.takeIf(String::isNotBlank)
                ?.let { asked -> "Results for \"$asked\"" }
                ?: "Results"
            listOf(MediaRow(id = "results", title = heading, items = results))
        } ?: rows
    }

    val highlighted: MediaItem?
        get() = visibleRows.getOrNull(cursor.row)?.items?.getOrNull(cursor.column)

    /** Resume metadata for [highlighted], when the cursor is on the continue shelf. */
    val highlightedProgress: WatchProgress?
        get() = visibleRows.getOrNull(cursor.row)?.progressAt(cursor.column)
}

/** The detail panel's own state, which lags the cursor by one fetch. */
data class DetailState(
    val item: MediaItem? = null,
    val loading: Boolean = false,
    val season: Season? = null,
    val selectedSeason: Int = 1,
    /** The exact episode all source discovery and playback should target. */
    val selectedEpisode: Int = 1,
    val similar: List<MediaItem> = emptyList(),
    /** The exact film or episode position represented by a continue-watching card. */
    val resumeProgress: WatchProgress? = null,
)

/** Everything about picking and opening a source. */
data class SourceState(
    val result: SourceResult? = null,
    val searching: Boolean = false,
    /** Episode coordinates used to produce [result], retained until playback starts. */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
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
    val requestHeaders: Map<String, String> = emptyMap(),
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
    /** Resume points, persisted, and the continue-watching shelf they feed. */
    private val watchProgress: WatchProgressRepository,
    /**
     * The player, owned here rather than by whichever panel draws the picture.
     *
     * Both panels need it — one for the video, the other for the transport — and
     * on this device either of their compositions can be the one that is torn
     * down or paused. Anything either of them owned would take the film with it.
     */
    val player: ThorPlayer,
) : ViewModel() {

    /** What the player is doing, read by both panels. */
    val playerStatus: StateFlow<PlayerStatus> = player.status

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

    /** Throttles resume-point writes; see [onProgress]. */
    private var lastProgressWriteAt = 0L

    /** Makes the ended-state write edge-triggered instead of repeating each status tick. */
    private var playerWasEnded = false

    /**
     * Holds the playing series in the detail panel between stopping one episode
     * and resolving the next. Continue-watching cleanup can otherwise remap the
     * browse cursor during that gap and replace the item the source search uses.
     */
    private var playbackTransitionItemId: MediaId? = null

    /** The catalogue rows, before the continue-watching shelf is put in front. */
    private val catalogueRows = MutableStateFlow<List<MediaRow>>(emptyList())

    /** Kept separate so filtering the dynamic shelf reacts before a catalogue fetch ends. */
    private val selectedMediaType = MutableStateFlow(MediaType.MOVIE)

    /** Cancelled when the cursor moves, so a slow fetch cannot land on a later title. */
    private var detailJob: Job? = null
    private var sourceJob: Job? = null
    private var resolveJob: Job? = null
    private var similarJob: Job? = null
    private var searchJob: Job? = null

    init {
        load(MediaType.MOVIE)

        /*
         * Progress comes from the player's own status, not from the panel that
         * draws it.
         *
         * It was relayed through that panel's composition, which on this device
         * is the one thing that cannot be relied upon to be running: a film
         * watched while an app holds the other screen recorded nothing at all,
         * because the composition doing the relaying had stopped.
         *
         * Below the property declarations on purpose — the collector reads
         * `_playback` on its first emission, and a property declared under an
         * `init` block does not exist yet while that block runs.
         */
        viewModelScope.launchSafely(TAG) {
            player.status.collect { status ->
                val justEnded = status.ended && !playerWasEnded
                playerWasEnded = status.ended
                if (status.durationMs > 0L) {
                    onProgress(
                        positionMs = status.positionMs,
                        durationMs = status.durationMs,
                        force = justEnded,
                    )
                }
            }
        }

        /*
         * Continue watching sits in front of the catalogue, and is kept there by
         * combining rather than by being inserted once.
         *
         * The shelf has to change the moment a film is stopped — the viewer is
         * looking straight at it when that happens — so it is a flow joined to
         * the catalogue rows, not a row copied into them at load time.
         *
         * Only when it has something in it: an empty "Continue watching" header
         * is a promise the screen is not keeping.
         */
        viewModelScope.launchSafely(TAG) {
            combine(
                catalogueRows,
                watchProgress.observeContinueWatching(),
                selectedMediaType,
            ) { rows, continueRow, type ->
                val matchingIndexes = continueRow.items.indices
                    .filter { continueRow.items[it].id.type == type }
                val filteredContinue = continueRow.copy(
                    items = matchingIndexes.map(continueRow.items::get),
                    progress = matchingIndexes.map(continueRow::progressAt),
                )
                if (filteredContinue.items.isEmpty()) rows else listOf(filteredContinue) + rows
            }.collect { rows ->
                val previous = _uiState.value
                _uiState.update { state ->
                    val cursor = if (state.isSearching) {
                        state.cursor
                    } else {
                        state.cursor.remappedFrom(state.rows, rows)
                    }
                    state.copy(rows = rows, cursor = cursor)
                }

                val current = _uiState.value
                val previousProgress = previous.highlightedProgress
                val currentProgress = current.highlightedProgress
                val itemChanged = previous.highlighted?.id?.key != current.highlighted?.id?.key
                val episodeChanged = current.highlighted?.isSeries == true && (
                    previousProgress?.seasonNumber != currentProgress?.seasonNumber ||
                        previousProgress?.episodeNumber != currentProgress?.episodeNumber
                    )

                when {
                    _playback.value != null || playbackTransitionItemId != null -> Unit
                    itemChanged || episodeChanged -> refreshDetail()
                    previousProgress != currentProgress -> _detail.update { detail ->
                        if (detail.item?.id?.key == current.highlighted?.id?.key) {
                            detail.copy(resumeProgress = currentProgress)
                        } else {
                            detail
                        }
                    }
                }
            }
        }
    }

    fun load(type: MediaType) {
        if (_playback.value == null) playbackTransitionItemId = null
        selectedMediaType.value = type
        searchJob?.cancel()
        // Never display the previous media type while its replacement is loading.
        catalogueRows.value = emptyList()
        _uiState.update {
            it.copy(
                type = type,
                loading = true,
                query = "",
                searchResults = null,
                searching = false,
                setupMessage = null,
            )
        }

        viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                if (selectedMediaType.value == type) {
                    _uiState.update {
                        it.copy(loading = false, setupMessage = error.message ?: "Could not load")
                    }
                }
            },
        ) {
            /*
             * No credential check, because there is no credential.
             *
             * This used to refuse to load without a TMDb API key, so the section's
             * entire first impression was an instruction to go and register for
             * one. The catalogue is the Stremio protocol now — the same one the
             * source addons speak, keyed by the same IMDb ids — and it needs
             * nothing configured to answer.
             */
            val rows = repository.browseRows(type)
            // A slower previous tab request must not replace the active tab.
            if (selectedMediaType.value != type) return@launchSafely
            catalogueRows.value = rows
            _uiState.update {
                it.copy(
                    loading = false,
                    cursor = BrowseCursor(),
                    setupMessage = if (rows.isEmpty()) {
                        "Nothing came back from the catalogue. Check the connection."
                    } else {
                        null
                    },
                )
            }
            refreshDetail()
        }
    }

    // ------------------------------------------------------------------ search

    /**
     * Types into the search field.
     *
     * Debounced, because this is driven by THOR's own on-screen keyboard and a
     * request per keystroke would be a request per press of A.
     */
    fun onQueryChanged(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            // Straight back to the shelves rather than to an empty result set:
            // clearing the box means "never mind", not "search for nothing".
            _uiState.update { it.copy(searchResults = null, searching = false) }
            refreshDetail()
            return
        }

        _uiState.update { it.copy(searching = true) }
        searchJob = viewModelScope.launchSafely(
            tag = TAG,
            onError = { _uiState.update { it.copy(searching = false) } },
        ) {
            delay(SEARCH_DEBOUNCE_MS)
            val results = repository.search(query, _uiState.value.type)
            // Guarded: the viewer can type on while this is in flight.
            if (_uiState.value.query == query) {
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        searching = false,
                        cursor = BrowseCursor(),
                    )
                }
                refreshDetail()
            }
        }
    }

    /** Leaves search and returns to the shelves. */
    fun clearSearch() = onQueryChanged("")

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
        // The visible rows, so the cursor walks search results exactly as it
        // walks shelves and nothing downstream needs to know which it is on.
        val rows = state.visibleRows
        if (rows.isEmpty()) return

        val row = (state.cursor.row + deltaRow).coerceIn(0, rows.lastIndex)
        val width = rows[row].items.size
        if (width == 0) return

        // Column is clamped against the *new* row, so moving between shelves of
        // different lengths lands somewhere real rather than off the end.
        val column = (state.cursor.column + deltaColumn).coerceIn(0, width - 1)
        if (row == state.cursor.row && column == state.cursor.column) return

        _uiState.update { it.copy(cursor = BrowseCursor(row, column)) }
        refreshDetail()
    }

    /**
     * Moves directly to a catalogue card selected by touch or pointer input.
     *
     * Returning the item lets the section choose its next panel from the exact
     * same row snapshot, rather than waiting for the asynchronous detail fetch.
     */
    fun selectBrowseItem(row: Int, column: Int): MediaItem? {
        val state = _uiState.value
        val rows = state.visibleRows
        val item = rows.getOrNull(row)?.items?.getOrNull(column) ?: return null

        if (state.cursor.row != row || state.cursor.column != column) {
            _uiState.update { it.copy(cursor = BrowseCursor(row, column)) }
            refreshDetail()
        }
        return item
    }

    private fun refreshDetail() {
        // A direct browse move is an explicit decision to abandon any pending
        // automatic episode transition. Flow-driven remaps are filtered before
        // they call this method while the transition is pinned.
        if (_playback.value == null) playbackTransitionItemId = null
        val browseState = _uiState.value
        val item = browseState.highlighted
        val resumeProgress = browseState.highlightedProgress
            ?.takeIf { progress -> progress.mediaId.key == item?.id?.key }
        detailJob?.cancel()
        sourceJob?.cancel()
        resolveJob?.cancel()
        similarJob?.cancel()
        _sources.value = SourceState()

        if (item == null) {
            _detail.value = DetailState()
            return
        }

        // The summary is shown immediately and replaced when the full record
        // arrives, so moving along a shelf never shows an empty panel.
        _detail.value = DetailState(
            item = item,
            loading = true,
            resumeProgress = resumeProgress,
        )

        detailJob = viewModelScope.launchSafely(TAG) {
            val full = repository.details(item.id) ?: item
            val seasons = full.orderedSeasons
            val selectedSeasonData = seasons.firstOrNull { season ->
                season.number == resumeProgress?.seasonNumber && season.episodes.isNotEmpty()
            } ?: seasons.firstOrNull { !it.isSpecials && it.episodes.isNotEmpty() }
                ?: seasons.firstOrNull { it.episodes.isNotEmpty() }
                ?: seasons.firstOrNull { !it.isSpecials }
                ?: seasons.firstOrNull()
            val selectedSeason = selectedSeasonData?.number
                ?: resumeProgress?.seasonNumber?.takeIf { full.isSeries }
                ?: 1
            val selectedEpisode = if (selectedSeasonData != null) {
                resumeProgress
                    ?.takeIf { progress ->
                        progress.seasonNumber == selectedSeason &&
                            selectedSeasonData.episodes.any {
                                it.number == progress.episodeNumber
                            }
                    }
                    ?.episodeNumber
                    ?: selectedSeasonData.episodes.firstOrNull()?.number
                    ?: 1
            } else {
                // A Continue Watching record is intentionally partial. If its
                // detail request fails, its persisted pair is still a better and
                // safer target than inventing S01E01.
                resumeProgress
                    ?.takeIf { it.seasonNumber == selectedSeason && full.isSeries }
                    ?.episodeNumber
                    ?: 1
            }

            _detail.update {
                it.copy(
                    item = full,
                    loading = false,
                    season = selectedSeasonData,
                    selectedSeason = selectedSeason,
                    selectedEpisode = selectedEpisode,
                )
            }

            // Recommendations have no bearing on whether a title can play. A
            // slow /similar call used to hold source discovery behind TMDb's full
            // network timeout, making the panel say “Searching” despite addons
            // having already been configured and ready to answer.
            similarJob = viewModelScope.launchSafely(TAG) {
                val similar = repository.similar(full.id)
                if (_detail.value.item?.id == full.id) {
                    _detail.update { it.copy(similar = similar) }
                }
            }

            /*
             * Sources are fetched after a dwell, not on arrival.
             *
             * They belong on screen — being unable to see what is available until
             * after committing to play is what made the choice feel like a lottery
             * — but a search hits every configured addon and indexer and then the
             * debrid service. Doing that for every title the cursor passes over
             * would hammer all three for results discarded almost every time. A
             * short pause is the difference between browsing and asking.
             */
            delay(SOURCE_DWELL_MS)
            val current = _detail.value
            if (
                current.item?.id == full.id &&
                (!full.isSeries || (
                    current.selectedSeason == selectedSeason &&
                        current.selectedEpisode == selectedEpisode
                    ))
            ) {
                findSources(
                    seasonNumber = selectedSeason.takeIf { full.isSeries },
                    episodeNumber = selectedEpisode.takeIf { full.isSeries },
                )
            }
        }
    }

    fun selectSeason(number: Int) {
        if (_playback.value == null) playbackTransitionItemId = null
        val current = _detail.value
        val item = current.item ?: return
        if (!item.isSeries) return
        val season = item.orderedSeasons.firstOrNull { it.number == number } ?: return
        val resumeProgress = current.resumeProgress
        val selectedEpisode = when {
            current.selectedSeason == number &&
                season.episodes.any { it.number == current.selectedEpisode } ->
                current.selectedEpisode

            resumeProgress?.seasonNumber == number &&
                season.episodes.any { it.number == resumeProgress.episodeNumber } ->
                resumeProgress.episodeNumber ?: 1

            else -> season.episodes.firstOrNull()?.number ?: 1
        }
        if (
            current.selectedSeason == number &&
            current.selectedEpisode == selectedEpisode &&
            current.season == season
        ) {
            return
        }

        resolveJob?.cancel()
        _detail.update {
            it.copy(
                season = season,
                selectedSeason = number,
                selectedEpisode = selectedEpisode,
            )
        }
        findSources(number, selectedEpisode, SELECTION_SOURCE_DWELL_MS)
    }

    fun selectEpisode(number: Int) {
        if (_playback.value == null) playbackTransitionItemId = null
        val current = _detail.value
        val item = current.item ?: return
        if (!item.isSeries) return
        val season = current.season?.takeIf { it.number == current.selectedSeason } ?: return
        if (season.episodes.none { it.number == number } || current.selectedEpisode == number) return

        resolveJob?.cancel()
        _detail.update { it.copy(selectedEpisode = number) }
        findSources(current.selectedSeason, number, SELECTION_SOURCE_DWELL_MS)
    }

    fun stepSeason(delta: Int) {
        if (delta == 0) return
        val current = _detail.value
        val seasons = current.item
            ?.takeIf { it.isSeries }
            ?.orderedSeasons
            ?.filter { it.episodes.isNotEmpty() }
            .orEmpty()
        if (seasons.isEmpty()) return
        val index = seasons.indexOfFirst { it.number == current.selectedSeason }
            .takeIf { it >= 0 }
            ?: 0
        selectSeason(seasons[(index + delta).coerceIn(0, seasons.lastIndex)].number)
    }

    fun stepEpisode(delta: Int) {
        if (delta == 0) return
        val current = _detail.value
        val episodes = current.season?.episodes.orEmpty()
        if (episodes.isEmpty()) return
        val index = episodes.indexOfFirst { it.number == current.selectedEpisode }
            .takeIf { it >= 0 }
            ?: 0
        selectEpisode(episodes[(index + delta).coerceIn(0, episodes.lastIndex)].number)
    }

    /**
     * Finds sources for what is highlighted.
     *
     * Separate from the detail fetch and never automatic. A search hits every
     * configured indexer and then the debrid service; doing that for each title
     * the cursor passes over would hammer both and be discarded almost every
     * time.
     */
    fun findSources(
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        dwellMillis: Long = 0L,
    ) {
        val item = _detail.value.item ?: return
        val targetSeason = if (item.isSeries) {
            seasonNumber ?: _detail.value.selectedSeason
        } else {
            null
        }
        val targetEpisode = if (item.isSeries) {
            episodeNumber ?: _detail.value.selectedEpisode
        } else {
            null
        }
        sourceJob?.cancel()
        resolveJob?.cancel()
        _sources.value = SourceState(
            searching = true,
            seasonNumber = targetSeason,
            episodeNumber = targetEpisode,
        )

        sourceJob = viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                if (sourceTargetIsCurrent(item.id, targetSeason, targetEpisode)) {
                    _sources.value = SourceState(
                        seasonNumber = targetSeason,
                        episodeNumber = targetEpisode,
                        resolveError = error.message ?: "Search failed",
                    )
                }
            },
        ) {
            if (dwellMillis > 0L) delay(dwellMillis)
            val result = repository.sourcesFor(item, targetSeason, targetEpisode)
            if (sourceTargetIsCurrent(item.id, targetSeason, targetEpisode)) {
                _sources.update { it.copy(result = result, searching = false) }
            }
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
        val sourceState = _sources.value
        val targetSeason = if (item.isSeries) {
            seasonNumber ?: sourceState.seasonNumber ?: _detail.value.selectedSeason
        } else {
            null
        }
        val targetEpisode = if (item.isSeries) {
            episodeNumber
                ?: sourceState.takeIf { it.seasonNumber == targetSeason }?.episodeNumber
                ?: _detail.value.selectedEpisode
        } else {
            null
        }
        val explicitContinueResume = _detail.value.resumeProgress?.let { progress ->
            progress.mediaId.key == item.id.key &&
                progress.seasonNumber == targetSeason &&
                progress.episodeNumber == targetEpisode
        } == true
        _sources.update {
            it.copy(
                seasonNumber = targetSeason,
                episodeNumber = targetEpisode,
                chosen = source,
                resolving = true,
                resolveError = null,
            )
        }

        resolveJob?.cancel()
        resolveJob = viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                if (playbackTransitionItemId == item.id) playbackTransitionItemId = null
                _sources.update {
                    it.copy(resolving = false, resolveError = error.message ?: "Could not open")
                }
            },
        ) {
            val resolved = repository.resolve(source)
            if (
                _detail.value.item?.id != item.id ||
                _sources.value.chosen?.id != source.id
            ) {
                if (playbackTransitionItemId == item.id) playbackTransitionItemId = null
                return@launchSafely
            }
            when (resolved) {
                is ResolvedStream.Ready -> {
                    _sources.update { it.copy(resolving = false, downloadProgress = null) }
                    val resumeEnabled = explicitContinueResume ||
                        settingsRepository.media.first().resumeAutomatically
                    val resumeFrom = if (resumeEnabled) {
                        resumePoint(item.id, targetSeason, targetEpisode)
                    } else {
                        0L
                    }
                    // A new title gets its own write cadence and ended edge.
                    lastProgressWriteAt = 0L
                    playerWasEnded = false
                    _playback.value = Playback(
                        url = resolved.url,
                        item = item,
                        source = source,
                        seasonNumber = targetSeason,
                        episodeNumber = targetEpisode,
                        resumeFromMs = resumeFrom,
                        requestHeaders = resolved.requestHeaders,
                    )
                    playbackTransitionItemId = null
                    // Opened here rather than by the panel that draws it, so the
                    // stream starts the moment it is resolved and does not wait
                    // for a composition to notice.
                    player.open(resolved.url, resumeFrom, resolved.requestHeaders)
                }

                is ResolvedStream.Downloading -> {
                    playbackTransitionItemId = null
                    _sources.update {
                        it.copy(
                            resolving = false,
                            downloadProgress = resolved.progress,
                            // Not named: which service this is depends on a
                            // setting, and a message that says the wrong one
                            // sends the reader to the wrong website.
                            resolveError = "Your debrid service is still fetching this " +
                                "source. Pick a cached one, or try again shortly.",
                        )
                    }
                }

                is ResolvedStream.Failed -> {
                    playbackTransitionItemId = null
                    _sources.update {
                        it.copy(resolving = false, resolveError = resolved.reason)
                    }
                }
            }
        }
    }

    /** Plays the best source without showing the list, when settings allow it. */
    fun playBest(seasonNumber: Int? = null, episodeNumber: Int? = null) {
        val item = _detail.value.item ?: return
        val targetSeason = if (item.isSeries) {
            seasonNumber ?: _detail.value.selectedSeason
        } else {
            null
        }
        val targetEpisode = if (item.isSeries) {
            episodeNumber ?: _detail.value.selectedEpisode
        } else {
            null
        }
        sourceJob?.cancel()
        resolveJob?.cancel()
        _sources.value = SourceState(
            searching = true,
            seasonNumber = targetSeason,
            episodeNumber = targetEpisode,
        )

        sourceJob = viewModelScope.launchSafely(
            tag = TAG,
            onError = { error ->
                if (playbackTransitionItemId == item.id) playbackTransitionItemId = null
                if (sourceTargetIsCurrent(item.id, targetSeason, targetEpisode)) {
                    _sources.value = SourceState(
                        seasonNumber = targetSeason,
                        episodeNumber = targetEpisode,
                        resolveError = error.message ?: "Search failed",
                    )
                }
            },
        ) {
            val result = repository.sourcesFor(item, targetSeason, targetEpisode)
            if (!sourceTargetIsCurrent(item.id, targetSeason, targetEpisode)) {
                if (playbackTransitionItemId == item.id) playbackTransitionItemId = null
                return@launchSafely
            }
            _sources.update { it.copy(result = result, searching = false) }

            val media = settingsRepository.media.first()
            val best = (result as? SourceResult.Found)
                ?.let { SourceRanking.best(it.all, media) }

            if (best != null && media.autoSelectSource) {
                play(best, targetSeason, targetEpisode)
            } else {
                playbackTransitionItemId = null
            }
        }
    }

    private fun sourceTargetIsCurrent(
        itemId: MediaId,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): Boolean {
        val detail = _detail.value
        val source = _sources.value
        if (detail.item?.id != itemId) return false
        if (source.seasonNumber != seasonNumber || source.episodeNumber != episodeNumber) return false
        return detail.item?.isSeries != true || (
            detail.selectedSeason == seasonNumber && detail.selectedEpisode == episodeNumber
            )
    }

    fun stopPlayback(refreshBrowseDetail: Boolean = true) {
        resolveJob?.cancel()
        _sources.update { it.copy(chosen = null, resolving = false) }
        val status = playerStatus.value
        if (_playback.value != null && status.durationMs > 0L) {
            onProgress(status.positionMs, status.durationMs, force = true)
        }
        _playback.value = null
        player.close()
        if (refreshBrowseDetail) {
            playbackTransitionItemId = null
            refreshDetail()
        }
    }

    /** Stops the current episode and resolves the next one without losing the series. */
    fun playNextEpisode() {
        val playing = _playback.value ?: return
        val seasonNumber = playing.seasonNumber ?: return
        val episodeNumber = playing.episodeNumber ?: return
        val season = playing.item.orderedSeasons.firstOrNull { it.number == seasonNumber } ?: return
        val currentIndex = season.episodes.indexOfFirst { it.number == episodeNumber }
        val next = season.episodes.getOrNull(currentIndex + 1) ?: return

        playbackTransitionItemId = playing.item.id
        stopPlayback(refreshBrowseDetail = false)
        _detail.update {
            it.copy(
                item = playing.item,
                loading = false,
                season = season,
                selectedSeason = seasonNumber,
                selectedEpisode = next.number,
                resumeProgress = null,
            )
        }
        playBest(seasonNumber, next.number)
    }

    /**
     * Releases the player.
     *
     * The one moment at which nothing can want it again: both panels are gone
     * with the activity that held them, and a released `ExoPlayer` is the only
     * kind that has given its codecs back.
     */
    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    fun clearSourceError() {
        _sources.update { it.copy(resolveError = null) }
    }

    /**
     * Records where the viewer got to, so the title can be resumed.
     *
     * Written to the database rather than to a map, and throttled: the player
     * samples four times a second and a write per sample is thousands of them
     * across a film, on a handheld, for a value that only has to be roughly
     * right. Every few seconds is indistinguishable to the viewer and is what
     * the last write before a crash costs.
     */
    fun onProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val playing = _playback.value ?: return
        if (durationMs <= 0L) return

        val now = System.currentTimeMillis()
        if (!force && now - lastProgressWriteAt < PROGRESS_WRITE_INTERVAL_MS) return
        lastProgressWriteAt = now

        viewModelScope.launchSafely(TAG) {
            watchProgress.record(
                item = playing.item,
                season = playing.seasonNumber,
                episode = playing.episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                nowEpochMs = now,
            )
        }
    }

    /** Forgets a resume point — "remove from continue watching". */
    fun forgetProgress(id: MediaId, season: Int? = null, episode: Int? = null) {
        viewModelScope.launchSafely(TAG) { watchProgress.forget(id, season, episode) }
    }

    private suspend fun resumePoint(id: MediaId, season: Int?, episode: Int?): Long =
        watchProgress.progressFor(id, season, episode)
            ?.takeIf { it.isResumable }
            ?.positionMs
            ?: 0L

    /** The episode after the one playing, or null at the end of a season. */
    fun nextEpisode(): Pair<Int, Int>? {
        val playing = _playback.value ?: return null
        val season = playing.seasonNumber ?: return null
        val episode = playing.episodeNumber ?: return null
        val episodes = playing.item.orderedSeasons
            .firstOrNull { it.number == season }
            ?.episodes
            ?: return null

        val index = episodes.indexOfFirst { it.number == episode }
        val next = episodes.getOrNull(index + 1) ?: return null
        return season to next.number
    }

    private companion object {
        const val TAG = "Movies"

        /** How long the cursor rests on a title before its sources are fetched. */
        const val SOURCE_DWELL_MS = 700L

        /** Debounces held d-pad movement across a show's episode selector. */
        const val SELECTION_SOURCE_DWELL_MS = 250L

        /** How long the typed query rests before the catalogue is asked about it. */
        const val SEARCH_DEBOUNCE_MS = 400L

        /**
         * How often a resume point is written.
         *
         * The player samples four times a second; writing each one is thousands
         * of database writes per film for a value that only has to be roughly
         * right, and the cost of the interval is losing at most this much
         * position to a crash.
         */
        const val PROGRESS_WRITE_INTERVAL_MS = 5_000L
    }
}
