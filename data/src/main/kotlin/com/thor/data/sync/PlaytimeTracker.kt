package com.thor.data.sync

import com.thor.core.common.coroutines.safely
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.PlaybackStateRepository
import com.thor.core.model.PlaybackState
import com.thor.data.library.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures how long each entry is actually played for.
 *
 * The launcher cannot observe a game while it runs — it is a separate process on
 * top, and the launcher is routinely killed to free memory for it. So a session
 * is bracketed instead of timed: the launch writes an open session to disk, and
 * the next time the launcher is in front it closes it against the wall clock.
 * That works whether the launcher merely lost focus, was killed, or the device
 * rebooted in between.
 *
 * Only one session is ever open. Launching something else while a session is
 * open closes the first — the user cannot be playing two things at once, and the
 * alternative is a session that never settles.
 */
@Singleton
class PlaytimeTracker @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackState: PlaybackStateRepository,
) {

    /**
     * Records a launch and opens a session for it.
     *
     * Settles any previous session first, so switching directly from one game to
     * another credits the first with the time it actually had.
     */
    suspend fun onLaunched(entryId: String, now: Long = System.currentTimeMillis()) {
        settle(now)
        safely(TAG) {
            libraryRepository.recordLaunch(entryId, now)
            playbackState.begin(entryId, now)
        }
    }

    /**
     * Closes the open session, if any, crediting the elapsed time.
     *
     * Safe to call repeatedly and from more than one trigger: the pending session
     * is taken and cleared atomically, so whichever caller gets it is the only
     * one that credits it.
     */
    suspend fun settle(now: Long = System.currentTimeMillis()) {
        safely(TAG) {
            val pending = playbackState.takePending() ?: return@safely
            val elapsed = now - pending.startedAtEpochMs

            // A negative span means the clock moved backwards; an implausibly
            // long one means the session never really closed. Neither is play
            // time, and crediting either corrupts the totals permanently.
            if (elapsed !in 1..PlaybackState.MAX_SESSION_MILLIS) {
                ThorLog.w(
                    TAG,
                    "Discarding implausible session for ${pending.entryId}: ${elapsed}ms",
                )
                return@safely
            }

            // Sessions shorter than this are the user bouncing off the wrong
            // icon, not playing. Recording them inflates "times played" and
            // clutters recently-played with mistakes.
            if (elapsed < MIN_CREDITED_MILLIS) return@safely

            libraryRepository.recordSession(
                entryId = pending.entryId,
                startedAt = pending.startedAtEpochMs,
                durationMillis = elapsed,
            )
            ThorLog.d(TAG) { "Credited ${elapsed}ms to ${pending.entryId}" }
        }
    }

    private companion object {
        const val TAG = "Playtime"

        /** Below this, a launch is treated as a misfire rather than a session. */
        const val MIN_CREDITED_MILLIS = 5_000L
    }
}
