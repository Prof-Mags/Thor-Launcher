package com.thor.feature.settings.component

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.thor.core.common.log.ThorLog

/**
 * A row that opens the system image picker and reports the chosen wallpaper.
 *
 * The URI is persisted, so read permission has to be persisted with it —
 * without `takePersistableUriPermission` the wallpaper loads until the next
 * reboot and then silently fails, which looks exactly like the setting not
 * working.
 */
@Composable
fun WallpaperPickerRow(
    title: String,
    subtitle: String?,
    currentUri: String?,
    focused: Boolean = false,
    onPicked: (String?) -> Unit,
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess

        if (!granted) {
            // Some providers hand back a one-shot URI. It still works for this
            // session, so it is accepted rather than refused outright.
            ThorLog.w("Settings", "Wallpaper URI is not persistable: $uri")
        }
        onPicked(uri.toString())
    }

    ActionRow(
        title = title,
        subtitle = if (currentUri != null) "Custom image selected" else subtitle,
        focused = focused,
        onClick = { picker.launch(arrayOf("image/*")) },
    )

    if (currentUri != null) {
        ActionRow(
            title = "Clear $title",
            subtitle = null,
            onClick = { onPicked(null) },
        )
    }
}

/**
 * A row that opens the system directory picker and reports a ROM folder.
 *
 * Directory grants must be persisted for the same reason as wallpapers, and
 * additionally survive the scanner running long after the picker closed.
 */
@Composable
fun DirectoryPickerRow(
    title: String,
    subtitle: String?,
    focused: Boolean = false,
    onPicked: (uri: String, displayName: String) -> Unit,
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            ThorLog.w("Settings", "Could not persist directory grant for $uri", error)
        }
        onPicked(uri.toString(), uri.lastPathSegment ?: "ROM folder")
    }

    ActionRow(
        title = title,
        subtitle = subtitle,
        focused = focused,
        onClick = { picker.launch(null) },
    )
}
