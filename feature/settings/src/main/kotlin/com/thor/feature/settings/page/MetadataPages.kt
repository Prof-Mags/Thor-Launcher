package com.thor.feature.settings.page

import androidx.compose.runtime.Composable
import com.thor.core.model.MetadataSettings
import com.thor.core.model.ThorSettings
import com.thor.data.metadata.ProviderStatus
import com.thor.data.sync.ScrapeState
import com.thor.feature.settings.component.row.ActionRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.TextFieldRow
import com.thor.feature.settings.SettingsViewModel

@Composable
internal fun MetadataPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    scrapeState: ScrapeState,
    providerStatus: Map<String, ProviderStatus>,
    checking: Boolean,
    artworkOnly: Boolean,
    noScreenshots: Boolean,
    screenScraperKeyMissing: Boolean,
) {
    val metadata = settings.metadata

    ActionRow(
        title = "Download metadata",
        subtitle = when (scrapeState) {
            is ScrapeState.Running ->
                "${scrapeState.done} of ${scrapeState.total} — ${scrapeState.currentTitle}"

            is ScrapeState.Completed ->
                "Updated ${scrapeState.updated}, skipped ${scrapeState.skipped}"

            is ScrapeState.Failed -> scrapeState.message
            ScrapeState.NotConfigured -> "Add credentials below before scraping"
            ScrapeState.Idle -> "Fetch artwork and details for your games"
        },
        focused = focusedRow == 0,
        trailingLabel = if (scrapeState is ScrapeState.Running) "Cancel" else "Start",
        onClick = {
            if (scrapeState is ScrapeState.Running) {
                viewModel.cancelScrape()
            } else {
                viewModel.scrapeMetadata(onlyMissing = metadata.scrapeOnlyMissing)
            }
        },
    )
    RowDivider()
    SwitchRow(
        title = "Only fill in missing data",
        subtitle = "Leave already-scraped entries alone",
        checked = metadata.scrapeOnlyMissing,
        focused = focusedRow == 1,
        onCheckedChange = { on -> viewModel.updateMetadata { it.copy(scrapeOnlyMissing = on) } },
    )
    RowDivider()
    SwitchRow(
        title = "Choose matches myself",
        subtitle = "Shows the candidates when providers disagree, and takes the " +
            "best guess after ${MetadataSettings.SCRAPE_CHOICE_SECONDS} seconds " +
            "if nobody answers",
        checked = metadata.askForMatches,
        focused = focusedRow == 2,
        onCheckedChange = { on -> viewModel.updateMetadata { it.copy(askForMatches = on) } },
    )
    RowDivider()
    /*
     * Trailers, for libraries scraped before THOR could fetch them.
     *
     * Its own action rather than a full re-scrape: "only missing" means *never
     * scraped*, so every existing game is skipped and no trailer ever arrives —
     * but re-scraping everything is hundreds of rate-limited calls to fill one
     * field most of them will not have. This asks only about games without one.
     */
    ActionRow(
        title = "Fetch missing trailers",
        subtitle = "Look up trailers for games that have none, without re-scraping " +
            "everything else",
        focused = focusedRow == 3,
        trailingLabel = "Fetch",
        onClick = viewModel::refreshTrailers,
    )

    RowDivider()
    ActionRow(
        title = "Check connections",
        subtitle = "Verify each provider's credentials actually work",
        focused = focusedRow == 4,
        trailingLabel = if (checking) "Checking…" else "Check",
        onClick = viewModel::checkProviderConnections,
    )

    // Artwork arriving while every text field stays blank looks like a broken
    // scraper. It is usually just SteamGridDB being the only configured provider,
    // and SteamGridDB serves artwork only — so say which providers supply text.
    if (artworkOnly) {
        RowDivider()
        InfoRow(
            "No description source",
            "Enable Wikidata for key-free Wikipedia descriptions, or add a RAWG key " +
                "for RAWG descriptions and credits.",
        )
    }

    // The same shape of fault one layer along. SteamGridDB fills every cover, so
    // the scrape plainly worked — but it holds nothing landscape, so the panel
    // has no image to show and nothing anywhere says why.
    if (noScreenshots) {
        RowDivider()
        InfoRow(
            "No screenshot source",
            "SteamGridDB has covers, banners and logos but no widescreen images. " +
                "Add IGDB credentials, a RAWG key, or a ScreenScraper account " +
                "below — without one of those the game panel has nothing to show.",
        )
    }

    PROVIDERS.forEachIndexed { index, provider ->
        RowDivider()
        SwitchRow(
            title = provider.second,
            subtitle = when {
                provider.first !in IMPLEMENTED_PROVIDERS -> "Not yet implemented"
                else -> providerStatus[provider.first].describe()
            },
            checked = provider.first in metadata.enabledProviders,
            focused = focusedRow == PROVIDER_FIRST_ROW + index,
            onCheckedChange = { on ->
                viewModel.updateMetadata { current ->
                    current.copy(
                        enabledProviders = if (on) {
                            current.enabledProviders + provider.first
                        } else {
                            current.enabledProviders - provider.first
                        },
                    )
                }
            },
        )
    }

    RowDivider()
    TextFieldRow(
        title = "SteamGridDB key",
        subtitle = "Artwork. From steamgriddb.com/profile/preferences/api",
        value = metadata.apiKeys[PROVIDER_STEAMGRIDDB].orEmpty(),
        placeholder = "API key",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size,
        onValueChange = { viewModel.setApiKey(PROVIDER_STEAMGRIDDB, it) },
    )
    RowDivider()
    TextFieldRow(
        title = "RAWG key",
        // Screenshots named first, deliberately. With no ScreenScraper developer
        // key in the build this is the only source of a widescreen image the
        // game panel can show, and a row describing it as prose reads as
        // optional to somebody looking at an empty panel.
        subtitle = "Screenshots, descriptions and credits. From rawg.io/apidocs",
        value = metadata.apiKeys[PROVIDER_RAWG].orEmpty(),
        placeholder = "API key",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 1,
        onValueChange = { viewModel.setApiKey(PROVIDER_RAWG, it) },
    )
    RowDivider()
    TextFieldRow(
        title = "ScreenScraper account",
        // These fields are what turns the provider on in a build with no
        // developer key of its own, which is this one.
        subtitle = if (screenScraperKeyMissing) {
            "Signs this launcher in to ScreenScraper. From screenscraper.fr"
        } else {
            "Optional — raises the daily quota and image quality"
        },
        value = metadata.screenScraperUser,
        placeholder = "Username",
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 2,
        onValueChange = viewModel::setScreenScraperUser,
    )
    RowDivider()
    TextFieldRow(
        title = "ScreenScraper password",
        value = metadata.screenScraperPassword,
        placeholder = "Password",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 3,
        onValueChange = viewModel::setScreenScraperPassword,
    )
    RowDivider()
    TextFieldRow(
        title = "IGDB client ID",
        // Named for where they come from rather than what they are: the pair is
        // issued by Twitch, and somebody hunting for an "IGDB key" on igdb.com
        // will not find one.
        subtitle = "Screenshots at a fixed 16:9. From the Twitch developer console",
        value = metadata.igdbClientId,
        placeholder = "Client ID",
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 4,
        onValueChange = viewModel::setIgdbClientId,
    )
    RowDivider()
    TextFieldRow(
        title = "IGDB client secret",
        value = metadata.igdbClientSecret,
        placeholder = "Client secret",
        isSecret = true,
        focused = focusedRow == PROVIDER_FIRST_ROW + PROVIDERS.size + 5,
        onValueChange = viewModel::setIgdbClientSecret,
    )
}

/**
 * Where the provider switches start on the Metadata page.
 *
 * Named, and the credential rows below are counted from it, because the fixed
 * rows above have been renumbered by hand twice now — and every time, the rows
 * after them silently stopped matching the cursor. An index expressed as
 * arithmetic cannot drift from the list it is indexing.
 */
internal const val PROVIDER_FIRST_ROW = 5

private const val PROVIDER_STEAMGRIDDB = "steamgriddb"
private const val PROVIDER_RAWG = "rawg"
private const val PROVIDER_IGDB = "igdb"

internal val PROVIDERS = listOf(
    PROVIDER_STEAMGRIDDB to "SteamGridDB",
    "wikidata" to "Wikidata",
    PROVIDER_RAWG to "RAWG",
    PROVIDER_IGDB to "IGDB",
    "screenscraper" to "ScreenScraper",
)

/** Providers with a working client; the rest are listed but inert. */
private val IMPLEMENTED_PROVIDERS = setOf(
    PROVIDER_STEAMGRIDDB,
    "wikidata",
    PROVIDER_RAWG,
    PROVIDER_IGDB,
    "screenscraper",
)

/** Human-readable form of a provider probe result. */
private fun ProviderStatus?.describe(): String = when (this) {
    null, ProviderStatus.Unknown -> "Not checked"
    ProviderStatus.NotConfigured -> "No credentials"
    ProviderStatus.Connected -> "Connected"
    ProviderStatus.InvalidCredentials -> "Rejected — check the key"
    is ProviderStatus.Unreachable -> "Unreachable — $detail"
    is ProviderStatus.Error -> detail
}
