package com.thor.feature.settings.component.row

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.SettingsTextButton

/** A tappable row that performs an action rather than holding a value. */
@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    focused: Boolean = false,
    destructive: Boolean = false,
    trailingLabel: String? = null,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    ActivateOnConfirm(focused, onClick)
    SettingsRowShell(
        title = title,
        subtitle = subtitle,
        focused = focused,
        titleColor = if (destructive) colors.error else null,
        onClick = onClick,
        trailing = trailingLabel?.let {
            {
                val tint = if (destructive) colors.error else colors.cursor
                SettingsTextButton(
                    label = it.uppercase(),
                    containerColor = tint.copy(alpha = 0.12f),
                    contentColor = tint,
                    borderColor = tint.copy(alpha = 0.34f),
                )
            }
        },
    )
}

/** A read-only informational row. */
@Composable
fun InfoRow(title: String, value: String) {
    val colors = ThorTheme.colors
    SettingsRowShell(
        title = title,
        subtitle = null,
        focused = false,
        onClick = null,
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                modifier = Modifier
                    .widthIn(max = VALUE_MAX_WIDTH.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(colors.surfaceHighest)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
