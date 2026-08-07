package com.thor.feature.home.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.AppEntry
import com.thor.core.model.FolderEntry
import com.thor.core.model.GameEntry
import com.thor.core.model.GridEntry
import com.thor.core.model.PlatformFolders
import com.thor.core.model.WidgetEntry
import com.thor.core.ui.component.ArtworkImage
import com.thor.feature.home.couch.platform
import com.thor.feature.home.grid.AppIcon
import com.thor.feature.home.shell.icon

/**
 * An action offered for the highlighted entry.
 *
 * Each carries a one-line [description] for the same reason the side menu's
 * rows do: several of these differ in ways the label alone does not carry, and
 * "Remove from grid" beside "Remove from library" is the pair that has to be
 * told apart correctly the first time.
 */
enum class ContextAction(
    val label: String,
    val description: String,
    val icon: ImageVector,
    /**
     * The caption on the tile itself.
     *
     * The menu is a grid of half-width tiles now, which is what lets a game's
     * eleven actions be on screen at once — but half a card is about twelve
     * characters, and "Launch on second screen" is twenty-three. The full
     * [label] and [description] are still read before anything is pressed:
     * both are shown for whichever tile holds the cursor, on one line under the
     * grid. So the tile says which one it is and the line underneath says what
     * it does, rather than every row paying for a caption in full.
     */
    val short: String,
) {
    LAUNCH("Launch", "Start this now", Icons.Rounded.PlayArrow, "Launch"),
    LAUNCH_MAIN_SCREEN(
        "Launch on main screen",
        "Open on the top display",
        Icons.Rounded.Monitor,
        "Top screen",
    ),
    LAUNCH_SECOND_SCREEN(
        "Launch on second screen",
        "Open on the bottom display",
        Icons.Rounded.Tablet,
        "Bottom screen",
    ),

    /**
     * A standing preference, as opposed to the two above it.
     *
     * Those send this launch somewhere; this decides where every launch goes,
     * including the ones started from search, from a widget, or from couch mode —
     * none of which has a context menu to reach for. It cycles rather than opening
     * a sub-menu, because there are three states and a menu inside a menu is a
     * long way to go for a three-way switch.
     */
    ALWAYS_ON_PANEL(
        "Always open on…",
        "Remember which screen this uses, everywhere",
        Icons.Rounded.PushPin,
        "Always on",
    ),
    ADD_TO_GRID(
        "Add to grid",
        "Give this a cell on the home screen",
        Icons.AutoMirrored.Rounded.AddToHomeScreen,
        "Add to grid",
    ),
    REMOVE_FROM_GRID(
        "Remove from grid",
        "Free the cell; keeps the entry",
        Icons.Rounded.VisibilityOff,
        "Clear cell",
    ),
    MOVE_TO_FOLDER(
        "Move to folder…",
        "File this under another folder",
        Icons.AutoMirrored.Rounded.DriveFileMove,
        "Move…",
    ),
    REMOVE_FROM_FOLDER(
        "Take out of folder",
        "Return this to the home grid",
        Icons.Rounded.FolderOff,
        "Remove",
    ),
    EDIT("Edit…", "Title, artwork, details and emulator", Icons.Rounded.Edit, "Edit…"),

    /**
     * Separate from [EDIT], because it edits a different kind of thing.
     *
     * Everything in the editor is a correction to what was scraped — the title is
     * wrong, the artwork is the Japanese cover, the emulator is the wrong one. A
     * note is not a correction to anything; it is the only field in the library
     * that no provider has an opinion about. Filing it inside a form of scraped
     * values would put it behind two presses and imply it could be overwritten.
     */
    NOTE("Note…", "Write where you got to", Icons.Rounded.EditNote, "Note…"),
    APP_INFO("App info", "Open Android's settings page", Icons.Rounded.Info, "App info"),
    TOGGLE_FAVORITE(
        "Favourite",
        "Keep this at the front of the rail",
        Icons.Rounded.StarOutline,
        "Favourite",
    ),
    HIDE("Hide from grid", "Stays in the library and in search", Icons.Rounded.VisibilityOff, "Hide"),

    /**
     * Offered in place of [HIDE] on an entry that is already hidden.
     *
     * A separate action rather than a re-labelled one because the menu is built
     * from a fixed enum and the label is part of it — and because a toggle whose
     * caption depends on state is exactly the kind of thing that ends up saying
     * "Hide" over an entry that is already hidden.
     */
    UNHIDE("Show on grid", "Put this back on the home screen", Icons.Rounded.Visibility, "Show"),

    UNINSTALL(
        "Uninstall",
        "Removes the app from the device",
        Icons.Rounded.DeleteOutline,
        "Uninstall",
    ),

    /**
     * Removes the entry from the library, as opposed to hiding it.
     *
     * A rescan that still finds the file will bring it back, unhidden — which is
     * the point: this is the way to undo a state an entry has got stuck in, not a
     * way to delete anything from disk. THOR never touches the user's files.
     */
    DELETE(
        "Remove from library",
        "Your files are left untouched",
        Icons.Rounded.DeleteForever,
        // "Remove" belongs to taking something out of a folder, which is the
        // harmless one. This is the destructive member of the pair and does not
        // get to wear the milder word.
        "Forget",
    ),

    /**
     * Hand-picked artwork for a game, as a shortcut.
     *
     * Only the two pictures that are worth changing on their own. The editor
     * holds every slot — cover, icon, backdrop, logo and screenshots — and is
     * where a full correction belongs; these are here because swapping a wrong
     * cover is the single most common fix and should not need a dialog.
     */
    SET_GAME_COVER("Choose cover…", "Replace the tall box art", Icons.Rounded.Image, "Cover…"),
    SET_GAME_BACKDROP(
        "Choose backdrop…",
        "Replace the wide banner",
        Icons.Rounded.Wallpaper,
        "Backdrop…",
    ),

    /**
     * Hands a game's pictures back to the scrapers.
     *
     * Needs its own row because neither the shortcuts nor the editor can express
     * it: emptying five fields one at a time is not the same gesture as "go and
     * find these again", and the editor's own edits now lock artwork against the
     * next scrape, so there has to be a way to unlock it.
     */
    CLEAR_GAME_ARTWORK(
        "Reset artwork",
        "Let the scrapers choose again",
        Icons.Rounded.Restore,
        "Reset art",
    ),

    /**
     * Hand-picked artwork for a platform folder.
     *
     * Offered because the alternative is a scrape, and a scrape cannot be told
     * what a system looks like: these providers index games, so asking one about
     * "Super Nintendo" returns whatever game happens to mention it. The launcher
     * picks a representative image from the folder's own contents as a default,
     * which is reasonable and never what someone with a particular image in mind
     * wanted. Choosing one by hand marks it as the user's, and nothing — not a
     * rescrape, not a newly installed icon pack — overwrites it afterwards.
     */
    SET_PLATFORM_ICON("Choose icon…", "Pick an image for this system", Icons.Rounded.Image, "Icon…"),
    SET_PLATFORM_HERO(
        "Choose backdrop…",
        "Pick the wide banner image",
        Icons.Rounded.Wallpaper,
        "Backdrop…",
    ),
    CLEAR_PLATFORM_ARTWORK(
        "Reset artwork",
        "Back to the pack's own icon",
        Icons.Rounded.Restore,
        "Reset art",
    ),

    DELETE_FOLDER(
        "Delete folder",
        "Its contents return to the grid",
        Icons.Rounded.Delete,
        "Delete folder",
    ),

    /**
     * A widget's size, which nothing else on the grid has.
     *
     * Every other entry is one cell and always will be, so "resize" is not an
     * action the menu has ever needed. A widget is the first thing here whose
     * size is a property of the entry rather than of the grid.
     */
    /**
     * Corrects a scrape that matched the wrong game.
     *
     * The scraper picks the highest-scoring candidate and for most files it is
     * right; where it is not, there was nothing to be done except edit every
     * field by hand, because re-running it made the same decision again. This is
     * the way out.
     */
    CHOOSE_MATCH(
        "Choose match…",
        "Pick the right game from the scrapers",
        Icons.Rounded.ManageSearch,
        "Match…",
    ),

    RESIZE_WIDGET(
        "Resize",
        "D-pad to change its size, A when it looks right",
        Icons.Rounded.OpenWith,
        "Resize",
    ),

    /**
     * Takes a widget off the grid for good.
     *
     * Destructive in a way [REMOVE_FROM_GRID] is not, and worded to say so: an
     * app removed from the grid is still installed and still in the drawer,
     * whereas a widget has nowhere else to be. Its id goes back to the platform
     * and adding it again is a new widget with new settings.
     */
    REMOVE_WIDGET(
        "Remove widget",
        "Takes it off the grid and forgets its settings",
        Icons.Rounded.DeleteForever,
        "Remove",
    ),
}

/**
 * Builds the action list for an entry.
 *
 * The menu is assembled per entry type rather than shown wholesale and greyed
 * out — an "Uninstall" row on a ROM or an "App info" row on a folder is noise
 * that makes the useful rows harder to find.
 */
fun contextActionsFor(
    entry: GridEntry,
    hasSecondScreen: Boolean,
    /** True when opened from the app drawer rather than from the grid. */
    fromDrawer: Boolean = false,
    /** Whether this entry already occupies a grid cell. */
    onGrid: Boolean = true,
    /** Whether any folder exists to move this entry into. */
    foldersExist: Boolean = false,
    /** Whether this entry currently sits inside a folder. */
    inFolder: Boolean = false,
    /** Whether this platform folder already wears hand-picked artwork. */
    hasCustomArtwork: Boolean = false,
): List<ContextAction> = buildList {
    /*
     * A widget answers almost none of the questions this menu asks.
     *
     * It cannot be launched, favourited, hidden, filed into a folder, edited or
     * uninstalled — it is not an entry the library found, it is a view another
     * app draws in a box the user positioned. Offering the full list greyed out
     * would be eleven rows of "no" around the two that work.
     */
    if (entry is WidgetEntry) {
        add(ContextAction.RESIZE_WIDGET)
        add(ContextAction.REMOVE_WIDGET)
        return@buildList
    }

    add(ContextAction.LAUNCH)
    if (entry !is FolderEntry) {
        add(ContextAction.LAUNCH_MAIN_SCREEN)
        if (hasSecondScreen) {
            add(ContextAction.LAUNCH_SECOND_SCREEN)
            // Only where there are two panels to choose between. On one screen a
            // standing preference for a panel is a preference between one thing.
            add(ContextAction.ALWAYS_ON_PANEL)
        }
    }

    // The drawer's whole purpose is choosing what reaches the grid, so that is
    // the action offered there — and only in the direction that applies.
    if (fromDrawer) {
        if (onGrid) add(ContextAction.REMOVE_FROM_GRID) else add(ContextAction.ADD_TO_GRID)
    }

    // Filing only applies to things that can go in a folder, and only when there
    // is a folder to put them in — offering it with none would open an empty
    // picker.
    if (entry !is FolderEntry && !fromDrawer) {
        if (inFolder) {
            add(ContextAction.REMOVE_FROM_FOLDER)
        } else if (foldersExist) {
            add(ContextAction.MOVE_TO_FOLDER)
        }
    }

    add(ContextAction.EDIT)

    /*
     * Artwork, for a game.
     *
     * The scrapers match by title and sometimes match the wrong game, and until
     * now the only recourse was to run one again and hope for a better guess.
     * Reset is offered only when there is something to undo: on a game whose
     * artwork nobody has chosen it would do nothing visible.
     */
    if (entry is GameEntry) {
        // Only games, because "where you got to" is a question a game has and an
        // app or a folder does not.
        add(ContextAction.NOTE)
        add(ContextAction.CHOOSE_MATCH)
        add(ContextAction.SET_GAME_COVER)
        add(ContextAction.SET_GAME_BACKDROP)
        if (hasCustomArtwork) add(ContextAction.CLEAR_GAME_ARTWORK)
    }

    if (entry is AppEntry) add(ContextAction.APP_INFO)
    add(ContextAction.TOGGLE_FAVORITE)
    if (!fromDrawer && entry !is FolderEntry) {
        // The direction that applies. A hidden entry is only reachable at all
        // because "show hidden" is on, and the one thing wanted there is the way
        // back.
        if (entry.isHidden) add(ContextAction.UNHIDE) else add(ContextAction.HIDE)
    }
    /*
     * Artwork, but only for a platform folder.
     *
     * A folder the user made already takes custom artwork through Edit, which
     * writes the folder's own image. A platform folder's artwork belongs to the
     * *platform* — it is the same icon the information panel and the open-folder
     * banner draw — so it is set here and written there, and offering the folder
     * route for it would leave the two disagreeing.
     */
    if (entry is FolderEntry && PlatformFolders.platformIdOf(entry.id) != null) {
        add(ContextAction.SET_PLATFORM_ICON)
        add(ContextAction.SET_PLATFORM_HERO)
        if (hasCustomArtwork) add(ContextAction.CLEAR_PLATFORM_ARTWORK)
    }

    if (entry is AppEntry && !entry.isSystemApp) add(ContextAction.UNINSTALL)
    if (entry !is FolderEntry) add(ContextAction.DELETE)
    if (entry is FolderEntry) add(ContextAction.DELETE_FOLDER)
}

/**
 * The context menu, opened with Y on the highlighted entry.
 *
 * Presented as a centred card rather than a bottom sheet: the bottom of the
 * panel is occupied by the dock, and a sheet sliding up from there would cover
 * it and read as part of it.
 *
 * A grid of tiles rather than a list of rows. A game on the grid offers eleven
 * actions, and as full-width rows carrying an icon tile, a label and a line of
 * description that came to some seven hundred device-independent pixels against
 * a panel about four hundred tall — so the menu scrolled, and the actions past
 * the fold were reachable only by discovering that it did. Two columns halves
 * the height; the tile height is then measured against the space actually
 * available rather than fixed, so the whole set is on screen on the handheld
 * panel and merely generous on a television.
 *
 * What the tiles give up is the per-row description, which is restored where it
 * is actually read: one line under the grid, for whichever tile holds the
 * cursor. Nobody reads eleven descriptions; they read the one they are about to
 * press.
 */
@Composable
fun EntryContextMenu(
    entry: GridEntry?,
    hasSecondScreen: Boolean,
    focusedIndex: Int,
    fromDrawer: Boolean = false,
    onGrid: Boolean = true,
    foldersExist: Boolean = false,
    inFolder: Boolean = false,
    onAction: (ContextAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val motion = ThorTheme.motion

    AnimatedVisibility(
        visible = entry != null,
        enter = fadeIn(motion.tweenSpec(motion.selectionMillis)),
        exit = fadeOut(motion.tweenSpec(motion.selectionMillis)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // `entry` is captured once so the card keeps rendering its content
            // through the exit animation instead of blanking on dismissal.
            val target = entry ?: return@BoxWithConstraints
            val actions = contextActionsFor(
                target, hasSecondScreen, fromDrawer, onGrid, foldersExist, inFolder,
            )
            if (actions.isEmpty()) return@BoxWithConstraints

            val rows = (actions.size + CONTEXT_MENU_COLUMNS - 1) / CONTEXT_MENU_COLUMNS
            /*
             * What is left for the tiles once everything around them is paid for.
             *
             * Measured rather than assumed: this card is raised over the handheld
             * panel and over a television, and the same fixed tile height cannot be
             * right for both. The clamp is what keeps it sane at the extremes — a
             * floor so the tiles do not become unreadable slivers on a short panel,
             * a ceiling so eleven actions on a big screen do not become eleven
             * billboards.
             */
            val chrome = (HEADER_HEIGHT + HEADER_GAP + HINT_HEIGHT + CARD_PADDING * 2).dp +
                TILE_GAP.dp * (rows - 1)
            val budget = maxHeight * CARD_HEIGHT_FRACTION - chrome
            val tileHeight = (budget / rows).coerceIn(TILE_MIN.dp, TILE_MAX.dp)
            // Only when even the floor will not fit, which the handheld does not
            // reach; a card that never scrolls is the point of all of the above.
            val overflows = tileHeight * rows + chrome > maxHeight

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(motion.tweenSpec(motion.selectionMillis), initialScale = 0.92f),
                exit = scaleOut(motion.tweenSpec(motion.selectionMillis)),
            ) {
                GlassSurface(
                    shape = ThorTheme.shapes.large,
                    /*
                     * The surface the side menu uses, which is `GlassSurface`'s
                     * own default.
                     *
                     * This card asked for `surfaceHighest` on the reasoning that
                     * it sits over an already-raised panel. That put it at the
                     * top of the ramp and left nothing above it for the tiles,
                     * which is how they ended up filled with the darkest colour
                     * in the palette. The side menu is the same kind of object —
                     * raised over the grid, holding a list of actions — and it
                     * simply takes the default, so this does too and the two
                     * drawers read as one launcher.
                     */
                    modifier = Modifier
                        // Capped rather than fixed: a flat width had no answer for a
                        // panel narrower than itself — the card would simply have run
                        // off the edge.
                        .fillMaxWidth(CARD_FRACTION)
                        .widthIn(max = CARD_WIDTH.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column(modifier = Modifier.padding(CARD_PADDING.dp)) {
                        ContextHeader(target)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(TILE_GAP.dp),
                            modifier = Modifier
                                .padding(top = HEADER_GAP.dp)
                                .then(
                                    if (overflows) {
                                        Modifier.verticalScroll(rememberScrollState())
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            repeat(rows) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP.dp)) {
                                    repeat(CONTEXT_MENU_COLUMNS) { column ->
                                        // Down the first column, then down the
                                        // second — see [contextMenuRows].
                                        val index = column * rows + row
                                        val action = actions.getOrNull(index)
                                        if (action == null) {
                                            // Holds the column so a ragged last row
                                            // keeps its tiles the width of every other.
                                            Spacer(Modifier.weight(1f))
                                        } else {
                                            ContextTile(
                                                action = action,
                                                entry = target,
                                                focused = index == focusedIndex,
                                                height = tileHeight,
                                                onClick = { onAction(action) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ContextHint(action = actions.getOrNull(focusedIndex), entry = target)
                    }
                }
            }
        }
    }
}

/** The entry this menu is about: its own picture, its name, and what it is. */
@Composable
private fun ContextHeader(entry: GridEntry) {
    val colors = ThorTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(HEADER_HEIGHT.dp),
    ) {
        Box(
            modifier = Modifier
                .size(HEADER_ICON.dp)
                .clip(ThorTheme.shapes.small)
                // The plate a side-menu row puts behind its own icon.
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            ContextEntryIcon(entry)
        }

        Column(
            modifier = Modifier
                .padding(start = HEADER_GAP.dp)
                .weight(1f),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                // Two lines here where the rest of the menu takes one: this is the
                // one string on the card the user came in knowing, and a game whose
                // name is cut at "The Legend of Zelda: Ocarina of…" is the entry
                // they cannot confirm they picked.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The same picture the entry wears on the grid.
 *
 * Deliberately the grid's own choice rather than a fresh one: the user pressed Y
 * on a cell they were looking at, and a menu that opened with a different image
 * at the top would read as being about something else.
 */
@Composable
private fun ContextEntryIcon(entry: GridEntry) {
    val inset = Modifier
        .fillMaxSize()
        .padding(HEADER_ICON_INSET.dp)

    when (entry) {
        is GameEntry -> ArtworkImage(
            model = entry.metadata.artwork.cellImage,
            contentDescription = entry.title,
            fallbackText = entry.title,
            // The cell tints its fallback with the platform's accent; the menu
            // has no platform map to hand and the fallback only shows when there
            // is no artwork at all, so it is not worth threading one through.
            fallbackTint = ThorTheme.colors.primary,
            // Fit rather than crop, as on the cell: box art is rarely square.
            contentScale = ContentScale.Fit,
            modifier = inset,
        )

        is AppEntry -> if (entry.customIconUri != null) {
            ArtworkImage(
                model = entry.customIconUri,
                contentDescription = entry.title,
                fallbackText = entry.title,
                contentScale = ContentScale.Fit,
                modifier = inset,
            )
        } else {
            AppIcon(
                packageName = entry.packageName,
                title = entry.title,
                shape = ThorTheme.shapes.small,
            )
        }

        is FolderEntry -> Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = entry.title,
            tint = ThorTheme.colors.primary,
            modifier = Modifier.size(HEADER_GLYPH.dp),
        )

        // A glyph rather than the widget itself: the live view is on the grid
        // behind this card, and a second copy of it in the header would be an
        // inter-process inflate for a thumbnail.
        is WidgetEntry -> Icon(
            imageVector = Icons.Rounded.Widgets,
            contentDescription = entry.title,
            tint = ThorTheme.colors.primary,
            modifier = Modifier.size(HEADER_GLYPH.dp),
        )

        else -> ArtworkImage(
            model = null,
            contentDescription = entry.title,
            fallbackText = entry.title,
            contentScale = ContentScale.Fit,
            modifier = inset,
        )
    }
}

/**
 * What the tile under the cursor actually does.
 *
 * Height is reserved whether or not there is anything to say, so the card does
 * not change size as the cursor moves across it.
 */
@Composable
private fun ContextHint(action: ContextAction?, entry: GridEntry) {
    val colors = ThorTheme.colors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(HINT_HEIGHT.dp),
    ) {
        if (action == null) return@Row
        Text(
            text = action.labelFor(entry),
            style = MaterialTheme.typography.labelLarge,
            color = if (action.isDestructive) colors.error else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = action.descriptionFor(entry),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = HINT_GAP.dp),
        )
    }
}

@Composable
private fun ContextTile(
    action: ContextAction,
    entry: GridEntry,
    focused: Boolean,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuTile(
        icon = action.iconFor(entry),
        caption = action.shortFor(entry),
        focused = focused,
        height = height,
        destructive = action.isDestructive,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Favouriting is the one action whose caption depends on where it already is.
 *
 * Resolved here rather than in the enum because the enum describes the action
 * and this describes the entry — and a "Favourite" tile over something already
 * favourited is the sort of thing that survives review by being technically the
 * name of the action.
 */
private fun ContextAction.isFavouriteOn(entry: GridEntry): Boolean =
    this == ContextAction.TOGGLE_FAVORITE && entry.isFavorite

private fun ContextAction.labelFor(entry: GridEntry): String =
    if (isFavouriteOn(entry)) "Remove from favourites" else label

private fun ContextAction.shortFor(entry: GridEntry): String =
    if (isFavouriteOn(entry)) "Unfavourite" else short

private fun ContextAction.descriptionFor(entry: GridEntry): String =
    if (isFavouriteOn(entry)) "Stop keeping this at the front" else description

private fun ContextAction.iconFor(entry: GridEntry): ImageVector =
    if (isFavouriteOn(entry)) Icons.Rounded.Star else icon

private val ContextAction.isDestructive: Boolean
    get() = this == ContextAction.UNINSTALL ||
        this == ContextAction.DELETE ||
        this == ContextAction.DELETE_FOLDER ||
        this == ContextAction.REMOVE_WIDGET

/** One-line description shown under the entry's title. */
private fun GridEntry.subtitle(): String = when (this) {
    is GameEntry -> listOfNotNull(
        platformId.uppercase(),
        metadata.developer,
        metadata.releaseYear?.toString(),
    ).joinToString(" · ")

    is AppEntry -> if (isEmulator) "Emulator · $packageName" else packageName
    is FolderEntry -> "Folder · ${childIds.size} items"
    // The multiplication sign, not the letter: this is a size.
    is WidgetEntry -> "Widget · ${spanColumns}×$spanRows"
    else -> ""
}

/**
 * How many tiles stand across the card.
 *
 * Shared with the view model, which moves the cursor: left and right step by
 * one, up and down step by a row, and a layout that knew its own width while
 * the navigation did not is a cursor that appears to jump at random. Fixed
 * rather than derived from the width for the same reason — the two have to
 * agree, and only one of them can measure the screen.
 */
const val CONTEXT_MENU_COLUMNS = 2

/**
 * How many rows the menu stands in.
 *
 * The grid is filled down the first column and then down the second, rather
 * than left to right along each row. That is what keeps a group of related
 * actions together: the list is written with related things next to each other
 * — launch, then on the top screen, then on the bottom — and filling by rows
 * scatters exactly those pairs diagonally across the card, so the two screen
 * targets sat side by side with an unrelated action beneath each. Filling by
 * columns stacks them, and the card reads down one column and then down the
 * next like a page.
 */
fun contextMenuRows(count: Int): Int =
    (count + CONTEXT_MENU_COLUMNS - 1) / CONTEXT_MENU_COLUMNS

/** How many tiles a given column actually holds; the last one is often short. */
private fun columnHeight(column: Int, count: Int): Int {
    val rows = contextMenuRows(count)
    return (count - column * rows).coerceIn(0, rows)
}

/**
 * One tile up or down, wrapping inside the column the cursor is already in.
 *
 * Lives here beside [CONTEXT_MENU_COLUMNS] rather than in the view model
 * because the two have to agree, and this is the one that knows the number.
 */
fun stepContextMenuRow(index: Int, direction: Int, count: Int): Int {
    if (count <= 0) return 0
    val rows = contextMenuRows(count)
    val column = index / rows
    val height = columnHeight(column, count)
    if (height <= 0) return index

    val row = ((index % rows) + direction + height) % height
    return column * rows + row
}

/**
 * One tile left or right, onto the same row of another column.
 *
 * Eleven actions leave the second column one short, so the cell beside the last
 * tile of the first column is not drawn. Stepping onto it would park the cursor
 * on nothing — the highlight disappears and the next press does something the
 * user did not aim at — so an absent cell is stepped over rather than onto.
 */
fun stepContextMenuColumn(index: Int, direction: Int, count: Int): Int {
    if (count <= 0) return 0
    val rows = contextMenuRows(count)
    val row = index % rows
    var column = index / rows

    repeat(CONTEXT_MENU_COLUMNS) {
        column = (column + direction + CONTEXT_MENU_COLUMNS) % CONTEXT_MENU_COLUMNS
        val candidate = column * rows + row
        if (candidate < count) return candidate
    }
    return index
}

/**
 * Wider than the single-column menu it replaces, because it now holds two.
 */
private const val CARD_WIDTH = 460

/** Leaves the grid showing at the edges, so the card reads as sitting over it. */
private const val CARD_FRACTION = 0.88f

/** The same, vertically: the card is not allowed to reach the panel's edges. */
private const val CARD_HEIGHT_FRACTION = 0.94f
private const val CARD_PADDING = 12

private const val HEADER_HEIGHT = 56
private const val HEADER_ICON = 48
private const val HEADER_ICON_INSET = 4
private const val HEADER_GLYPH = 26
private const val HEADER_GAP = 10

/** Reserved whether or not a tile is focused, so the card never resizes. */
private const val HINT_HEIGHT = 26
private const val HINT_GAP = 8

/** The gap between tiles; the same number that sets the gap inside one. */
private const val TILE_GAP = MENU_TILE_GAP
private const val TILE_MIN = MENU_TILE_MIN
private const val TILE_MAX = MENU_TILE_MAX
