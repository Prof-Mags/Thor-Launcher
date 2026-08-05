package com.thor.feature.settings.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier

/**
 * Scrolls this row into view whenever it takes the cursor.
 *
 * Controller focus is the launcher's own concept, not the framework's, so no
 * scroll container knows to follow it — moving down a pane with the D-pad
 * walked the highlight straight off the bottom of the screen while the list
 * stayed put. `BringIntoViewRequester` asks the nearest scrollable ancestor to
 * reveal this element, which works regardless of how the pane is chunked into
 * lazy items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.revealWhenFocused(focused: Boolean): Modifier {
    val requester = remember { BringIntoViewRequester() }

    LaunchedEffect(focused) {
        if (focused) {
            // Yielding a frame lets the row be laid out before its bounds are
            // requested; asking during the same composition reveals the
            // position it had *before* this change.
            withFrameNanos { }
            runCatching { requester.bringIntoView() }
        }
    }

    return this.bringIntoViewRequester(requester)
}
