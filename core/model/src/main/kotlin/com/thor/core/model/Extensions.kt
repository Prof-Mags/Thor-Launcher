package com.thor.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A part of Loki that ships switched off until it is asked for.
 *
 * The launcher does far more than launch games, and not everyone wants all of
 * it. Someone who only plays their own ROMs has no use for a films section or a
 * streaming client, and a nav bar offering two sections they will never open —
 * plus five settings pages behind them — is the launcher being about itself
 * rather than about their library.
 *
 * So these are opt-in. Until one is enabled the launcher contains no trace of
 * it: no tab, no settings category, no pages, nothing to scroll past.
 *
 * The code is still in the build, which is a deliberate trade. Android has no
 * plugin mechanism a sideloaded app can use — feature delivery needs Play, a
 * separate app cannot draw inside Loki's own panels, and loading classes from a
 * foreign APK fights compile-time DI and the vendored native core. Shipping
 * everything and revealing it on request is the one arrangement that keeps each
 * section fully integrated where it *is* wanted.
 */
enum class LauncherExtension(
    /** Matches the `extension` field of a manifest, and is stored as-is. */
    val id: String,
    val displayName: String,
    val summary: String,
) {
    MOVIES(
        id = "movies",
        displayName = "Movies & TV",
        summary = "Browse films and shows, find sources for them, and play them " +
            "in the launcher.",
    ),
    STREAM(
        id = "stream",
        displayName = "PC streaming",
        summary = "Find PCs on your network, pair with Sunshine, and play what " +
            "they can stream.",
    ),
    ;

    companion object {
        fun byId(id: String): LauncherExtension? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * The file that turns one on.
 *
 * Deliberately tiny, and deliberately not a key. Anyone can write one of these
 * in a text editor, which is the point: it is a way of saying "I want this part"
 * that survives being backed up, shared and read by a human. It is not a lock,
 * and nothing here should ever be mistaken for one.
 *
 * ```json
 * { "extension": "movies", "name": "Movies & TV", "version": 1 }
 * ```
 *
 * Only [extension] is read. The rest is there so the file explains itself when
 * somebody opens it a year later.
 */
@Serializable
data class ExtensionManifest(
    @SerialName("extension") val extension: String,
    @SerialName("name") val name: String? = null,
    @SerialName("version") val version: Int = 1,
) {
    /** The extension this names, or null if it names nothing Loki has. */
    val resolved: LauncherExtension? get() = LauncherExtension.byId(extension)
}
