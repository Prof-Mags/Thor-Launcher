package com.thor.core.display

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The lifecycle of the launcher's *second window*, which is not the activity's.
 *
 * This exists because of a specific and expensive failure. A `Presentation` created
 * by an activity used to borrow that activity's lifecycle owner, and its Compose
 * content was a subcomposition of the activity's — which meant two things when an app
 * was launched onto the activity's own display and covered it:
 *
 *  - the activity went to `STOPPED`, so every `collectAsStateWithLifecycle` in the
 *    second window stopped collecting, and
 *  - the activity's `Recomposer` paused its frame clock, so the second window stopped
 *    producing frames at all.
 *
 * The second panel therefore froze solid the moment anything opened on the first one,
 * and only came back when the launcher did. On a device whose entire point is running
 * something on one screen while using the other, that is not a bug to work around; it
 * is the wrong ownership.
 *
 * So the second window owns its own lifecycle, driven by whether it is *showing*
 * rather than by what the activity is doing. It shares the activity's
 * `ViewModelStore` — the two panels are one launcher and must read one state — but
 * nothing else.
 */
class PresentationHost : LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    init {
        /*
         * Restored with nothing, because this window has no state of its own to
         * restore: everything it renders comes from the shared view model. The
         * registry still has to be prepared, since Compose refuses to attach to a
         * tree whose saved-state owner has not been restored.
         */
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    /** The window is on screen: content composes, collects and animates. */
    fun onShown() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * The window is gone for good.
     *
     * Destroyed rather than stopped, because a dismissed presentation is not coming
     * back — the next one is a new window with a new host, and leaving this one
     * merely stopped would keep its composition and every subscription in it alive.
     */
    fun onDismissed() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
