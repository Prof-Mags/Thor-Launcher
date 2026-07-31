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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thor.core.designsystem.modifier.thorCursor
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.feature.settings.component.AddPlatformDialog
import com.thor.feature.settings.component.LocalRowActivation
import com.thor.feature.settings.component.LocalRowStep
import com.thor.feature.settings.component.revealWhenFocused
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
    onDismiss: () -> Unit,
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
    val debridStatus by viewModel.debridStatus.collectAsStateWithLifecycle()

    val pages = SettingsPage.forCategory(category)

    // Re-read on entering the Diagnostics page: the user changes this in the
    // system chooser, so the answer can only have changed while we were paused.
    LaunchedEffect(openPage) {
        if (openPage == SettingsPage.DIAGNOSTICS) viewModel.refreshDefaultLauncher()
    }

    // The focusable row count depends on which level is showing: a category
    // shows one row per page, a page shows its own controls.
    LaunchedEffect(category, openPage, platformOptions.size, iconPacks.size) {
        onRowCountChanged(
            when {
                openPage != null -> rowCountFor(
                    page = openPage!!,
                    platformCount = platformOptions.size,
                    iconPackCount = iconPacks.size,
                    indexerCount = settings.media.indexers.size,
                )
                category == SettingsCategory.ABOUT -> 0
                else -> pages.size
            },
        )
    }

    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(colors.background)) {
            // ---- Category rail ---------------------------------------------
            Column(
                modifier = Modifier
                    .width(RAIL_WIDTH.dp)
                    .fillMaxHeight()
                    // A faint tint rather than a filled panel: the rail should
                    // read as part of the same surface, not a second window.
                    .background(colors.surface.copy(alpha = 0.35f))
                    .padding(vertical = dimens.spacingLarge),
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                    modifier = Modifier.padding(
                        start = dimens.spacingLarge,
                        bottom = dimens.spacingLarge,
                    ),
                )
                SettingsCategory.entries.forEach { entry ->
                    CategoryRow(
                        category = entry,
                        selected = entry == category,
                        // The cursor ring only shows while the rail holds input,
                        // so it is obvious which column presses are moving in.
                        cursorHere = entry == category && focusOnRail && openPage == null,
                        onClick = { viewModel.selectCategory(entry) },
                    )
                }
            }

            // ---- Detail ----------------------------------------------------
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DetailHeader(
                    title = openPage?.title ?: category.title,
                    subtitle = openPage?.summary ?: category.summary,
                    showBack = openPage != null,
                    onBack = viewModel::closePage,
                    onDismiss = onDismiss,
                )

                Column(
                    // Capped width: settings rows read badly when a label sits a
                    // full panel-width away from the control it belongs to.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimens.spacingLarge)
                        .widthIn(max = CONTENT_MAX_WIDTH.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        category == SettingsCategory.ABOUT -> AboutPane(settings = settings)

                        openPage != null -> CompositionLocalProvider(
                            LocalRowActivation provides activationTick,
                            LocalRowStep provides horizontalStep,
                        ) {
                            SettingsPageContent(
                                page = openPage!!,
                                settings = settings,
                                focusedRow = focusedRow,
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
                                debridStatus = debridStatus,
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
            AddPlatformDialog(
                platform = platform,
                installedEmulators = viewModel.installedEmulatorsFor(platform),
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

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (showBack) dimens.spacingSmall else dimens.spacingLarge,
                end = dimens.spacingSmall,
                top = dimens.spacing,
                bottom = dimens.spacingSmall,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onSurface,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
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
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close settings",
                tint = colors.onSurface,
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
            .clip(RoundedCornerShape(dimens.cornerRadiusSmall))
            .revealWhenFocused(focused)
            .thorCursor(focused = focused, cornerRadius = dimens.cornerRadiusSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
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
            .padding(horizontal = dimens.spacingSmall, vertical = 2.dp)
            .clip(RoundedCornerShape(dimens.cornerRadius))
            .background(if (selected) colors.cursor.copy(alpha = 0.16f) else Color.Transparent)
            .thorCursor(focused = cursorHere, cornerRadius = dimens.cornerRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (selected) colors.cursor else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = category.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

private const val RAIL_WIDTH = 220
private const val CONTENT_MAX_WIDTH = 720
