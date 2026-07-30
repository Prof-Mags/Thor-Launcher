package com.thor.data.launcher

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home presses, from whichever display they were made on.
 *
 * Android routes `CATEGORY_HOME` **per display**: pressing Home while the focused
 * display is the second panel starts that display's *secondary* home activity, not
 * the one on the main panel. A launcher that declares only a primary home therefore
 * never hears about it — the system starts its own default launcher on that panel
 * instead, which is exactly the stock home screen appearing where THOR should be.
 *
 * So THOR declares a secondary home as well, and both of them report here rather than
 * each trying to be the launcher. Whichever activity the system chooses to deliver the
 * intent to, the launcher gets told once.
 */
@Singleton
class HomeRequests @Inject constructor() {

    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once per Home press, whichever display it arrived on. */
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun onHomePressed() {
        _requests.tryEmit(Unit)
    }
}
