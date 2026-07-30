package com.thor.core.model

import kotlinx.serialization.Serializable

/**
 * A launch that has not yet been accounted for.
 *
 * Play time cannot be measured in memory. The launcher is frequently killed
 * while a game is in the foreground — that is the whole point of handing the
 * device over to the emulator — so a session held in a field is lost exactly
 * when it matters. Persisting the open session means the elapsed time is
 * recoverable whenever the launcher next runs, including after a reboot.
 */
@Serializable
data class PendingPlaySession(
    val entryId: String,
    /**
     * Wall-clock start time.
     *
     * Wall clock rather than `elapsedRealtime`, because that resets on reboot
     * and a session interrupted by one would be unrecoverable.
     */
    val startedAtEpochMs: Long,
)

/**
 * Runtime playback bookkeeping, persisted separately from user settings.
 *
 * Kept out of [ThorSettings] deliberately: that document is the user's
 * configuration, and it is exported, imported and backed up as such. An open
 * session is transient machine state and has no business travelling with it.
 */
@Serializable
data class PlaybackState(
    val pending: PendingPlaySession? = null,
) {
    companion object {
        val EMPTY = PlaybackState()

        /**
         * Longest session that will be credited, in milliseconds.
         *
         * A session is closed by the launcher coming back, so one that was never
         * closed — the device was powered off mid-game, or the clock was
         * adjusted — would otherwise be credited with days of play. Twelve hours
         * is well beyond any real handheld session and well short of absurd.
         */
        const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000
    }
}
