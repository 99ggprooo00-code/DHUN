package dev.dhun.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide flag: the platform player is mid 403-recovery (invalidate URL →
 * re-resolve → seek → prepare). Shared so MediaSessionService's ExoPlayer
 * listener and the UI-facing [DhunPlayer] can agree without coupling UI to
 * Media3. Desktop may set it if/when it grows equivalent recovery.
 *
 * Phase 14 / ADR-002: surfaces as [dev.dhun.core.PlaybackState.Recovering]
 * → "Reconnecting…" chip. Never a Liquid Glass or decorative concern.
 */
object StreamRecoverySignal {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun begin() {
        _active.value = true
    }

    fun end() {
        _active.value = false
    }
}
