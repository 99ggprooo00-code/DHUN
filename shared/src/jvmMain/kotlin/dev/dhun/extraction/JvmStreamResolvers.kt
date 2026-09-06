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

/**
 * Desktop extraction engines (ADR-001):
 *  - [YtDlpStreamResolver]: subprocess around the fastest-moving extractor
 *    in existence. Proven working from hostile IPs on 2026-09-01.
 *  - [NewPipeStreamResolver]: in-JVM engine. Currently BROKEN upstream
 *    (v0.26.5, no fix on master) — kept as a first-class implementation so
 *    the rot drill can measure its recovery, not our hopes.
 */

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
                    DhunResult.Failure(DhunError.Unavailable())
                } else {
                    DhunResult.Success(
                        StreamInfo(
                            videoId = videoId,
                            audioUrl = best.content,
                            mimeType = best.format?.mimeType ?: "audio/unknown",
                            bitrateKbps = best.averageBitrate.takeIf { it > 0 },
                            userAgent = SimpleDownloader.USER_AGENT,
                        )
                    )
                }
            } catch (e: ReCaptchaException) {
                DhunResult.Failure(DhunError.RateLimited())
            } catch (e: IOException) {
                DhunResult.Failure(DhunError.Network())
            } catch (e: org.schabi.newpipe.extractor.exceptions.ParsingException) {
                DhunResult.Failure(DhunError.Parse(e.message?.take(200)))
            } catch (e: org.schabi.newpipe.extractor.exceptions.ExtractionException) {
                DhunResult.Failure(DhunError.Unavailable())
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
