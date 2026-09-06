package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stream-URL resolution abstraction (ADR-001). Implementations:
 *  - OwnClientStreamResolver (common)   — InnerTube /player, no tokens
 *  - YtDlpStreamResolver     (jvm)      — subprocess, desktop
 *  - NewPipeStreamResolver   (jvm)      — drill-watched, currently broken upstream
 */
interface StreamResolver {
    val name: String
    suspend fun resolve(videoId: String): DhunResult<StreamInfo>
}

/**
 * Primary-with-failover resolver, under a wall-clock budget.
 *
 * The budget exists because the chain is slow *by construction*, not because
 * anything hangs: every InnerTube call is individually bounded by Ktor
 * `HttpTimeout` (`defaultHttpClient`), but the worst case is
 * WEB_REMIX (3 attempts × 25 s + backoffs ≈ 77 s) followed by seven
 * alternate identities (2 attempts × 12 s + backoff each ≈ 172 s) — about
 * **4 minutes** during which the UI can only say "Resolving". That is
 * exactly the "stuck on Resolving" symptom reported from Windows hardware on
 * 2026-09-06. [budgetMs] turns it into a typed verdict in bounded time; the
 * partial per-identity outcomes still reach the log, because
 * [OwnClientStreamResolver] throws on cancellation at the next suspension
 * point and its caller records what completed.
 *
 * If the primary fails, the fallback gets one shot; on double failure the
 * PRIMARY's error is reported (the more meaningful one — the fallback is an
 * insurance path, not the story).
 */
class ResolvingStreamResolver(
    private val primary: StreamResolver,
    private val fallback: StreamResolver? = null,
    private val budgetMs: Long = DEFAULT_RESOLVE_BUDGET_MS,
) : StreamResolver {
    override val name: String =
        if (fallback != null) "resolving(${primary.name} -> ${fallback.name})" else primary.name

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> =
        withTimeoutOrNull(budgetMs.milliseconds) { resolveChain(videoId) }
            ?: DhunResult.Failure(timeoutError())

    /**
     * Typed as [DhunError.Parse] rather than `Network` deliberately: `Network`
     * carries no `detail` slot, and `toUserMessage()` renders `Parse.detail`,
     * so this is the taxonomy member that can actually tell the user (and a
     * bug report) what happened.
     */
    private fun timeoutError(): DhunError = DhunError.Parse(
        "stream resolution exceeded the ${budgetMs / 1000}s budget — tokenless " +
            "client identities were still being tried",
    )

    private suspend fun resolveChain(videoId: String): DhunResult<StreamInfo> {
        val primaryResult = primary.resolve(videoId)
        if (primaryResult is DhunResult.Success) return primaryResult
        if (fallback == null) return primaryResult
        return when (val fallbackResult = fallback.resolve(videoId)) {
            is DhunResult.Success -> fallbackResult
            // report the primary error: it is the configured engine
            is DhunResult.Failure -> primaryResult
        }
    }

    companion object {
        /**
         * Long enough for a slow-but-working chain (a healthy first identity
         * answers in ~1-2 s), short enough that a fully gated network reports
         * a verdict instead of an apparently hung player.
         */
        const val DEFAULT_RESOLVE_BUDGET_MS = 45_000L
    }
}
