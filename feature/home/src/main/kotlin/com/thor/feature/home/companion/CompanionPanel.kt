package com.thor.feature.home.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thor.core.designsystem.theme.ThorTheme
import com.thor.core.model.GameEntry
import com.thor.core.model.GameJournal
import com.thor.core.model.GridEntry
import com.thor.core.model.Platform
import com.thor.core.model.Screenshot
import com.thor.core.model.completionProgress
import com.thor.core.ui.component.ArtworkImage
import kotlinx.coroutines.delay

/**
 * What the companion panel can do.
 *
 * Declared once and consumed by both the panel and the input routing, because the
 * first version had the tiles listed in the composable and the cursor counted in
 * the view model — two lists that had to stay in the same order and nothing to
 * make them.
 */
enum class CompanionAction(val label: String) {
    SCREENSHOT("Screenshot"),

    /**
     * Start or stop capturing the screen, from beside the game being captured.
     *
     * Replaces the note tile, which was the wrong thing on this panel: a note is
     * written *about* a session, usually when you stop, and it is reachable from
     * every game's own menu. A recording is started *during* one and there was
     * nowhere to start it from without leaving the game.
     */
    RECORD("Record"),

    HOME("Take panel back"),
}

/** In cursor order, which is also the order they are drawn. */
val COMPANION_ACTIONS: List<CompanionAction> = CompanionAction.entries

/**
 * The panel that stays with you while the game plays on the other screen.
 *
 * This is what the second screen is for. Until now the launcher handed a panel to
 * a game and the other one went back to being a grid — a menu for choosing
 * something you had already chosen. The moment a game starts is the moment the
 * spare screen becomes worth having, and nothing was using it.
 *
 * What it shows is what you cannot see from inside the game: how long this sitting
 * has run, how far through the game you are against the times the scrapers
 * returned, and the frames you kept. All of it is
 * about *this* game, because the launcher records which entry it handed the panel
 * to; see `LauncherViewModel.runningEntryId`.
 *
 * Deliberately not a remote control. Everything here is either information or an
 * action on the launcher's own side — the game is not ours to drive, and a panel
 * of buttons that mostly did nothing would be worse than one that admits its
 * scope.
 */
@Composable
fun CompanionPanel(
    entry: GridEntry,
    platform: Platform?,
    journal: GameJournal,
    sinceEpochMs: Long?,
    canScreenshot: Boolean,
    /** Whether a screen recording is already running, so the tile can say Stop. */
    recording: Boolean,
    /** Which tile the controller cursor is on; see [COMPANION_ACTIONS]. */
    focusedAction: Int,
    onScreenshot: () -> Unit,
    onToggleRecording: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val game = entry as? GameEntry
    val accent = platform?.let { Color(it.accentArgb) } ?: colors.primary

    Box(modifier = modifier.fillMaxSize()) {
        // The game's own backdrop, dimmed hard. This panel is read while looking
        // mostly at the other screen, so it has to be quiet.
        val backdrop = game?.metadata?.artwork?.let { it.backgroundImage ?: it.hero ?: it.boxArt }
        if (backdrop != null) {
            ArtworkImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background.copy(alpha = BACKDROP_SCRIM_TOP),
                            colors.background.copy(alpha = BACKDROP_SCRIM_BOTTOM),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(dimens.spacing),
        ) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            SessionClock(sinceEpochMs = sinceEpochMs, accent = accent)

            /*
             * Progress against the scrapers' completion times.
             *
             * Only where a time was actually returned. A bar with no denominator
             * would either sit empty forever or invent one, and this panel is read
             * at a glance — an invented number is worse than no bar.
             */
            game?.completionProgress()?.let { progress ->
                CompletionBar(progress = progress, accent = accent)
            }

            if (journal.screenshots.isNotEmpty()) {
                ShotStrip(screenshots = journal.screenshots, accent = accent)
            }

            Box(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)) {
                COMPANION_ACTIONS.forEachIndexed { index, action ->
                    ActionTile(
                        icon = when (action) {
                            CompanionAction.SCREENSHOT -> Icons.Rounded.PhotoCamera
                            CompanionAction.RECORD ->
                                if (recording) Icons.Rounded.StopCircle else Icons.Rounded.Videocam

                            CompanionAction.HOME -> Icons.Rounded.Home
                        },
                        // Says which of the two it will do. A tile reading "Record"
                        // while recording is the one label that could cost somebody
                        // the take they were making.
                        label = when {
                            action == CompanionAction.RECORD && recording -> "Stop recording"
                            else -> action.label
                        },
                        accent = accent,
                        focused = index == focusedAction,
                        // Shown but inert without the pointer service, and it says
                        // so when pressed rather than being hidden — a button that
                        // disappears for reasons the user cannot see is worse than
                        // one that explains itself.
                        dimmed = action == CompanionAction.SCREENSHOT && !canScreenshot,
                        onClick = {
                            when (action) {
                                CompanionAction.SCREENSHOT -> onScreenshot()
                                CompanionAction.RECORD -> onToggleRecording()
                                CompanionAction.HOME -> onHome()
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * How long this sitting has run.
 *
 * Counted from when the launcher handed the panel over rather than from the
 * play-time total, because those answer different questions: the total is what the
 * game has cost you, and this is whether you should stop. A handheld played in bed
 * is the device this matters on.
 *
 * Ticks once a second only while it is on screen, and the loop dies with the
 * composition — nothing is counting while this panel is not being looked at.
 */
@Composable
private fun SessionClock(sinceEpochMs: Long?, accent: Color) {
    val colors = ThorTheme.colors
    if (sinceEpochMs == null) return

    var elapsed by remember(sinceEpochMs) { mutableLongStateOf(0L) }
    LaunchedEffect(sinceEpochMs) {
        while (true) {
            elapsed = (System.currentTimeMillis() - sinceEpochMs).coerceAtLeast(0L)
            delay(TICK_MILLIS)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatSession(elapsed),
            style = MaterialTheme.typography.displaySmall,
            color = colors.onSurface,
            fontWeight = FontWeight.Light,
        )
        Text(
            text = "  this sitting",
            style = MaterialTheme.typography.labelMedium,
            color = accent,
        )
    }
}

@Composable
private fun CompletionBar(progress: Float, accent: Color) {
    val colors = ThorTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${(progress * 100).toInt()}% of a typical playthrough",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT.dp)
                .clip(RoundedCornerShape(BAR_HEIGHT.dp))
                .background(colors.surfaceElevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(BAR_HEIGHT.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun ShotStrip(screenshots: List<Screenshot>, accent: Color) {
    val colors = ThorTheme.colors
    val shape = RoundedCornerShape(SHOT_RADIUS.dp)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(SHOT_GAP.dp)) {
        items(screenshots, key = Screenshot::path) { shot ->
            ArtworkImage(
                model = shot.path,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = SHOT_WIDTH.dp, height = SHOT_HEIGHT.dp)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, accent.copy(alpha = SHOT_BORDER_ALPHA), shape),
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    focused: Boolean,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val colors = ThorTheme.colors
    val dimens = ThorTheme.dimens
    val tint = if (dimmed) colors.onSurfaceVariant else accent

    Row(
        modifier = Modifier
            .clip(ThorTheme.shapes.pill)
            .background(if (focused) colors.surfaceHighest else colors.surfaceElevated)
            // The cursor, drawn the way the rest of the launcher draws one: an
            // outline in the theme's cursor colour rather than a colour swap,
            // which is the only treatment that reads on both light and dark.
            .then(
                if (focused) {
                    Modifier.border(2.dp, colors.cursor, ThorTheme.shapes.pill)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spacingSmall, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ACTION_ICON.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (dimmed) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * A running clock, in the units somebody glancing at it wants.
 *
 * Minutes and seconds until an hour, then hours and minutes — the seconds stop
 * being interesting once there is an hour on it, and a three-part clock is harder
 * to read at a glance than a two-part one.
 */
internal fun formatSession(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d".format(hours, minutes)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val TICK_MILLIS = 1_000L
private const val BACKDROP_SCRIM_TOP = 0.82f
private const val BACKDROP_SCRIM_BOTTOM = 0.96f
private const val BAR_HEIGHT = 6
private const val NOTE_LINES = 3
private const val SHOT_WIDTH = 96
private const val SHOT_HEIGHT = 54
private const val SHOT_GAP = 6
private const val SHOT_RADIUS = 6
private const val SHOT_BORDER_ALPHA = 0.3f
private const val ACTION_ICON = 18
