package com.thor.core.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.thor.core.model.AudioSettings
import com.thor.core.model.ControlSettings
import com.thor.core.ui.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * The distinct feedback moments the launcher produces.
 *
 * A cue names *what happened*, never which sound to play. That is what lets a
 * screen say "a folder opened" and stay out of the argument about how loud it
 * should be or whether it vibrates.
 */
enum class FeedbackCue {
    /** Cursor moved to a new cell. */
    NAVIGATE,

    /** A list scrolled under the cursor — fires more often than [NAVIGATE]. */
    SCROLL,

    /** Selection confirmed. */
    CONFIRM,

    /** Went back or dismissed. */
    BACK,

    /** An icon was picked up for dragging. */
    PICK_UP,

    /** An icon was dropped into place. */
    DROP,

    /** Page turned. */
    PAGE,

    /** An action was refused — edge of grid, occupied cell. */
    REJECT,

    /** An operation failed outright. */
    ERROR,

    /** An operation completed. */
    SUCCESS,

    /** An app or game was launched. */
    LAUNCH,

    FOLDER_OPEN,
    FOLDER_CLOSE,
    DRAWER_OPEN,
    DRAWER_CLOSE,

    /** A context menu or the side menu appeared. */
    MENU_OPEN,

    /** The settings overlay appeared. */
    SETTINGS_OPEN,

    /** Returned to the launcher's home state. */
    HOME,

    /** The launcher started: the one cue that fires once per process. */
    BOOT,
}

/**
 * Haptics and UI sound.
 *
 * Both channels are driven from one cue so they stay in sync and so a screen
 * says "the cursor moved" rather than choosing a vibration pattern itself.
 * Amplitude-controlled haptics are used where available and fall back to plain
 * timed vibration on older hardware.
 */
class ThorFeedback(
    private val context: Context,
    private var controls: ControlSettings,
    private var audio: AudioSettings,
) {

    private val vibrator: Vibrator? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        }

        else -> {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                /*
                 * Media, not sonification. `USAGE_ASSISTANCE_SONIFICATION`
                 * routes to the system stream, which is muted or at zero on most
                 * devices and is also silenced by turning off system touch
                 * sounds — so the entire pack was inaudible however loud the
                 * device was. Media is also what the user expects the media
                 * volume keys to control.
                 */
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    /**
     * Sample ids, keyed by cue.
     *
     * Concurrent because entries are published from SoundPool's load callback on
     * a binder thread and read from whichever thread produced the cue.
     */
    private val loadedSounds = ConcurrentHashMap<FeedbackCue, Int>()

    /**
     * Ids that have finished decoding.
     *
     * `SoundPool.load` returns immediately with an id that is not yet playable,
     * and playing it is silently dropped. Without tracking readiness the first
     * seconds after launch — exactly when the user is pressing things — were
     * always silent.
     */
    private val readySounds = ConcurrentHashMap.newKeySet<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) readySounds.add(sampleId)
        }
        // The bundled pack loads immediately. It used to be left empty pending a
        // theme supplying one, which meant the audio settings controlled nothing
        // at all.
        loadSoundPack(DEFAULT_SOUND_PACK)
    }

    fun updateSettings(controls: ControlSettings, audio: AudioSettings) {
        this.controls = controls
        this.audio = audio
    }

    /** Fires the haptic and sound associated with [cue]. */
    fun play(cue: FeedbackCue) {
        if (controls.hapticsEnabled) vibrate(cue)
        // The two channels are independent: with sound muted the haptic still
        // fires, and a cue with no loaded sample degrades to haptic only.
        if (shouldPlaySound(cue)) playSound(cue)
    }

    private fun shouldPlaySound(cue: FeedbackCue): Boolean {
        if (!audio.soundEffectsEnabled) return false
        return when (cue) {
            // Its own branch rather than falling in with navigation: a start-up chime
            // gated behind "navigation sounds" would be off for a reason that has
            // nothing to do with it.
            FeedbackCue.BOOT -> true
            FeedbackCue.NAVIGATE, FeedbackCue.SCROLL, FeedbackCue.PAGE -> audio.navigationSounds
            FeedbackCue.CONFIRM, FeedbackCue.LAUNCH -> audio.launchSounds
            else -> audio.navigationSounds
        }
    }

    private fun vibrate(cue: FeedbackCue) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        val intensity = controls.hapticIntensity.coerceIn(0f, 1f)
        if (intensity <= 0f) return

        val (durationMs, amplitudeFraction) = when (cue) {
            FeedbackCue.NAVIGATE -> 8L to 0.35f
            FeedbackCue.SCROLL -> 5L to 0.22f
            FeedbackCue.CONFIRM -> 18L to 0.6f
            FeedbackCue.BACK -> 12L to 0.4f
            FeedbackCue.PICK_UP -> 28L to 0.85f
            FeedbackCue.DROP -> 20L to 0.7f
            FeedbackCue.PAGE -> 14L to 0.5f
            FeedbackCue.REJECT -> 34L to 1.0f
            FeedbackCue.ERROR -> 46L to 1.0f
            FeedbackCue.SUCCESS -> 22L to 0.6f
            FeedbackCue.LAUNCH -> 40L to 0.8f
            FeedbackCue.FOLDER_OPEN, FeedbackCue.DRAWER_OPEN -> 16L to 0.5f
            FeedbackCue.FOLDER_CLOSE, FeedbackCue.DRAWER_CLOSE -> 12L to 0.4f
            FeedbackCue.MENU_OPEN, FeedbackCue.SETTINGS_OPEN -> 14L to 0.45f
            FeedbackCue.HOME -> 24L to 0.65f
            // A single soft thud under the chime, not a rattle.
            FeedbackCue.BOOT -> 55L to 0.75f
        }

        val amplitude = (amplitudeFraction * intensity * 255f)
            .roundToInt()
            .coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun playSound(cue: FeedbackCue) {
        val soundId = loadedSounds[cue] ?: return
        // Skipped rather than queued while still decoding: a cursor tick that
        // arrives late is worse than one that never arrives.
        if (soundId !in readySounds) return
        val volume = audio.uiVolume.coerceIn(0f, 1f)
        if (volume <= 0f) return
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    /** Loads a sound pack from raw resource ids. */
    fun loadSoundPack(samples: Map<FeedbackCue, Int>) {
        unloadSounds()
        samples.forEach { (cue, resId) ->
            loadedSounds[cue] = soundPool.load(context, resId, 1)
        }
    }

    private fun unloadSounds() {
        loadedSounds.values.forEach { id ->
            soundPool.unload(id)
            readySounds.remove(id)
        }
        loadedSounds.clear()
    }

    fun release() {
        unloadSounds()
        soundPool.release()
    }

    private companion object {
        /**
         * Concurrent streams.
         *
         * Four was not enough once folder and drawer transitions overlapped with
         * cursor ticks — SoundPool drops the newest stream when it runs out, so
         * the shorter, more frequent cue was the one being silenced.
         */
        const val MAX_STREAMS = 8

        /**
         * The bundled interface sounds.
         *
         * Synthesised as one set (see `GenSounds`) so they share a sample rate,
         * envelope and level — mixing sourced clips tends to leave one cue
         * noticeably louder or harsher than its neighbours, and these play
         * constantly.
         */
        val DEFAULT_SOUND_PACK: Map<FeedbackCue, Int> = mapOf(
            FeedbackCue.NAVIGATE to R.raw.ui_navigate,
            FeedbackCue.SCROLL to R.raw.ui_scroll,
            FeedbackCue.CONFIRM to R.raw.ui_confirm,
            FeedbackCue.BACK to R.raw.ui_back,
            FeedbackCue.PICK_UP to R.raw.ui_pick_up,
            FeedbackCue.DROP to R.raw.ui_drop,
            FeedbackCue.PAGE to R.raw.ui_page,
            FeedbackCue.REJECT to R.raw.ui_reject,
            FeedbackCue.ERROR to R.raw.ui_error,
            FeedbackCue.SUCCESS to R.raw.ui_success,
            FeedbackCue.LAUNCH to R.raw.ui_launch,
            FeedbackCue.FOLDER_OPEN to R.raw.ui_folder_open,
            FeedbackCue.FOLDER_CLOSE to R.raw.ui_folder_close,
            FeedbackCue.DRAWER_OPEN to R.raw.ui_drawer_open,
            FeedbackCue.DRAWER_CLOSE to R.raw.ui_drawer_close,
            FeedbackCue.MENU_OPEN to R.raw.ui_menu,
            FeedbackCue.SETTINGS_OPEN to R.raw.ui_settings,
            FeedbackCue.HOME to R.raw.ui_home,
            FeedbackCue.BOOT to R.raw.ui_boot,
        )
    }
}

val LocalThorFeedback: ProvidableCompositionLocal<ThorFeedback?> =
    staticCompositionLocalOf { null }

/**
 * Creates a feedback controller bound to this composition and releases its
 * SoundPool when the composition leaves — a leaked SoundPool holds an audio
 * session open for the process lifetime.
 */
@Composable
fun rememberThorFeedback(
    controls: ControlSettings,
    audio: AudioSettings,
): ThorFeedback {
    val context = LocalContext.current
    val feedback = remember(context) { ThorFeedback(context, controls, audio) }

    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }

    // Settings changes update the existing controller rather than rebuilding it,
    // so adjusting the haptics slider does not recreate the audio session.
    feedback.updateSettings(controls, audio)
    return feedback
}
