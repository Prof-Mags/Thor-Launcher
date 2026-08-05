package com.thor.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.ContentPaste
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.KeyboardKey
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.ThorKeyboardLayout
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover

/**
 * THOR's own on-screen keyboard.
 *
 * The launcher cannot use the platform IME. An IME is drawn by the system on the
 * display owning the focused window, and the launcher's interactive surface is a
 * `Presentation` on the second panel — so the keyboard came up on the wrong screen
 * or, in practice, never appeared at all. This one is part of the launcher's own
 * composition: it renders wherever the grid does, in the current theme, and it is
 * driven by the same controller router as every other surface.
 *
 * Stateless by design. The buffer, the cursor, the layer and the shift latch all
 * live in the view model, which is what lets the controller and a finger drive the
 * same keyboard without the two disagreeing.
 *
 * @param text what has been typed so far
 * @param label what the text is for, shown above the field
 * @param onKey a key was pressed by touch; the caller applies it and plays feedback
 * @param onDismiss the scrim was tapped
 * @param compact draws it as a card at the foot of the screen rather than docked
 *   across the whole of it; see the note in the body
 */
@Composable
fun ThorKeyboard(
    text: String,
    label: String,
    layer: KeyboardLayer,
    shifted: Boolean,
    cursorRow: Int,
    cursorColumn: Int,
    onKey: (KeyboardKey) -> Unit,
    onDismiss: () -> Unit,
    /** Clips on offer; the sheet is up when this is non-null. */
    clips: List<String>? = null,
    /** Which row the controller is on: a clip, or one past them to copy. */
    clipIndex: Int = 0,
    onPasteClip: (String) -> Unit = {},
    onCopyText: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val rows = ThorKeyboardLayout.rows(layer)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            // Takes every touch on the panel, so a tap that misses a key cannot
            // reach the grid underneath and launch something.
            .clickable(onClick = onDismiss),
        // Pinned to the bottom edge, where a keyboard belongs and where the thumbs
        // already are on a handheld.
        contentAlignment = Alignment.BottomCenter,
    ) {
        /*
         * Docked on a handheld, a card on a television.
         *
         * Docking is what a keyboard does on a panel held in two hands: it is
         * square and full width because the thumbs reach the edges and because a
         * floating rounded card on a screen that size reads as a dialog. None of
         * that is true across a room. Nothing is reaching for the edges of a
         * television — the keys are pressed by a cursor or a stick — so the full
         * width buys a row of enormous keys and a field of empty surface, and it
         * covers the thing being typed into while doing it.
         *
         * A share of the panel rather than a width in dp, because couch mode
         * composes through a scaled density: a fixed dp card would change size
         * with the user's interface scale, which is the setting for how big
         * everything *else* is.
         */
        GlassSurface(
            shape = if (compact) ThorTheme.shapes.panel else RectangleShape,
            color = colors.surfaceHighest,
            modifier = Modifier
                .then(
                    if (compact) {
                        Modifier
                            .fillMaxWidth(COMPACT_WIDTH_FRACTION)
                            .padding(bottom = COMPACT_LIFT.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                )
                // Swallows taps on the keyboard so they do not reach the scrim.
                .clickable(enabled = false) {},
        ) {
            Column(
                modifier = Modifier.padding(dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )

                TypedText(text = text)

                // The sheet replaces the keys rather than covering them. Two grids
                // of things to point a cursor at, stacked, would be two places the
                // cursor could appear to be.
                if (clips != null) {
                    ClipboardSheet(
                        clips = clips,
                        focusedIndex = clipIndex,
                        canCopy = text.isNotBlank(),
                        onPaste = onPasteClip,
                        onCopy = onCopyText,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP.dp)) {
                        rows.forEachIndexed { rowIndex, keys ->
                            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP.dp)) {
                                keys.forEachIndexed { columnIndex, key ->
                                    KeyCap(
                                        key = key,
                                        shifted = shifted,
                                        focused = rowIndex == cursorRow &&
                                            columnIndex == cursorColumn,
                                        // Shorter on a card than on a dock: the
                                        // card is a little over half the panel,
                                        // and a key that keeps its docked height
                                        // in that width is a tall thin tile.
                                        height = if (compact) COMPACT_KEY_HEIGHT else KEY_HEIGHT,
                                        onClick = { onKey(key) },
                                        // Wider keys earn their width from the same
                                        // row budget, so every row still spans the card.
                                        modifier = Modifier.weight(key.weight()),
                                    )
                                }
                            }
                        }
                    }
                }

                // Named because a keyboard driven by a game pad is not something a
                // user can be expected to guess.
                Text(
                    text = if (clips != null) {
                        "A paste · B back"
                    } else {
                        "A select · B delete · X space · Y shift · L2/R2 symbols · Start done"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    // Six shortcuts in a card a little over half a screen wide
                    // do not fit on one line, and a legend that ellipsises has
                    // dropped the last thing it had to say.
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Recent clips, and a way to put this field's text back on the clipboard.
 *
 * This is the *system* clipboard, so anything copied elsewhere with the platform
 * keyboard is here and anything copied here is available to it — there is one
 * primary clip and every app shares it. What is not shared is another keyboard's
 * *history*; Gboard keeps its list to itself with no API over it, so the entries
 * below are the clips THOR has seen while it was in front.
 *
 * The copy row is always last and always present, so the sheet is worth opening
 * even with an empty clipboard.
 */
@Composable
private fun ClipboardSheet(
    clips: List<String>,
    focusedIndex: Int,
    canCopy: Boolean,
    onPaste: (String) -> Unit,
    onCopy: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        verticalArrangement = Arrangement.spacedBy(KEY_GAP.dp),
        // Capped and scrollable: the sheet stands in for the keys, and a long
        // clipboard must not make the keyboard taller than the panel.
        modifier = Modifier
            .heightIn(max = SHEET_MAX_HEIGHT.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (clips.isEmpty()) {
            Text(
                text = "Nothing has been copied yet.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = dimens.spacingSmall),
            )
        }

        clips.forEachIndexed { index, clip ->
            ClipRow(
                text = clip,
                focused = index == focusedIndex,
                onClick = { onPaste(clip) },
            )
        }

        ClipRow(
            text = if (canCopy) "Copy what is typed" else "Nothing to copy",
            focused = focusedIndex == clips.size,
            emphasised = true,
            onClick = { if (canCopy) onCopy() },
        )
    }
}

@Composable
private fun ClipRow(
    text: String,
    focused: Boolean,
    emphasised: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.small
    val hover = rememberPointerHover()
    val lit = focused || hover.isHovered

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerHover(hover)
            .clip(shape)
            .background(
                if (hover.isHovered) colors.surfaceHighest else colors.surfaceElevated,
                shape,
            )
            .thorCursor(focused = lit, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacing, vertical = dimens.spacingSmall),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasised) colors.cursor else colors.onSurface,
            // One line: a clipboard entry can be a whole article, and the sheet is
            // for recognising which clip it is rather than reading it.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The field being typed into, with a blinking caret.
 *
 * Not a `BasicTextField`: a real text field would want platform focus and would try
 * to raise the very IME this keyboard exists to replace. It is a `Text` with a caret
 * drawn after it, which is all a field needs to be when something else owns the
 * editing.
 */
@Composable
private fun TypedText(text: String) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val animate = ThorTheme.materials.animationsEnabled

    val transition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by transition.animateFloat(
        // Both ends at full when motion is reduced, so the caret is simply solid.
        // Driving a constant target rather than skipping the call keeps the slot
        // table the same shape whatever the accessibility setting says.
        initialValue = if (animate) 0f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CARET_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caretAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .background(colors.surface)
            .padding(horizontal = dimens.spacingSmall, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            // The tail is what is being typed, so that is the end kept in view.
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(width = CARET_WIDTH.dp, height = CARET_HEIGHT.dp)
                .background(colors.cursor.copy(alpha = caretAlpha)),
        )
    }
}

@Composable
private fun KeyCap(
    key: KeyboardKey,
    shifted: Boolean,
    focused: Boolean,
    height: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerRadiusSmall)

    /*
     * The cursor lights a key the way the controller does.
     *
     * A keyboard is the surface a pointer spends the longest on — every other
     * screen in the launcher is chosen from, and this one is typed on, a press at
     * a time — and it was the one surface that answered a click without ever
     * showing what was about to be clicked. From a sofa that is the difference
     * between typing an address and guessing at one.
     */
    val hover = rememberPointerHover()
    // Shift and the layer switch are stateful, so they show their state rather than
    // looking identical whether or not they are engaged.
    val active = key == KeyboardKey.Shift && shifted
    val lit = focused || hover.isHovered
    val tint = when {
        lit -> colors.cursor
        active -> colors.cursor
        key is KeyboardKey.Character -> colors.onSurface
        else -> colors.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(height.dp)
            .pointerHover(hover)
            .clip(shape)
            .background(
                when {
                    active -> colors.cursor.copy(alpha = 0.18f)
                    hover.isHovered -> colors.surfaceHighest
                    else -> colors.surfaceElevated
                },
            )
            .thorCursor(focused = lit, shape = shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val icon = key.icon()
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = key.describe(),
                tint = tint,
                modifier = Modifier.size(KEY_ICON_SIZE.dp),
            )
        } else {
            Text(
                text = key.label(shifted),
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** Function keys are icons; characters are their own glyph. */
private fun KeyboardKey.icon(): ImageVector? = when (this) {
    is KeyboardKey.Character -> null
    KeyboardKey.Space -> Icons.Rounded.SpaceBar
    KeyboardKey.Backspace -> Icons.AutoMirrored.Rounded.Backspace
    KeyboardKey.Shift -> Icons.Rounded.KeyboardCapslock
    KeyboardKey.Layer -> Icons.Rounded.Tune
    KeyboardKey.Enter -> Icons.AutoMirrored.Rounded.KeyboardReturn
    KeyboardKey.Cancel -> Icons.Rounded.Close
    KeyboardKey.Clipboard -> Icons.Rounded.ContentPaste
}

private fun KeyboardKey.label(shifted: Boolean): String = when (this) {
    is KeyboardKey.Character -> resolve(shifted).toString()
    else -> ""
}

/** Screen-reader name for a key that renders as an icon. */
private fun KeyboardKey.describe(): String = when (this) {
    is KeyboardKey.Character -> resolve(shifted = false).toString()
    KeyboardKey.Space -> "Space"
    KeyboardKey.Backspace -> "Delete"
    KeyboardKey.Shift -> "Shift"
    KeyboardKey.Layer -> "Symbols"
    KeyboardKey.Enter -> "Done"
    KeyboardKey.Cancel -> "Cancel"
    KeyboardKey.Clipboard -> "Clipboard"
}

/**
 * How much of a row's width a key takes.
 *
 * Space earns the most because it is the key most often hit with a thumb, and the
 * bottom row has six keys against the letter rows' ten — without the extra weight
 * its keys would sit under the letters at odd offsets.
 */
private fun KeyboardKey.weight(): Float = when (this) {
    KeyboardKey.Space -> 4f
    KeyboardKey.Enter -> 1.5f
    KeyboardKey.Backspace -> 1.5f
    KeyboardKey.Shift, KeyboardKey.Layer -> 1.5f
    KeyboardKey.Cancel -> 1.2f
    else -> 1f
}

private const val KEY_HEIGHT = 40
private const val KEY_GAP = 5

/**
 * How much of a television the card takes, and how far off the bottom it sits.
 *
 * A little over half, which is wide enough for ten letters at a size that can be
 * read across a room and narrow enough to leave the screen behind it visible —
 * on a television the thing being typed into is usually the thing being covered.
 * Lifted off the edge because it is a card now: a rounded card flush with the
 * bottom of the screen looks like a docked one that failed to reach it.
 */
private const val COMPACT_WIDTH_FRACTION = 0.56f
private const val COMPACT_LIFT = 26
private const val COMPACT_KEY_HEIGHT = 34

/**
 * Ceiling on the clipboard sheet.
 *
 * About the height the key rows occupy, so opening the sheet does not make the
 * keyboard card jump taller than the panel it is pinned to.
 */
private const val SHEET_MAX_HEIGHT = 220
private const val KEY_ICON_SIZE = 18
private const val CARET_WIDTH = 2
private const val CARET_HEIGHT = 22
private const val CARET_PERIOD_MS = 600
