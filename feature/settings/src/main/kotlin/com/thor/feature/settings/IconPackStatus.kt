package com.thor.feature.settings

/**
 * What the last icon-pack import did.
 *
 * [Installed] carries counts rather than a bare "done" because an import can
 * legitimately be partial: a pack covers the platforms THOR models, and holds
 * artwork for the ones it does not. A user who imported a 29-platform pack and
 * saw 26 dressed deserves to know the other three were kept rather than dropped —
 * otherwise the sensible conclusion is that the pack was faulty.
 */
sealed interface IconPackStatus {
    data object Idle : IconPackStatus

    /** Copying. Packs run to tens of megabytes, so this is visible for seconds. */
    data object Working : IconPackStatus

    data class Installed(val name: String, val applied: Int, val held: Int) : IconPackStatus

    data class Failed(val reason: String) : IconPackStatus

    /** One line describing this state, or null when there is nothing to say. */
    val message: String?
        get() = when (this) {
            Idle -> null
            Working -> "Importing…"
            is Installed -> buildString {
                append("Installed $name — $applied platform")
                if (applied != 1) append("s")
                if (held > 0) {
                    append(", $held held for systems Loki does not have yet")
                }
            }

            is Failed -> reason
        }
}
