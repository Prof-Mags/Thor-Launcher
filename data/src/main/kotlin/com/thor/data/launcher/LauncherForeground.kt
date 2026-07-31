package com.thor.data.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether THOR is the activity the user is actually interacting with.
 *
 * Reported by the launcher activity from `onTopResumedActivityChanged`, which is
 * the one signal Android gives for "this is the thing in front" — as distinct
 * from window focus, which moves whenever the user touches the launcher's other
 * panel while an app keeps the first, and from lifecycle state, which stays
 * resumed for a launcher that is completely covered on its own display.
 *
 * Exists for exactly one question: after a launch, did anything actually come to
 * the foreground? `startMainActivity` returning without throwing does not answer
 * it — a ROM can queue the app, refuse it silently, or bring it up behind — and
 * the launcher has to know, because it has already given the panel away by then.
 *
 * A separate holder rather than a field on the view model because the activity
 * cannot reach the view model, and rather than a flag on some larger object
 * because a signal this easy to misuse is better off with its own name and its
 * own note about what it does not mean.
 */
@Singleton
class LauncherForeground @Inject constructor() {

    private val _topResumed = MutableStateFlow(false)
    val topResumed: StateFlow<Boolean> = _topResumed.asStateFlow()

    fun setTopResumed(topResumed: Boolean) {
        _topResumed.value = topResumed
    }
}
