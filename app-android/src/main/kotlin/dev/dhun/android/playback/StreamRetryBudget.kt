package dev.dhun.android.playback

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-track retry budget for [PlaybackGraph]'s 403 / dropped-stream recovery
 * listener.
 *
 * Lifted out of the listener — which carried the same counter map inline —
 * because the numbers deciding whether a track is silently re-resolved or
 * handed to the user's manual Retry button are exactly the kind of policy this
 * repo pins in pure objects (`TrackMenuPolicy`, `NowPlayingBackdropPolicy`).
 * The listener wiring still needs a real ExoPlayer, so it stays
 * reviewed-not-executed like the rest of `PlaybackGraph`; the policy below is
 * JVM unit-tested.
 *
 * **Why the budget is cleared at all.** It is per track id *and* per process,
 * and the listener lives as long as the playback service. A track that
 * recovered at minute 2 kept its earlier failures counted at minute 90, so a
 * long listening session — the S6 30-minute soak, a mobile carrier that gates
 * periodically — silently drained that track's allowance until an automatic
 * recovery stopped happening and the user was shown an error for a fault the
 * engine used to fix by itself.
 *
 * **Why the clear is delayed.** Resetting on the first `isPlaying = true`
 * would let a flapping track (plays two seconds, errors, replays) earn an
 * endless supply of fresh re-resolves. The listener therefore schedules the
 * clear only after [RESET_AFTER_PLAYING_MS] of continuous audible playback and
 * cancels it on any error or pause: a track that stays up has proven the
 * earlier failures were transient, a track that flaps has not.
 *
 * Backed by a [ConcurrentHashMap] because listener callbacks are not
 * contractually main-thread; the compound read-modify-write in
 * [recordFailure] only ever runs on the recovery path, and a race with a
 * scheduled [clear] costs at most one granted or skipped recovery.
 */
internal class StreamRetryBudget(
    private val maxRetries: Int = MAX_RETRIES,
    private val backoffMillis: Long = RETRY_BACKOFF_MS,
) {
    private val failures = ConcurrentHashMap<String, Int>()

    /**
     * Records one recoverable failure for [trackId].
     *
     * @return the 1-based attempt number when an automatic re-resolve is still
     *   allowed, or `null` when the budget is exhausted and the error should
     *   reach the user (manual Retry stays available either way).
     */
    fun recordFailure(trackId: String): Int? {
        val attempt = (failures[trackId] ?: 0) + 1
        failures[trackId] = attempt
        return if (attempt > maxRetries) null else attempt
    }

    /**
     * Delay before re-preparing for [attempt]. The first retry is immediate —
     * a rotated/expired URL should not cost the listener a second of silence —
     * and later ones back off so a gated endpoint is not hammered.
     */
    fun backoffMillisFor(attempt: Int): Long =
        if (attempt <= 1) 0L else backoffMillis * (attempt - 1)

    /** Automatic recoveries still available for [trackId] right now. */
    fun attemptsLeft(trackId: String): Int =
        (maxRetries - (failures[trackId] ?: 0)).coerceAtLeast(0)

    /** Restores [trackId]'s full budget (sustained audible playback). */
    fun clear(trackId: String) {
        failures.remove(trackId)
    }

    companion object {
        /** Automatic re-resolves per track before the error reaches the user. */
        const val MAX_RETRIES = 3

        /** Backoff step: attempt N waits (N - 1) × this. */
        const val RETRY_BACKOFF_MS = 1_500L

        /** Continuous audible playback that earns a track its budget back. */
        const val RESET_AFTER_PLAYING_MS = 10_000L
    }
}
