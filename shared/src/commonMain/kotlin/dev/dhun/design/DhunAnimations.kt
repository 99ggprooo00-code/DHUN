package dev.dhun.design

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object DhunAnimations {
    // Durations (ms) — Material motion scale
    const val fast = 150
    const val medium = 300
    const val slow = 500

    // Easing uses the platform's default (FastOutSlowIn) via tween without
    // an explicit easing param — qualifies as "Material motion".

    /** 150 ms crossfade (chips, small UI). */
    fun <T> fastTween() = tween<T>(durationMillis = fast)

    /** 300 ms — the workhorse (card presses, theme switches). */
    fun <T> mediumTween() = tween<T>(durationMillis = medium)

    /** 500 ms — artwork / color crossfades on track change. */
    fun <T> slowTween() = tween<T>(durationMillis = slow)

    /** Spring for artwork scale (playing vs paused) and track-change slide. */
    fun <T> springSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Gentle spring for reordering / equalizer ticks. */
    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Offset spring for artwork slide on skip. */
    fun offsetSpring() = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
