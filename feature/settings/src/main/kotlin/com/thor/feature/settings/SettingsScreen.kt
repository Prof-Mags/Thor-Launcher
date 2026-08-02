package com.thor.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thor.core.designsystem.component.GlassSurface
import com.thor.core.designsystem.modifier.SurfaceLevel
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.AddPlatformDialog
import com.thor.feature.settings.component.LocalRowActivation
import com.thor.feature.settings.component.LocalHorizontalRowRegistration
import com.thor.feature.settings.component.LocalRowStep
import com.thor.feature.settings.component.SettingsTextButton
import com.thor.feature.settings.component.revealWhenFocused
import com.thor.feature.settings.pane.ABOUT_ROWS
import com.thor.feature.settings.pane.AboutPane
import com.thor.feature.settings.pane.SettingsPageContent
import com.thor.feature.settings.pane.rowCountFor

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
    val availablePlatforms by viewModel.availablePlatforms.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val scrapeState by viewModel.scrapeState.collectAsStateWithLifecycle()
    val focusOnRail by viewModel.focusOnRail.collectAsStateWithLifecycle()
    val pendingPlatform by viewModel.pendingPlatform.collectAsStateWithLifecycle()
    val activationTick by viewModel.activationTick.collectAsStateWithLifecycle()
    val horizontalStep by viewModel.horizontalStep.collectAsStateWithLifecycle()
    val providerStatus by viewModel.providerStatus.collectAsStateWithLifecycle()
    val checkingProviders by viewModel.checkingProviders.collectAsStateWithLifecycle()
    val artworkOnlyProviders by viewModel.artworkOnlyProviders.collectAsStateWithLifecycle()
    val keyCaptureEnabled by viewModel.keyCaptureEnabled.collectAsStateWithLifecycle()
    val capturedKeys by viewModel.capturedKeys.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
    val iconPackStatus by viewModel.iconPackStatus.collectAsStateWithLifecycle()
    val pointerServiceEnabled by viewModel.pointerServiceEnabled.collectAsStateWithLifecycle()
    val pointerRunning by viewModel.pointerRunning.collectAsStateWithLifecycle()
    val notificationGranted by viewModel.notificationAccessGranted.collectAsStateWithLifecycle()
    val notificationConnected by
        viewModel.notificationServiceConnected.collectAsStateWithLifecycle()
    val debridStatus by viewModel.debridStatus.collectAsStateWithLifecycle()
    val gridClearResult by viewModel.gridClearResult.collectAsStateWithLifecycle()
    val indexerStatus by viewModel.indexerStatus.collectAsStateWithLifecycle()
    val addonStatus by viewModel.addonStatus.collectAsStateWithLifecycle()
    val pendingPlatformEmulators = pendingPlatform
        ?.let(viewModel::installedEmulatorsFor)
        .orEmpty()

    val pages = SettingsPage.forCategory(category)
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
        if (openPage == SettingsPage.NOTIFICATIONS) viewModel.refreshNotificationAccess()
    }

    // Derived as one value so dynamic pages update their controller bounds as
    // soon as a platform, folder, wallpaper, addon, indexer, or icon pack changes.
    val visibleRowCount = when {
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

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(colors.surfaceElevated, colors.background),
                    ),
                )
                .padding(dimens.spacingSmall),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
        ) {
            // ---- Category rail ---------------------------------------------
            GlassSurface(
                modifier = Modifier
                    .width(RAIL_WIDTH.dp)
                    .fillMaxHeight(),
                shape = ThorTheme.shapes.large,
                color = colors.surface,
                alphaOverride = 0.92f,
                level = SurfaceLevel.RAISED,
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
                Text(
                    text = "Loki",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.cursor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = dimens.spacingLarge),
                )
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
                SettingsCategory.navigationEntries.forEach { entry ->
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        CategoryRow(
                            category = entry,
                            selected = entry == category,
                            // The cursor ring only shows while the rail holds
                            // input, so it is obvious which column presses move in.
                            cursorHere = entry == category && focusOnRail && openPage == null,
                            onClick = { viewModel.selectCategory(entry) },
                        )
                    }
                }
                }
            }

            // ---- Detail ----------------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(ThorTheme.shapes.large)
                    .background(colors.surface.copy(alpha = 0.38f)),
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
                                focusedRow = focusedRow.takeIf { pendingPlatform == null } ?: -1,
                                viewModel = viewModel,
                                platformOptions = platformOptions,
                                availablePlatforms = availablePlatforms,
                                scanState = scanState,
                                scrapeState = scrapeState,
                                providerStatus = providerStatus,
                                checkingProviders = checkingProviders,
                                artworkOnlyProviders = artworkOnlyProviders,
                                keyCaptureEnabled = keyCaptureEnabled,
                                capturedKeys = capturedKeys,
                                isDefaultLauncher = isDefaultLauncher,
                                iconPacks = iconPacks,
                                iconPackStatus = iconPackStatus,
                                pointerServiceEnabled = pointerServiceEnabled,
                                pointerRunning = pointerRunning,
                                notificationAccessGranted = notificationGranted,
                                notificationServiceConnected = notificationConnected,
                                debridStatus = debridStatus,
                                gridClearResult = gridClearResult,
                                indexerStatus = indexerStatus,
                                addonStatus = addonStatus,
                            )
                        }

                        else -> pages.forEachIndexed { index, page ->
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
            .background(colors.surface.copy(alpha = 0.48f))
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

/** A row that opens a settings page. */
@Composable
private fun PageNavRow(
    page: SettingsPage,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .revealWhenFocused(focused)
            .clip(ThorTheme.shapes.panel)
            .background(
                if (focused) colors.surfaceHighest else colors.surface.copy(alpha = 0.58f),
            )
            .thorCursor(focused = focused, cornerRadius = dimens.cornerRadiusSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
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
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spacingSmall, vertical = 3.dp)
            .revealWhenFocused(cursorHere)
            .clip(ThorTheme.shapes.panel)
            .background(
                if (selected) colors.surfaceHighest else Color.Transparent,
            )
            .thorCursor(focused = cursorHere, cornerRadius = dimens.cornerRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(ThorTheme.shapes.pill)
                    .background(Brush.verticalGradient(colors.accentStops)),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
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
                modifier = Modifier.size(19.dp),
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

private const val RAIL_WIDTH = 256
private const val CONTENT_MAX_WIDTH = 820
