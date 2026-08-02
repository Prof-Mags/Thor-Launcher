package com.thor.data.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.thor.core.common.log.ThorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The system clipboard, as THOR's keyboard can reach it.
 *
 * **This is the same clipboard everything else uses**, which is the whole of the
 * "sync": copy a link in a browser with the system keyboard and it is here, and
 * anything copied here is available to that keyboard in turn. There is no separate
 * THOR clipboard to keep in step, because Android has one primary clip and every
 * app shares it.
 *
 * Two limits are worth stating plainly, both of them Android's rather than
 * choices:
 *
 *  - **Reading needs focus.** Since Android 10, only the foreground app or the
 *    active input method may read the clipboard. THOR is neither while a game is
 *    running, so the clip is read when the keyboard opens — at which point the
 *    launcher *is* in front — rather than watched continuously.
 *  - **Another keyboard's clipboard *history* is not readable.** Gboard's list of
 *    recent clips is Gboard's own storage with no API over it. Only the current
 *    primary clip is shared. The history below is what THOR has seen itself.
 *
 * The history is deliberately in memory only. Clipboards carry passwords, one-time
 * codes and card numbers; writing that to disk so a launcher can offer a nicer
 * paste menu is a poor trade, and it would outlive the reason the user copied it.
 */
@Singleton
class ThorClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: ClipboardManager? =
        context.getSystemService(ClipboardManager::class.java)

    private val _history = MutableStateFlow<List<String>>(emptyList())

    /** Clips THOR has seen, most recent first. Never persisted. */
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /**
     * Reads the current clip and folds it into the history.
     *
     * Called when the keyboard opens rather than from a change listener: the
     * listener only fires while THOR has focus anyway, so it would miss every clip
     * copied in another app — which is most of them — while costing a callback for
     * the ones it would catch regardless.
     *
     * On Android 12 and later this surfaces a system toast saying THOR pasted from
     * the clipboard. That is the platform telling the user the truth, and is not
     * suppressible.
     */
    fun refresh() {
        val clip = runCatching { manager?.primaryClip }
            .onFailure { error -> ThorLog.w(TAG, "Clipboard unreadable", error) }
            .getOrNull()
            ?: return

        val text = (0 until clip.itemCount)
            .asSequence()
            .mapNotNull { index -> clip.getItemAt(index)?.coerceToText(context)?.toString() }
            .firstOrNull { it.isNotBlank() }
            ?: return

        remember(text)
    }

    /** Puts [text] on the system clipboard, where every other app can reach it. */
    fun copy(text: String, label: String = DEFAULT_LABEL) {
        if (text.isBlank()) return
        runCatching { manager?.setPrimaryClip(ClipData.newPlainText(label, text)) }
            .onFailure { error -> ThorLog.w(TAG, "Could not set the clipboard", error) }
        remember(text)
    }

    /** Forgets everything THOR has seen. The system clip is untouched. */
    fun clearHistory() {
        _history.value = emptyList()
    }

    /**
     * Adds a clip to the front, de-duplicated.
     *
     * Re-copying something already in the list moves it up rather than adding a
     * second copy — a paste menu with the same string three times is a menu that
     * has to be read rather than glanced at.
     */
    private fun remember(text: String) {
        _history.update { current ->
            (listOf(text) + current.filterNot { it == text }).take(MAX_HISTORY)
        }
    }

    private companion object {
        const val TAG = "Clipboard"
        const val DEFAULT_LABEL = "Loki"

        /**
         * Short on purpose. This is a paste menu reachable from a cursor on a
         * handheld, not an archive — past about this many the list is quicker to
         * scroll past than to read.
         */
        const val MAX_HISTORY = 8
    }
}
