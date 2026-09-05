package dev.dhun.player

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Phase 14 bounded **audio-file** cache for the desktop (vlcj) player.
 *
 * Desktop counterpart of Android's `DhunAudioSegmentCache`. libVLC plays a
 * URL or a file — it has no pluggable data-source layer like Media3 — so the
 * desktop cache stores **whole tracks**, keyed by stable video id:
 *
 * - `<dir>/<videoId>.audio` — complete file, eligible for playback/eviction.
 * - `<dir>/<videoId>.part`  — in-flight download; never served, always
 *   swept on open (crash leftovers).
 * - LRU = file `lastModified`; [touch]ed on every cache hit; oldest evicted
 *   first once the total exceeds [maxBytes] ([AudioCacheBudget] /
 *   `SettingsKeys.CACHE_SIZE_MB`, default 1 GiB).
 * - A single file larger than the whole budget is never stored.
 *
 * The network layer is injectable ([fetch]) so eviction and download
 * semantics are unit-tested without sockets. Thread-safe: one lock guards
 * index mutations; downloads stream outside the lock.
 */
class AudioFileCache(
    val dir: File,
    val maxBytes: Long,
    private val fetch: (url: String) -> InputStream = ::openHttp,
) {
    private val lock = ReentrantLock()

    init {
        dir.mkdirs()
        // Leftover partial files from a previous crash are worthless.
        dir.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
    }

    /** Complete cached file for [videoId], or null. Marks it most-recently-used. */
    fun fileFor(videoId: String): File? {
        val f = completeFile(videoId) ?: return null
        return lock.withLock {
            if (f.isFile && f.length() > 0) f.also { touch(it) } else null
        }
    }

    fun has(videoId: String): Boolean = completeFile(videoId)?.let { it.isFile && it.length() > 0 } == true

    fun cachedBytes(videoId: String): Long = completeFile(videoId)?.takeIf { it.isFile }?.length() ?: 0L

    /** Sum of all complete files (partials excluded — they are transient). */
    fun totalBytes(): Long = completeFiles().sumOf { it.length() }

    /** Cached video ids, most-recently-used first. */
    fun ids(): List<String> = completeFiles()
        .sortedByDescending { it.lastModified() }
        .map { it.name.removeSuffix(AUDIO_SUFFIX) }

    /**
     * Streams [url] into the cache for [videoId]. Returns the complete file,
     * or null when the download was cancelled ([cancel] set), the stream
     * ended short of [expectedBytes], or the file could not fit the budget.
     * Never throws for I/O failures — a cache miss is not a playback error.
     */
    fun download(
        videoId: String,
        url: String,
        expectedBytes: Long? = null,
        cancel: AtomicBoolean = AtomicBoolean(false),
        onProgress: ((bytes: Long) -> Unit)? = null,
    ): File? {
        if (!isSafeId(videoId)) return null
        fileFor(videoId)?.let { return it }
        if (expectedBytes != null && expectedBytes > maxBytes) return null

        val part = File(dir, videoId + PART_SUFFIX)
        val target = File(dir, videoId + AUDIO_SUFFIX)
        var written = 0L
        val ok = try {
            copyToPart(url, part, cancel, onProgress) { written = it }
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }

        if (!ok || written == 0L || (expectedBytes != null && written != expectedBytes)) {
            part.delete()
            return null
        }
        return lock.withLock {
            target.delete()
            if (!part.renameTo(target)) {
                part.delete()
                return@withLock null
            }
            touch(target)
            evictLocked(keep = videoId)
            target
        }
    }

    /** Removes one entry. */
    fun remove(videoId: String): Boolean = lock.withLock {
        completeFile(videoId)?.delete() == true
    }

    /** Deletes everything (complete + partial). */
    fun clear() = lock.withLock {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Public eviction entry point (e.g. after the budget setting shrinks). */
    fun evictToBudget() = lock.withLock { evictLocked(keep = null) }

    /* ---------------- internals ---------------- */

    /** False = cancelled or over budget (caller deletes [part]). Throws on I/O. */
    private fun copyToPart(
        url: String,
        part: File,
        cancel: AtomicBoolean,
        onProgress: ((Long) -> Unit)?,
        report: (Long) -> Unit,
    ): Boolean {
        var written = 0L
        fetch(url).use { input ->
            part.outputStream().buffered().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (cancel.get()) return false
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    written += n
                    report(written)
                    if (written > maxBytes) return false
                    onProgress?.invoke(written)
                }
            }
        }
        return true
    }

    private fun evictLocked(keep: String?) {
        val files = completeFiles().sortedBy { it.lastModified() } // oldest first
        var total = files.sumOf { it.length() }
        val keepName = keep?.let { it + AUDIO_SUFFIX }
        for (f in files) {
            if (total <= maxBytes) break
            if (f.name == keepName) continue
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    private fun touch(f: File) {
        // Monotonic within a process even when the fs clock resolution is coarse.
        val now = maxOf(System.currentTimeMillis(), lastTouch + 1)
        lastTouch = now
        f.setLastModified(now)
    }

    @Volatile
    private var lastTouch = 0L

    private fun completeFile(videoId: String): File? =
        if (isSafeId(videoId)) File(dir, videoId + AUDIO_SUFFIX) else null

    private fun completeFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(AUDIO_SUFFIX) }?.toList() ?: emptyList()

    companion object {
        const val AUDIO_SUFFIX = ".audio"
        const val PART_SUFFIX = ".part"
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

        /** YouTube ids are `[A-Za-z0-9_-]{11}`; anything else is refused as a path. */
        fun isSafeId(id: String): Boolean = SAFE_ID.matches(id)

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private fun openHttp(url: String): InputStream {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code while caching audio")
            }
            return conn.inputStream
        }

        /** `<per-OS DHUN data dir>/cache/audio`, beside the SQLite file. */
        fun defaultDir(dataDir: File): File = File(dataDir, "cache/audio")
    }
}
