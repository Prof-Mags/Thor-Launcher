package com.thor.feature.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.MediaType
import com.thor.core.ui.component.ArtworkImage

/**
 * Movies as a complete one-screen couch experience.
 *
 * Browsing keeps the catalogue and the selected title visible together. During
 * playback the video and its full transport console share the television, so no
 * command or source information is stranded on the darkened handheld panel.
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
    onItemSelected: (row: Int, column: Int) -> Unit,
    onPlayerAction: (PlayerAction) -> Unit,
    onSeek: (Long) -> Unit,
    onSourcePicked: (Int) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    query: String,
    onQueryChanged: (String) -> Unit,
    searchRequested: Boolean,
    onSearchFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(colors.background, colors.surfaceElevated.copy(alpha = 0.72f)),
                ),
            ),
    ) {
        if (mode == MoviesMode.PLAYING && playback != null) {
            PlayerSurface(
                player = player,
                playback = playback,
                status = status,
                showStateOverlay = false,
                modifier = Modifier.fillMaxSize(),
            )
            CouchPlayerControlsOverlay(
                playback = playback,
                status = status,
                focusedAction = focusedAction,
                hasNextEpisode = hasNextEpisode,
                skipSeconds = skipSeconds,
                onAction = onPlayerAction,
                onSeek = onSeek,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            return@Box
        }

        if (mode == MoviesMode.BROWSE) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(CATALOGUE_WEIGHT)
                        .fillMaxHeight()
                        .border(1.dp, colors.outline.copy(alpha = 0.22f), ThorTheme.shapes.panel),
                ) {
                    MoviesBrowseScreen(
                        state = state,
                        onTypeSelected = onTypeSelected,
                        onItemSelected = onItemSelected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f - CATALOGUE_WEIGHT).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MediaSearchField(
                        query = query,
                        onQueryChanged = onQueryChanged,
                        requestFocus = searchRequested,
                        onFocused = onSearchFocused,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                colors.outline.copy(alpha = 0.24f),
                                ThorTheme.shapes.panel,
                            ),
                    ) {
                        detail.item?.backdropUrl?.let { backdrop ->
                            ArtworkImage(
                                model = backdrop,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(
                                        colors.background.copy(alpha = 0.70f),
                                        colors.background.copy(alpha = 0.96f),
                                    ),
                                ),
                            ),
                        )
                        if (detail.item == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.material3.Text(
                                    text = "Choose something to watch",
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        } else {
                            GlassSurface(
                                modifier = Modifier.fillMaxSize(),
                                shape = ThorTheme.shapes.panel,
                                color = Color.Transparent,
                                alphaOverride = 1f,
                            ) {
                                InformationPanel(
                                    detail = detail,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            MediaDetailPanel(
                detail = detail,
                sources = sources,
                focusedSource = focusedSource.takeIf { mode == MoviesMode.SOURCES },
                onSourcePicked = onSourcePicked,
                selectorFocused = mode == MoviesMode.EPISODES,
                showSeriesSelector = detail.item?.isSeries == true && mode != MoviesMode.SOURCES,
                onSeasonSelected = onSeasonSelected,
                onEpisodeSelected = onEpisodeSelected,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
    }
}

private const val CATALOGUE_WEIGHT = 0.68f
