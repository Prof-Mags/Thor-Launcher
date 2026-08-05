package com.thor.feature.movies.couch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.MediaType
import com.thor.feature.movies.DetailState
import com.thor.feature.movies.MoviesMode
import com.thor.feature.movies.MoviesUiState
import com.thor.feature.movies.Playback
import com.thor.feature.movies.player.PlayerAction
import com.thor.feature.movies.player.PlayerStatus
import com.thor.feature.movies.player.ThorPlayer
import com.thor.feature.movies.SourceState

/**
 * Movies as a complete one-screen couch experience.
 *
 * Three screens, not three panels. Browsing is a billboard over a field of
 * shelves, a chosen title is its own page, and a playing film is the film. The
 * arrangement this replaced put the catalogue and the detail panel side by side
 * — the handheld's two displays folded onto one — which on a television left
 * every picture thumbnail-sized and a third of the screen given to prose.
 */
@Composable
fun MoviesCouchScreen(
    mode: MoviesMode,
    state: MoviesUiState,
    detail: DetailState,
    sources: SourceState,
    playback: Playback?,
    player: ThorPlayer,
    status: PlayerStatus,
    focusedSource: Int,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    skipSeconds: Int,
    onTypeSelected: (MediaType) -> Unit,
    onItemFocused: (row: Int, column: Int) -> Unit,
    onItemSelected: (row: Int, column: Int) -> Unit,
    onPlayerAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    onSourcePicked: (Int) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onPlayBest: () -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    searchRequested: Boolean,
    onSearchFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(ThorTheme.colors.background)) {
        if (mode == MoviesMode.PLAYING && playback != null) {
            // The film alone, with the stock controls on tap. The bespoke
            // console belongs to the handheld, where the picture and the
            // transport are on different screens.
            CouchMoviePlayer(player = player, modifier = Modifier.fillMaxSize())
            return@Box
        }

        if (mode == MoviesMode.BROWSE) {
            MoviesCouchBrowse(
                state = state,
                detail = detail,
                query = query,
                onQueryChanged = onQueryChanged,
                searchRequested = searchRequested,
                onSearchFocused = onSearchFocused,
                onTypeSelected = onTypeSelected,
                onItemFocused = onItemFocused,
                onItemSelected = onItemSelected,
                onPlay = onPlayBest,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MoviesCouchTitlePage(
                detail = detail,
                sources = sources,
                focusedSource = focusedSource.takeIf { mode == MoviesMode.SOURCES },
                selectorFocused = mode == MoviesMode.EPISODES,
                showSeriesSelector = detail.item?.isSeries == true && mode != MoviesMode.SOURCES,
                onSourcePicked = onSourcePicked,
                onSeasonSelected = onSeasonSelected,
                onEpisodeSelected = onEpisodeSelected,
                onPlay = onPlayBest,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
