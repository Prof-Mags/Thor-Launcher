package com.thor.core.common.log

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Records uncaught exceptions before the process dies.
 *
 * A launcher that force-closes leaves the user on a black screen with no shell,
 * and by the time they can plug in a cable the logcat buffer has usually rolled
 * over. Writing the trace to a file in the app's own storage means the crash is
 * still recoverable afterwards, which is the only way to diagnose the
 * intermittent ones.
 *
 * The previously installed handler is always invoked afterwards. Swallowing the
 * exception instead would leave the process alive in an unknown state, which is
 * worse than a clean restart — the system restarts the default launcher
 * immediately anyway.
 */
object CrashReporter {

    private const val TAG = "Crash"
    private const val FILE_NAME = "thor-crash.log"

    /** Keeps the file bounded; only the most recent crashes are of any use. */
    private const val MAX_BYTES = 256 * 1024

    @Volatile
    private var logDirectory: File? = null

    /**
     * Installs the handler.
     *
     * @param directory app-private directory the trace is written to, normally
     *   `Context.filesDir`. Passed in rather than resolved here so this stays
     *   free of Android imports and remains unit-testable.
     */
    fun install(directory: File) {
        logDirectory = directory
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(thread.name, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Logs a non-fatal failure that was caught and handled.
     *
     * Used by [com.thor.core.common.coroutines.launchSafely] so a background
     * failure is still visible without taking the launcher down with it.
     */
    fun nonFatal(tag: String, message: String, throwable: Throwable) {
        ThorLog.e(tag, message, throwable)
        runCatching { append("NON-FATAL [$tag] $message", throwable) }
    }

    /** The recorded trace, or null when nothing has crashed. */
    fun lastCrash(): String? = crashFile()?.takeIf(File::exists)?.let { file ->
        runCatching { file.readText() }.getOrNull()
    }

    fun clear() {
        crashFile()?.let { file -> runCatching { file.delete() } }
    }

    private fun record(threadName: String, throwable: Throwable) {
        append("FATAL on thread $threadName", throwable)
    }

    private fun append(header: String, throwable: Throwable) {
        val file = crashFile() ?: return
        // Truncated rather than rotated: a second file to manage buys nothing
        // when only the newest trace is ever read.
        if (file.exists() && file.length() > MAX_BYTES) file.delete()
        val trace = StringWriter().also { writer ->
            PrintWriter(writer).use(throwable::printStackTrace)
        }
        file.appendText(
            buildString {
                append("=== ")
                append(header)
                append(" @ ")
                append(System.currentTimeMillis())
                append(" ===\n")
                append(trace.toString())
                append('\n')
            },
        )
    }

    private fun crashFile(): File? = logDirectory?.let { File(it, FILE_NAME) }
}
