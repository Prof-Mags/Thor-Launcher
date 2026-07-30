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
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.KeyboardKey
import com.thor.core.model.KeyboardLayer
import com.thor.core.model.ThorKeyboardLayout

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
        GlassSurface(
            // Square, and the full width of the panel: this is a keyboard, not a
            // dialog, and a floating rounded card read as one.
            shape = RectangleShape,
            color = colors.surfaceHighest,
            modifier = Modifier
                .fillMaxWidth()
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

                Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP.dp)) {
                    rows.forEachIndexed { rowIndex, keys ->
                        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP.dp)) {
                            keys.forEachIndexed { columnIndex, key ->
                                KeyCap(
                                    key = key,
                                    shifted = shifted,
                                    focused = rowIndex == cursorRow && columnIndex == cursorColumn,
                                    onClick = { onKey(key) },
                                    // Wider keys earn their width from the same row
                                    // budget, so every row still spans the card.
                                    modifier = Modifier.weight(key.weight()),
                                )
                            }
                        }
                    }
                }

                // Named because a keyboard driven by a game pad is not something a
                // user can be expected to guess.
                Text(
                    text = "A select · B delete · X space · Y shift · L2/R2 symbols · Start done",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = RoundedCornerShape(dimens.cornerRadiusSmall)

    // Shift and the layer switch are stateful, so they show their state rather than
    // looking identical whether or not they are engaged.
    val active = key == KeyboardKey.Shift && shifted
    val tint = when {
        focused -> colors.cursor
        active -> colors.cursor
        key is KeyboardKey.Character -> colors.onSurface
        else -> colors.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(KEY_HEIGHT.dp)
            .clip(shape)
            .background(if (active) colors.cursor.copy(alpha = 0.18f) else colors.surfaceElevated)
            .thorCursor(focused = focused, shape = shape)
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
private const val KEY_ICON_SIZE = 18
private const val CARET_WIDTH = 2
private const val CARET_HEIGHT = 22
private const val CARET_PERIOD_MS = 600
