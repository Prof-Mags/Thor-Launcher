package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A PC THOR can stream games from.
 *
 * The Stream section talks the GameStream protocol, which is what **Sunshine**
 * serves and what NVIDIA's own GameStream served before it was retired. THOR
 * ships no hosts and discovers rather than guesses: a host is either announced
 * on the network or typed in, and either way it is the user's machine on the
 * user's network.
 *
 * Held in settings rather than the library database, and deliberately: a host is
 * a piece of configuration — an address and a credential — not an entry that can
 * appear on the grid. Keeping it here also means it comes back with a settings
 * restore, which is the behaviour a user expects of something they typed in.
 */
@Serializable
data class StreamHost(
    /**
     * The host's own UUID, as it reports itself.
     *
     * Identity, rather than the address: a machine on DHCP changes address and
     * is still the same machine, and pairing is bound to the machine. Blank
     * until the host has answered once.
     */
    val id: String = "",
    /** What the host calls itself, or what the user called it. */
    val name: String = "",
    /** IP or hostname. The one field a manually added host must have. */
    val address: String = "",
    /**
     * Whether THOR has paired with this host.
     *
     * Recorded because pairing is a one-time exchange the user has to complete
     * on the PC — typing a PIN into Sunshine's web interface — and a host that
     * has to be paired again should say so before the user tries to launch
     * something and is refused.
     */
    val paired: Boolean = false,
    /** Announced on the network rather than typed in. */
    val discovered: Boolean = false,
) {
    val isUsable: Boolean get() = address.isNotBlank()

    /** What to show when the host has never answered and has no name yet. */
    val displayName: String get() = name.ifBlank { address.ifBlank { "Unknown host" } }
}

/**
 * What a host said when last asked.
 *
 * Separate from [StreamHost] because it is the answer to a question asked just
 * now, not something to persist: a host that was online an hour ago tells the
 * user nothing about whether it is online while they are looking at it.
 */
sealed interface HostStatus {

    /** Not asked yet. */
    data object Unknown : HostStatus

    data object Checking : HostStatus

    /**
     * Answering, with what it said about itself.
     *
     * @param paired whether *this* client is paired with it, which the host
     *   reports per-client rather than as a property of itself
     * @param currentGame the app it is already streaming, or null when idle. A
     *   host mid-session can only be resumed, not launched into, and saying so
     *   beforehand is better than a refusal afterwards.
     */
    data class Online(
        val name: String,
        val paired: Boolean,
        val currentGame: String? = null,
        /** Set when something about the answer needs explaining; see the client. */
        val note: String? = null,
    ) : HostStatus

    /** Reachable is not the same as usable; [reason] says which failed. */
    data class Offline(val reason: String) : HostStatus
}

/** Everything the Stream section needs configuring. */
@Serializable
data class StreamSettings(
    val hosts: List<StreamHost> = emptyList(),

    /**
     * Whether to look for hosts on the network.
     *
     * On by default. Sunshine announces itself over mDNS, so the common case
     * needs no configuration at all — and a section that made everyone type an
     * IP address first would be asking for something the network already knows.
     */
    val discoverAutomatically: Boolean = true,

    /**
     * The name THOR gives when pairing.
     *
     * Shown in Sunshine's own client list, so it wants to be recognisable on the
     * PC rather than unique to a protocol.
     */
    val clientName: String = "Loki",

    /**
     * The identity THOR presents to hosts, generated on first use.
     *
     * Blank until then, and kept forever after: pairing is bound to it, so a
     * client that announced a new id each time would be an unknown device on
     * every launch and the user would be entering PINs forever.
     */
    val clientId: String = "",

    /** How the stream itself should look. */
    val quality: SessionQuality = SessionQuality(),
) {
    val hasHosts: Boolean get() = hosts.any(StreamHost::isUsable)
}

/**
 * What THOR asks a host to encode.
 *
 * A request rather than a description: the host decides what it can actually
 * produce, and a machine that cannot manage the asked-for resolution sends
 * something else rather than refusing. The defaults suit the AYN Thor's own
 * panel — asking for more than the screen can show costs bandwidth and latency
 * to produce detail that is then thrown away in scaling.
 */
/**
 * Which video codec to ask the host for.
 *
 * Newer codecs carry the same picture in less bandwidth, which matters most over
 * a link that has little to spare — but they cost more to decode, and a handheld
 * decoding AV1 in software would fall behind rather than look better. [AUTO]
 * offers everything the device can decode in hardware and lets the host pick,
 * which is right unless a specific one is misbehaving.
 */
@Serializable
enum class StreamCodec(val label: String, val detail: String) {
    AUTO("Automatic", "Offer everything this device can decode and let the PC choose"),
    H264("H.264", "Works everywhere; needs the most bandwidth"),
    HEVC("HEVC (H.265)", "Same picture for less bandwidth than H.264"),
    AV1("AV1", "Least bandwidth; needs a recent PC and decoder"),
}

/** How many audio channels to ask for. */
@Serializable
enum class StreamAudio(
    val label: String,
    val channels: Int,
    val mask: Int,
) {
    STEREO("Stereo", 2, 0x3),
    SURROUND_51("5.1 surround", 6, 0x3F),
    SURROUND_71("7.1 surround", 8, 0x63F),
    ;

    /** What `/launch` wants: the mask above the count, in one integer. */
    val surroundInfo: Int get() = mask shl 16 or channels
}

/**
 * Whether the link should be treated as local or distant.
 *
 * It decides packet size. A local network carries a full-sized packet happily;
 * anything crossing a VPN or the internet has a smaller usable MTU, and a packet
 * that needs fragmenting costs far more than a slightly small one. [AUTO] lets
 * the streaming core decide, which it does by looking at the address.
 */
@Serializable
enum class StreamNetwork(val label: String, val detail: String) {
    AUTO("Automatic", "Let the connection decide, from the PC's address"),
    LOCAL("Same network", "Largest packets. Only for a PC on your own LAN"),
    // Short enough for the settings row's value button, which is one line wide
    // and ellipsises rather than wraps. The detail carries the rest.
    REMOTE("VPN or internet", "Smaller packets that survive a lower MTU"),
}

@Serializable
data class SessionQuality(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    /**
     * Kilobits per second, and the setting that matters most.
     *
     * Bitrate decides whether motion looks like the game or like a smear, far
     * more than resolution does. 20 Mbps is Moonlight's own figure for 1080p60
     * and is comfortable over a local network; a link across a VPN usually wants
     * less.
     */
    val bitrateKbps: Int = 20_000,
    val enableHdr: Boolean = false,
    /**
     * Whether the PC keeps playing the sound as well.
     *
     * Off, because the usual reason to stream is that the user is not at the PC,
     * and audio coming out of an empty room is a surprise rather than a feature.
     */
    val playAudioOnHost: Boolean = false,

    val codec: StreamCodec = StreamCodec.AUTO,
    val audio: StreamAudio = StreamAudio.STEREO,
    val network: StreamNetwork = StreamNetwork.AUTO,

    /**
     * Whether the host may rewrite the game's own graphics settings.
     *
     * Off. "Optimal playable settings" is reasonable on a PC monitor and poor
     * here: it silently changes options the user chose, on their own machine,
     * and only NVIDIA's host ever implemented it properly.
     */
    val optimizeGameSettings: Boolean = false,

    // ---- The bottom panel --------------------------------------------------

    /**
     * Whether the second screen becomes a trackpad and keyboard while streaming.
     *
     * The reason the panel exists: a streamed desktop needs a pointer and needs
     * typing, and Android's own keyboard cannot be raised over a surface THOR
     * does not own — nor can it render on the second display at all.
     */
    val bottomPanel: Boolean = true,

    /** Pointer speed on the trackpad, as a multiplier of finger movement. */
    val trackpadSpeed: Float = 1.5f,

    /** Whether dragging two fingers scrolls the way the content moves. */
    val naturalScroll: Boolean = true,

    /** Whether a tap on the trackpad is a left click. */
    val tapToClick: Boolean = true,

    /**
     * Whether touching the video itself moves the pointer to that spot.
     *
     * Separate from the trackpad and useful for different things: this is
     * pointing at what you can see, the trackpad is for precision. Off by
     * default once the trackpad exists, because a stray touch while holding the
     * handheld would otherwise fling the cursor across the screen.
     */
    val touchVideoAsPointer: Boolean = false,

    // ---- Controls ----------------------------------------------------------

    /**
     * How far a stick must move before it counts.
     *
     * Sticks rest slightly off centre and drift with wear, and the stream sends a
     * packet on every change — so too small a value means a pad sitting untouched
     * produces a trickle of input and a character that slowly walks away.
     */
    val stickDeadZone: Float = 0.12f,

    /**
     * Whether Start opens the panel's settings instead of reaching the PC.
     *
     * On. It costs the game its Start button — a key press goes to one window,
     * and consuming it here means the PC never sees it — which is a real loss
     * worth stating. It is on because reaching the stream's own settings without
     * tearing the session down is worth more, and because the switch is here for
     * anyone who disagrees.
     */
    val startOpensSettings: Boolean = true,

    /** Whether to show round-trip time and frame rate over the picture. */
    val showStats: Boolean = false,
)

/**
 * One game or application a host can stream.
 *
 * The id is the host's own, and it is what `/launch` and `/resume` take — not the
 * title, which is a label a user renamed in Sunshine and may not be unique.
 */
@Serializable
data class StreamApp(
    val id: String,
    val title: String,
    /**
     * Whether the host will stream this one in HDR.
     *
     * Reported per app rather than per host: it depends on what the application
     * itself renders, so a machine capable of HDR still has a library where most
     * entries are not.
     */
    val hdr: Boolean = false,
) {
    /** Sunshine's own entry for the whole desktop, which every host has. */
    val isDesktop: Boolean get() = title.equals("Desktop", ignoreCase = true)
}
