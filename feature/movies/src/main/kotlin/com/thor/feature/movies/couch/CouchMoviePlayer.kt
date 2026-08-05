package com.thor.feature.movies.couch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.thor.feature.movies.player.ThorPlayer

/**
 * The film, and nothing else until you ask.
 *
 * Couch mode uses the stock `PlayerView` rather than the launcher's own
 * transport. On the handheld the two panels are the reason for a custom console:
 * the picture is on one screen and the controls on the other, which no
 * off-the-shelf view is built to do. A television has one screen and one
 * expectation — tap, and the controls a video player has everywhere else appear
 * over the picture: a scrub bar you can press anywhere on, skip back, skip
 * forward, play and pause.
 *
 * Nothing here re-implements any of that. `PlayerView` already shows and hides
 * itself on tap, times its own dismissal, follows the player's state and handles
 * a scrub that lands mid-buffer. Reproducing it in Compose to make it look
 * bespoke would be a great deal of work spent making something less familiar.
 */
@Composable
fun CouchMoviePlayer(
    player: ThorPlayer,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = true
                    // Fitted, never cropped: a film is framed as its director
                    // framed it, and the black bars are part of that.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    /*
                     * Nothing here manages a playlist, so the track buttons would
                     * be permanently dead controls, and the subtitle button opens
                     * a menu over a screen the user is driving with a pad.
                     */
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowSubtitleButton(false)
                    controllerShowTimeoutMs = CONTROLLER_TIMEOUT_MS
                    // Starts hidden: the point of this screen is the film, and a
                    // console that greets you is one you have to dismiss.
                    controllerAutoShow = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = player.media },
            // Released on the way out rather than left holding the player: the
            // player outlives this screen, and a detached view still bound to it
            // keeps a surface the next one wants.
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize(),
        )
    }

    /*
     * The handheld path attaches a texture view to the same player, and whichever
     * surface attached last wins. Clearing on the way out means leaving couch
     * mode hands the picture back rather than leaving the player pointed at a
     * view that no longer exists.
     */
    DisposableEffect(player) {
        onDispose { player.attach(null) }
    }
}

/** Long enough to read the time, short enough not to sit over the film. */
private const val CONTROLLER_TIMEOUT_MS = 3_500
