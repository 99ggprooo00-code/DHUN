package dev.dhun.extraction

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo as NPStreamInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Desktop extraction engines (ADR-001):
 *  - [YtDlpStreamResolver]: subprocess around the fastest-moving extractor
 *    in existence. Proven working from hostile IPs on 2026-09-01.
 *  - [NewPipeStreamResolver]: in-JVM engine. Currently BROKEN upstream
 *    (v0.26.5, no fix on master) — kept as a first-class implementation so
 *    the rot drill can measure its recovery, not our hopes.
 */

class YtDlpStreamResolver(
    private val binary: List<String> = locate(),
) : StreamResolver {

    override val name: String = "yt-dlp"

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> =
        withContext(Dispatchers.IO) {
            try {
                val command = binary + listOf(
                    "--no-warnings", "--no-playlist",
                    "-f", "bestaudio", "-g",
                    "https://www.youtube.com/watch?v=$videoId",
                )
                val process = ProcessBuilder(command).start()
                val finished = process.waitFor(60, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext DhunResult.Failure(DhunError.Network) // treat as transient
                }
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()

                if (process.exitValue() != 0) {
                    val message = stderr.lineSequence().lastOrNull { it.isNotBlank() } ?: ""
                    return@withContext DhunResult.Failure(
                        when {
                            message.contains("Sign in to confirm", ignoreCase = true) -> DhunError.AuthRequired()
                            message.contains("Video unavailable", ignoreCase = true) -> DhunError.Unavailable
                            else -> DhunError.Unknown(message.take(200))
                        }
                    )
                }
                val url = stdout.lineSequence().firstOrNull { it.startsWith("http") }
                    ?: return@withContext DhunResult.Failure(DhunError.Parse("yt-dlp printed no URL"))
                DhunResult.Success(
                    StreamInfo(
                        videoId = videoId,
                        audioUrl = url,
                        mimeType = "audio/webm", // bestaudio is itag 251 (opus/webm) in practice; container verified at playback
                        codec = "opus",
                    )
                )
            } catch (e: java.util.concurrent.TimeoutException) {
                DhunResult.Failure(DhunError.Network)
            } catch (e: IOException) {
                DhunResult.Failure(DhunError.Network)
            } catch (e: Exception) {
                DhunResult.Failure(DhunError.Unknown(e.message))
            }
        }

    companion object {
        /** DHUN_YTDLP env var overrides; else `yt-dlp`; else `python3 -m yt_dlp`. */
        fun locate(): List<String> {
            System.getenv("DHUN_YTDLP")?.let { return listOf(it) }
            val found = try {
                ProcessBuilder("which", "yt-dlp").start()
                    .inputStream.bufferedReader().readText().trim()
            } catch (_: Exception) {
                ""
            }
            return if (found.isNotEmpty()) listOf(found) else listOf("python3", "-m", "yt_dlp")
        }
    }
}

/**
 * NewPipe Extractor engine. Drill-watched: currently fails upstream
 * (client-version discovery + ANDROID player path), so it is NOT wired into
 * the production chain — the rot drill tells us when it can return.
 */
class NewPipeStreamResolver : StreamResolver {

    override val name: String = "newpipe-extractor"

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> =
        withContext(Dispatchers.IO) {
            try {
                initOnce()
                val info = NPStreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
                val best = info.audioStreams
                    .filter { it.averageBitrate > 0 && !it.content.isNullOrBlank() }
                    .maxByOrNull { it.averageBitrate }
                if (best == null) {
                    DhunResult.Failure(DhunError.Unavailable)
                } else {
                    DhunResult.Success(
                        StreamInfo(
                            videoId = videoId,
                            audioUrl = best.content,
                            mimeType = best.format?.mimeType ?: "audio/unknown",
                            bitrateKbps = best.averageBitrate.takeIf { it > 0 },
                        )
                    )
                }
            } catch (e: ReCaptchaException) {
                DhunResult.Failure(DhunError.RateLimited())
            } catch (e: IOException) {
                DhunResult.Failure(DhunError.Network)
            } catch (e: org.schabi.newpipe.extractor.exceptions.ParsingException) {
                DhunResult.Failure(DhunError.Parse(e.message?.take(200)))
            } catch (e: org.schabi.newpipe.extractor.exceptions.ExtractionException) {
                DhunResult.Failure(DhunError.Unavailable)
            } catch (e: Exception) {
                DhunResult.Failure(DhunError.Unknown(e.message))
            }
        }

    companion object {
        @Volatile private var initialized = false

        fun initOnce() {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                NewPipe.init(SimpleDownloader())
                initialized = true
            }
        }
    }
}

/** Minimal JVM Downloader for NewPipe Extractor (also used by the probe). */
class SimpleDownloader : Downloader() {
    override fun execute(request: Request): Response {
        var currentUrl = URL(request.url())
        var redirects = 0
        while (true) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = request.httpMethod()
                connectTimeout = 15_000
                readTimeout = 25_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                for ((k, v) in request.headers()) {
                    if (k.equals("User-Agent", true) || k.equals("Accept-Language", true)) continue
                    setRequestProperty(k, v.joinToString(", "))
                }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                    ?: throw IOException("redirect $code without Location header")
                if (++redirects > 8) throw IOException("too many redirects (>8)")
                val next = URL(currentUrl, location)
                conn.disconnect()
                currentUrl = next
                continue
            }
            if (code == 429) throw ReCaptchaException("rate limited (429)", currentUrl.toString())
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (body.contains("recaptcha", ignoreCase = true) && code == 200) {
                throw ReCaptchaException("reCAPTCHA challenge served", currentUrl.toString())
            }
            @Suppress("UNCHECKED_CAST")
            val headers = conn.headerFields.filterKeys { it != null } as Map<String, List<String>>
            val response = Response(code, body, headers, conn.responseMessage ?: "", currentUrl.toString())
            conn.disconnect()
            return response
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
