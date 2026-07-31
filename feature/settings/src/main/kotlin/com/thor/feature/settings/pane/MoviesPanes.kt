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
 * Three independent things, and the page keeps them visibly separate because
 * they fail independently: without a catalogue key nothing can be browsed,
 * without an indexer nothing can be found, and without a debrid token what is
 * found cannot be played. Collapsing them into "set up streaming" would make a
 * single missing field look like the whole feature being broken.
 */
@Composable
internal fun MoviesCataloguePage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val media = settings.media

    Column(modifier = Modifier.fillMaxWidth()) {
        TextFieldRow(
            title = "TMDb API key",
            subtitle = "Posters, synopses, cast, seasons and episodes. Free from " +
                "themoviedb.org.",
            value = media.tmdbApiKey,
            placeholder = "Required to browse",
            isSecret = true,
            focused = focusedRow == 0,
            onValueChange = { key -> viewModel.updateMedia { it.copy(tmdbApiKey = key) } },
        )
        RowDivider()

        TextFieldRow(
            title = "Real-Debrid token",
            subtitle = "Turns a torrent into an instant stream. Without it, sources " +
                "are listed but cannot be opened.",
            value = media.realDebridToken,
            placeholder = "API token",
            isSecret = true,
            focused = focusedRow == 1,
            onValueChange = { token ->
                viewModel.updateMedia { it.copy(realDebridToken = token) }
            },
        )
        RowDivider()

        InfoRow("Real-Debrid", if (media.isDebridConfigured) "Configured" else "Not set")
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
        media.indexers.forEachIndexed { index, indexer ->
            ActionRow(
                title = indexer.name.ifBlank { indexer.url.ifBlank { "Indexer ${index + 1}" } },
                subtitle = when {
                    !indexer.enabled -> "Disabled"
                    indexer.isUsable -> indexer.url
                    else -> "Needs a URL and an API key"
                },
                focused = focusedRow == INDEXER_FIRST_ROW + index,
                trailingLabel = "Remove",
                onClick = { viewModel.removeIndexer(index) },
            )
            RowDivider()
        }

        ActionRow(
            title = "Add a torrent indexer",
            subtitle = "A Torznab endpoint — Jackett, Prowlarr or NZBHydra. THOR " +
                "searches it directly; nothing else is installed.",
            focused = focusedRow == INDEXER_FIRST_ROW + media.indexers.size,
            trailingLabel = "Add",
            onClick = { viewModel.addIndexer() },
        )
        RowDivider()

        InfoRow(
            "Sources",
            if (media.hasSources) "${media.indexers.count { it.isUsable }} indexers" else "None",
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

/** Rows above the indexer list: the two keys and the debrid status line. */
internal const val INDEXER_FIRST_ROW = 3

/** How many rows each page renders, for the controller's cursor clamp. */
internal fun moviesCatalogueRowCount(media: MediaSettings): Int =
    INDEXER_FIRST_ROW + media.indexers.size + 2

internal const val MOVIES_PLAYBACK_ROWS = 9

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
