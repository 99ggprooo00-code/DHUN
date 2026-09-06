package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.core.detailString
import dev.dhun.core.diagnosticText
import dev.dhun.core.withDetail
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull

/** Stream resolution implementations are selected under ADR-001. */
interface StreamResolver {
    val name: String
    suspend fun resolve(videoId: String): DhunResult<StreamInfo>
}

/**
 * Sequential primary-with-failover, under one wall-clock budget. No parallel
 * identity requests (ADR-003 is unapproved). A primary 429 never starts the
 * fallback. On double failure retain both engines' evidence, not just the
 * primary's headline; otherwise a missing Windows fallback looks like an
 * unavailable track. Caller cancellation always propagates.
 */
class ResolvingStreamResolver(
    private val primary: StreamResolver,
    private val fallback: StreamResolver? = null,
    private val budgetMs: Long = DEFAULT_RESOLVE_BUDGET_MS,
) : StreamResolver {
    override val name: String =
        if (fallback != null) "resolving(${primary.name} -> ${fallback.name})" else primary.name

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
        var activeEngine = primary.name
        val failures = linkedMapOf<String, DhunError>()
        fun summary(): String = failures.entries.joinToString("; ") { (engine, error) ->
            "$engine: ${diagnosticText(error.detailString() ?: error.toString(), 2_000)}"
        }

        return withTimeoutOrNull(budgetMs.milliseconds) {
            val primaryResult = primary.resolve(videoId)
            if (primaryResult is DhunResult.Success) return@withTimeoutOrNull primaryResult
            val primaryError = (primaryResult as DhunResult.Failure).error
            failures[primary.name] = primaryError
            if (fallback == null || primaryError is DhunError.RateLimited) {
                return@withTimeoutOrNull primaryResult
            }

            activeEngine = fallback.name
            when (val result = fallback.resolve(videoId)) {
                is DhunResult.Success -> result
                is DhunResult.Failure -> {
                    failures[fallback.name] = result.error
                    // Preserve the configured engine's category, except that
                    // a rate limit must always retain its retry semantics.
                    val error = if (result.error is DhunError.RateLimited) result.error else primaryError
                    DhunResult.Failure(error.withDetail(summary()))
                }
            }
        } ?: DhunResult.Failure(
            DhunError.Parse(
                "Stream resolution exceeded the ${budgetMs / 1000}s budget " +
                    "while trying $activeEngine. ${summary()}",
            ),
        )
    }

    companion object {
        const val DEFAULT_RESOLVE_BUDGET_MS = 45_000L
    }
}
