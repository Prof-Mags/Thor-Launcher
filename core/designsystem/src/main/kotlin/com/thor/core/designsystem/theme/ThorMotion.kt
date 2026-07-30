package com.thor.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import com.thor.core.model.MotionStyle

/**
 * The launcher's motion vocabulary.
 *
 * Durations are derived rather than hard-coded per call site so that the
 * "transition speed" slider, the theme's [MotionStyle] and the accessibility
 * "reduce motion" switch all compose into one number. When motion is reduced
 * every duration collapses to zero and springs become snap transitions, which
 * removes animation without any screen needing a second code path.
 */
@Immutable
data class ThorMotion(
    val style: MotionStyle,
    /** User-facing speed multiplier; higher is faster. */
    val speedMultiplier: Float,
    val reduceMotion: Boolean,
) {
    private val scale: Float
        get() = if (reduceMotion) 0f else style.durationScale / speedMultiplier.coerceAtLeast(0.1f)

    /** Cursor moving between grid cells. Deliberately the fastest thing on screen. */
    val cursorMillis: Int get() = duration(140)

    /** Focus/selection visual changes: scale, elevation, glow. */
    val selectionMillis: Int get() = duration(220)

    /** Page turns on the bottom grid. */
    val pageMillis: Int get() = duration(360)

    /** Folder open/close. */
    val folderMillis: Int get() = duration(420)

    /** Top-screen detail panel cross-fade when the selection changes. */
    val detailMillis: Int get() = duration(300)

    /** Side menu and settings overlay slide. */
    val panelMillis: Int get() = duration(340)

    /** Background artwork cross-fade; slow on purpose so it reads as ambient. */
    val backdropMillis: Int get() = duration(650)

    private fun duration(base: Int): Int = (base * scale).toInt().coerceAtLeast(0)

    val easing: Easing
        get() = when (style) {
            MotionStyle.FLUID -> EaseFluid
            MotionStyle.SMOOTH -> EaseSmooth
            MotionStyle.SNAPPY -> EaseSnappy
            MotionStyle.MECHANICAL -> LinearOutSlowInEasing
        }

    fun <T> tweenSpec(durationMillis: Int): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easing)

    /** Spring used for anything the user is directly dragging. */
    fun <T> dragSpring(): AnimationSpec<T> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        spring(
            dampingRatio = when (style) {
                MotionStyle.FLUID -> 0.82f
                MotionStyle.SMOOTH -> Spring.DampingRatioNoBouncy
                MotionStyle.SNAPPY -> 0.9f
                MotionStyle.MECHANICAL -> 1f
            },
            stiffness = when (style) {
                MotionStyle.FLUID -> Spring.StiffnessMediumLow
                MotionStyle.SMOOTH -> Spring.StiffnessMedium
                MotionStyle.SNAPPY -> Spring.StiffnessHigh
                MotionStyle.MECHANICAL -> Spring.StiffnessHigh
            },
        )
    }

    /** Spring used when an icon settles into a grid cell. */
    fun settleSpring(): AnimationSpec<IntOffset> = if (reduceMotion) {
        tween(durationMillis = 0)
    } else {
        spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset(1, 1),
        )
    }

    companion object {
        /** Long, soft deceleration. */
        val EaseFluid = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

        /** The default: eased in and out, no overshoot. */
        val EaseSmooth = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

        /** Fast start, hard stop. */
        val EaseSnappy = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

        val Default = ThorMotion(
            style = MotionStyle.SMOOTH,
            speedMultiplier = 1f,
            reduceMotion = false,
        )
    }
}
