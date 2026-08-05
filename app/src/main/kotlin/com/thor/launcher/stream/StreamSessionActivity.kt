package com.thor.launcher.stream

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.thor.core.common.log.ThorLog
import com.thor.core.datastore.SettingsRepository
import com.thor.core.ui.feedback.ThorFeedback
import com.thor.data.stream.SessionState
import com.thor.core.model.SessionQuality
import com.thor.data.stream.StreamInput
import com.thor.data.stream.StreamPad
import com.thor.feature.stream.panel.StreamPanelController
import com.thor.data.stream.StreamPresence
import com.thor.data.stream.StreamSessionManager
import com.thor.data.stream.StreamTouch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The window a stream is drawn in.
 *
 * An activity rather than another panel in the launcher, and for a reason that
 * is not presentation: the decoder renders into a `Surface`, and Compose has
 * none to give. It also wants the whole screen, the system bars gone, every
 * controller button to itself and the display kept awake — none of which the
 * launcher's own window can do while remaining a launcher.
 *
 * A plain view hierarchy, deliberately. There are exactly three things on screen
 * — a surface, a line of status text and nothing else — and a Compose tree over
 * a video surface would recompose against the frame rate for no gain.
 */
@AndroidEntryPoint
class StreamSessionActivity : ComponentActivity() {

    @Inject lateinit var sessions: StreamSessionManager

    @Inject lateinit var settings: SettingsRepository

    /**
     * The combination fires on the pad's own thread, so leaving is posted.
     *
     * `finish()` and the view work below it must happen on the main thread, and
     * an input callback arrives on whichever thread delivered the key.
     */
    private val input = StreamInput(
        onQuitCombo = { runOnUiThread(::leave) },
        onStart = { runOnUiThread { panel.toggleSettings() } },
    )

    private val touch = StreamTouch()

    /** The mouse and keyboard the second screen drives. */
    private val pad = StreamPad()

    /**
     * What the second screen is showing, and who owns the controller.
     *
     * Held by the activity rather than by the panel's composition, because the
     * keys that drive it arrive here — the panel is a non-focusable window and
     * receives none.
     */
    private val panel = StreamPanelController(pad) { cue -> feedback?.play(cue) }

    /**
     * The launcher's haptics and sound, for the on-screen keyboard.
     *
     * Built once the user's settings have been read, so it is null for the first
     * moments of a session — which is correct, and better than buzzing at a
     * volume and strength the user has not chosen. Nothing else in this window
     * produces feedback: the pad's own buttons belong to the PC.
     */
    private var feedback: ThorFeedback? = null

    /**
     * The settings this session was started with.
     *
     * Read once from the session rather than collected: changing resolution
     * mid-stream would mean renegotiating with the host, and the pointer and
     * keyboard options are read through [pad], which is updated in place.
     */
    private var quality: SessionQuality = SessionQuality()

    private lateinit var status: TextView
    private lateinit var surfaceView: SurfaceView

    /** Set once the core is connected, so teardown knows what it is undoing. */
    private var attached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * The screen stays on for as long as this window is in front.
         *
         * A stream is watched rather than touched — the user may go minutes
         * without pressing anything during a cutscene — and the display timing
         * out mid-game is indistinguishable from a crash.
         */
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        /*
         * Read once, from the session rather than from settings.
         *
         * These are the values the host actually agreed to. Reading the settings
         * store instead would describe what the user has since changed them to,
         * which is a different stream from the one on screen.
         */
        quality = sessions.quality ?: SessionQuality()
        input.updateSettings(quality)
        pad.updateSettings(quality)
        touch.updateSettings(quality)

        /*
         * Follows the launcher's feedback settings rather than vibrating on its
         * own account.
         *
         * The keyboard here is THOR's, so it should feel like THOR's everywhere
         * else — including being silent when the user has turned haptics off.
         * Collected rather than read once so a change made in settings applies
         * to a session already running.
         */
        lifecycleScope.launch {
            settings.controls.combine(settings.audio) { controls, audio -> controls to audio }
                .collect { (controls, audio) ->
                    feedback?.updateSettings(controls, audio)
                        ?: run { feedback = ThorFeedback(this@StreamSessionActivity, controls, audio) }
                }
        }

        surfaceView = SurfaceView(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = STATUS_TEXT_SP
            text = getString(
                com.thor.launcher.R.string.stream_connecting,
                sessions.pendingTitle.orEmpty(),
            )
        }

        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    status,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER },
                )

                /*
                 * A view with nothing in it, which owns the other screen.
                 *
                 * `SecondaryDisplay` is a composable side effect: it draws
                 * nothing where it sits and instead holds a window on the second
                 * display. It needs a composition to live in, and this activity
                 * is otherwise plain views — so it gets a zero-sized host rather
                 * than the whole screen being turned into Compose for it.
                 */
                addView(
                    ComposeView(this@StreamSessionActivity).apply {
                        setContent {
                            StreamPadHost(
                                pad = pad,
                                quality = quality,
                                controller = panel,
                                // So the panel's keyboard is the user's keyboard
                                // and not THOR's default one; see StreamPadHost.
                                settings = settings,
                            )
                        }
                    },
                    FrameLayout.LayoutParams(0, 0),
                )
            },
        )

        // After the view exists: the insets controller is built around it, and
        // calling this from the top of onCreate would touch it before it is set.
        goFullScreen()

        /*
         * The surface has to exist before the core can be told about it.
         *
         * `startConnection` configures a decoder against it during negotiation,
         * so starting from `onCreate` would hand across a surface that is not
         * ready — which fails inside MediaCodec, several layers below anywhere
         * the error could be explained.
         */
        surfaceView.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = connect(holder)

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int,
                ) = Unit

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    // The decoder is rendering into this surface; letting it go
                    // while frames are still being submitted is a native crash.
                    sessions.end()
                }
            },
        )

        watchSession()
    }

    private fun connect(holder: SurfaceHolder) {
        if (attached) return
        attached = true

        lifecycleScope.launch {
            /*
             * Off the main thread, because `attach` blocks.
             *
             * RTSP negotiation is several round trips performed inline by the
             * core. On the main thread that is a guaranteed ANR on any host
             * slower than instant.
             */
            val session = withContext(Dispatchers.IO) { sessions.attach(holder.surface) }

            if (session == null) {
                // Nothing was prepared — the window outlived its session, which
                // happens if it is recreated after the stream ended.
                ThorLog.i(TAG, "No session to attach to")
                finish()
            }
        }
    }

    /** Follows the session's own account of itself onto the screen. */
    private fun watchSession() {
        lifecycleScope.launch {
            /*
             * `collectLatest`, because the inner collect never finishes.
             *
             * A state flow is endless, so collecting it inside a plain `collect`
             * would strand the outer one on the first session — a second session
             * in the same window would then be watched by nobody, and the screen
             * would sit on the previous one's last state forever.
             */
            sessions.session.collectLatest { session ->
                if (session == null) return@collectLatest

                session.state.collect { state ->
                    when (state) {
                        is SessionState.Starting -> {
                            status.visibility = View.VISIBLE
                            status.text = getString(
                                com.thor.launcher.R.string.stream_stage,
                                state.stage,
                            )
                        }

                        SessionState.Streaming -> status.visibility = View.GONE

                        is SessionState.Ended -> {
                            /*
                             * A reason is worth stopping for; a clean end is not.
                             *
                             * Quitting a game ends the session normally and the
                             * user is already expecting to be back in THOR, so
                             * closing straight away is right. A failure is the
                             * opposite: vanishing without saying why is the
                             * behaviour that makes a stream feel unreliable even
                             * when the cause was a firewall.
                             */
                            if (state.reason == null) {
                                finish()
                            } else {
                                status.visibility = View.VISIBLE
                                status.text = state.reason
                                status.postDelayed(::finish, ERROR_DWELL_MS)
                            }
                        }

                        SessionState.Idle -> Unit
                    }
                }
            }
        }
    }

    /*
     * Every gamepad button goes to the game. Back is the one exception.
     *
     * A stream that closed on Back would be a stream in which no game could use
     * its own back action — and on a handheld the B button often *is* Back. So
     * Back is held for this window: a long press leaves, and a short one is
     * passed to the game as B.
     */
    /*
     * Back leaves, on the press rather than on a hold.
     *
     * It was a long press, on the reasoning that Back is often the handheld's B
     * button and games need it — but the hold never fired reliably on this
     * device, which left the stream with no way out but the four-button
     * combination. A gesture that works beats a tidier one that does not, and B
     * itself still reaches the game as `KEYCODE_BUTTON_B`.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Consumed here, acted on in onKeyUp, so a held Back does not repeat.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true

        // While the panel holds the controller, its keys are the keyboard's.
        if (panel.handleKey(event)) return true

        return if (input.onKey(event)) true else super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            /*
             * Back means "give the controller back" first, and only leaves once
             * the game already has it — otherwise typing would drop the stream.
             */
            if (panel.keyboardFocused) panel.releaseKeyboard() else leave()
            return true
        }

        if (panel.handleKey(event)) return true

        return if (input.onKey(event)) true else super.onKeyUp(keyCode, event)
    }

    /*
     * The panel gets first refusal on motion, not just on keys.
     *
     * A gamepad's D-pad is reported as the hat *axes* of a motion event rather
     * than as key codes, so routing only `onKeyDown` here left the on-screen
     * keyboard unreachable by the controller however hard the D-pad was pressed —
     * every direction went straight past it to the game.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (panel.handleMotion(event)) return true
        return if (input.onMotion(event)) true else super.onGenericMotionEvent(event)
    }

    /*
     * Touches reach the PC's pointer.
     *
     * Handled on the activity rather than by a listener on the surface, because
     * the surface is drawn into by the decoder and has no interest in input —
     * and a `SurfaceView` that consumed touches would have to be made focusable,
     * which would then compete with the window for controller keys.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (touch.onTouch(surfaceView, event)) true else super.onTouchEvent(event)

    override fun onResume() {
        super.onResume()
        // THOR's pointer service stands down while this is in front, so the
        // controller reaches the PC rather than moving a cursor over the picture.
        StreamPresence.begin()
    }

    override fun onPause() {
        super.onPause()
        StreamPresence.end()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            // The bars come back whenever the window is re-shown, so hiding them
            // is done on every focus gain rather than once at creation.
            goFullScreen()
        } else {
            /*
             * Everything held is released the moment focus goes.
             *
             * Key-up never arrives for a window that has lost focus, so whatever
             * was down stays down on the host — a trigger held as a notification
             * steals focus leaves the game accelerating with nobody driving.
             */
            input.releaseAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        input.releaseAll()
        sessions.end()

        /*
         * The sound pool is native memory and does not go with the activity.
         *
         * The launcher's own feedback is built by `rememberThorFeedback`, which
         * disposes it when its composition leaves. This one is constructed by
         * hand, so nothing was releasing it — every stream opened another audio
         * session and left it open, which is a leak that grows with each game
         * launched rather than one that shows up immediately.
         */
        feedback?.release()
        feedback = null
    }

    private fun leave() {
        input.releaseAll()
        sessions.end()
        finish()
    }

    /**
     * Hides the system bars, and keeps them hidden.
     *
     * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` matters as much as the hiding:
     * without it the bars reappear on the first touch or edge swipe and stay,
     * which on a handheld happens constantly just from holding it.
     */
    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, surfaceView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val TAG = "Stream"
        const val STATUS_TEXT_SP = 18f

        /** Long enough to read a sentence before the window closes itself. */
        const val ERROR_DWELL_MS = 4_000L
    }
}
