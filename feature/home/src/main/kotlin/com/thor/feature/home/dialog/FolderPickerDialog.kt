package com.thor.feature.home.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.FolderEntry
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.ThorMenuRow

/** State of the folder picker, raised by "Move to folder…". */
data class FolderPickerState(
    val visible: Boolean = false,
    /** The entry being filed. */
    val entryId: String? = null,
    val entryTitle: String = "",
    val folders: List<FolderEntry> = emptyList(),
    val focusedIndex: Int = 0,
) {
    /**
     * Rows, with "New folder" last.
     *
     * Offered even when folders exist, because the reason to file something is
     * often that the right folder does not exist yet — and making the user leave,
     * create one from the side menu, and come back is three steps for one intent.
     */
    val rowCount: Int get() = folders.size + 1
    val isNewFolderRow: Boolean get() = focusedIndex == folders.size
}

/**
 * Picks the folder an entry is filed into.
 *
 * A list rather than a drag target, because dragging onto a folder only works
 * when both are on the same page — and with unlimited pages, the folder you want
 * is usually somewhere else.
 */
@Composable
fun FolderPickerDialog(
    state: FolderPickerState,
    onPick: (folderId: String) -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(dimens.cornerRadiusLarge),
            color = colors.surfaceHighest,
            modifier = Modifier
                .width(CARD_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Text(
                    text = "Move to folder",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = state.entryTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = dimens.spacingSmall),
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.folders.forEachIndexed { index, folder ->
                        FolderRow(
                            title = folder.title,
                            subtitle = "${folder.childIds.size} items",
                            artworkUri = folder.artworkUri,
                            accent = folder.accentArgb?.let(::Color) ?: colors.primary,
                            focused = index == state.focusedIndex,
                            onClick = { onPick(folder.id) },
                        )
                    }

                    FolderRow(
                        title = "New folder",
                        subtitle = "Create one and file this into it",
                        artworkUri = null,
                        accent = colors.cursor,
                        focused = state.isNewFolderRow,
                        isCreate = true,
                        onClick = onCreateFolder,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    title: String,
    subtitle: String,
    artworkUri: String?,
    accent: Color,
    focused: Boolean,
    isCreate: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    ThorMenuRow(
        label = title,
        description = subtitle,
        focused = focused,
        // The folder's own colour, in the slot the accent gradient takes in
        // every other menu.
        accent = accent,
        leading = {
            Box(
                modifier = Modifier
                    .size(THUMB.dp)
                    .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                if (artworkUri != null) {
                    ArtworkImage(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (isCreate) {
                            Icons.Rounded.CreateNewFolder
                        } else {
                            Icons.Rounded.Folder
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        onClick = onClick,
    )
}

private const val CARD_WIDTH = 340
private const val CONTENT_MAX_HEIGHT = 300
private const val THUMB = 34
