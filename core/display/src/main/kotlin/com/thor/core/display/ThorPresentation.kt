package com.thor.core.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A Compose-hosting window on a secondary display.
 *
 * A [Presentation] creates its own window with its own decor view, and Compose
 * refuses to attach without lifecycle, saved-state and view-model owners on the view
 * tree. Two of those are its own and one is borrowed, and the split is the whole
 * point of this class:
 *
 *  - **Lifecycle and saved state are its own** ([PresentationHost]), so this window
 *    keeps composing, collecting and animating while the activity is stopped behind
 *    an app on the *other* display. Borrowing the activity's froze this panel solid
 *    whenever anything opened on the first one.
 *  - **The `ViewModelStore` is the activity's**, because the two panels are one
 *    launcher reading one state object. A selection change updates both with no
 *    cross-window messaging at all.
 *
 * It deliberately does *not* take the activity's composition context. A
 * subcomposition is driven by its parent's `Recomposer`, and that recomposer pauses
 * its frame clock when the activity stops — which is the second half of the same
 * freeze. This window runs its own recomposer, and the caller provides the theme and
 * any composition locals inside [content], since none can be inherited across the
 * boundary.
 */
class ThorPresentation(
    outerContext: Context,
    display: Display,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val content: @Composable () -> Unit,
) : Presentation(outerContext, display) {

    /** This window's own lifecycle, resumed while it is showing. */
    private val host = PresentationHost()

    constructor(
        activity: ComponentActivity,
        display: Display,
        content: @Composable () -> Unit,
    ) : this(
        outerContext = activity,
        display = display,
        viewModelStoreOwner = activity,
        content = content,
    )

    /**
     * Physical input received while this window holds focus.
     *
     * Only the focused window is sent key events, and on a dual-screen device the
     * focused one is whichever panel the user last touched. Without a dispatcher
     * here, this panel could take focus by being touched and then respond to no
     * button at all — so an app running on the other display left the launcher's
     * own surface visible and inert.
     *
     * Points at the same router the activity uses, so both panels drive one cursor.
     */
    var keyDispatcher: ((KeyEvent) -> Boolean)? = null

    /** Analog sticks and triggers, which arrive separately from key events. */
    var motionDispatcher: ((MotionEvent) -> Boolean)? = null

    /**
     * Reports this window gaining or losing the device's key focus.
     *
     * The only way the launcher can learn that the user touched the *other* display
     * when what is on it belongs to somebody else. An app covering the other panel
     * consumes that touch entirely — no view of ours sees it — so the one observable
     * consequence is this window quietly losing focus. Without noticing that, the
     * launcher went on holding focus and the app the user had just tapped received
     * nothing.
     */
    var focusListener: ((Boolean) -> Unit)? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        focusListener?.invoke(hasFocus)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyDispatcher?.invoke(event) == true) return true

        /*
         * Back and Escape are swallowed rather than passed on.
         *
         * `Dialog`'s own handling of them is to dismiss itself, and this window is
         * a whole panel of the launcher — losing it would uncover whatever the
         * system keeps behind it on that display. The router already consumes Back
         * while it is routing; this covers the moments it deliberately does not,
         * such as while a text field is collecting input.
         */
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) return true

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        motionDispatcher?.invoke(event) == true || super.dispatchGenericMotionEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            // Presentation already assigns the correct presentation window
            // type; only the background and input behaviour need adjusting.
            setBackgroundDrawableResource(android.R.color.transparent)
            // Normally unfocusable: all key input is captured by the hosting
            // activity's dispatchKeyEvent and routed manually, so a focusable
            // second window would divert events away from that path and break
            // controller navigation. Touch still reaches this window.
            //
            // See [setFocusable] for the exception — a text field on this panel
            // cannot be typed into while the window refuses focus.
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            )
            decorView.apply {
                setViewTreeLifecycleOwner(host)
                setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                setViewTreeSavedStateRegistryOwner(host)
            }

            // This window has its own insets controller, so full screen has to
            // be requested here too — hiding the bars on the activity alone left
            // the notification strip visible on this panel.
            hideSystemBars(this)
        }

        val composeView = ComposeView(context).apply {
            /*
             * Dispose when this window's view detaches, not when the activity is
             * destroyed. The presentation is dismissed and recreated every time the
             * launcher hands its panel over and takes it back; tying disposal to the
             * activity meant each dismissal left its composition alive and still
             * subscribed, so the accumulated compositions all drove the same state.
             *
             * No parent composition context is set, deliberately. That would make
             * this a subcomposition of the activity's — and a subcomposition is
             * driven by its parent's `Recomposer`, whose frame clock pauses when the
             * activity stops. This window would then stop drawing the instant
             * anything opened on the *other* display, which is the freeze this class
             * exists to not have. Its own recomposer is built from the view tree's
             * lifecycle owner, which is [host], which is resumed while it is showing.
             */
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(host)
            setContent(content)
        }

        setContentView(composeView)
    }

    override fun show() {
        super.show()
        // Resumed only once the window is actually up, so the composition starts
        // against a real surface rather than against one that may never appear.
        host.onShown()
    }

    override fun dismiss() {
        // Destroyed *before* the window goes, so the composition unsubscribes while
        // its view is still attached and cannot be left running against a window
        // that no longer exists.
        host.onDismissed()
        super.dismiss()
    }

    override fun onStop() {
        super.onStop()
        /*
         * A Presentation can be stopped when its owning activity is covered on
         * the other panel while the presentation itself is still visible.  That
         * is the normal dual-screen case. Destroying its independent lifecycle
         * here leaves the window on screen but permanently unable to recompose;
         * the shared state and haptics keep changing, so it looks exactly like a
         * frozen grid. `dismiss()` is the definitive teardown path and already
         * destroys the host before the window is removed.
         */
    }

    /**
     * Lets this window take focus.
     *
     * `FLAG_NOT_FOCUSABLE` blocks two separate things. A window that refuses focus
     * can hold no focused view, so a text field inside it never becomes editable
     * and the soft keyboard is never requested. It also never becomes the key
     * target, so touching this panel delivered the touch but left every button
     * going to whatever was focused before — which is why an app running on the
     * other display made this surface unresponsive to the controller no matter how
     * many times it was tapped.
     *
     * Toggled rather than set once, because focus here costs the activity its own:
     * when this panel is not the one the user is working on, the activity should
     * keep the focus and the IME.
     *
     * Whether the IME will *render* on a secondary display remains up to the
     * platform; this removes only the launcher's own obstacle to it.
     */
    fun setFocusable(focusable: Boolean) {
        val window = window ?: return

        /*
         * Nothing is touched unless the flag actually differs.
         *
         * Changing window flags on a window that is already shown asks the window
         * manager to re-evaluate focus for the whole display; doing that when nothing
         * has changed is a needless chance for the display to end up with no focused
         * window at all, which reads as every button doing nothing.
         */
        val current = window.attributes.flags and
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        if (current != focusable) return

        if (focusable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            // Nothing more to set: `SOFT_INPUT_ADJUST_RESIZE` was here, and is both
            // deprecated and redundant now that this window is laid out with
            // `setDecorFitsSystemWindows(false)` — the content handles the keyboard
            // through its own IME insets instead of the window being resized.
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }
}
