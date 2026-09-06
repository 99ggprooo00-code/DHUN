package dev.dhun.core

/** DHUN's universal result/error taxonomy. Messages and diagnostics stay separate. */
sealed interface DhunResult<out T> {
    data class Success<T>(val value: T) : DhunResult<T>
    data class Failure(val error: DhunError) : DhunResult<Nothing>

    companion object {
        fun <T> success(value: T): DhunResult<T> = Success(value)
        fun failure(error: DhunError): DhunResult<Nothing> = Failure(error)
    }
}

inline fun <T, R> DhunResult<T>.map(transform: (T) -> R): DhunResult<R> = when (this) {
    is DhunResult.Success -> DhunResult.Success(transform(value))
    is DhunResult.Failure -> this
}

fun <T> DhunResult<T>.getOrNull(): T? = (this as? DhunResult.Success)?.value

fun <T> DhunResult<T>.getOrThrow(): T = when (this) {
    is DhunResult.Success -> value
    is DhunResult.Failure -> throw IllegalStateException(error.toUserMessage())
}

sealed interface DhunError {
    /** No network / DNS / connection refused / timeout. */
    data class Network(val detail: String? = null) : DhunError

    /** YouTube changed response shape, or our parser is wrong. */
    data class Parse(val detail: String? = null) : DhunError

    /** Item exists but this request is unplayable/unavailable (removed, region, client…). */
    data class Unavailable(val detail: String? = null) : DhunError

    /** HTTP 429 — global backoff required. */
    data class RateLimited(val retryAfterSeconds: Int? = null, val detail: String? = null) : DhunError

    /** Bot-gating / sign-in required. Evidence belongs in diagnostics, not the headline. */
    data class AuthRequired(val detail: String? = null) : DhunError

    data class Unknown(val causeMessage: String? = null) : DhunError
}

/** Human, actionable, non-alarming. Never leak technical detail here. */
fun DhunError.toUserMessage(): String = when (this) {
    is DhunError.Network -> "You're offline. Check your connection and try again."
    is DhunError.Parse -> "The music service sent something DHUN couldn't read. Try again in a moment."
    is DhunError.Unavailable -> "This track isn't available right now."
    is DhunError.RateLimited -> "Too many requests. Waiting a moment before retrying…"
    is DhunError.AuthRequired -> "This content needs a signed-in session."
    is DhunError.Unknown -> "Something went wrong. Try again."
}

/** Technical companion to [toUserMessage] for logs and the playback diagnostics UI. */
fun DhunError.detailString(): String? = when (this) {
    is DhunError.Network -> detail
    is DhunError.Unavailable -> detail
    is DhunError.Parse -> detail
    is DhunError.AuthRequired -> detail
    is DhunError.RateLimited -> listOfNotNull(detail, "retryAfter=${retryAfterSeconds ?: "?"}s").joinToString("; ")
    is DhunError.Unknown -> causeMessage
}

/** Attach the complete attempt summary without changing the error category or retry advice. */
internal fun DhunError.withDetail(detail: String): DhunError = when (this) {
    is DhunError.Network -> copy(detail = detail)
    is DhunError.Unavailable -> copy(detail = detail)
    is DhunError.Parse -> copy(detail = detail)
    is DhunError.AuthRequired -> copy(detail = detail)
    is DhunError.RateLimited -> copy(detail = detail)
    is DhunError.Unknown -> copy(causeMessage = detail)
}

/** Bound untrusted service/tool text; never put a signed stream URL in a report. */
internal fun diagnosticText(text: String, limit: Int = 240): String = text
    .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "<url>")
    .replace(Regex("[\\p{Cntrl}\\s]+"), " ")
    .trim()
    .take(limit)

/** Thrown by internals; carries the taxonomy value. */
class DhunException(val error: DhunError) : RuntimeException(error.toUserMessage())
