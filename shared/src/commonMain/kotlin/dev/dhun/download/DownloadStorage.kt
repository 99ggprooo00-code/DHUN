package dev.dhun.download

/**
 * Filesystem abstraction for persistent downloads (ADR-006). Platform actuals
 * (JVM / Android) provide real file IO; tests provide an in-memory or
 * temp-directory implementation. Keeping IO behind this interface means the
 * download engine in commonMain stays target-agnostic.
 */
interface DownloadStorage {
    /** Root directory that holds `audio/` and `art/`. */
    val root: String

    fun audioPath(trackId: String, mimeType: String): String
    fun artPath(trackId: String): String

    suspend fun size(path: String): Long
    suspend fun exists(path: String): Boolean

    /** Append bytes to [path], creating parents if missing. */
    suspend fun append(path: String, bytes: ByteArray)

    /** Atomically move [part] to [final] (delete [final] first if present). */
    suspend fun commit(part: String, final: String)

    suspend fun delete(path: String)
    suspend fun ensureDirExists(path: String)
}

/** Map a stream MIME type to the audio file extension (no dot on return). */
fun extensionForMimeType(mimeType: String): String = when {
    mimeType.contains("webm", ignoreCase = true) -> "webm"
    mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) -> "m4a"
    mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true) -> "mp3"
    mimeType.contains("ogg", ignoreCase = true) -> "ogg"
    mimeType.contains("opus", ignoreCase = true) -> "opus"
    mimeType.contains("aac", ignoreCase = true) -> "aac"
    else -> "audio"
}

/** A YouTube video id is URL-safe ([A-Za-z0-9_-]{11}); never trust arbitrary ids. */
internal fun safeTrackId(trackId: String): String = trackId.replace(Regex("[^A-Za-z0-9_-]"), "_")
