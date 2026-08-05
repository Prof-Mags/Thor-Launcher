package com.thor.feature.settings.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thor.core.model.StreamAudio
import com.thor.core.model.StreamCodec
import com.thor.core.model.StreamNetwork
import com.thor.core.model.ThorSettings
import com.thor.feature.settings.component.row.ChoiceRow
import com.thor.feature.settings.component.row.InfoRow
import com.thor.feature.settings.component.row.IntSliderRow
import com.thor.feature.settings.component.RowDivider
import com.thor.feature.settings.component.row.SliderRow
import com.thor.feature.settings.component.row.SwitchRow
import com.thor.feature.settings.component.row.TextFieldRow
import com.thor.feature.settings.SettingsViewModel

/** How many rows [StreamQualityPage] draws. */
internal const val STREAM_QUALITY_ROWS = 9

/** How many rows [StreamControlsPage] draws. */
// The explanatory "Leaving a stream" item is an InfoRow, so only the seven
// controls above it belong to controller navigation.
internal const val STREAM_CONTROLS_ROWS = 7

/** How many rows [StreamHostsPage] draws. */
internal const val STREAM_HOSTS_ROWS = 2

/**
 * A resolution to ask the host for.
 *
 * A short list rather than free entry: these are what encoders are built around,
 * and a host asked for something unusual either refuses or quietly sends
 * something else. Below the panel's own resolution is a legitimate choice — it
 * is the cheapest way to make a stream over a poor link watchable.
 */
private data class Resolution(val width: Int, val height: Int, val label: String)

private val RESOLUTIONS = listOf(
    Resolution(1280, 720, "720p — kindest to a weak connection"),
    Resolution(1920, 1080, "1080p — matches this screen"),
    Resolution(2560, 1440, "1440p — sharper than the panel can show"),
    Resolution(3840, 2160, "4K — costs bandwidth this screen cannot use"),
)

private val FRAME_RATES = listOf(30, 60, 120)

/**
 * What THOR asks a PC to send.
 *
 * Requests rather than commands, and the page says so: the host decides what it
 * can actually encode, and one that cannot manage the asked-for mode sends
 * something else rather than refusing. Presenting these as guarantees would make
 * a perfectly working stream look broken whenever a PC quietly did otherwise.
 */
@Composable
internal fun StreamQualityPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val quality = settings.stream.quality

    Column(modifier = Modifier.fillMaxWidth()) {
        ChoiceRow(
            title = "Resolution",
            subtitle = "What to ask the PC to encode. Higher than this panel costs " +
                "bandwidth and decoding for detail the screen cannot show.",
            options = RESOLUTIONS,
            selected = RESOLUTIONS.firstOrNull {
                it.width == quality.width && it.height == quality.height
            } ?: RESOLUTIONS[1],
            focused = focusedRow == 0,
            label = { it.label },
            onSelected = { value ->
                viewModel.updateStream {
                    it.copy(quality = it.quality.copy(width = value.width, height = value.height))
                }
            },
        )
        RowDivider()

        ChoiceRow(
            title = "Frame rate",
            subtitle = "Higher is smoother and more responsive, and costs bandwidth in " +
                "proportion. A PC that cannot hold it sends fewer frames rather " +
                "than failing.",
            options = FRAME_RATES,
            selected = FRAME_RATES.firstOrNull { it == quality.fps } ?: 60,
            focused = focusedRow == 1,
            label = { "$it fps" },
            onSelected = { value ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(fps = value)) }
            },
        )
        RowDivider()

        IntSliderRow(
            title = "Bandwidth",
            /*
             * Named as the setting that actually decides how it looks.
             *
             * Resolution gets the attention, but bitrate is what separates a
             * stream that looks like the game from one that smears whenever
             * anything moves. Worth saying, because the instinct is to raise the
             * resolution when a picture looks poor and that usually makes it
             * worse.
             */
            subtitle = "The setting that decides how it looks in motion. 20 Mbps suits " +
                "1080p60 on a home network; lower it for a link over the internet " +
                "or a VPN.",
            value = quality.bitrateKbps / 1000,
            range = 3..100,
            focused = focusedRow == 2,
            suffix = " Mbps",
            onValueChange = { value ->
                viewModel.updateStream {
                    it.copy(quality = it.quality.copy(bitrateKbps = value * 1000))
                }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Ask for HDR",
            subtitle = "Only if the PC and the game both support it. Loki does not yet " +
                "apply the colour data, so this is off — and a stream that claims " +
                "HDR without honouring it looks washed out.",
            checked = quality.enableHdr,
            focused = focusedRow == 3,
            onCheckedChange = { on ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(enableHdr = on)) }
            },
        )
        RowDivider()

        ChoiceRow(
            title = "Video codec",
            subtitle = "Newer codecs carry the same picture in less bandwidth but cost " +
                "more to decode. A codec this device cannot decode in hardware is " +
                "quietly not offered.",
            options = StreamCodec.entries,
            selected = quality.codec,
            focused = focusedRow == 4,
            label = { it.label },
            optionDescription = { it.detail },
            onSelected = { value ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(codec = value)) }
            },
        )
        RowDivider()

        ChoiceRow(
            title = "Audio",
            subtitle = "Surround costs bandwidth to encode channels this handheld then " +
                "mixes back down. Worth it only through headphones that do something " +
                "with them.",
            options = StreamAudio.entries,
            selected = quality.audio,
            focused = focusedRow == 5,
            label = { it.label },
            onSelected = { value ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(audio = value)) }
            },
        )
        RowDivider()

        ChoiceRow(
            title = "Connection",
            subtitle = "Decides packet size. A packet too large for the path is split, " +
                "which costs far more than a slightly small one — VPNs in particular " +
                "reduce what fits.",
            options = StreamNetwork.entries,
            selected = quality.network,
            focused = focusedRow == 6,
            label = { it.label },
            optionDescription = { it.detail },
            onSelected = { value ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(network = value)) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Keep playing sound on the PC",
            subtitle = "Off, because the usual reason to stream is that you are not at " +
                "the PC, and sound from an empty room is a surprise rather than a " +
                "feature.",
            checked = quality.playAudioOnHost,
            focused = focusedRow == 7,
            onCheckedChange = { on ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(playAudioOnHost = on)) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Let the PC change game settings",
            subtitle = "Sunshine and GeForce Experience call this “optimal playable " +
                "settings”. Off: it rewrites options you chose, on your own machine, " +
                "and only NVIDIA's host implemented it properly.",
            checked = quality.optimizeGameSettings,
            focused = focusedRow == 8,
            onCheckedChange = { on ->
                viewModel.updateStream {
                    it.copy(quality = it.quality.copy(optimizeGameSettings = on))
                }
            },
        )
        RowDivider()
    }
}

/**
 * How the handheld drives the PC.
 *
 * Its own page because these are adjusted for a different reason than picture
 * quality: one is tuned once against the network, the other is fiddled with
 * until the controls feel right.
 */
@Composable
internal fun StreamControlsPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val quality = settings.stream.quality

    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(
            title = "Trackpad and keyboard on the bottom screen",
            subtitle = "The reason the panel exists: Android's own keyboard cannot " +
                "appear over a stream, or on the second screen at all, so without " +
                "this there is no way to type on the PC.",
            checked = quality.bottomPanel,
            focused = focusedRow == 0,
            onCheckedChange = { on ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(bottomPanel = on)) }
            },
        )
        RowDivider()

        SliderRow(
            title = "Pointer speed",
            subtitle = "How far the pointer travels for a given swipe on the trackpad.",
            value = quality.trackpadSpeed,
            range = 0.5f..4f,
            steps = 13,
            focused = focusedRow == 1,
            valueLabel = { "%.1f×".format(it) },
            onValueChange = { value ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(trackpadSpeed = value)) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Tap to click",
            subtitle = "A tap on the trackpad is a left click; two fingers is a right " +
                "click.",
            checked = quality.tapToClick,
            focused = focusedRow == 2,
            onCheckedChange = { on ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(tapToClick = on)) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Natural scrolling",
            subtitle = "Content follows your fingers, as it does everywhere else on a " +
                "touchscreen. Off scrolls the way a mouse wheel does.",
            checked = quality.naturalScroll,
            focused = focusedRow == 3,
            onCheckedChange = { on ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(naturalScroll = on)) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Touch the picture to point",
            subtitle = "Puts the PC's cursor exactly where you touch the video. Off by " +
                "default: a hand resting on the screen would fling the cursor across " +
                "the PC.",
            checked = quality.touchVideoAsPointer,
            focused = focusedRow == 4,
            onCheckedChange = { on ->
                viewModel.updateStream {
                    it.copy(quality = it.quality.copy(touchVideoAsPointer = on))
                }
            },
        )
        RowDivider()

        SliderRow(
            title = "Stick dead zone",
            subtitle = "How far a stick must move before it counts. Too small and a pad " +
                "at rest walks on its own; too large and fine aiming is lost.",
            value = quality.stickDeadZone,
            range = 0f..0.4f,
            steps = 7,
            focused = focusedRow == 5,
            valueLabel = { "%.0f%%".format(it * 100) },
            onValueChange = { value ->
                viewModel.updateStream { it.copy(quality = it.quality.copy(stickDeadZone = value)) }
            },
        )
        RowDivider()

        SwitchRow(
            title = "Start opens these settings",
            subtitle = "Opens the trackpad panel's settings on the bottom screen. The PC " +
                "then never sees Start, because a key press goes to one window — turn " +
                "this off if a game needs it.",
            checked = quality.startOpensSettings,
            focused = focusedRow == 6,
            onCheckedChange = { on ->
                viewModel.updateStream {
                    it.copy(quality = it.quality.copy(startOpensSettings = on))
                }
            },
        )
        RowDivider()

        InfoRow(
            title = "Leaving a stream",
            value = "Hold Back, or press Start, Select, LB and RB together. The session " +
                "keeps running on the PC so you can rejoin it; press Y on the PC in the " +
                "Stream tab to stop it entirely.",
        )
        RowDivider()
    }
}

/** How PCs are found, and what this device calls itself to them. */
@Composable
internal fun StreamHostsPage(
    settings: ThorSettings,
    focusedRow: Int,
    viewModel: SettingsViewModel,
) {
    val stream = settings.stream

    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchRow(
            title = "Find PCs automatically",
            subtitle = "Sunshine announces itself on the network, so most PCs need no " +
                "setting up. Announcements do not cross a VPN or a different " +
                "subnet — over Tailscale, add the PC by address instead.",
            checked = stream.discoverAutomatically,
            focused = focusedRow == 0,
            onCheckedChange = { on ->
                viewModel.updateStream { it.copy(discoverAutomatically = on) }
            },
        )
        RowDivider()

        TextFieldRow(
            title = "This device's name",
            subtitle = "What Loki is listed as in Sunshine's client list on the PC. " +
                "Changing it does not undo a pairing.",
            value = stream.clientName,
            focused = focusedRow == 1,
            onValueChange = { value ->
                viewModel.updateStream { it.copy(clientName = value) }
            },
        )
        RowDivider()
    }
}
