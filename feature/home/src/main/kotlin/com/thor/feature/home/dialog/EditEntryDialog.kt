package com.thor.feature.home.dialog

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.common.log.ThorLog
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.ArtworkSet
import com.thor.core.model.GameEntry
import com.thor.core.model.GameMetadata
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.ui.component.ArtworkImage
import com.thor.core.ui.component.ThorDropdownItem
import com.thor.core.ui.component.ThorDropdownMenu
import com.thor.core.ui.input.ThorInputField
import com.thor.core.ui.pointer.pointerHover
import com.thor.core.ui.pointer.rememberPointerHover
import com.thor.feature.home.couch.platform
import com.thor.feature.home.shell.icon

/** The edited values handed back when the dialog is confirmed. */
data class EntryEdits(
    val title: String,
    val customIconUri: String?,
    /** Null for entries that carry no game metadata. */
    val metadata: GameMetadata?,
    /** Set when the user reassigned the game to another system. */
    val platformId: String? = null,
    /**
     * Emulator override for this game alone. Null means "no change requested";
     * [EntryEdits.clearEmulator] distinguishes that from "clear the override".
     */
    val emulatorPackage: String? = null,
    val clearEmulator: Boolean = false,
)

/** One selectable emulator in the executable picker. */
data class EmulatorOption(val packageName: String, val label: String)

/**
 * Editor for an entry's presentation and, for games, its metadata.
 *
 * Fields are seeded from the current values and only written back on confirm,
 * so abandoning the dialog changes nothing. Anything the user fills in here is
 * marked locked by the repository, which is what stops the next scrape from
 * silently reverting the edit.
 *
 * @param platforms systems the game may be reassigned to
 * @param emulatorOptions installed emulators able to run the current platform
 */
@Composable
fun EditEntryDialog(
    entry: GridEntry?,
    onConfirm: (EntryEdits) -> Unit,
    onDismiss: () -> Unit,
    platforms: List<Platform> = emptyList(),
    emulatorOptions: List<EmulatorOption> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (entry == null) return

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val context = LocalContext.current
    val game = entry as? GameEntry

    // While this dialog is open the controller router is suspended so its fields
    // can be typed into, which means Back no longer reaches the view model's
    // state machine. Handling it here keeps the dialog dismissable.
    BackHandler(enabled = true, onBack = onDismiss)

    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var iconUri by remember(entry.id) {
        mutableStateOf(
            when (entry) {
                is AppEntry -> entry.customIconUri
                is GameEntry -> entry.metadata.artwork.boxArt
                else -> null
            },
        )
    }
    /*
     * The rest of a game's pictures, each its own slot.
     *
     * The dialog edited the cover and the screenshots and nothing else, which
     * left the square cell icon, the panel backdrop and the wordmark reachable
     * only by a scrape, so a wrong one could be corrected in two places out of
     * five. They are separate fields rather than one because they are separate
     * shapes: a portrait cover cannot stand in for a square cell, and neither can
     * stand in for a widescreen backdrop.
     */
    var squareIconUri by remember(entry.id) { mutableStateOf(game?.metadata?.artwork?.icon) }
    var heroUri by remember(entry.id) { mutableStateOf(game?.metadata?.artwork?.hero) }
    var logoUri by remember(entry.id) { mutableStateOf(game?.metadata?.artwork?.logo) }

    var developer by remember(entry.id) { mutableStateOf(game?.metadata?.developer.orEmpty()) }
    var publisher by remember(entry.id) { mutableStateOf(game?.metadata?.publisher.orEmpty()) }
    var genres by remember(entry.id) {
        mutableStateOf(game?.metadata?.genres?.joinToString(", ").orEmpty())
    }
    var year by remember(entry.id) {
        mutableStateOf(game?.metadata?.releaseYear?.toString().orEmpty())
    }
    var description by remember(entry.id) {
        mutableStateOf(game?.metadata?.description.orEmpty())
    }
    var platformId by remember(entry.id) { mutableStateOf(game?.platformId) }
    var emulatorPackage by remember(entry.id) { mutableStateOf(game?.emulatorPackage) }

    // A snapshot list so add/remove recompose without rebuilding the whole
    // dialog's state on every change.
    val screenshots = remember(entry.id) {
        game?.metadata?.artwork?.cappedScreenshots.orEmpty().toMutableStateList()
    }

    /** Persists read access so a picked image survives a reboot. */
    fun persist(uri: Uri) {
        // Without a persisted grant the image renders now and breaks after a
        // reboot, which looks like the edit silently failing.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { ThorLog.w("Edit", "Image URI is not persistable: $uri", it) }
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persist(uri)
        iconUri = uri.toString()
    }

    // One launcher per slot, because a result callback has no way to ask which
    // row started it. A shared one would need a pending-target variable, and a
    // way to get it wrong.
    val squareIconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persist(uri)
        squareIconUri = uri.toString()
    }

    val heroPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persist(uri)
        heroUri = uri.toString()
    }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        persist(uri)
        logoUri = uri.toString()
    }

    val screenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        // Capped here as well as in the scraper: the slideshow and the strip are
        // both built for a small fixed set.
        val room = ArtworkSet.MAX_SCREENSHOTS - screenshots.size
        uris.take(room.coerceAtLeast(0)).forEach { uri ->
            persist(uri)
            screenshots.add(uri.toString())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            shape = RoundedCornerShape(dimens.cornerRadiusLarge),
            // Highest surface: these sit over an already-elevated panel, and
            // reusing the base surface made them read as part of it.
            color = ThorTheme.colors.surfaceHighest,
            modifier = Modifier
                .width(DIALOG_WIDTH.dp)
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(dimens.spacing)) {
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(bottom = dimens.spacingSmall),
                )

                Column(
                    modifier = Modifier
                        .heightIn(max = CONTENT_MAX_HEIGHT.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
                ) {
                    if (game == null) {
                        // An app has one picture and it is square, so the row of
                        // shaped slots below would be three empty boxes.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ICON_PREVIEW.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
                                    .clickable { iconPicker.launch(IMAGE_MIME_FILTER) },
                            ) {
                                ArtworkImage(
                                    model = iconUri,
                                    contentDescription = "Icon",
                                    fallbackText = title,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Column {
                                TextButton(onClick = { iconPicker.launch(IMAGE_MIME_FILTER) }) {
                                    Text("Choose artwork", color = colors.cursor)
                                }
                                if (iconUri != null) {
                                    TextButton(onClick = { iconUri = null }) {
                                        Text("Reset", color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "ARTWORK",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        // Each slot at its own shape rather than a uniform grid:
                        // the preview is the only thing telling the user which
                        // picture belongs where, and four equal squares would
                        // make a cover and a backdrop look interchangeable.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.spacingTiny),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            ArtworkSlot(
                                label = "Cover",
                                ratio = COVER_RATIO,
                                uri = iconUri,
                                fallbackText = title,
                                onPick = { iconPicker.launch(IMAGE_MIME_FILTER) },
                                onClear = { iconUri = null },
                            )
                            ArtworkSlot(
                                label = "Icon",
                                ratio = 1f,
                                uri = squareIconUri,
                                fallbackText = title,
                                onPick = { squareIconPicker.launch(IMAGE_MIME_FILTER) },
                                onClear = { squareIconUri = null },
                            )
                            ArtworkSlot(
                                label = "Backdrop",
                                ratio = WIDE_RATIO,
                                uri = heroUri,
                                onPick = { heroPicker.launch(IMAGE_MIME_FILTER) },
                                onClear = { heroUri = null },
                            )
                            ArtworkSlot(
                                label = "Logo",
                                ratio = WIDE_RATIO,
                                uri = logoUri,
                                onPick = { logoPicker.launch(IMAGE_MIME_FILTER) },
                                onClear = { logoUri = null },
                            )
                        }
                    }

                    EditField(
                        label = "Title",
                        value = title,
                        onValueChange = { title = it },
                    )

                    if (game != null) {
                        EditField("Developer", developer) { developer = it }
                        EditField("Publisher", publisher) { publisher = it }
                        EditField("Genres", genres, "Comma separated") { genres = it }
                        EditField("Release year", year, "e.g. 1998") { year = it }
                        EditField("Description", description) { description = it }

                        // The scanner guesses the platform from the folder and
                        // file extension, and a bare `.bin` could be any of half
                        // a dozen systems — so the guess has to be correctable.
                        PickerRow(
                            label = "Platform",
                            selected = platforms.firstOrNull { it.id == platformId }?.name
                                ?: platformId.orEmpty(),
                            options = platforms.map { it.id to it.name },
                            onSelected = { platformId = it },
                        )

                        PickerRow(
                            label = "Executable",
                            selected = emulatorOptions
                                .firstOrNull { it.packageName == emulatorPackage }
                                ?.label
                                ?: PLATFORM_DEFAULT_LABEL,
                            // The empty id clears the override and returns the
                            // game to whatever the platform is configured with.
                            options = listOf("" to PLATFORM_DEFAULT_LABEL) +
                                emulatorOptions.map { it.packageName to it.label },
                            onSelected = { emulatorPackage = it.ifEmpty { null } },
                        )

                        ScreenshotEditor(
                            screenshots = screenshots,
                            onAdd = { screenshotPicker.launch(IMAGE_MIME_FILTER) },
                            onRemove = { index ->
                                if (index in screenshots.indices) screenshots.removeAt(index)
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacingSmall),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = {
                            onConfirm(
                                EntryEdits(
                                    title = title,
                                    customIconUri = iconUri,
                                    metadata = game?.metadata?.copy(
                                        developer = developer.ifBlank { null },
                                        publisher = publisher.ifBlank { null },
                                        genres = genres.split(',')
                                            .map(String::trim)
                                            .filter(String::isNotEmpty),
                                        releaseYear = year.toIntOrNull(),
                                        description = description.ifBlank { null },
                                        artwork = game.metadata.artwork.copy(
                                            icon = squareIconUri,
                                            hero = heroUri,
                                            logo = logoUri,
                                            screenshots = screenshots.toList(),
                                        ),
                                    ),
                                    platformId = platformId,
                                    emulatorPackage = emulatorPackage,
                                    clearEmulator = game != null && emulatorPackage == null,
                                ),
                            )
                        },
                    ) {
                        Text("Save", color = colors.cursor)
                    }
                }
            }
        }
    }
}

/**
 * A labelled dropdown.
 *
 * Plain [DropdownMenu] anchored on a row rather than an exposed dropdown text
 * field: the value is always chosen from a fixed list, so a text field would
 * imply the user could type something that is not an option.
 */
@Composable
private fun PickerRow(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    var expanded by remember { mutableStateOf(false) }
    // A bordered row that never changed under the cursor was indistinguishable
    // from the read-only facts above it.
    val hover = rememberPointerHover()

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerHover(hover)
                .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
                .border(
                    width = 1.dp,
                    color = if (hover.isHovered) colors.cursor else colors.outline,
                    shape = RoundedCornerShape(dimens.cornerRadiusSmall),
                )
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
                .padding(horizontal = dimens.spacingSmall, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = selected.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        ThorDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, optionLabel) ->
                ThorDropdownItem(
                    label = optionLabel,
                    selected = optionLabel == selected,
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Add/remove list for the game's screenshots, bounded by the artwork cap. */
/**
 * One picture slot: tap the preview to replace it, the corner to empty it.
 *
 * [ratio] is the slot's shape, not the file's. A picture put here is drawn the
 * way the launcher will draw it, so choosing a portrait file for the backdrop
 * shows the crop immediately instead of at the next scrape.
 */
@Composable
private fun ArtworkSlot(
    label: String,
    ratio: Float,
    uri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    fallbackText: String = "",
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    // An empty slot is a grey rectangle and a filled one is a picture; neither
    // says it can be pressed until something happens when the cursor arrives.
    val hover = rememberPointerHover()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingTiny),
    ) {
        Box(
            modifier = Modifier
                .height(SLOT_PREVIEW.dp)
                .aspectRatio(ratio)
                .pointerHover(hover)
                .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
                .border(
                    width = if (hover.isHovered) 2.dp else 1.dp,
                    color = if (hover.isHovered) {
                        colors.cursor
                    } else {
                        colors.outline.copy(alpha = 0.36f)
                    },
                    shape = RoundedCornerShape(dimens.cornerRadiusSmall),
                )
                .clickable(onClick = onPick),
        ) {
            ArtworkImage(
                model = uri,
                contentDescription = label,
                fallbackText = fallbackText,
                modifier = Modifier.fillMaxSize(),
            )
            if (uri != null) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear $label",
                    tint = colors.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(colors.scrim, RoundedCornerShape(bottomStart = 6.dp))
                        .clickable(onClick = onClear)
                        .padding(2.dp)
                        .size(14.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScreenshotEditor(
    screenshots: List<String>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingTiny)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SCREENSHOTS ${screenshots.size}/${ArtworkSet.MAX_SCREENSHOTS}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (screenshots.size < ArtworkSet.MAX_SCREENSHOTS) {
                TextButton(onClick = onAdd) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = colors.cursor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Add", color = colors.cursor)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingTiny)) {
            screenshots.forEachIndexed { index, url ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(dimens.cornerRadiusSmall)),
                ) {
                    ArtworkImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove screenshot",
                        tint = colors.onSurface,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(colors.scrim, RoundedCornerShape(bottomStart = 6.dp))
                            .clickable { onRemove(index) }
                            .padding(2.dp)
                            .size(14.dp),
                    )
                }
            }
            // Keeps the thumbnails at a constant size as the list shrinks
            // instead of letting two images stretch across the whole row.
            repeat(ArtworkSet.MAX_SCREENSHOTS - screenshots.size) {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * One labelled field in the editor.
 *
 * A [ThorInputField] rather than a platform text field: tapping it claims the
 * launcher's own text focus and raises THOR's keyboard on the panel the user is
 * holding. A platform field would ask for platform focus and summon an IME, which on
 * this hardware is the one keyboard that never appears.
 */
@Composable
private fun EditField(
    label: String,
    value: String,
    placeholder: String? = null,
    onValueChange: (String) -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingTiny)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        ThorInputField(
            id = "editor-" + label,
            label = label,
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
        )
    }
}

private val IMAGE_MIME_FILTER = arrayOf("image/*")
private const val PLATFORM_DEFAULT_LABEL = "Platform default"
private const val DIALOG_WIDTH = 420
private const val CONTENT_MAX_HEIGHT = 340
private const val ICON_PREVIEW = 72

/**
 * Slot previews are sized by height so their widths follow their shapes.
 * At 68dp the four of them come to roughly 335dp, inside the dialog's usable
 * width; raising it much further pushes the backdrop and logo off the edge.
 */
private const val SLOT_PREVIEW = 68
private const val COVER_RATIO = 2f / 3f
private const val WIDE_RATIO = 16f / 9f
