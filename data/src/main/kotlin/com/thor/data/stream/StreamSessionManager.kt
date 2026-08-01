package com.thor.data.stream

import android.view.Surface
import com.thor.core.common.log.ThorLog
import com.thor.core.model.SessionQuality
import com.thor.core.model.StreamApp
import com.thor.core.model.StreamHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the session between the screen that starts it and the one that shows it.
 *
 * The launch happens in the Stream section — it is slow, and its progress and
 * failures belong on the panel the user is already looking at — while the
 * picture needs a window of its own. Something has to carry the agreed session
 * across that gap.
 *
 * Deliberately **not** an Intent extra. A [LaunchedSession] carries the AES key
 * the whole stream is encrypted with, and an Intent is a message the system
 * copies, logs and can hand to whatever claims the action. Keeping it in the
 * process means the key never leaves it.
 */
@Singleton
class StreamSessionManager @Inject constructor(
    private val repository: StreamRepository,
) {

    private val _session = MutableStateFlow<StreamSession?>(null)

    /** The running session, for whoever is drawing it. */
    val session: StateFlow<StreamSession?> = _session.asStateFlow()

    /** Agreed with the host but not yet given a surface to draw on. */
    private var pending: LaunchedSession? = null

    /** What the pending session is of, so the window can name it while it waits. */
    val pendingTitle: String? get() = pending?.app?.title

    /**
     * The settings the current session was agreed under.
     *
     * Held so the streaming window can read them without injecting the settings
     * store: it needs the pointer and keyboard preferences, and the resolution
     * the host actually agreed to rather than whatever the user has since
     * changed the setting to.
     */
    val quality: SessionQuality? get() = (pending ?: _session.value?.launched)?.quality

    /**
     * Asks the host to start [app], and keeps the result for the window to claim.
     *
     * Throws [LaunchFailure] with something worth reading when the host refuses.
     */
    suspend fun prepare(
        host: StreamHost,
        app: StreamApp,
        onStage: (LaunchStage) -> Unit = {},
    ): LaunchedSession {
        /*
         * Anything already running is torn down first.
         *
         * A GameStream host serves one session, and so does this: leaving an old
         * one attached would keep its decoder holding a surface that has been
         * destroyed, which is a crash inside MediaCodec rather than an error.
         */
        end()

        val launched = repository.launch(host, app, onStage)
        pending = launched
        return launched
    }

    /**
     * Binds the prepared session to a surface and connects.
     *
     * Blocking: the core performs the whole RTSP negotiation inline. Call it off
     * the main thread.
     *
     * @return the session, or null when there was nothing prepared — which is
     *   what happens if the window is recreated after the session has ended, and
     *   is a reason to close rather than an error to report.
     */
    fun attach(surface: Surface): StreamSession? {
        val launched = pending ?: return null
        pending = null

        val session = StreamSession(launched, surface)
        _session.value = session

        /*
         * Left in place even when it fails to start, deliberately.
         *
         * The session's own state is what carries the reason, and clearing the
         * holder here would cancel whoever is collecting it — in a race with the
         * failure being published, so the window would show either the error or
         * nothing at all depending on which won. The window closes itself on an
         * ended session; that is what clears this.
         */
        session.start()
        return session
    }

    /** Ends whatever is running, and forgets anything prepared. */
    fun end() {
        pending = null
        _session.value?.let { session ->
            ThorLog.i(TAG, "Ending the session")
            session.stop()
        }
        _session.value = null
    }

    /**
     * Tells the host to stop streaming entirely, rather than just disconnecting.
     *
     * Two different intentions: leaving a session lets it be resumed and keeps
     * the game running on the PC, while quitting closes it. Both are wanted, and
     * a client that only did the first leaves games running on a machine nobody
     * is at.
     */
    suspend fun quit(host: StreamHost): Boolean {
        end()
        return repository.stopStreaming(host)
    }

    private companion object {
        const val TAG = "Stream"
    }
}
