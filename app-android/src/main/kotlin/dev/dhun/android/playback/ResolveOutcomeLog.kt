package dev.dhun.android.playback

import android.util.Log
import dev.dhun.core.DhunError
import dev.dhun.core.detailString
import java.util.concurrent.ConcurrentHashMap

/**
 * Playback-side record of the most recent **stream-resolve outcome per video
 * id** (playback-diagnostics session, 2026-09-09).
 *
 * Why this exists: the "Android playback starts but stays buffering / never
 * plays audio" report. The evidence (rot-drill issue #14 + the user's Windows
 * report) says the own-client resolve chain (`web_remix`/`visionos`/`tv`)
 * answers `AUTH_REQUIRED` "Sign in to confirm you're not a bot" for every
 * identity, so no stream URL ever materializes — while the engine's bounded
 * retry cascade (load-error policy × recovery listener) keeps the player in
 * `Buffering`/`Recovering` for minutes before surfacing the typed error.
 * This log is the source of truth that lets [AndroidDhunPlayer] fast-fail a
 * **terminal** outcome out of that cascade, and it doubles as the structured
 * `adb logcat -s DHUN` diagnostics line for the resolve chain.
 *
 * Terminal = [DhunError.AuthRequired] / [DhunError.Unavailable]: every
 * identity said "no", retrying soon will not change the answer. Everything
 * else (Network/Parse/RateLimited/Unknown) stays on the engine's patient
 * retry path — that patience is deliberate for mobile carriers.
 *
 * Records are keyed by video id: a later success **supersedes** a failure,
 * and [clear] (called by [AndroidDhunPlayer.retry]) drops the record so a
 * manual retry is never instantly fast-failed by its own stale verdict.
 */
class ResolveOutcomeLog(
    private val clock: () -> Long = System::currentTimeMillis,
    private val logger: (String) -> Unit = { Log.i(TAG, it) },
) {

    /** `error == null` means the resolve **succeeded** (supersedes any prior failure). */
    data class Outcome(val videoId: String, val error: DhunError?, val atEpochMs: Long)

    private val outcomes = ConcurrentHashMap<String, Outcome>()

    /**
     * Records the outcome of one resolve attempt and emits the structured
     * diagnostics line:
     * `resolve-outcome|<videoId>|OK|audio/webm 128kbps host=…` or
     * `resolve-outcome|<videoId>|AuthRequired|web_remix=AUTH_REQUIRED(…)…`
     */
    fun record(videoId: String, error: DhunError?) {
        if (videoId.isBlank()) return
        val outcome = Outcome(videoId, error, clock())
        outcomes[videoId] = outcome
        logger(
            when (error) {
                null -> "resolve-outcome|$videoId|OK"
                else -> "resolve-outcome|$videoId|${error::class.simpleName}|${error.detailString() ?: "-"}"
            },
        )
    }

    fun clear(videoId: String) {
        outcomes.remove(videoId)
    }

    /** Most recent recorded outcome for [videoId], regardless of kind/freshness. */
    fun last(videoId: String): Outcome? = outcomes[videoId]

    /**
     * The fresh **terminal** error for [videoId], or null when: no record,
     * the last record is a success, the error is non-terminal
     * (Network/Parse/RateLimited/Unknown), or the record is older than
     * [FRESHNESS_MS] (stale verdicts must not fast-fail a later session).
     */
    fun terminalFor(videoId: String, nowEpochMs: Long = clock()): DhunError? {
        val outcome = outcomes[videoId] ?: return null
        val error = outcome.error ?: return null
        if (!isTerminal(error)) return null
        if (nowEpochMs - outcome.atEpochMs > FRESHNESS_MS) return null
        return error
    }

    companion object {
        private const val TAG = "DHUN"

        /** Terminal verdicts stop being trusted after this window. */
        const val FRESHNESS_MS: Long = 5 * 60_000L

        fun isTerminal(error: DhunError): Boolean =
            error is DhunError.AuthRequired || error is DhunError.Unavailable

        /**
         * Process-wide instance: the DI-wrapped provider writes it
         * (`ResolveObservingMusicProvider`, AppModule) and `AndroidDhunPlayer`
         * reads it by default. Tests construct their own isolated instance.
         */
        val global = ResolveOutcomeLog()
    }
}
