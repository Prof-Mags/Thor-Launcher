package com.thor.feature.movies

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.thor.core.model.ControllerCommand
import com.thor.core.model.MediaType
import com.thor.data.media.SourceResult
import com.thor.feature.movies.player.PlayerAction
import com.thor.feature.movies.player.ThorPlayer

/**
 * Everything the Movies section needs, in one object the shell can hold.
 *
 * The section spans two windows, and neither of them can own its state: the
 * bottom panel is the one with the controls but the top panel is the one with
 * the player, and on this device either can be the composition that is still
 * running. So the state lives here, is remembered once by the shell, and both
 * panels read it.
 */
class MoviesSectionState internal constructor(
    internal val viewModel: MoviesViewModel,
) {
    var mode by mutableStateOf(MoviesMode.BROWSE)
        internal set

    /** Cursor within the source list, and within the transport buttons. */
    var focusedSource by mutableIntStateOf(0)
        internal set

    var focusedAction by mutableStateOf(PlayerAction.PLAY_PAUSE)
        internal set

    /** Where the controls should land when playback is deliberately closed. */
    internal var playbackExitMode = MoviesMode.BROWSE

    /**
     * Set when the controller has asked for the search box.
     *
     * A request rather than a call, because claiming THOR's text focus needs the
     * composition local the field is drawn under — and this object is deliberately
     * outside any composition so both panels can hold it. The panel that draws the
     * box acts on this and clears it.
     */
    var searchRequested by mutableStateOf(false)
        internal set

    fun requestSearch() {
        searchRequested = true
    }

    fun onSearchFocused() {
        searchRequested = false
    }

    /**
     * The player, reached through the view model that owns it.
     *
     * There used to be a relay here: the panel drawing the video sampled the
     * player into this object and handed up a `PlayerCommands` for the other
     * panel to drive it through. Both halves of that were a value produced by
     * one window's composition and consumed by the other's, which is precisely
     * the arrangement that freezes when the producing window is covered — the
     * controls would go on showing the last sample taken before an app opened on
     * the other screen, and the buttons would drive a player through an object
     * captured when the panel was last composed.
     *
     * Now there is no relay. One object owns the player, both panels read the
     * same flow, and neither can be stale.
     */
    internal val player: ThorPlayer get() = viewModel.player

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }
}

/**
 * Builds the section's state and keeps it in step with the view model.
 *
 * Held by the shell rather than by either panel, and remembered against the view
 * model so it survives the panels being recomposed, moved between windows, or
 * torn down and rebuilt when the display configuration changes.
 */
@Composable
fun rememberMoviesSection(viewModel: MoviesViewModel): MoviesSectionState {
    val section = remember(viewModel) { MoviesSectionState(viewModel) }
    val playback by viewModel.playback.collectAsState()
    val browseState by viewModel.uiState.collectAsState()

    // Playback starting or stopping is what moves the section in and out of its
    // player state; nothing else may set it, so the two cannot disagree.
    LaunchedEffect(playback) {
        if (playback != null) {
            section.mode = MoviesMode.PLAYING
            section.playbackExitMode = MoviesMode.BROWSE
            section.focusedAction = PlayerAction.PLAY_PAUSE
        } else {
            section.mode = section.playbackExitMode
            section.playbackExitMode = MoviesMode.BROWSE
        }
    }

    // The top-panel tabs are touchable even while the controller is in the
    // episode selector. Switching to films must not leave that invisible mode
    // consuming directions against a screen with no selector.
    LaunchedEffect(browseState.type) {
        if (playback == null) {
            section.mode = MoviesMode.BROWSE
            section.focusedSource = 0
        }
    }

    return section
}

/**
 * Routes one controller command into the section.
 *
 * @return true when the section consumed it. The shell keeps whatever it does
 *   not — Home, the nav bar and the overlays still work while Movies is open,
 *   because a section that swallowed everything would be a trap.
 */
fun MoviesSectionState.handleCommand(command: ControllerCommand): Boolean {
    // Bumpers always mean Films/Shows while browsing this section. Keeping this
    // above the individual modes prevents the same buttons changing seasons in
    // one panel and doing nothing in another.
    if (mode != MoviesMode.PLAYING) {
        when (command) {
            ControllerCommand.PAGE_PREVIOUS -> return switchMediaType(MediaType.MOVIE)
            ControllerCommand.PAGE_NEXT -> return switchMediaType(MediaType.SERIES)
            else -> Unit
        }
    }

    return when (mode) {
        MoviesMode.BROWSE -> handleBrowse(command)
        MoviesMode.EPISODES -> handleEpisodes(command)
        MoviesMode.SOURCES -> handleSources(command)
        MoviesMode.PLAYING -> handlePlaying(command)
    }
}

private fun MoviesSectionState.switchMediaType(type: MediaType): Boolean {
    focusedSource = 0
    mode = MoviesMode.BROWSE
    viewModel.switchType(type)
    return true
}

private fun MoviesSectionState.handleBrowse(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_LEFT -> { viewModel.move(0, -1); true }
    ControllerCommand.NAVIGATE_RIGHT -> { viewModel.move(0, 1); true }
    ControllerCommand.NAVIGATE_UP -> { viewModel.move(-1, 0); true }
    ControllerCommand.NAVIGATE_DOWN -> { viewModel.move(1, 0); true }

    // Shows stop at their season/episode pair before entering the source list.
    // Films have no such intermediate choice and retain the old direct route.
    ControllerCommand.CONFIRM -> {
        val selected = viewModel.detail.value.item
        mode = if (
            selected?.isSeries == true &&
            selected.orderedSeasons.any { it.episodes.isNotEmpty() }
        ) {
            MoviesMode.EPISODES
        } else {
            MoviesMode.SOURCES
        }
        focusedSource = 0
        true
    }

    // The long press keeps the one-press route for anyone who wants it: take the
    // best-ranked source and go.
    ControllerCommand.PICK_UP -> {
        viewModel.playBest()
        true
    }

    /*
     * Y opens the search box, the way it opens a context menu on the grid.
     *
     * A search box the controller cannot reach would be a touch-only feature on
     * a device built to be driven by a pad — and the on-screen keyboard it
     * raises is itself controller-driven, so the whole path works from the
     * stick. B leaves it again, which `handleBrowse` already treats as back.
     */
    ControllerCommand.CONTEXT_MENU -> {
        requestSearch()
        true
    }

    // Back leaves a search before it leaves the section.
    ControllerCommand.BACK -> {
        if (viewModel.uiState.value.isSearching) {
            viewModel.clearSearch()
            true
        } else {
            false
        }
    }

    else -> false
}

/**
 * A show's pair selector: horizontal movement changes season, vertical movement
 * changes episode, and confirm hands the chosen pair to the source list.
 */
private fun MoviesSectionState.handleEpisodes(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_LEFT -> {
        focusedSource = 0
        viewModel.stepSeason(-1)
        true
    }

    ControllerCommand.NAVIGATE_RIGHT -> {
        focusedSource = 0
        viewModel.stepSeason(1)
        true
    }

    ControllerCommand.NAVIGATE_UP -> {
        focusedSource = 0
        viewModel.stepEpisode(-1)
        true
    }

    ControllerCommand.NAVIGATE_DOWN -> {
        focusedSource = 0
        viewModel.stepEpisode(1)
        true
    }

    ControllerCommand.CONFIRM -> {
        mode = MoviesMode.SOURCES
        focusedSource = 0
        true
    }

    ControllerCommand.PICK_UP -> {
        viewModel.playBest()
        true
    }

    ControllerCommand.BACK -> {
        mode = MoviesMode.BROWSE
        true
    }

    else -> false
}

private fun MoviesSectionState.handleSources(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_UP -> { focusedSource = (focusedSource - 1).coerceAtLeast(0); true }
    ControllerCommand.NAVIGATE_DOWN -> {
        val count = rankedCount()
        focusedSource = (focusedSource + 1).coerceAtMost((count - 1).coerceAtLeast(0))
        true
    }

    // A show returns to its pair selector; a film returns straight to browsing.
    ControllerCommand.NAVIGATE_LEFT, ControllerCommand.BACK -> {
        val selected = viewModel.detail.value.item
        mode = if (
            selected?.isSeries == true &&
            selected.orderedSeasons.any { it.episodes.isNotEmpty() }
        ) {
            MoviesMode.EPISODES
        } else {
            MoviesMode.BROWSE
        }
        true
    }

    ControllerCommand.CONFIRM -> {
        chosenSource()?.let(viewModel::play)
        true
    }

    else -> false
}

/** Plays the source at [index] in the ranked list, from a touch or the pointer. */
fun MoviesSectionState.pickSource(index: Int) {
    focusedSource = index
    mode = MoviesMode.SOURCES
    sourceAt(index)?.let(viewModel::play)
}

/** Touch/pointer selection also hands subsequent controller input to the selector. */
fun MoviesSectionState.pickSeason(number: Int) {
    focusedSource = 0
    mode = MoviesMode.EPISODES
    viewModel.selectSeason(number)
}

fun MoviesSectionState.pickEpisode(number: Int) {
    focusedSource = 0
    viewModel.selectEpisode(number)
    mode = MoviesMode.SOURCES
}

/**
 * Opens a catalogue title selected by touch. The view model remains the single
 * owner of the browse cursor, so the controller resumes from the tapped card.
 */
fun MoviesSectionState.pickTitle(row: Int, column: Int) {
    val item = viewModel.selectBrowseItem(row, column) ?: return
    focusedSource = 0
    mode = if (item.isSeries) MoviesMode.EPISODES else MoviesMode.SOURCES
}

private fun MoviesSectionState.handlePlaying(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_LEFT -> { stepAction(-1); true }
    ControllerCommand.NAVIGATE_RIGHT -> { stepAction(1); true }
    ControllerCommand.NAVIGATE_UP -> { stepActionRow(-1); true }
    ControllerCommand.NAVIGATE_DOWN -> { stepActionRow(1); true }
    ControllerCommand.CONFIRM -> { perform(focusedAction); true }

    // Back stops rather than stepping the cursor: leaving a film is the one
    // thing that must work from any state of the controls.
    ControllerCommand.BACK -> { perform(PlayerAction.STOP); true }

    else -> false
}

/** Carries out a transport action, wherever it came from. */
fun MoviesSectionState.perform(action: PlayerAction) {
    val skipMs = viewModel.settings.value.skipSeconds.coerceIn(1, 120) * 1_000L
    when (action) {
        PlayerAction.PLAY_PAUSE -> player.playPause()
        PlayerAction.REWIND -> player.seekBy(-skipMs)
        PlayerAction.FORWARD -> player.seekBy(skipMs)
        PlayerAction.RESTART -> player.seekTo(0L)
        PlayerAction.SPEED -> player.cycleSpeed()
        PlayerAction.AUDIO -> player.cycleAudioTrack()
        PlayerAction.STOP -> {
            // stopPlayback takes the final player snapshot before closing it,
            // bypassing the periodic-write throttle so resume cannot lag behind.
            playbackExitMode = MoviesMode.BROWSE
            viewModel.stopPlayback()
            mode = MoviesMode.BROWSE
        }

        PlayerAction.NEXT_EPISODE -> viewModel.playNextEpisode()
        PlayerAction.CHANGE_SOURCE -> {
            // Keep the selected title and its ranked results intact. The source
            // list can therefore be used immediately without another search.
            playbackExitMode = MoviesMode.SOURCES
            mode = MoviesMode.SOURCES
            viewModel.stopPlayback(refreshBrowseDetail = false)
        }
    }
}

private fun MoviesSectionState.stepAction(delta: Int) {
    val actions = playerActionRows().firstOrNull { focusedAction in it }
        ?: playerActionRows().first()
    val index = actions.indexOf(focusedAction).coerceAtLeast(0)
    focusedAction = actions[(index + delta).coerceIn(0, actions.lastIndex)]
}

private fun MoviesSectionState.stepActionRow(delta: Int) {
    val rows = playerActionRows()
    val currentRow = rows.indexOfFirst { focusedAction in it }.coerceAtLeast(0)
    val targetRow = (currentRow + delta).coerceIn(0, rows.lastIndex)
    if (targetRow == currentRow) return

    val currentColumn = rows[currentRow].indexOf(focusedAction).coerceAtLeast(0)
    focusedAction = rows[targetRow][currentColumn.coerceAtMost(rows[targetRow].lastIndex)]
}

private fun MoviesSectionState.playerActionRows(): List<List<PlayerAction>> {
    val transport = buildList {
        add(PlayerAction.REWIND)
        add(PlayerAction.PLAY_PAUSE)
        add(PlayerAction.FORWARD)
        if (viewModel.nextEpisode() != null) add(PlayerAction.NEXT_EPISODE)
        add(PlayerAction.STOP)
    }
    val tools = buildList {
        add(PlayerAction.RESTART)
        add(PlayerAction.SPEED)
        if (player.status.value.audioTracks.size > 1) add(PlayerAction.AUDIO)
        add(PlayerAction.CHANGE_SOURCE)
    }
    return listOf(transport, tools)
}

private fun MoviesSectionState.rankedCount(): Int =
    (viewModel.sources.value.result as? SourceResult.Found)?.ranked?.size ?: 0

private fun MoviesSectionState.chosenSource() = sourceAt(focusedSource)

private fun MoviesSectionState.sourceAt(index: Int) =
    (viewModel.sources.value.result as? SourceResult.Found)?.ranked?.getOrNull(index)
