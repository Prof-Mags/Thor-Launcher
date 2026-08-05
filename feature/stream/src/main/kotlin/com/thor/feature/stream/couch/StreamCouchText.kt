package com.thor.feature.stream.couch

import androidx.compose.material.icons.rounded.Stop
import com.thor.core.model.HostStatus

/**
 * How a host's state reads on a card.
 *
 * Sentence case and short, where the handheld's badge shouts it in capitals: a
 * card carries its status as a caption beside a coloured dot rather than as a
 * pill, and a wall of capitals is a wall of shouting.
 */
internal fun HostStatus.couchLabel(): String = when (this) {
    HostStatus.Unknown -> "Waiting"
    HostStatus.Checking -> "Checking"
    is HostStatus.Offline -> "Offline"
    is HostStatus.Online -> when {
        currentGame != null -> "In session"
        paired -> "Online"
        else -> "Pair needed"
    }
}

internal val COMPUTERS_LEGEND = listOf(
    "A" to "Select",
    "Y" to "Pair",
    "LEFT" to "Menu",
    "B" to "Back",
)

internal val ADD_HOST_LEGEND = listOf(
    "A" to "Select",
    "Y" to "Keyboard",
    "B" to "Cancel",
)

internal val HELP_LEGEND = listOf(
    "UP / DOWN" to "Read",
    "LEFT" to "Menu",
    "B" to "Back",
)

internal val RAIL_LEGEND = listOf(
    "A" to "Open",
    "RIGHT" to "Back to the page",
)

internal val HEADER_LEGEND = listOf(
    "A" to "Open",
    "DOWN" to "Back to the PCs",
)

/**
 * What the pad does once a stream is up.
 *
 * Read from the streaming window's own handling rather than invented for this
 * page: Back leaves on the press, every other button belongs to the PC, and the
 * combination is the way out when a game has taken the pad whole.
 */
internal val PAD_REFERENCE = listOf(
    "B / BACK" to "Leave the stream. The session keeps running on the PC.",
    "START" to "Show the trackpad's own settings on the bottom screen.",
    "EVERY OTHER" to "Goes to the PC, exactly as a pad plugged into it would.",
)

/** Stands in for whatever the user has named this device to Sunshine. */
internal const val CLIENT_NAME_TOKEN = "%CLIENT%"

/** One numbered part of the help page. */
internal data class StreamHelpSection(val title: String, val body: String)

/**
 * What somebody has to know, in the order they have to know it.
 *
 * Held here rather than in strings because the view model counts them: the help
 * page's cursor is clamped to this list, and a count kept in two places is a
 * cursor that eventually points past the end of the page.
 */
internal val STREAM_HELP_SECTIONS = listOf(
    StreamHelpSection(
        title = "Install Sunshine on the PC",
        body = "Sunshine is what answers when Loki asks — the same host software " +
            "Moonlight talks to. Install it on the computer, start it, and leave " +
            "it running. Windows asks whether to allow it through the firewall the " +
            "first time, and it has to be allowed on the private network or nothing " +
            "on this device will ever reach it.",
    ),
    StreamHelpSection(
        title = "Let it be found",
        body = "A PC running Sunshine on this network announces itself and appears " +
            "on the Computers page on its own. One on a VPN, on another subnet, or " +
            "on a network that blocks those announcements will not — add it by " +
            "address instead, and it is remembered from then on.",
    ),
    StreamHelpSection(
        title = "Pair, once",
        body = "Pairing is a one-time exchange, and it is per device rather than per " +
            "network. Press Y on the PC here; Loki shows a PIN and waits. Type that " +
            "PIN into Sunshine's web interface on the PC, under PIN. It will list " +
            "this device as \"$CLIENT_NAME_TOKEN\". Unpairing happens on the PC, and " +
            "the first sign of it here is a machine asking to be paired again.",
    ),
    StreamHelpSection(
        title = "Stream the desktop",
        body = "Press A on a paired machine and it shares its whole screen rather " +
            "than one chosen game — so whatever is then started on the PC appears " +
            "here, and nothing has to be picked beforehand. A PC already streaming " +
            "can only be resumed or stopped, which is why it says so on its card.",
    ),
    StreamHelpSection(
        title = "Leaving is not stopping",
        body = "Back leaves the stream and the session keeps running on the PC, " +
            "which is what makes going straight back into it instant. Use Stop " +
            "session to actually end it — otherwise the only other way is to walk " +
            "to the machine.",
    ),
    StreamHelpSection(
        title = "If a PC will not answer",
        body = "The card says which failure it was rather than only \"offline\": " +
            "connection refused means the machine is there and Sunshine is not, a " +
            "timeout means it is not answering at all, and an address that cannot " +
            "be resolved is the wrong address. A machine that has answered once is " +
            "kept in the list while it sleeps, so a PC being listed is not a claim " +
            "that it is awake.",
    ),
)

internal val HELP_STEPS = listOf(
    "Install Sunshine on the PC and leave it running.",
    "Open Sunshine's web interface on that machine.",
    "Note the address it is listening on.",
    "Type it here, then pair from the list.",
)
