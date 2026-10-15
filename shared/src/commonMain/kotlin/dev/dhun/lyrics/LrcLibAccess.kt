package dev.dhun.lyrics

import dev.dhun.data.EpochClock
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** One origin-wide lane for /get and /search. LRCLIB's documented identification,
 * sequential access, 250ms spacing and Retry-After apply even across picker/fallback.
 * Inject one shared instance in each host; tests use private instances/fake clocks.
 */
class LrcLibAccess(
    version: String = "development",
    private val clock: EpochClock = EpochClock.System,
    private val monotonicMs: () -> Long = LrcLibMonotonic::nowMs,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var hasRequested = false
    private var retryUntil = 0L
    private val identity = "DHUN/${version.filter { it.isLetterOrDigit() || it in ".-_" }.take(64).ifEmpty { "development" }} (https://github.com/99ggprooo00-code/DHUN)"

    internal suspend fun get(client: HttpClient, url: String, parameters: HttpRequestBuilder.() -> Unit): LrcLibReply = mutex.withLock {
        fun checkBackoff() {
            if (retryUntil > monotonicMs()) throw LrcLibBackoff(remainingSeconds(retryUntil, monotonicMs()))
        }
        checkBackoff()
        // Delay is relative, so a wall-clock adjustment cannot remove spacing.
        if (hasRequested) pause(250)
        checkBackoff()
        try {
            val response = client.get(url) {
                parameters()
                header("User-Agent", identity)
                timeout { requestTimeoutMillis = 12_000 }
            }
            val status = response.status.value
            if (status == 429 || status == 503) {
                val now = clock.nowMs()
                val deadline = retryAfterDeadline(response.headers["Retry-After"], now)
                retryUntil = saturatingAdd(monotonicMs(), (deadline - now).coerceAtLeast(0))
            }
            val channel = response.bodyAsChannel()
            val body = try {
                if (status !in 200..299) "" else {
                    // Bound streamed bytes before allocating/parsing an untrusted JSON tree.
                    val buffer = ByteArray(8_192)
                    val chunks = mutableListOf<ByteArray>()
                    var size = 0
                    while (true) {
                        val count = channel.readAvailable(buffer)
                        if (count < 0) break
                        if (count == 0) { yield(); continue }
                        size += count
                        if (size > 2_000_000) throw LrcLibInvalidResponse()
                        chunks.add(buffer.copyOf(count))
                    }
                    val bytes = ByteArray(size)
                    var offset = 0
                    chunks.forEach { chunk -> chunk.copyInto(bytes, offset); offset += chunk.size }
                    bytes.decodeToString().also {
                        if (!boundedLyricsJson(it)) throw LrcLibInvalidResponse()
                    }
                }
            } finally { channel.cancel(null) }
            LrcLibReply(status, body, if (status == 429 || status == 503) remainingSeconds(retryUntil, monotonicMs()) else null)
        } finally {
            // Space requests after completion, not merely after dispatching headers.
            hasRequested = true
        }
    }
}

internal data class LrcLibReply(val status: Int, val body: String, val retryAfterSeconds: Int?)
internal class LrcLibInvalidResponse : Exception("Invalid LRCLIB response")
internal class LrcLibBackoff(val seconds: Int) : Exception("LRCLIB requested backoff")

private fun saturatingAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
private fun remainingSeconds(until: Long, now: Long): Int {
    val remaining = (until - now.coerceAtLeast(0)).coerceAtLeast(0)
    return (remaining / 1_000 + if (remaining % 1_000 > 0) 1 else 0)
        .coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
}

/** RFC 9110 delay-seconds or IMF-fixdate. Unknown supplied formats fail closed
 * for this process rather than silently ignoring an explicitly requested delay.
 */
internal fun retryAfterDeadline(header: String?, now: Long): Long {
    if (header == null) return saturatingAdd(now, 60_000)
    val text = header.trim()
    if (text.isNotEmpty() && text.all { it in '0'..'9' }) {
        val seconds = text.toLongOrNull() ?: return Long.MAX_VALUE
        return if (seconds > Long.MAX_VALUE / 1_000) Long.MAX_VALUE else saturatingAdd(now, seconds * 1_000)
    }
    val date = Regex("(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), (\\d{2}) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) (\\d{4}) (\\d{2}):(\\d{2}):(\\d{2}) GMT").matchEntire(text)
        ?: return Long.MAX_VALUE
    val day = date.groupValues[1].toInt()
    val month = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").indexOf(date.groupValues[2])
    val year = date.groupValues[3].toInt()
    val hour = date.groupValues[4].toInt(); val minute = date.groupValues[5].toInt(); val second = date.groupValues[6].toInt()
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val months = listOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    if (year < 1970 || day !in 1..months[month] || hour !in 0..23 || minute !in 0..59 || second !in 0..59) return Long.MAX_VALUE
    fun leaps(y: Int) = y / 4 - y / 100 + y / 400
    val days = (year - 1970L) * 365 + leaps(year - 1) - leaps(1969) + months.take(month).sum() + day - 1
    return ((days * 86_400 + hour * 3_600 + minute * 60 + second) * 1_000).coerceAtLeast(now)
}

private object LrcLibMonotonic {
    private val origin = TimeSource.Monotonic.markNow()
    fun nowMs(): Long = origin.elapsedNow().inWholeMilliseconds
}
