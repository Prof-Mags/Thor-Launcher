package com.thor.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The bounds that decide whether an open session is credited.
 *
 * These matter because a session is closed by the launcher coming back, and
 * there is no guarantee that ever happens cleanly — the device can be powered
 * off mid-game, or the clock can be adjusted underneath a running session. Both
 * produce spans that are not play time, and crediting either corrupts a game's
 * totals permanently.
 */
class PlaybackStateTest {

    @Test
    fun `empty state has no open session`() {
        assertThat(PlaybackState.EMPTY.pending).isNull()
    }

    @Test
    fun `a plausible handheld session is inside the ceiling`() {
        val threeHours = 3L * 60 * 60 * 1000
        assertThat(threeHours).isLessThan(PlaybackState.MAX_SESSION_MILLIS)
    }

    @Test
    fun `a session left open for days is outside the ceiling`() {
        val twoDays = 2L * 24 * 60 * 60 * 1000
        assertThat(twoDays).isGreaterThan(PlaybackState.MAX_SESSION_MILLIS)
    }

    @Test
    fun `pending session round trips its fields`() {
        val session = PendingPlaySession(entryId = "game:abc", startedAtEpochMs = 1_700_000_000_000)

        assertThat(session.entryId).isEqualTo("game:abc")
        assertThat(session.startedAtEpochMs).isEqualTo(1_700_000_000_000)
    }
}
