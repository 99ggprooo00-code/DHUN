package dev.dhun.core

/**
 * DHUN's universal result/error taxonomy. Every layer maps failures into
 * these; user-facing messages come from [DhunError.toUserMessage] only
 * (no raw technical strings in UI).
 */
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
    data object Network : DhunError

    /** YouTube changed response shape, or our parser is wrong. */
    data class Parse(val detail: String? = null) : DhunError

    /** Item exists but is unplayable/unavailable (removed, region…). */
    data object Unavailable : DhunError

    /** HTTP 429 — global backoff required. */
    data class RateLimited(val retryAfterSeconds: Int? = null) : DhunError

    /** Bot-gating / sign-in required for this action. */
    data object AuthRequired : DhunError

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

/** Thrown by internals; carries the taxonomy value. */
class DhunException(val error: DhunError) : RuntimeException(error.toUserMessage())
