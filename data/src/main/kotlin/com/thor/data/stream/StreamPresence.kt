package com.thor.data.stream

/**
 * Whether a stream is on screen right now.
 *
 * Exists so THOR's own global input handling can stand down while the handheld
 * is being used as a screen for another machine. The pointer service is an
 * accessibility service, which means it sees every key **before** the focused
 * window does and can consume it — and while streaming that is exactly wrong
 * twice over.
 *
 * Start and Select together toggle the pointer, and the service swallows both,
 * so the stream's own quit combination could never complete. Worse, once the
 * pointer is up the service claims the D-pad and face buttons to move a cursor,
 * which silently takes the controller away from the game: the picture keeps
 * moving and nothing responds, which reads as the stream having frozen.
 *
 * A plain object rather than injected state because the reader is a service
 * constructed by the system, in the same process, on the input thread. It is
 * consulted on every key press, so it has to be a field read and nothing more.
 */
object StreamPresence {

    /**
     * `@Volatile` because it is written on the main thread and read on the
     * input dispatch thread, and a stale read here means the pointer stealing a
     * button press from a game.
     */
    @Volatile
    var streaming: Boolean = false
        private set

    fun begin() {
        streaming = true
    }

    fun end() {
        streaming = false
    }
}
