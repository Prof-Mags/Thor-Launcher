package com.thor.feature.settings.pane

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.core.model.MediaSettings
import com.thor.core.model.Resolution
import com.thor.core.model.ThorSettings
import com.thor.core.model.TorznabIndexer
import com.thor.feature.settings.SettingsViewModel
import com.thor.feature.settings.component.ActionRow
import com.thor.feature.settings.component.InfoRow
import com.thor.feature.settings.component.ChoiceRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.SliderRow
import com.thor.feature.settings.component.SwitchRow
import com.thor.feature.settings.component.TextFieldRow

/**
 * Where the Movies section gets its content.
 *
 * Two independent things, and the page keeps them visibly separate because they
 * fail independently: without a source nothing can be found, and without a
 * debrid token what is found cannot be opened. Collapsing them into "set up
 * streaming" would make one missing field look like the whole feature being
 * broken.
 *
 * Browsing is on neither list, because it needs nothing at all.
 */
@Composable
internal fun MoviesCataloguePage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
    debridStatus: String?,
    indexerStatus: Map<Int, String>,
    addonStatus: Map<Int, String>,
) {
    val media = settings.media

    Column(modifier = Modifier.fillMaxWidth()) {
        /*
         * The TMDb key row used to be first, and is gone rather than disabled.
         *
         * Browsing needs no credential: the catalogue is the Stremio protocol,
         * keyless, and keyed by the same IMDb ids the source addons use. A field
         * asking for a key nothing reads is worse than no field — it is the
         * launcher asking for something and then ignoring the answer.
         */
        TextFieldRow(
            title = "Real-Debrid token",
            subtitle = "Turns a torrent into an instant stream. Without it, sources " +
                "are listed but cannot be opened.",
            value = media.realDebridToken,
            placeholder = "API token",
            isSecret = true,
            focused = focusedRow == 0,
            onValueChange = { token ->
                viewModel.updateMedia { it.copy(realDebridToken = token) }
            },
        )
        RowDivider()

        /*
         * Asked of the service rather than inferred from the field being filled.
         *
         * A token that is present but expired, revoked or mistyped looks exactly
         * like a working one from here, and the symptom it produces — sources
         * listed but nothing ever opening — points nowhere near this screen.
         */
        ActionRow(
            title = "Check Real-Debrid",
            subtitle = debridStatus ?: "Confirms the token works and the account is active.",
            focused = focusedRow == 1,
            trailingLabel = "Check",
            onClick = viewModel::checkDebrid,
        )
        RowDivider()

        /*
         * Indexers, listed one per row.
         *
         * THOR searches these itself — there is no addon and nothing else in the
         * stream path — but it ships none and knows of none. The launcher speaks
         * Torznab, which is what Jackett, Prowlarr and NZBHydra all expose; which
         * indexers to ask is the user's decision and their responsibility, the
         * same as the game metadata providers.
         */
        /*
         * Addons first, because they are the easy path.
         *
         * One URL, no credential, and the same install links people already share
         * for Stremio — including the `stremio://` form an install button
         * produces and the configured form that carries its options in the path.
         * All of them are accepted; see `StremioAddons.normalise`.
         */
        media.addons.forEachIndexed { index, addon ->
            val base = ADDON_FIRST_ROW + index * ROWS_PER_ADDON

            TextFieldRow(
                title = "Addon ${index + 1}",
                subtitle = if (addon.name.isNotBlank()) {
                    "${addon.name} — installed"
                } else {
                    "Paste the addon's install or manifest URL, then press Check."
                },
                value = addon.url,
                placeholder = "https://…/manifest.json",
                focused = focusedRow == base,
                onValueChange = { url -> viewModel.setAddonUrl(index, url) },
            )
            RowDivider()

            /*
             * Testing and removing are separate rows, and were one.
             *
             * The single row turned into "Remove" as soon as a name was known,
             * so an addon that had worked once could never be tested again —
             * exactly when testing matters, which is when it has stopped
             * working. Sharing a row also made the destructive action sit where
             * the harmless one had been.
             */
            ActionRow(
                title = "Test this addon",
                subtitle = addonStatus[index]
                    ?: "Asks it for a stream it certainly has, which is the only " +
                    "way to tell a working addon from a URL that merely looks right.",
                focused = focusedRow == base + 1,
                trailingLabel = "Test",
                onClick = { viewModel.checkAddon(index) },
            )
            RowDivider()

            ActionRow(
                title = "Remove this addon",
                focused = focusedRow == base + 2,
                trailingLabel = "Remove",
                destructive = true,
                onClick = { viewModel.removeAddon(index) },
            )
            RowDivider()
        }

        ActionRow(
            /*
             * Named for what the user does rather than for whose protocol it is.
             *
             * "Stremio addon" describes the format and assumes the reader already
             * knows it; "URL-based addon" describes the action — you paste a URL —
             * which is the whole of what this row asks for. The protocol is still
             * named in the subtitle, because someone holding an install link needs
             * to know it will be understood.
             */
            title = "Add a URL-based addon",
            subtitle = "Paste an addon's URL and it is installed. THOR speaks the " +
                "Stremio addon protocol, so any addon serving streams works — it " +
                "ships none, and which you install is your choice.",
            focused = focusedRow == ADDON_FIRST_ROW + media.addons.size * ROWS_PER_ADDON,
            trailingLabel = "Add",
            onClick = { viewModel.addAddon() },
        )
        RowDivider()

        media.indexers.forEachIndexed { index, indexer ->
            val base = indexerFirstRow(media) + index * ROWS_PER_INDEXER

            TextFieldRow(
                title = "Indexer ${index + 1} — name",
                subtitle = indexer.status(),
                value = indexer.name,
                placeholder = "Whatever you want to call it",
                focused = focusedRow == base,
                onValueChange = { name ->
                    viewModel.updateIndexer(index) { it.copy(name = name) }
                },
            )
            RowDivider()

            TextFieldRow(
                title = "Torznab URL",
                subtitle = "The base endpoint, without the trailing /api. Jackett " +
                    "shows this as “Torznab Feed” on each configured indexer.",
                value = indexer.url,
                placeholder = "http://192.168.1.10:9117/api/v2.0/indexers/xxx/results/torznab",
                focused = focusedRow == base + 1,
                onValueChange = { url ->
                    viewModel.updateIndexer(index) { it.copy(url = url.trim()) }
                },
            )
            RowDivider()

            TextFieldRow(
                title = "API key",
                subtitle = "From the same page as the URL.",
                value = indexer.apiKey,
                placeholder = "Required",
                isSecret = true,
                focused = focusedRow == base + 2,
                onValueChange = { key ->
                    viewModel.updateIndexer(index) { it.copy(apiKey = key.trim()) }
                },
            )
            RowDivider()

            /*
             * Asked of the indexer, not inferred from the fields.
             *
             * The row above this one can only say whether a URL and a key are
             * present, which is true of a mistyped host, a revoked key and a
             * Jackett that is not running alike. Each of those shows up much
             * later as a title with no sources, and nothing on that screen can
             * say which — so the question is worth asking here, where the answer
             * is actionable.
             */
            ActionRow(
                title = "Test this indexer",
                subtitle = indexerStatus[index]
                    ?: "Asks it directly, which is the only way to tell a working " +
                    "endpoint from a filled-in one.",
                focused = focusedRow == base + 3,
                trailingLabel = "Test",
                onClick = { viewModel.checkIndexer(index) },
            )
            RowDivider()

            ActionRow(
                title = "Remove this indexer",
                focused = focusedRow == base + 4,
                destructive = true,
                trailingLabel = "Remove",
                onClick = { viewModel.removeIndexer(index) },
            )
            RowDivider()
        }

        ActionRow(
            title = "Add a torrent indexer",
            subtitle = "The other route: a Torznab endpoint — Jackett, Prowlarr or " +
                "NZBHydra — searched by THOR directly. Needs a URL and a key per " +
                "site, so an addon is usually less work.",
            focused = focusedRow ==
                indexerFirstRow(media) + media.indexers.size * ROWS_PER_INDEXER,
            trailingLabel = "Add",
            onClick = { viewModel.addIndexer() },
        )
        RowDivider()

        InfoRow(
            "Ready to search",
            if (media.hasSources) {
                listOfNotNull(
                    media.addons.count { it.isUsable }
                        .takeIf { it > 0 }?.let { "$it addons" },
                    media.indexers.count { it.isUsable }
                        .takeIf { it > 0 }?.let { "$it indexers" },
                ).joinToString(" · ")
            } else {
                "Nothing yet"
            },
        )
    }
}

/**
 * Which source is chosen, and how it plays.
 *
 * These are preferences rather than credentials, and every one of them is a
 * trade the user is better placed to make than the launcher — panel size,
 * connection speed and what their ears and eyes actually notice.
 */
@Composable
internal fun MoviesPlaybackPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val media = settings.media

    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(
            title = "Choose a source automatically",
            subtitle = "Play the best match instead of opening the list. The list is " +
                "always one press away.",
            checked = media.autoSelectSource,
            focused = focusedRow == 0,
            onCheckedChange = { on -> viewModel.updateMedia { it.copy(autoSelectSource = on) } },
        )
        RowDivider()

        ChoiceRow(
            title = "Preferred resolution",
            subtitle = "An exact match wins. Below this beats above it — a 4K stream " +
                "costs bandwidth and decode for a difference this panel cannot show.",
            options = PICKABLE_RESOLUTIONS,
            selected = media.preferredResolution,
            focused = focusedRow == 1,
            label = { it.label },
            onSelected = { value ->
                viewModel.updateMedia { it.copy(preferredResolution = value) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Only instantly playable sources",
            subtitle = "Hide anything Real-Debrid does not already hold. An uncached " +
                "torrent is a download, not a stream.",
            checked = media.cachedOnly,
            focused = focusedRow == 2,
            onCheckedChange = { on -> viewModel.updateMedia { it.copy(cachedOnly = on) } },
        )
        RowDivider()

        SwitchRow(
            title = "Prefer HDR",
            subtitle = "Off by default: HDR on a panel that cannot present it looks " +
                "washed out, which reads as a broken stream.",
            checked = media.preferHdr,
            focused = focusedRow == 3,
            onCheckedChange = { on -> viewModel.updateMedia { it.copy(preferHdr = on) } },
        )
        RowDivider()

        SwitchRow(
            title = "Skip dubbed releases",
            checked = media.avoidDubbed,
            focused = focusedRow == 4,
            onCheckedChange = { on -> viewModel.updateMedia { it.copy(avoidDubbed = on) } },
        )
        RowDivider()

        SliderRow(
            title = "Largest file",
            subtitle = "Sources bigger than this are hidden. Zero means no limit.",
            value = media.maxSizeGb,
            range = 0f..80f,
            steps = 15,
            focused = focusedRow == 5,
            valueLabel = { if (it <= 0f) "No limit" else "%.0f GB".format(it) },
            onValueChange = { value -> viewModel.updateMedia { it.copy(maxSizeGb = value) } },
        )
        RowDivider()

        SwitchRow(
            title = "Play the next episode",
            subtitle = "Roll straight into it when one finishes.",
            checked = media.autoPlayNextEpisode,
            focused = focusedRow == 6,
            onCheckedChange = { on ->
                viewModel.updateMedia { it.copy(autoPlayNextEpisode = on) }
            },
        )
        RowDivider()

        SliderRow(
            title = "Skip step",
            subtitle = "How far the skip buttons move.",
            value = media.skipSeconds.toFloat(),
            range = 5f..60f,
            steps = 10,
            focused = focusedRow == 7,
            valueLabel = { "${it.toInt()}s" },
            onValueChange = { value ->
                viewModel.updateMedia { it.copy(skipSeconds = value.toInt()) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Resume automatically",
            subtitle = "Pick up where you stopped, without asking.",
            checked = media.resumeAutomatically,
            focused = focusedRow == 8,
            onCheckedChange = { on ->
                viewModel.updateMedia { it.copy(resumeAutomatically = on) }
            },
        )
    }
}

/**
 * Rows above the addon list: the debrid token and its connection check.
 *
 * Was three, when a TMDb API key sat above them. Derived indices like this are
 * why that row could not simply be deleted — every row below it is placed
 * relative to this number, and leaving it at three would have left row zero
 * focusable and pointing at nothing.
 */
internal const val ADDON_FIRST_ROW = 2

/** A URL, a test button and a remove button, per addon. */
internal const val ROWS_PER_ADDON = 3

/** Name, URL, key, a test button and a remove button, per indexer. */
internal const val ROWS_PER_INDEXER = 5

/** Where the indexer list starts, after the addons and their Add button. */
internal fun indexerFirstRow(media: MediaSettings): Int =
    ADDON_FIRST_ROW + media.addons.size * ROWS_PER_ADDON + 1

/**
 * Every focusable row on the catalogue page.
 *
 * Derived from the same constants the page lays out with, rather than written as
 * a number beside them — a count that drifts from the layout produces presses
 * that appear to do nothing, with nothing to point at.
 */
internal fun moviesCatalogueRows(media: MediaSettings): Int =
    // The "Add a torrent indexer" button, and nothing after it. This said `+ 2`,
    // counting the summary line below it — but that is an `InfoRow`, which takes
    // no `focused` parameter and so can neither highlight nor be pressed. The
    // cursor moved onto it, the haptic fired, and the screen did not change:
    // a row that exists to the controller and not to the eye.
    indexerFirstRow(media) + media.indexers.size * ROWS_PER_INDEXER + 1

internal const val MOVIES_PLAYBACK_ROWS = 9

/** What this indexer is currently missing, if anything. */
private fun TorznabIndexer.status(): String = when {
    isUsable -> "Ready"
    url.isBlank() && apiKey.isBlank() -> "Needs a URL and an API key"
    url.isBlank() -> "Needs a URL"
    apiKey.isBlank() -> "Needs an API key"
    else -> "Disabled"
}

/**
 * Resolutions worth offering.
 *
 * `UNKNOWN` is a parser outcome rather than a preference, and 1440p is
 * vanishingly rare in released files — offering either would be a setting that
 * changes nothing.
 */
private val PICKABLE_RESOLUTIONS = listOf(
    Resolution.SD,
    Resolution.HD_720,
    Resolution.FHD_1080,
    Resolution.UHD_4K,
)

/** A blank indexer, for the user to fill in. */
internal fun newIndexer(): TorznabIndexer = TorznabIndexer(name = "New indexer")
