package com.thor.feature.settings.component.row

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.thor.core.designsystem.theme.contrastingContentColor
import com.thor.feature.settings.component.SettingsTextButton

/** A boolean setting. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    focused: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateColor = if (checked) TOGGLE_ON_COLOR else TOGGLE_OFF_COLOR
    ActivateOnConfirm(focused) { onCheckedChange(!checked) }
    SettingsRowShell(
        title = title,
        subtitle = subtitle,
        focused = focused,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            SettingsTextButton(
                label = if (checked) "ON" else "OFF",
                containerColor = stateColor,
                contentColor = contrastingContentColor(stateColor),
                borderColor = contrastingContentColor(stateColor).copy(alpha = 0.34f),
                onClick = { onCheckedChange(!checked) },
            )
        },
    )
}

private val TOGGLE_ON_COLOR = Color(0xFF2E7D32)
private val TOGGLE_OFF_COLOR = Color(0xFFC62828)
