package com.thor.core.common.log

import android.util.Log

/**
 * Thin logging facade.
 *
 * Verbose and debug output is compiled against [enabled], which the developer
 * settings toggle at runtime, so a shipped build stays quiet without needing a
 * separate no-op implementation.
 */
object ThorLog {

    private const val TAG_PREFIX = "THOR/"

    @Volatile
    var enabled: Boolean = false

    fun v(tag: String, message: () -> String) {
        if (enabled) Log.v(TAG_PREFIX + tag, message())
    }

    fun d(tag: String, message: () -> String) {
        if (enabled) Log.d(TAG_PREFIX + tag, message())
    }

    fun i(tag: String, message: String) {
        Log.i(TAG_PREFIX + tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG_PREFIX + tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG_PREFIX + tag, message, throwable)
    }
}
