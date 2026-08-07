package com.thor.core.common.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A way for the launcher to take a picture of a screen it does not own.
 *
 * An app can only capture the display it is drawing on, and the whole point here
 * is the opposite: the interesting screenshot is of the *game*, which is another
 * app on another display. Android offers two routes to that and only one of them
 * is usable here.
 *
 * `MediaProjection` is the familiar one and is what the screen recorder uses, but
 * it asks the system's consent dialog for every projection. Mid-game that dialog
 * is on top of the thing being photographed, which makes it useless for the one
 * moment a screenshot is worth taking.
 *
 * `AccessibilityService.takeScreenshot` is the other, and it needs no dialog —
 * the permission was granted once, when the pointer service was enabled. So the
 * capability lives on that service, and this is how the rest of the launcher
 * reaches it: the service binds itself on connect and clears itself on teardown,
 * exactly as [com.thor.core.input.MouseController] is shared with it.
 *
 * Held as a singleton rather than passed, because a service's lifetime and a view
 * model's have nothing to do with each other and either may outlive the other.
 */
@Singleton
class ScreenshotBridge @Inject constructor() {

    private var capture: (suspend (displayId: Int) -> ByteArray?)? = null

    private val _available = MutableStateFlow(false)

    /**
     * Whether a screenshot can be taken at all.
     *
     * Observed rather than asked, so a surface offering the action can say it is
     * unavailable instead of failing when pressed. It is false whenever the
     * pointer service is off, which is a setting the user controls and may never
     * have turned on — a screenshot button that silently did nothing would be
     * indistinguishable from a broken one.
     */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    /** Called by the accessibility service as it connects. */
    fun bind(block: suspend (displayId: Int) -> ByteArray?) {
        capture = block
        _available.value = true
    }

    /** Called as the service goes away, so nothing holds a dead reference to it. */
    fun unbind() {
        capture = null
        _available.value = false
    }

    /**
     * A PNG of the given display, or null if nothing can take one.
     *
     * Null rather than an exception: the service being off is an ordinary state
     * and not an error, and every caller has the same thing to do about it.
     */
    suspend fun capture(displayId: Int): ByteArray? = capture?.invoke(displayId)

    // ---- The other direction: the launcher telling the service things --------

    private val _nowPlaying = MutableStateFlow("")

    /**
     * What the launcher last handed a panel to.
     *
     * Published here rather than discovered, because the alternative is the
     * service asking the system which app is in front — and the reason this
     * service can be trusted with the permissions it holds is that it reads
     * nothing about any app. The launcher already knows the answer; this is it
     * saying so.
     */
    val nowPlaying: StateFlow<String> = _nowPlaying.asStateFlow()

    fun setNowPlaying(title: String?) { _nowPlaying.value = title.orEmpty() }

    private var fileCapture: (suspend () -> Unit)? = null

    /**
     * Installed by the launcher: take a shot and file it against the right game.
     *
     * The service can produce a PNG but has no idea which entry it belongs to, and
     * no way to write into the active profile. So the overlay asks for the whole
     * operation rather than for a frame, and it lands in exactly the same place as
     * a capture started from the launcher's own tile — one code path, one set of
     * rules about attribution.
     */
    fun onCaptureRequested(block: suspend () -> Unit) { fileCapture = block }

    suspend fun captureAndFile() { fileCapture?.invoke() }
}
