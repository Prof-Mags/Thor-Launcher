package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.MediaType
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorInputField
import com.thor.feature.movies.panel.MediaDetailPanel
import com.thor.feature.movies.panel.MoviesBrowseScreen
import com.thor.feature.movies.player.PlayerAction
import com.thor.feature.movies.player.PlayerControls
import com.thor.feature.movies.player.PlayerStatus
import com.thor.feature.movies.player.PlayerSurface
import com.thor.feature.movies.player.ThorPlayer

/** Which of the section's three states the paired panels are showing. */
enum class MoviesMode { BROWSE, EPISODES, SOURCES, PLAYING }

/** Catalogue or video on the information display. */
@Composable
fun MoviesTopPanel(
    mode: MoviesMode,
    state: MoviesUiState,
    playback: Playback?,
    player: ThorPlayer,
    status: PlayerStatus,
    onTypeSelected: (MediaType) -> Unit = {},
    onItemSelected: (row: Int, column: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (mode == MoviesMode.PLAYING && playback != null) {
            PlayerSurface(
                player = player,
                playback = playback,
                status = status,
            )
        } else {
            MoviesBrowseScreen(
                state = state,
                onTypeSelected = onTypeSelected,
                onItemSelected = onItemSelected,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Search/details/sources, or transport controls, on the companion display. */
@Composable
fun MoviesBottomPanel(
    mode: MoviesMode,
    detail: DetailState,
    sources: SourceState,
    playback: Playback?,
    status: PlayerStatus,
    focusedSource: Int,
    focusedAction: PlayerAction,
    hasNextEpisode: Boolean,
    skipSeconds: Int = 15,
    onPlayerAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    onSourcePicked: (Int) -> Unit,
    onSeasonSelected: (Int) -> Unit = {},
    onEpisodeSelected: (Int) -> Unit = {},
    query: String,
    onQueryChanged: (String) -> Unit,
    searchRequested: Boolean = false,
    onSearchFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (mode == MoviesMode.PLAYING && playback != null) {
        PlayerControls(
            playback = playback,
            status = status,
            focusedAction = focusedAction,
            hasNextEpisode = hasNextEpisode,
            skipSeconds = skipSeconds,
            onAction = onPlayerAction,
            onSeek = onSeek,
            modifier = modifier,
        )
        return
    }

    // Search stays on this display because THOR's own keyboard is composed here;
    // the results can update independently on the other screen.
    Column(modifier = modifier.fillMaxSize()) {
        MediaSearchField(
            query = query,
            onQueryChanged = onQueryChanged,
            requestFocus = searchRequested,
            onFocused = onSearchFocused,
            modifier = Modifier.fillMaxWidth(),
        )
        MediaDetailPanel(
            detail = detail,
            sources = sources,
            focusedSource = focusedSource.takeIf { mode == MoviesMode.SOURCES },
            onSourcePicked = onSourcePicked,
            selectorFocused = mode == MoviesMode.EPISODES,
            showSeriesSelector = detail.item?.isSeries == true && mode != MoviesMode.SOURCES,
            onSeasonSelected = onSeasonSelected,
            onEpisodeSelected = onEpisodeSelected,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/** A compact media toolbar backed by the launcher's custom text input. */
@Composable
internal fun MediaSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = ThorTheme.dimens
    val colors = ThorTheme.colors
    val textInput = LocalThorTextInput.current
    val currentOnQueryChanged by rememberUpdatedState(onQueryChanged)

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            textInput.focus(
                id = SEARCH_FIELD_ID,
                label = "Search",
                initial = query,
            ) { edited -> currentOnQueryChanged(edited) }
            onFocused()
        }
    }

    GlassSurface(
        modifier = modifier.padding(top = dimens.spacingSmall),
        shape = ThorTheme.shapes.panel,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Box(
                modifier = Modifier
                    .clip(ThorTheme.shapes.small)
                    .background(colors.cursor)
                    .padding(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = contrastingContentColor(colors.cursor),
                )
            }
            ThorInputField(
                id = SEARCH_FIELD_ID,
                label = "Search",
                value = query,
                onValueChange = onQueryChanged,
                placeholder = "Title, actor, or series",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private const val SEARCH_FIELD_ID = "movies-search"
