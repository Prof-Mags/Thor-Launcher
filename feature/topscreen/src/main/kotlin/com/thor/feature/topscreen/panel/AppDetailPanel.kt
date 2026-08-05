package com.thor.feature.topscreen.panel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry

/** Detail view for a highlighted application. */
@Composable
fun AppDetailPanel(app: AppEntry, modifier: Modifier = Modifier) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(
        modifier = modifier.fillMaxSize().padding(dimens.spacingHuge),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        Text(
            text = app.title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.onBackground,
        )
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingLarge)) {
            app.versionName?.let { LabelledValue("Version", it) }
            LabelledValue("Times opened", app.launchCount.toString())
            app.lastPlayedEpochMs?.let { LabelledValue("Last opened", formatRelative(it)) }
            if (app.isEmulator) LabelledValue("Type", "Emulator")
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    val colors = ThorTheme.colors
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
        )
    }
}
