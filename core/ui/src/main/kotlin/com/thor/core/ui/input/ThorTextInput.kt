package com.thor.core.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme

/**
 * Which field the launcher's keyboard is typing into.
 *
 * This is the launcher's stand-in for platform text focus, and it exists for the
 * same reason the keyboard does: the launcher draws on two displays, and Android's
 * focus and IME follow the *window*, so a field on one panel and a keyboard on the
 * other cannot be connected by the platform at all. Here they are connected by
 * state instead — a field says "I am the one being typed into", the keyboard edits
 * a buffer, and whatever is focused receives it.
 *
 * Compose-scoped rather than a singleton: it dies with the composition, which means
 * a field's callback cannot outlive the screen that registered it.
 */
@Stable
class ThorTextInputState {

    /** Identifies the focused field, or null when nothing is being typed into. */
    var focusedId by mutableStateOf<String?>(null)
        private set

    /** What the keyboard should call the field it is filling in. */
    var label by mutableStateOf("")
        private set

    /** The focused field's value at the moment focus was taken. */
    var initialText by mutableStateOf("")
        private set

    private var sink: ((String) -> Unit)? = null

    /**
     * Claims text input for a field.
     *
     * @param onChange receives every edit as it happens, so the field it belongs to
     *   updates live rather than on a commit — which is what makes search-as-you-type
     *   work across two panels.
     */
    fun focus(id: String, label: String, initial: String, onChange: (String) -> Unit) {
        focusedId = id
        this.label = label
        initialText = initial
        sink = onChange
    }

    /** Applies an edit from the keyboard to whatever holds focus. */
    fun setText(value: String) {
        sink?.invoke(value)
    }

    /**
     * Releases focus.
     *
     * @param id when given, only releases if that field still holds focus — so a
     *   field being disposed cannot steal the focus a newer one has taken.
     */
    fun release(id: String? = null) {
        if (id != null && focusedId != id) return
        focusedId = null
        label = ""
        initialText = ""
        sink = null
    }
}

/**
 * The launcher's text focus, shared by both windows.
 *
 * Reaches the secondary panel because that window is composed with the activity's
 * `CompositionContext`, so composition locals cross the display boundary — the same
 * mechanism the theme uses.
 */
val LocalThorTextInput = staticCompositionLocalOf { ThorTextInputState() }

/**
 * A text field the launcher's own keyboard can type into.
 *
 * Not a `BasicTextField` or an `OutlinedTextField`: a platform field asks for
 * platform focus, which asks for the platform IME, which is the thing that does not
 * work here. This renders the value, shows where the text is going, and hands
 * editing to [ThorTextInputState].
 *
 * @param id unique among the fields that can be on screen together
 * @param secret masks the value, for API keys and passwords
 */
@Composable
fun ThorInputField(
    id: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val textInput = LocalThorTextInput.current
    val focused = textInput.focusedId == id

    // Kept current so the callback registered on focus always writes to the latest
    // state holder, however many times this screen has recomposed since.
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    // A field that leaves the screen must not keep the keyboard pointed at it.
    DisposableEffect(id) {
        onDispose { textInput.release(id) }
    }

    val shown = when {
        value.isEmpty() -> placeholder.orEmpty()
        secret -> "•".repeat(value.length.coerceAtMost(SECRET_MAX_DOTS))
        else -> value
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .background(colors.surface)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.cursor else colors.outline,
                shape = RoundedCornerShape(dimens.cornerRadiusSmall),
            )
            .clickable {
                textInput.focus(id = id, label = label, initial = value) { edited ->
                    currentOnValueChange(edited)
                }
            }
            .padding(horizontal = dimens.spacingSmall, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = shown,
            style = MaterialTheme.typography.bodyLarge,
            color = if (value.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        // A caret only where the text is actually going, so two fields on two panels
        // can never both look active.
        if (focused) {
            Box(
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(width = CARET_WIDTH.dp, height = CARET_HEIGHT.dp)
                    .background(colors.cursor),
            )
        }
    }
}


private const val CARET_WIDTH = 2
private const val CARET_HEIGHT = 20

/** Long secrets are shown as a fixed run of dots rather than a length hint. */
private const val SECRET_MAX_DOTS = 24
