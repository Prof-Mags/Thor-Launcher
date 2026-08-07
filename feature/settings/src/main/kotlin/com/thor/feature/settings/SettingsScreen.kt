package com.thor.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.data.sync.ScrapeState
import com.thor.feature.settings.component.AddPlatformDialog
import com.thor.feature.settings.component.EmulatorPickerDialog
import com.thor.feature.settings.component.row.LocalRowActivation
import com.thor.feature.settings.component.row.LocalHorizontalRowRegistration
import com.thor.feature.settings.component.row.LocalRowStep
import com.thor.feature.settings.component.SettingsTextButton
import com.thor.feature.settings.component.revealWhenFocused
import com.thor.feature.settings.page.ABOUT_ROWS
import com.thor.feature.settings.page.AboutPane
import com.thor.feature.settings.page.SettingsPageContent
import com.thor.feature.settings.page.rowCountFor

/**
 * The settings overlay.
 *
 * Three columns of navigation depth would be too much for a handheld, and one
 * flat page per category was too little — Appearance alone ran to thirty rows.
 * This is two levels: a category rail, and within a category a short list of
 * pages that each open onto their own controls.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    /** Uses the shared couch shell instead of presenting as a standalone overlay. */
    couchMode: Boolean = false,
    /**
     * Reports how many focusable rows the visible surface has, so the host can
     * clamp controller navigation. Only this screen knows what it rendered.
     */
    onRowCountChanged: (Int) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val openPage by viewModel.openPage.collectAsStateWithLifecycle()
    val focusedRow by viewModel.focusedRow.collectAsStateWithLifecycle()
    val platformOptions by viewModel.platformOptions.collectAsStateWithLifecycle()
    val achievementSync by viewModel.achievementSync.collectAsStateWithLifecycle()
    val retroAchievementsStatus by viewModel.retroAchievementsStatus.collectAsStateWithLifecycle()
    val checkingRetroAchievements by
        viewModel.checkingRetroAchievements.collectAsStateWithLifecycle()
    val availablePlatforms by viewModel.availablePlatforms.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scrapeState by viewModel.scrapeState.collectAsStateWithLifecycle()
    val focusOnRail by viewModel.focusOnRail.collectAsStateWithLifecycle()
    val pendingPlatform by viewModel.pendingPlatform.collectAsStateWithLifecycle()
    val emulatorPicker by viewModel.emulatorPicker.collectAsStateWithLifecycle()
    val activationTick by viewModel.activationTick.collectAsStateWithLifecycle()
    val horizontalStep by viewModel.horizontalStep.collectAsStateWithLifecycle()
    val providerStatus by viewModel.providerStatus.collectAsStateWithLifecycle()
    val checkingProviders by viewModel.checkingProviders.collectAsStateWithLifecycle()
    val artworkOnlyProviders by viewModel.artworkOnlyProviders.collectAsStateWithLifecycle()
    val noScreenshotProvider by viewModel.noScreenshotProvider.collectAsStateWithLifecycle()
    val screenScraperKeyMissing by viewModel.screenScraperKeyMissing.collectAsStateWithLifecycle()
    val keyCaptureEnabled by viewModel.keyCaptureEnabled.collectAsStateWithLifecycle()
    val capturedKeys by viewModel.capturedKeys.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
    val iconPackStatus by viewModel.iconPackStatus.collectAsStateWithLifecycle()
    val pointerServiceEnabled by viewModel.pointerServiceEnabled.collectAsStateWithLifecycle()
    val pointerRunning by viewModel.pointerRunning.collectAsStateWithLifecycle()
    val debridStatus by viewModel.debridStatus.collectAsStateWithLifecycle()
    val gridClearResult by viewModel.gridClearResult.collectAsStateWithLifecycle()
    val indexerStatus by viewModel.indexerStatus.collectAsStateWithLifecycle()
    val addonStatus by viewModel.addonStatus.collectAsStateWithLifecycle()
    val extensionStatus by viewModel.extensionStatus.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val profileRegistry by viewModel.profiles.collectAsStateWithLifecycle()
    val editingThemeId by viewModel.editingThemeId.collectAsStateWithLifecycle()
    val themeStatus by viewModel.themeStatus.collectAsStateWithLifecycle()
    val smartFolders by viewModel.smartFolders.collectAsStateWithLifecycle()
    val editingSmartFolderId by viewModel.editingSmartFolderId.collectAsStateWithLifecycle()
    val smartFolderStatus by viewModel.smartFolderStatus.collectAsStateWithLifecycle()
    val editingProfileId by viewModel.editingProfileId.collectAsStateWithLifecycle()
    val awaitingBindingFor by viewModel.awaitingBindingFor.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val restartRequired by viewModel.restartRequired.collectAsStateWithLifecycle()

    // Recomputed with the registry: the picture row adds a "Remove" row beneath
    // it, and a row count that misses it leaves the last row unreachable.
    val activeProfileHasAvatar = profileRegistry.active?.let(viewModel::avatarPathFor) != null
    val pendingPlatformEmulators = pendingPlatform
        ?.let(viewModel::installedEmulatorsFor)
        .orEmpty()

    val enabledExtensions = settings.enabledExtensions
    val pages = SettingsPage.forCategory(category, enabledExtensions)
    val horizontalRowRegistration: (Boolean) -> Unit = remember(viewModel, focusedRow, openPage) {
        { takesHorizontal ->
            if (openPage != null) {
                viewModel.setRowTakesHorizontal(focusedRow, takesHorizontal)
            }
        }
    }

    // Re-read on arriving where the answer is shown: both are changed in a system
    // screen, so they can only have changed while the launcher was paused.
    LaunchedEffect(openPage, category) {
        if (category == SettingsCategory.ABOUT) viewModel.refreshDefaultLauncher()
    }

    // Derived as one value so dynamic pages update their controller bounds as
    // soon as a platform, folder, wallpaper, addon, indexer, or icon pack changes.
    val visibleRowCount = when {
        // Open dialogs own the cursor; the page beneath must not move under them.
        emulatorPicker.visible -> emulatorPicker.rowCount
        pendingPlatform != null -> if (pendingPlatformEmulators.isEmpty()) 4 else 5
        openPage != null -> rowCountFor(
            page = openPage!!,
            platformCount = platformOptions.size,
            iconPackCount = iconPacks.size,
            mediaSettings = settings.media,
            wallpaperClearRows = listOfNotNull(
                settings.personalization.wallpaperUri,
                settings.personalization.topScreenWallpaperUri,
            ).size,
            extraRomFolderCount = settings.library.romDirectoryUris.count {
                it.platformId == null
            },
            profileRegistry = profileRegistry,
            activeProfileHasAvatar = activeProfileHasAvatar,
            customProfileCount = settings.controls.customProfiles.size,
            editingProfile = settings.controls.customProfiles.any { it.id == editingProfileId },
            smartFolderCount = smartFolders.size,
            // Same guard as the theme editor: deleting the open folder leaves its
            // id behind for a frame, and counting the long page over rows that are
            // no longer drawn strands the cursor past the end.
            editingSmartFolder = smartFolders.any { it.id == editingSmartFolderId },
            customThemeCount = settings.personalization.customThemes.size,
            // Only counts as open if the theme it names still exists — deleting the
            // open theme leaves the id behind for a frame, and a count for the long
            // page over rows that are no longer drawn strands the cursor past the end.
            editingTheme = settings.personalization.customThemes.any {
                it.id == editingThemeId
            },
        )
        // About is a pane rather than a list of pages, and it now carries the
        // diagnostics controls, so it has rows of its own to walk.
        category == SettingsCategory.ABOUT -> ABOUT_ROWS
        else -> pages.size
    }
    LaunchedEffect(visibleRowCount) {
        viewModel.clampFocusedRow(visibleRowCount)
        onRowCountChanged(visibleRowCount)
    }

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val screenBackground = if (couchMode) {
        Modifier.background(colors.background)
    } else {
        Modifier.background(
            Brush.horizontalGradient(
                colors = listOf(colors.surfaceElevated, colors.background),
            ),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(screenBackground)
                .padding(if (couchMode) dimens.spacing else dimens.spacingSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            // ---- Category rail ---------------------------------------------
            GlassSurface(
                modifier = Modifier
                    .width(
                        CARD_RAIL_WIDTH.dp,
                    )
                    .fillMaxHeight(),
                shape =                     ThorTheme.shapes.large,
                color = colors.surface,
                alphaOverride = 0.92f,
                level = SurfaceLevel.RAISED,
                bordered = true,
            ) {
                /*
                 * Every category on screen at once, without a scroll.
                 *
                 * It used to scroll, and scrolling a navigation rail is the wrong
                 * shape of control: the list is short, fixed and known in advance,
                 * so a category below the fold was one the user had no reason to
                 * believe existed. `revealWhenFocused` kept it *reachable* by the
                 * controller, which is not the same as visible.
                 *
                 * The rows share the space left under the heading instead, so the
                 * rail fits whatever the list happens to hold — adding a category
                 * makes each one shorter rather than pushing the last one out of
                 * sight.
                 */
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = dimens.spacing),
                ) {
                if (!couchMode) {
                    Text(
                        text = "Loki",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cursor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = dimens.spacingLarge),
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    modifier = Modifier.padding(
                        start = dimens.spacingLarge,
                        end = dimens.spacingLarge,
                        bottom = 2.dp,
                    ),
                )
                Text(
                    text = "Shape your launcher",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = dimens.spacingLarge,
                        end = dimens.spacingLarge,
                        bottom = dimens.spacing,
                    ),
                )
                /*
                 * Measured once, for all of the rows.
                 *
                 * Sharing the leftover space keeps every category visible, but it
                 * only works while a row can still be drawn in its share. Past
                 * that the share simply clips the row, which is what a tenth
                 * category did: icon tiles cut off and titles half-height. So the
                 * row has a compact form, and the rail picks it when there is not
                 * room for the comfortable one — losing the summary line, which is
                 * a description of a category whose name is right above it, rather
                 * than losing the top of every name.
                 */
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val entries = SettingsCategory.navigationEntries(enabledExtensions)
                    val perRow = if (entries.isEmpty()) maxHeight else maxHeight / entries.size
                    val compact = perRow < COMFORTABLE_CATEGORY_ROW.dp

                    Column(modifier = Modifier.fillMaxSize()) {
                        entries.forEach { entry ->
                            /*
                             * The rows share the rail rather than taking their
                             * natural height and leaving the remainder.
                             *
                             * `fill = false` was the reason for the gap at the
                             * bottom: it lets a row be *at most* its share and
                             * settle for less, so eight rows drawn at the height
                             * their content wanted left everything below them
                             * empty. Filling divides the rail exactly, whatever
                             * the count, which is the same answer as making the
                             * rows bigger, arrived at once instead of re-tuned
                             * every time a category is added or removed.
                             *
                             * Capped, because a television in couch mode has far
                             * more height than eight rows should spend, and a
                             * rail of enormous bars is its own kind of wrong.
                             */
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .heightIn(max = MAX_CATEGORY_ROW.dp),
                            ) {
                                CategoryRow(
                                    category = entry,
                                    selected = entry == category,
                                    compact = compact,
                                    // The cursor ring only shows while the rail holds
                                    // input, so it is obvious which column presses move in.
                                    cursorHere = entry == category && focusOnRail &&
                                        openPage == null,
                                    onClick = { viewModel.selectCategory(entry) },
                                )
                            }
                        }
                    }
                }
                }
            }

            // ---- Detail ----------------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(
                                                    ThorTheme.shapes.large,
                    )
                    .background(
                        colors.surface.copy(alpha = 0.38f),
                    ),
            ) {
                DetailHeader(
                    title = openPage?.title ?: category.title,
                    subtitle = openPage?.summary ?: category.summary,
                    showBack = openPage != null,
                )

                Column(
                    // Capped width: settings rows read badly when a label sits a
                    // full panel-width away from the control it belongs to.
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .align(Alignment.CenterHorizontally)
                        .padding(
                            start = dimens.spacingLarge,
                            end = dimens.spacingLarge,
                            bottom = dimens.spacingLarge,
                        )
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        /*
                         * About is a pane rather than a page, and still needs the
                         * activation broadcast.
                         *
                         * A row learns that Confirm was pressed by watching
                         * `LocalRowActivation` — the shell bumps a counter and
                         * whichever row is focused acts on it. Only the page
                         * branch below used to provide it, which was fine while
                         * About held nothing but text. It holds the diagnostics
                         * controls now, and without this every one of them ignored
                         * the controller: pressing A on "Replay the walkthrough"
                         * did nothing at all, and neither did reset.
                         */
                        category == SettingsCategory.ABOUT -> CompositionLocalProvider(
                            LocalRowActivation provides activationTick,
                            LocalRowStep provides horizontalStep,
                            LocalHorizontalRowRegistration provides horizontalRowRegistration,
                        ) {
                            AboutPane(
                                settings = settings,
                                focusedRow = focusedRow,
                                viewModel = viewModel,
                                isDefaultLauncher = isDefaultLauncher,
                                keyCaptureEnabled = keyCaptureEnabled,
                                capturedKeys = capturedKeys,
                                platformCount = platformOptions.size,
                            )
                        }

                        openPage != null -> CompositionLocalProvider(
                            LocalRowActivation provides activationTick,
                            LocalRowStep provides horizontalStep,
                            LocalHorizontalRowRegistration provides horizontalRowRegistration,
                        ) {
                            SettingsPageContent(
                                page = openPage!!,
                                settings = settings,
                                focusedRow = focusedRow
                                    .takeIf { pendingPlatform == null && !emulatorPicker.visible }
                                    ?: -1,
                                viewModel = viewModel,
                                platformOptions = platformOptions,
                                availablePlatforms = availablePlatforms,
                                scanState = scanState,
                                scrapeState = scrapeState,
                                providerStatus = providerStatus,
                                checkingProviders = checkingProviders,
                                achievementSync = achievementSync,
                                retroAchievementsStatus = retroAchievementsStatus,
                                checkingRetroAchievements = checkingRetroAchievements,
                                artworkOnlyProviders = artworkOnlyProviders,
                                noScreenshotProvider = noScreenshotProvider,
                                screenScraperKeyMissing = screenScraperKeyMissing,
                                keyCaptureEnabled = keyCaptureEnabled,
                                capturedKeys = capturedKeys,
                                isDefaultLauncher = isDefaultLauncher,
                                iconPacks = iconPacks,
                                iconPackStatus = iconPackStatus,
                                pointerServiceEnabled = pointerServiceEnabled,
                                pointerRunning = pointerRunning,
                                debridStatus = debridStatus,
                                gridClearResult = gridClearResult,
                                indexerStatus = indexerStatus,
                                addonStatus = addonStatus,
                                extensionStatus = extensionStatus,
                                importStatus = importStatus,
                                profileRegistry = profileRegistry,
                                editingThemeId = editingThemeId,
                                themeStatus = themeStatus,
                                smartFolders = smartFolders,
                                editingSmartFolderId = editingSmartFolderId,
                                smartFolderStatus = smartFolderStatus,
                                editingProfileId = editingProfileId,
                                awaitingBindingFor = awaitingBindingFor,
                                backupStatus = backupStatus,
                                restartRequired = restartRequired,
                            )
                        }

                        else -> pages.forEachIndexed { index, page ->
                            /*
                             * A heading whenever the group changes, and never a
                             * focusable one.
                             *
                             * Drawn between the rows rather than as one of them,
                             * which is what lets the cursor keep stepping page to
                             * page and the row count keep being the page count —
                             * a focusable heading would have meant renumbering
                             * every row index in this screen for a label nobody
                             * can press.
                             */
                            val previous = pages.getOrNull(index - 1)?.group
                            if (page.group != null && page.group != previous) {
                                PageGroupHeading(title = page.group)
                            }
                            PageNavRow(
                                page = page,
                                focused = !focusOnRail && focusedRow == index,
                                onClick = { viewModel.openPage(page) },
                            )
                        }
                    }
                }
            }
        }

        (scrapeState as? ScrapeState.Running)?.let { running ->
            val platformName = running.platformId?.let { id ->
                platformOptions.firstOrNull { it.platform.id == id }?.platform?.name
            }
            ScrapeProgressOverlay(
                state = running,
                platformName = platformName,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimens.spacingLarge),
            )
        }

        /*
         * Inside the provider, because Confirm arrives through it.
         *
         * A press does not reach a composable here as an event: the shell bumps
         * `LocalRowActivation` and whichever row is focused reacts. A dialog
         * composed outside that provider reads the default value, which never
         * changes — so its rows were unreachable by the controller while
         * answering touch perfectly, which is exactly how this was reported.
         */
        CompositionLocalProvider(LocalRowActivation provides activationTick) {
            EmulatorPickerDialog(
                state = emulatorPicker.copy(focusedIndex = focusedRow.coerceAtLeast(0)),
                onToggle = { packageName ->
                    viewModel.toggleEmulatorFor(emulatorPicker.platformId, packageName)
                },
                onDismiss = viewModel::closeEmulatorPicker,
            )
        }

        // Above everything so it is not clipped by the detail scroll container.
        pendingPlatform?.let { platform ->
            CompositionLocalProvider(
                LocalRowActivation provides activationTick,
                LocalRowStep provides horizontalStep,
                LocalHorizontalRowRegistration provides horizontalRowRegistration,
            ) {
                AddPlatformDialog(
                    platform = platform,
                    installedEmulators = pendingPlatformEmulators,
                    focusedRow = focusedRow,
                    onConfirm = { setup ->
                        viewModel.confirmAddPlatform(
                            platform = platform,
                            romDirectoryUri = setup.romDirectoryUri,
                            romDirectoryName = setup.romDirectoryName,
                            emulatorPackage = setup.emulatorPackage,
                            scanSubfolders = setup.scanSubfolders,
                        )
                    },
                    onDismiss = viewModel::cancelAddPlatform,
                )
            }
        }
    }
}

/** Persistent progress that remains visible while moving between settings pages. */
@Composable
private fun ScrapeProgressOverlay(
    state: ScrapeState.Running,
    platformName: String?,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val progress = if (state.total > 0) {
        (state.done.toFloat() / state.total).coerceIn(0f, 1f)
    } else {
        0f
    }
    GlassSurface(
        modifier = modifier.width(330.dp),
        shape = ThorTheme.shapes.panel,
        color = colors.surfaceElevated,
        alphaOverride = .96f,
        level = SurfaceLevel.RAISED,
        bordered = true,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = platformName?.let { "SCRAPING $it" } ?: "SCRAPING METADATA",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cursor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.done} / ${state.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = state.currentTitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(colors.surfaceHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(colors.accentStops)),
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    showBack: Boolean,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                colors.surface.copy(alpha = 0.48f),
            )
            .padding(
                start = dimens.spacingLarge,
                end = dimens.spacingLarge,
                top = dimens.spacing,
                bottom = dimens.spacingSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
                modifier = Modifier
                    .padding(end = dimens.spacingSmall)
                    .width(4.dp)
                    .height(42.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Brush.verticalGradient(colors.accentStops)),
            )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (showBack) "SETTING PAGE" else "SETTINGS CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cursor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A heading over a run of pages that answer the same question.
 *
 * Quiet on purpose: it is a signpost, not an entry. Anything with a surface under
 * it or a cursor colour on it would read as a row the cursor had skipped, which is
 * worse than no heading at all — the one thing a heading must not do on a
 * D-pad-driven list is look pressable.
 */
@Composable
private fun PageGroupHeading(title: String) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = colors.onSurfaceVariant.copy(alpha = HEADING_ALPHA),
        modifier = Modifier.padding(
            start = 4.dp,
            // More above than below, so it belongs to what follows it rather than
            // floating between two groups.
            top = dimens.spacing,
            bottom = dimens.spacingTiny,
        ),
    )
}

/** A row that opens a settings page. */
@Composable
private fun PageNavRow(
    page: SettingsPage,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.panel

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .revealWhenFocused(focused)
            .clip(shape)
            .background(
                if (focused) colors.surfaceHighest else colors.surface.copy(alpha = 0.58f),
            )
            .thorCursor(focused = focused, shape = shape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = 14.dp,
                vertical = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacing),
    ) {
                    Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(ThorTheme.shapes.small)
                    .background(colors.cursor.copy(alpha = if (focused) 0.22f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.category.icon,
                    contentDescription = null,
                    tint = colors.cursor,
                    modifier = Modifier.size(21.dp),
                )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            Text(
                text = page.summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
                    SettingsTextButton(
                label = "OPEN",
                containerColor = colors.cursor.copy(alpha = if (focused) 0.16f else 0.08f),
                contentColor = if (focused) colors.cursor else colors.onSurfaceVariant,
                borderColor = colors.cursor.copy(alpha = if (focused) 0.48f else 0.18f),
                trailingIcon = Icons.Rounded.ChevronRight,
            )
    }
}

/**
 * A rail entry.
 *
 * The selected item is a filled pill rather than the grid's cursor ring: the
 * rail is a persistent list where exactly one row is always active, and a
 * glowing ring on a permanently-selected row reads as an error state.
 */
@Composable
private fun CategoryRow(
    category: SettingsCategory,
    selected: Boolean,
    cursorHere: Boolean,
    onClick: () -> Unit,
    /** Drops the summary and tightens the tile, for a rail with no room to spare. */
    compact: Boolean = false,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val shape = ThorTheme.shapes.panel

    Row(
        modifier = Modifier
            // Fills the slot it was given rather than wrapping its content, so a
            // row that shares a tall rail is a tall row, background and cursor ring
            // included, instead of a short one floating at the top of its share.
            .fillMaxSize()
            .padding(
                horizontal = dimens.spacingSmall,
                vertical = if (compact) 1.dp else 4.dp,
            )
            .revealWhenFocused(cursorHere)
            .clip(shape)
            .background(
                if (selected) colors.surfaceHighest else Color.Transparent,
            )
            .thorCursor(focused = cursorHere, shape = shape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = dimens.spacingSmall,
                vertical = if (compact) 5.dp else 13.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (compact) 22.dp else 38.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Brush.verticalGradient(colors.accentStops)),
            )
        }
                    Box(
                modifier = Modifier
                    .size(if (compact) 26.dp else 44.dp)
                    .clip(ThorTheme.shapes.small)
                    .background(
                        if (selected) colors.cursor.copy(alpha = 0.16f) else colors.surfaceElevated,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = if (selected) colors.cursor else colors.onSurfaceVariant,
                    modifier = Modifier.size(if (compact) 15.dp else 23.dp),
                )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    text = category.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Beyond this a row is a bar rather than a row; couch mode would reach it. */
private const val MAX_CATEGORY_ROW = 88

/**
 * Height a category row needs for its icon tile and both lines of text.
 *
 * Below this the rail switches every row to the compact form rather than
 * clipping them all. Raised with the row itself: folding Home screen and Artwork
 * back into their parents took the rail from ten entries to eight, and the point
 * of doing that was to spend the space on the rows that remain.
 */
private const val COMFORTABLE_CATEGORY_ROW = 72

private const val CARD_RAIL_WIDTH = 256
private const val CONSOLE_RAIL_WIDTH = 218
private const val INDEX_RAIL_WIDTH = 286
private const val CONTENT_MAX_WIDTH = 820

/** Faint enough to read as a label rather than as a row that was skipped. */
private const val HEADING_ALPHA = 0.7f
