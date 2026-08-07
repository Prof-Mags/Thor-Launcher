package com.thor.launcher.mouse

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import com.thor.core.common.log.ThorLog

/**
 * Loki, over the top of a game.
 *
 * The launcher's other surfaces all assume they are the thing on screen. This one
 * assumes the opposite: it is drawn over somebody else's fullscreen app, on the
 * display that app is using, and everything about it follows from that.
 *
 * Raw views and a canvas rather than Compose, matching [PointerOverlay] beside it.
 * A `ComposeView` in a service window needs a lifecycle owner, a saved-state
 * registry and a recomposer attached by hand before it will draw at all — real
 * plumbing for a panel that is four rectangles and three words, and plumbing that
 * fails silently by drawing nothing.
 *
 * `TYPE_ACCESSIBILITY_OVERLAY` for the same reason the pointer uses it: the
 * permission is the service already being enabled, where an application overlay
 * would need "Draw over other apps" as well.
 *
 * Unlike the pointer's window this one *is* touchable and focusable. It has to be:
 * a pointer is something you look past, and this is something you press. That also
 * means it takes the controller from the game while it is up, which is the whole
 * point — and why [hide] is on the same button that raised it.
 */
class GameOverlay(
    private val context: Context,
    private val onAction: (GameOverlayAction) -> Unit,
    private val onDismiss: () -> Unit,
) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var windowManager: WindowManager? = null
    private var view: GameOverlayView? = null

    val isShowing: Boolean get() = view != null

    /**
     * Raises the panel on one display, replacing any already up.
     *
     * @param title what is being played, so the panel says what it is about
     * @param accentArgb the theme's cursor colour, so it looks like Loki even here
     */
    fun show(displayId: Int, title: String, accentArgb: Long) {
        hide()

        val display = displayManager?.getDisplay(displayId) ?: return
        val displayContext = context.createDisplayContext(display)
        val manager = displayContext.getSystemService(WindowManager::class.java) ?: return

        val panel = GameOverlayView(
            context = displayContext,
            title = title,
            accent = accentArgb.toInt(),
            onAction = { action ->
                // Down before out: every action either leaves the launcher's own
                // surfaces showing or takes a picture of the screen underneath,
                // and a panel still up would be in both.
                hide()
                onAction(action)
            },
            onDismiss = {
                hide()
                onDismiss()
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Touchable and focusable, unlike the pointer's window: this is pressed
            // rather than looked past, and focus is what delivers the D-pad.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val added = runCatching { manager.addView(panel, params) }
            .onFailure { error -> ThorLog.w(TAG, "Could not raise the game overlay", error) }
            .isSuccess

        if (!added) return
        windowManager = manager
        view = panel
        panel.isFocusableInTouchMode = true
        panel.requestFocus()
    }

    fun hide() {
        val panel = view ?: return
        runCatching { windowManager?.removeViewImmediate(panel) }
        view = null
        windowManager = null
    }

    private companion object {
        const val TAG = "GameOverlay"
    }
}

/**
 * What the panel can do from on top of a game.
 *
 * Three, and no more. Everything else the launcher offers needs the launcher to be
 * in front — writing a note needs Loki's keyboard, which cannot appear over
 * somebody else's fullscreen app — and a tile that quietly dismissed the game to
 * do its job would be a worse version of [GO_HOME].
 */
enum class GameOverlayAction(val label: String) {
    /** The one action that is genuinely better from here than from anywhere else. */
    SCREENSHOT("Shot"),

    /**
     * The pointer, from inside a game.
     *
     * Its own chord already does this, but that chord is two buttons held in a
     * game that is listening to both — and nobody who has not read the README
     * knows it exists. A tile is how a feature stops being a secret.
     */
    POINTER("Pointer"),

    /** Brightness, which on a handheld is the setting reached for most in a game. */
    BRIGHTNESS_DOWN("Dim"),
    BRIGHTNESS_UP("Bright"),

    /**
     * The system's own panel, for everything Loki has no business reimplementing.
     *
     * Wi-Fi, Bluetooth, volume, aeroplane mode. Raised through the accessibility
     * service, which is the only route to it that does not need a notification
     * shade the game is covering.
     */
    QUICK_SETTINGS("System"),

    /** Back to the launcher, which is where everything else lives. */
    GO_HOME("Home"),

    CLOSE("Close"),
}

/**
 * The panel itself: a scrim, a card, a title and a row of tiles.
 *
 * Drawn rather than laid out, because a `Canvas` is fewer moving parts than a view
 * hierarchy for something this shape and because it is what the pointer beside it
 * already does. Sizes are in density-independent pixels resolved once, so the
 * panel is the same size on a phone and on the handheld.
 */
@SuppressLint("ViewConstructor")
private class GameOverlayView(
    context: Context,
    private val title: String,
    private val accent: Int,
    private val onAction: (GameOverlayAction) -> Unit,
    private val onDismiss: () -> Unit,
) : View(context) {

    private val actions = GameOverlayAction.entries
    private var focused = 0

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val scrimPaint = Paint().apply { color = Color.argb(SCRIM_ALPHA, 0, 0, 0) }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD_COLOR }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = accent
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        textAlign = Paint.Align.CENTER
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(scrimPaint)

        val cardWidth = dp(CARD_WIDTH)
        val cardHeight = dp(CARD_HEIGHT)
        val left = (width - cardWidth) / 2f
        val top = (height - cardHeight) / 2f
        val radius = dp(CARD_RADIUS)

        canvas.drawRoundRect(
            RectF(left, top, left + cardWidth, top + cardHeight),
            radius,
            radius,
            cardPaint,
        )

        canvas.drawText("LOKI", left + cardWidth / 2f, top + dp(26f), captionPaint)
        canvas.drawText(
            title.take(TITLE_MAX_CHARS),
            left + cardWidth / 2f,
            top + dp(50f),
            titlePaint,
        )

        /*
         * A grid rather than a row.
         *
         * Seven tiles across a card this width would be forty pixels each, which is
         * unreadable and unhittable. Wrapping at four keeps the card the width of a
         * dialog rather than the width of the screen — this is drawn over a game
         * and should cover as little of it as it can.
         */
        val tileWidth = dp(TILE_WIDTH)
        val tileHeight = dp(TILE_HEIGHT)
        val gap = dp(TILE_GAP)

        actions.forEachIndexed { index, action ->
            val row = index / TILES_PER_ROW
            val column = index % TILES_PER_ROW
            val inRow = countInRow(row)

            // Each row is centred on its own, so a short last row sits under the
            // middle of the one above rather than hanging off the left.
            val rowWidth = tileWidth * inRow + gap * (inRow - 1)
            val rowLeft = left + (cardWidth - rowWidth) / 2f

            val x = rowLeft + column * (tileWidth + gap)
            val y = top + dp(TILE_TOP) + row * (tileHeight + gap)
            val rect = RectF(x, y, x + tileWidth, y + tileHeight)

            tilePaint.color = if (index == focused) TILE_FOCUSED else TILE_COLOR
            canvas.drawRoundRect(rect, dp(TILE_RADIUS), dp(TILE_RADIUS), tilePaint)
            if (index == focused) {
                canvas.drawRoundRect(rect, dp(TILE_RADIUS), dp(TILE_RADIUS), cursorPaint)
            }
            canvas.drawText(
                action.label,
                rect.centerX(),
                rect.centerY() + dp(LABEL_BASELINE),
                labelPaint,
            )
        }
    }

    /** How many tiles a row actually holds; the last one is usually short. */
    private fun countInRow(row: Int): Int {
        val first = row * TILES_PER_ROW
        return (actions.size - first).coerceAtMost(TILES_PER_ROW)
    }

    /**
     * The D-pad walks the row; A presses; B closes.
     *
     * Every key is consumed while this is up, including the ones this does not
     * use. The app underneath is a game and is still listening — letting a
     * direction through would move the cursor here *and* the character there.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> step(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> step(1)
            KeyEvent.KEYCODE_DPAD_UP -> step(-TILES_PER_ROW)
            KeyEvent.KEYCODE_DPAD_DOWN -> step(TILES_PER_ROW)

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            -> onAction(actions[focused])

            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            -> onDismiss()
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    private fun step(delta: Int) {
        /*
         * Clamped rather than wrapped.
         *
         * The tiles have visible ends, and a cursor that jumped from one to the
         * other would read as a misfire. Vertical steps clamp too, which is what
         * makes Down from the short last row stay put instead of vanishing past
         * the end of the list.
         */
        focused = (focused + delta).coerceIn(0, actions.lastIndex)
        invalidate()
    }

    private companion object {
        const val SCRIM_ALPHA = 170
        val CARD_COLOR = Color.argb(242, 18, 18, 22)
        val TILE_COLOR = Color.argb(255, 38, 38, 44)
        val TILE_FOCUSED = Color.argb(255, 58, 58, 68)

        const val CARD_WIDTH = 340f
        const val CARD_HEIGHT = 200f
        const val CARD_RADIUS = 18f
        const val TILE_WIDTH = 76f
        const val TILE_HEIGHT = 44f
        const val TILE_GAP = 8f
        const val TILE_TOP = 74f
        const val TILE_RADIUS = 10f
        const val LABEL_BASELINE = 5f

        /** Four keeps the card dialog-width; see the note in `onDraw`. */
        const val TILES_PER_ROW = 4

        /** The card is a fixed width; a long title has to stop somewhere. */
        const val TITLE_MAX_CHARS = 34
    }
}
