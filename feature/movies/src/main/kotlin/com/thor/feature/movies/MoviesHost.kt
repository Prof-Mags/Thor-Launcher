package com.thor.feature.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.thor.core.model.ControllerCommand
import com.thor.core.model.MediaType
import com.thor.data.media.SourceResult

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

    var status by mutableStateOf(PlayerStatus())
        private set

    internal var commands: PlayerCommands? = null
        private set

    /**
     * Takes a status sample from the player.
     *
     * A method rather than a settable field because the player lives in the
     * *other* window: the shell is relaying across a boundary, and a public
     * setter invites anything to invent a state the player is not actually in.
     */
    fun onStatus(status: PlayerStatus) {
        this.status = status

        // Recorded continuously rather than only on stop, so a film abandoned by
        // pulling the battery is still resumable.
        if (status.durationMs > 0L) {
            viewModel.onProgress(status.positionMs, status.durationMs)
        }
    }

    /** Receives the player's control surface when the top panel builds one. */
    fun onCommands(commands: PlayerCommands?) {
        this.commands = commands
    }

    fun seekTo(positionMs: Long) {
        commands?.seekTo(positionMs)
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

    // Playback starting or stopping is what moves the section in and out of its
    // player state; nothing else may set it, so the two cannot disagree.
    LaunchedEffect(playback) {
        section.mode = if (playback != null) MoviesMode.PLAYING else MoviesMode.BROWSE
        if (playback != null) section.focusedAction = PlayerAction.PLAY_PAUSE
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
fun MoviesSectionState.handleCommand(command: ControllerCommand): Boolean = when (mode) {
    MoviesMode.BROWSE -> handleBrowse(command)
    MoviesMode.SOURCES -> handleSources(command)
    MoviesMode.PLAYING -> handlePlaying(command)
}

private fun MoviesSectionState.handleBrowse(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_LEFT -> { viewModel.move(0, -1); true }
    ControllerCommand.NAVIGATE_RIGHT -> { viewModel.move(0, 1); true }
    ControllerCommand.NAVIGATE_UP -> { viewModel.move(-1, 0); true }
    ControllerCommand.NAVIGATE_DOWN -> { viewModel.move(1, 0); true }

    /*
     * Confirm moves into the source list rather than playing something.
     *
     * The list is already on screen beside the description, populated after a
     * short dwell, so this is a move rather than a fetch. Playing the top-ranked
     * source outright was the old behaviour and it read as the launcher picking
     * at random: the ranking is an opinion, and the viewer could neither see it
     * nor overrule it before it acted.
     */
    ControllerCommand.CONFIRM -> {
        mode = MoviesMode.SOURCES
        focusedSource = 0
        true
    }

    // The long press keeps the one-press route for anyone who wants it: take the
    // best-ranked source and go.
    ControllerCommand.PICK_UP -> {
        viewModel.playBest()
        true
    }

    // Films and shows, on the bumpers, because they are the section's two halves
    // rather than a setting.
    ControllerCommand.PAGE_PREVIOUS -> { viewModel.switchType(MediaType.MOVIE); true }
    ControllerCommand.PAGE_NEXT -> { viewModel.switchType(MediaType.SERIES); true }

    else -> false
}

private fun MoviesSectionState.handleSources(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_UP -> { focusedSource = (focusedSource - 1).coerceAtLeast(0); true }
    ControllerCommand.NAVIGATE_DOWN -> {
        val count = rankedCount()
        focusedSource = (focusedSource + 1).coerceAtMost((count - 1).coerceAtLeast(0))
        true
    }

    // Left returns to the shelves, matching where the list sits on screen.
    ControllerCommand.NAVIGATE_LEFT, ControllerCommand.BACK -> {
        mode = MoviesMode.BROWSE
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

private fun MoviesSectionState.handlePlaying(command: ControllerCommand): Boolean = when (command) {
    ControllerCommand.NAVIGATE_LEFT -> { stepAction(-1); true }
    ControllerCommand.NAVIGATE_RIGHT -> { stepAction(1); true }
    ControllerCommand.CONFIRM -> { perform(focusedAction); true }

    // Back stops rather than stepping the cursor: leaving a film is the one
    // thing that must work from any state of the controls.
    ControllerCommand.BACK -> { perform(PlayerAction.STOP); true }

    else -> false
}

/** Carries out a transport action, wherever it came from. */
fun MoviesSectionState.perform(action: PlayerAction) {
    when (action) {
        PlayerAction.PLAY_PAUSE -> commands?.playPause()
        PlayerAction.REWIND -> commands?.seekBy(-SKIP_BACK_MS)
        PlayerAction.FORWARD -> commands?.seekBy(SKIP_FORWARD_MS)
        PlayerAction.STOP -> {
            // Recorded before the player goes away, or the position is lost and
            // the title cannot be resumed.
            viewModel.onProgress(status.positionMs, status.durationMs)
            viewModel.stopPlayback()
            mode = MoviesMode.BROWSE
        }

        PlayerAction.NEXT_EPISODE -> viewModel.nextEpisode()?.let { (season, episode) ->
            viewModel.stopPlayback()
            viewModel.playBest(season, episode)
        }
    }
}

private fun MoviesSectionState.stepAction(delta: Int) {
    val actions = PlayerAction.entries
    val index = actions.indexOf(focusedAction)
    focusedAction = actions[(index + delta).coerceIn(0, actions.lastIndex)]
}

private fun MoviesSectionState.rankedCount(): Int =
    (viewModel.sources.value.result as? SourceResult.Found)?.ranked?.size ?: 0

private fun MoviesSectionState.chosenSource() = sourceAt(focusedSource)

private fun MoviesSectionState.sourceAt(index: Int) =
    (viewModel.sources.value.result as? SourceResult.Found)?.ranked?.getOrNull(index)

private const val SKIP_BACK_MS = 10_000L
private const val SKIP_FORWARD_MS = 30_000L
