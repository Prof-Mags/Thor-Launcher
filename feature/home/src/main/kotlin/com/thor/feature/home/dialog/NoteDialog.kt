package com.thor.feature.home.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameNote
import com.thor.core.ui.input.ThorInputField

/**
 * Where the user writes what they were doing.
 *
 * The only surface in the launcher that stores something nobody else wrote. Its
 * whole job is to be reachable in two presses and to keep what was typed, which is
 * why it is a dialog over whatever raised it rather than a page: the question it
 * answers — "where was I" — is asked while looking at the game, and a screen that
 * replaced the game's details to ask it would have hidden the thing being written
 * about.
 *
 * A [ThorInputField] rather than a platform text field, so it claims the
 * launcher's own text focus and raises Loki's keyboard on the panel being held.
 * A platform field would summon an IME, which on this hardware never appears.
 */
@Composable
fun NoteDialog(
    state: NoteDialogState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    /*
     * Held here and written back only on Save.
     *
     * The alternative — writing through on every keystroke — would make Cancel a
     * lie, and would also write a row per character to a table that is observed by
     * two other surfaces.
     */
    var draft by rememberSaveable(state.entryId) { mutableStateOf(state.body) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth(DIALOG_WIDTH_FRACTION)
                .widthIn(max = DIALOG_MAX_WIDTH.dp)
                // Swallows the press, so tapping inside the card does not dismiss
                // through the scrim underneath it.
                .clickable(enabled = false) {},
        ) {
            Column(
                modifier = Modifier.padding(dimens.spacingLarge),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                Text(
                    text = "Note",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                ThorInputField(
                    id = "note-${state.entryId}",
                    label = "Note",
                    value = draft,
                    onValueChange = { typed -> draft = typed.take(GameNote.MAX_LENGTH) },
                    placeholder = "Where you got to, what to do next…",
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                ) {
                    Box(modifier = Modifier.weight(1f))
                    DialogAction(label = "Cancel", tint = colors.onSurfaceVariant, onClick = onDismiss)
                    /*
                     * One button for both writing and clearing.
                     *
                     * Emptying the field and saving deletes the note — see
                     * `GameJournalRepository.setNote` — so there is no separate
                     * Delete to find, and no state where the two disagree about
                     * whether a blank note exists.
                     */
                    DialogAction(
                        label = if (draft.isBlank() && state.body.isNotBlank()) "Clear" else "Save",
                        tint = colors.cursor,
                        onClick = { onSave(draft) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = tint,
        modifier = Modifier
            .clip(ThorTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * What the note dialog is showing, if anything.
 *
 * Carries the title as well as the id because the dialog is raised from surfaces
 * that already know it, and looking it up again would mean handing the dialog the
 * whole library to find one string.
 */
data class NoteDialogState(
    val entryId: String? = null,
    val title: String = "",
    val body: String = "",
) {
    val visible: Boolean get() = entryId != null
}

private const val DIALOG_WIDTH_FRACTION = 0.82f
private const val DIALOG_MAX_WIDTH = 460
