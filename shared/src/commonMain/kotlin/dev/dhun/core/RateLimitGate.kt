package dev.dhun.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Process-wide 429 backoff gate (Phase 14 error taxonomy: "429 global
 * backoff").
 *
 * Per-request retry only slows ONE call; a 429 means YouTube is throttling
 * the client as a whole, so every further request must wait out the
 * cooldown before firing. [await] suspends until the gate opens;
 * [trip] sets or EXTENDS the cooldown (a later trip never shortens an
 * active one). Callers that parsed a server `Retry-After` pass it through;
 * otherwise a default cooldown applies.
 *
 * Monotonic clock only ([TimeSource] is injectable for tests) — immune to
 * the user changing system time. Not a rate limiter: it reacts to 429s the
 * server already sent; it does not preempt requests.
 */
class RateLimitGate(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val mutex = Mutex()
    private var cooldownMark: TimeMark? = null
    private var cooldownDuration: Duration = Duration.ZERO

    /** Sets or extends the global cooldown; zero/negative durations are ignored. */
    suspend fun trip(duration: Duration) {
        if (duration <= Duration.ZERO) return
        mutex.withLock {
            val remaining = remainingLocked()
            cooldownMark = timeSource.markNow()
            cooldownDuration = if (remaining > Duration.ZERO) maxOf(remaining, duration) else duration
        }
    }

    /** Suspends until any active cooldown has expired; returns immediately when open. */
    suspend fun await() {
        while (true) {
            val remaining = mutex.withLock { remainingLocked() }
            if (remaining <= Duration.ZERO) return
            delay(remaining)
        }
    }

    private fun remainingLocked(): Duration {
        val mark = cooldownMark ?: return Duration.ZERO
        return cooldownDuration - mark.elapsedNow().coerceAtMost(cooldownDuration)
    }
}
