package com.thor.core.model

/**
 * What the user recorded about a game, as opposed to what was scraped about it.
 *
 * The distinction is the whole point of the type. Everything in [GameMetadata]
 * arrives from one of five providers and is merged and overwritten on every
 * rescrape; everything here was made by the person holding the device and may
 * never be overwritten by anything. Keeping them apart means no rule has to
 * remember which fields are which.
 */
data class GameJournal(
    val note: GameNote? = null,
    val screenshots: List<Screenshot> = emptyList(),
) {
    val isEmpty: Boolean get() = note == null && screenshots.isEmpty()

    /** Whether there is anything worth offering to show. */
    val hasContent: Boolean get() = !isEmpty

    companion object {
        val EMPTY = GameJournal()
    }
}

/**
 * A note the user wrote about a game.
 *
 * One per game rather than a list, because the question it answers is "where was
 * I" and that has one current answer. A log of entries would be a different
 * feature — one where the old answers still matter — and would ask the user to
 * scroll to find out where they got to, which is the thing this exists to avoid.
 */
data class GameNote(
    val entryId: String,
    val body: String,
    val updatedAtEpochMs: Long,
) {
    /**
     * The first line, for the surfaces with room for one line.
     *
     * The first line rather than a truncation of the whole, because a note is
     * written by someone who knows a summary is useful — the habit of putting the
     * important part first is worth rewarding, and a mid-sentence cut is not.
     */
    val summary: String get() = body.lineSequence().firstOrNull()?.trim().orEmpty()

    companion object {
        /**
         * Long enough for a paragraph, short enough to stay a note.
         *
         * Enforced on the way in rather than at the storage layer, so the limit is
         * a fact about the feature rather than about SQLite.
         */
        const val MAX_LENGTH = 2_000
    }
}

/**
 * One captured frame, identified by where it is rather than by a database row.
 *
 * Deliberately not a table. The files are the record: a directory listing is the
 * query, deleting the file is the delete, and a backup that copies the profile
 * directory carries them with no schema to keep in step. A row per file would add
 * a second source of truth that could disagree with the disk, and every way it
 * disagrees — a file removed underneath, a row for a file that never landed —
 * shows as a broken image with nothing to explain it.
 */
data class Screenshot(
    val entryId: String,
    /** Absolute path; the file is the identity. */
    val path: String,
    val capturedAtEpochMs: Long,
    val sizeBytes: Long,
)
