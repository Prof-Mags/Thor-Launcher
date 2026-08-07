package com.thor.feature.settings.component.row

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.thor.core.common.log.ThorLog
import com.thor.feature.settings.component.RowDivider

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
    clearFocused: Boolean = false,
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
        trailingLabel = if (currentUri != null) "CHANGE" else "CHOOSE",
        onClick = { picker.launch(arrayOf("image/*")) },
    )

    if (currentUri != null) {
        RowDivider()
        ActionRow(
            title = "Clear $title",
            subtitle = null,
            focused = clearFocused,
            destructive = true,
            trailingLabel = "CLEAR",
            onClick = { onPicked(null) },
        )
    }
}

/**
 * A row that opens the system file picker for a single file of [mimeTypes].
 *
 * Read permission is persisted like every other picked URI, though for an icon
 * pack it matters less than usual: the archive is copied in immediately and never
 * read again, so a one-shot grant is enough. It is taken anyway, because the
 * import runs on a background dispatcher and a grant that expires between the
 * picker closing and the copy starting would fail for no visible reason.
 */
@Composable
fun FilePickerRow(
    title: String,
    subtitle: String?,
    mimeTypes: Array<String>,
    focused: Boolean = false,
    onPicked: (uri: String, displayName: String) -> Unit,
) {
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            ThorLog.w("Settings", "Could not persist file grant for $uri", error)
        }
        onPicked(uri.toString(), uri.lastPathSegment?.substringAfterLast('/') ?: "Pack")
    }

    ActionRow(
        title = title,
        subtitle = subtitle,
        focused = focused,
        trailingLabel = "IMPORT",
        onClick = { picker.launch(mimeTypes) },
    )
}

/**
 * A row that opens the system's "save as" dialog and reports where to write.
 *
 * The mirror of [FilePickerRow], and it has to be a separate contract rather than
 * a flag on that one: `OpenDocument` can only return a document that already
 * exists, so there is no way to express "somewhere new called this" through it.
 *
 * No permission is persisted, deliberately. The grant that comes back lasts as
 * long as this process needs it, the write happens immediately, and the launcher
 * has no business holding a lasting claim on a file it exported once.
 */
@Composable
fun FileSaverRow(
    title: String,
    subtitle: String?,
    /** Offered as the file name; the user is free to change it. */
    suggestedName: String,
    mimeType: String = "application/json",
    focused: Boolean = false,
    trailingLabel: String = "EXPORT",
    onChosen: (uri: String) -> Unit,
) {
    val saver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        onChosen(uri.toString())
    }

    ActionRow(
        title = title,
        subtitle = subtitle,
        focused = focused,
        trailingLabel = trailingLabel,
        onClick = { saver.launch(suggestedName) },
    )
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
        trailingLabel = "CHOOSE",
        onClick = { picker.launch(null) },
    )
}
