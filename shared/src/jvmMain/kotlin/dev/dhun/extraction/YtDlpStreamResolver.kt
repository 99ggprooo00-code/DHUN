package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.core.diagnosticText
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Optional, user-provided desktop extractor. No shell, cookies or token minting. */
class YtDlpStreamResolver(
    private val binary: List<String> = locate(),
    private val startProcess: (List<String>) -> Process = { ProcessBuilder(it).start() },
    private val timeoutSeconds: Long = 60,
) : StreamResolver {
    override val name: String = "yt-dlp"

    init {
        require(binary.isNotEmpty() && binary.first().isNotBlank()) { "An extractor executable is required" }
        require(timeoutSeconds > 0) { "Extractor timeout must be positive" }
    }

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> = withContext(Dispatchers.IO) {
        val command = binary + listOf(
            "--ignore-config", "--no-playlist", "--no-progress",
            "--user-agent", YTDLP_USER_AGENT,
            // ADR-001 order unchanged; do not silently parallelise or add identities.
            "--extractor-args",
            "youtube:player_client=web_embedded,tv,tv_downgraded,tv_simply,mweb,web_safari,android",
            "-f", "bestaudio/best", "-g",
            "https://www.youtube.com/watch?v=$videoId",
        )
        val process = try {
            startProcess(command)
        } catch (e: IOException) {
            // Missing python/yt-dlp is not an offline network or an unavailable track.
            return@withContext DhunResult.Failure(DhunError.Unknown(MISSING_TOOL_MESSAGE))
        } catch (e: SecurityException) {
            return@withContext DhunResult.Failure(DhunError.Unknown("The operating system denied permission to start the desktop extractor."))
        }
        var disposed = false
        fun disposeProcess() {
            if (!disposed) {
                disposed = true
                process.dispose()
            }
        }
        try {
            // Budget the entire exchange, not just waitFor: a launcher may
            // exit while a child still holds its stdout/stderr pipe open.
            withTimeoutOrNull(timeoutSeconds.seconds) {
                coroutineScope {
                    // Drain BOTH pipes while waiting: waitFor-before-read can deadlock
                    // on a full stderr pipe. Retain bounded text, not unlimited tool output.
                    val stdout = async(Dispatchers.IO) { process.inputStream.readTail() }
                    val stderr = async(Dispatchers.IO) { process.errorStream.readTail() }
                    try {
                        val exitCode = runInterruptible(Dispatchers.IO) { process.waitFor() }
                        val output = stdout.await()
                        val errors = stderr.await()
                        if (exitCode != 0) {
                            return@coroutineScope DhunResult.Failure(classifyYtDlpFailure(errors))
                        }
                        val url = output.lineSequence().firstOrNull { it.startsWith("https://") }
                            ?: return@coroutineScope DhunResult.Failure(DhunError.Parse("yt-dlp printed no HTTPS stream URL"))
                        DhunResult.Success(
                            StreamInfo(
                                videoId = videoId,
                                audioUrl = url,
                                mimeType = "audio/unknown", // libVLC probes the actual returned container
                                userAgent = YTDLP_USER_AGENT,
                            ),
                        )
                    } finally {
                        // Terminate/close BEFORE the scope joins reader jobs
                        // when the timeout expires or the caller cancels.
                        disposeProcess()
                    }
                }
            } ?: DhunResult.Failure(DhunError.Network("yt-dlp exceeded its ${timeoutSeconds}s process/output timeout"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DhunResult.Failure(DhunError.Network("Could not read yt-dlp output: ${diagnosticText(e.message.orEmpty())}"))
        } catch (e: Exception) {
            DhunResult.Failure(DhunError.Unknown("yt-dlp: ${diagnosticText(e.message.orEmpty())}"))
        } finally {
            // Also cover cancellation between process creation and entering the reader scope.
            disposeProcess()
        }
    }

    companion object {
        const val YTDLP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        internal const val MISSING_TOOL_MESSAGE =
            "Desktop fallback could not start. Install yt-dlp and restart DHUN, or set " +
                "DHUN_YTDLP to the full path of yt-dlp.exe. VLC alone does not provide this extractor."

        /**
         * Resolve executables without Unix-only `which`. Honour Windows Path
         * casing, semicolons and quoted directories. Python is only a fallback
         * when present; no implicit requirement for `python3` on Windows.
         */
        fun locate(
            environment: Map<String, String> = System.getenv(),
            windows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
        ): List<String> {
            fun env(key: String): String? = environment.entries
                .firstOrNull { it.key.equals(key, ignoreCase = windows) }?.value
            env("DHUN_YTDLP")?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
                ?.let { return listOf(it) }
            val directories = env("PATH").orEmpty().split(if (windows) ';' else ':')
                .map { it.trim().removeSurrounding("\"") }.filter { it.isNotBlank() }
            fun find(names: List<String>, skipStoreAliases: Boolean = false): String? = directories.firstNotNullOfOrNull { directory ->
                // Windows advertises python.exe Store stubs even when Python
                // is not installed. Playback must not open a Store window.
                if (skipStoreAliases && directory.replace('\\', '/').lowercase().trimEnd('/').endsWith("/microsoft/windowsapps")) {
                    return@firstNotNullOfOrNull null
                }
                names.firstNotNullOfOrNull { name ->
                    File(directory, name).takeIf { it.isFile && (windows || it.canExecute()) }?.absolutePath
                }
            }
            find(if (windows) listOf("yt-dlp.exe") else listOf("yt-dlp"))
                ?.let { return listOf(it) }
            if (windows) {
                find(listOf("py.exe"))?.let { return listOf(it, "-3", "-m", "yt_dlp") }
                find(listOf("python.exe", "python3.exe"), skipStoreAliases = true)?.let { return listOf(it, "-m", "yt_dlp") }
            } else {
                find(listOf("python3", "python"))?.let { return listOf(it, "-m", "yt_dlp") }
            }
            // Let ProcessBuilder report missing/inaccessible tool explicitly.
            return listOf(if (windows) "yt-dlp.exe" else "yt-dlp")
        }
    }
}

private val http429 = Regex("\\bHTTP(?: Error)?\\s+429\\b", RegexOption.IGNORE_CASE)

internal fun classifyYtDlpFailure(stderr: String): DhunError {
    if (stderr.contains("No module named", ignoreCase = true) && stderr.contains("yt_dlp")) {
        return DhunError.Unknown(YtDlpStreamResolver.MISSING_TOOL_MESSAGE)
    }
    val message = stderr.lineSequence().lastOrNull { it.contains("ERROR:") }
        ?: stderr.lineSequence().lastOrNull { it.isNotBlank() }
        ?: "yt-dlp exited unsuccessfully without an error message"
    val detail = diagnosticText(message, 800)
    return when {
        http429.containsMatchIn(message) || message.contains("Too Many Requests", ignoreCase = true) ->
            DhunError.RateLimited(detail = detail)
        message.contains("Sign in", ignoreCase = true) -> DhunError.AuthRequired(detail)
        message.contains("unavailable", ignoreCase = true) -> DhunError.Unavailable(detail)
        message.contains("timed out", ignoreCase = true) -> DhunError.Network(detail)
        else -> DhunError.Unknown(detail)
    }
}

/** Drain to EOF but retain at most 16 KiB. No signed URL is sent to diagnostics. */
private fun InputStream.readTail(limit: Int = 16_384): String = bufferedReader().use { reader ->
    val tail = StringBuilder()
    val buffer = CharArray(2_048)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        tail.append(buffer, 0, count)
        if (tail.length > limit) tail.delete(0, tail.length - limit)
    }
    tail.toString()
}

private fun Process.dispose() {
    // Desktop-only jvmMain; the bundled JDK 17 supplies ProcessHandle.
    // Kill launcher children too (e.g. a Windows standalone extractor).
    runCatching {
        if (isAlive) descendants().use { children -> children.forEach { it.destroyForcibly() } }
    }
    runCatching { destroyForcibly() }
    runCatching { inputStream.close() }
    runCatching { errorStream.close() }
    runCatching { outputStream.close() }
}
