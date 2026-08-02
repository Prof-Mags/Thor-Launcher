package com.thor.feature.settings.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.SettingsTextButton

/** One thing Loki needs granting, and whether it has been. */
data class PermissionItem(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val granted: Boolean,
    val onGrant: () -> Unit,
)

/**
 * What Loki needs, asked for once, on the panel the user is holding.
 *
 * Every one of these lives in a system screen the launcher cannot fill in for
 * you — Android grants them by hand, on purpose, because each is the kind of
 * permission an app should not be able to talk its way into. So this is a list
 * of doors rather than a set of switches: it says what each is for, whether it
 * is already open, and opens the right settings screen.
 *
 * Nothing here is required. A launcher that will not start until every box is
 * ticked is worse than one that works with none of them, and all of it can be
 * granted later from Settings.
 */
@Composable
fun PermissionsScreen(
    items: List<PermissionItem>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val outstanding = items.count { !it.granted }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = ThorTheme.shapes.panel,
            color = colors.surface,
            level = SurfaceLevel.RAISED,
            modifier = Modifier
                .fillMaxWidth(CARD_WIDTH_FRACTION)
                .padding(dimens.spacing),
        ) {
            Column(
                modifier = Modifier.padding(dimens.spacing),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
            ) {
                Text(
                    text = "Before you start",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )
                Text(
                    text = if (outstanding == 0) {
                        "Everything Loki asks for has been granted."
                    } else {
                        "Loki works without any of these. Each one turns on a part " +
                            "of it, and each is granted in Android's own settings — " +
                            "no app can grant them for you."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                ) {
                    items.forEach { item -> PermissionRow(item) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SettingsTextButton(
                        // Never "Skip": nothing is being skipped. The list is a
                        // set of offers, and closing it declines them for now.
                        label = if (outstanding == 0) "DONE" else "CONTINUE",
                        containerColor = colors.cursor.copy(alpha = 0.16f),
                        contentColor = colors.cursor,
                        borderColor = colors.cursor.copy(alpha = 0.5f),
                        focused = true,
                        reactToHover = true,
                        onClick = onDone,
                    )
                }

                Text(
                    text = "A continues  ·  these are all in Settings later",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(item: PermissionItem) {
    val colors = ThorTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.granted) Icons.Rounded.Check else item.icon,
            contentDescription = null,
            tint = if (item.granted) colors.cursor else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        SettingsTextButton(
            label = if (item.granted) "ON" else "GRANT",
            containerColor = if (item.granted) {
                colors.cursor.copy(alpha = 0.12f)
            } else {
                colors.surfaceHighest
            },
            contentColor = if (item.granted) colors.cursor else colors.onSurface,
            enabled = !item.granted,
            reactToHover = !item.granted,
            onClick = item.onGrant.takeIf { !item.granted },
        )
    }
}

/** The set Loki asks for, in the order they matter. */
@Composable
fun rememberPermissionItems(
    isDefaultLauncher: Boolean,
    pointerServiceEnabled: Boolean,
    onSetDefaultLauncher: () -> Unit,
    onOpenPointerSettings: () -> Unit,
): List<PermissionItem> = listOf(
    PermissionItem(
        title = "Set Loki as your home app",
        detail = "Otherwise the stock launcher opens when you press Home",
        icon = Icons.Rounded.Home,
        granted = isDefaultLauncher,
        onGrant = onSetDefaultLauncher,
    ),
    PermissionItem(
        title = "Controller pointer",
        detail = "A cursor you can use in any app, and the keyboard that types into them",
        icon = Icons.Rounded.Mouse,
        granted = pointerServiceEnabled,
        onGrant = onOpenPointerSettings,
    ),
)

private const val CARD_WIDTH_FRACTION = 0.86f
