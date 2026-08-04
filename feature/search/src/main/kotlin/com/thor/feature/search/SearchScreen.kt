package com.thor.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.input.LocalThorTextInput
import com.thor.core.ui.input.ThorInputField
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * The global search overlay.
 *
 * The field is a [ThorInputField], not a platform one. Tapping it claims the
 * launcher's own text focus, and THOR's keyboard — raised on whichever panel the user
 * is holding — types into it from there. A platform field would ask for platform
 * focus and summon an IME that never renders on that panel.
 */
@Composable
fun SearchScreen(
    onEntrySelected: (GridEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.focused.collectAsStateWithLifecycle()

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    /*
     * Claims the launcher's text focus as it opens, which is what raises the
     * keyboard. Search is the one screen that exists to be typed into, so waiting for
     * the user to tap its field would be a step for nothing.
     */
    val textInput = LocalThorTextInput.current
    val currentQuery by rememberUpdatedState(state.query)
    LaunchedEffect(Unit) {
        textInput.focus(
            id = FIELD_ID,
            label = "Search your library",
            initial = currentQuery,
        ) { edited -> viewModel.onQueryChanged(edited) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimens.spacingLarge),
        verticalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
            ThorInputField(
                id = FIELD_ID,
                label = "Search your library",
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = "Search games, apps, folders and settings",
            )
        }

        when {
            state.isSearching -> Box(
                modifier = Modifier.fillMaxWidth().padding(dimens.spacingLarge),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.cursor)
            }

            state.isEmpty -> Text(
                text = "No results for \"${state.query}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            /*
             * Nothing typed, nothing listed.
             *
             * An empty query matches the whole library, so this screen used to open as
             * a list of every installed app — which read as an app drawer appearing
             * unbidden whenever the keyboard came up. Search shows results for a
             * search.
             */
            !state.hasQuery -> Text(
                text = if (state.results.isEmpty()) {
                    "No applications found yet. Run a library scan from the side menu."
                } else {
                    "Type to search games, apps, folders and settings."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingTiny),
            ) {
                itemsIndexed(state.results, key = { _, entry -> entry.id }) { index, entry ->
                    SearchResultRow(
                        entry = entry,
                        focused = index == focusedIndex,
                        onClick = { onEntrySelected(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    entry: GridEntry,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    // The pointer marks a result the same way the cursor does; see the note in
    // `GridCell` for why hover reuses the launcher's own language for "this is
    // what a press acts on" rather than inventing a second one.
    val hover = rememberPointerHover()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .thorCursor(
                focused = focused || hover.isHovered,
                cornerRadius = dimens.cornerRadiusSmall,
            )
            .pointerHover(hover)
            .clickable(onClick = onClick)
            .padding(dimens.spacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(dimens.cornerRadiusSmall)),
        ) {
            ArtworkImage(
                model = (entry as? GameEntry)?.metadata?.artwork?.cellImage,
                contentDescription = null,
                fallbackText = entry.title,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.describe(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Identifies this field to the launcher's text focus. */
private const val FIELD_ID = "search-query"

/** Secondary line shown beneath a result's title. */
private fun GridEntry.describe(): String = when (this) {
    is GameEntry -> listOfNotNull(
        platformId.uppercase(),
        metadata.developer,
        metadata.releaseYear?.toString(),
    ).joinToString(" · ")

    is AppEntry -> if (isEmulator) "Emulator · $packageName" else packageName
    is FolderEntry -> "Folder · ${childIds.size} items"
    else -> ""
}
