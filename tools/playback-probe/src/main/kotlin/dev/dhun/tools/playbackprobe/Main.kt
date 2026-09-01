package dev.dhun.tools.playbackprobe

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * DHUN Phase 01 — extraction kill-switch probe (v2, doctrine-correct).
 *
 * Tests the pipeline the architecture actually ships:
 *   1. SEARCH  — DHUN's OWN InnerTube client (WEB_REMIX), client version
 *                freshly scraped from the music.youtube.com homepage HTML
 *                (exactly the discovery step NewPipeExtractor v0.26.5
 *                failed at — see docs/research/01-extraction-spike.md)
 *   2. RESOLVE — yt-dlp subprocess -> bestaudio URL (proven healthy from
 *                hostile IPs as of 2026-09; the vision_platform client
 *                path currently works tokenless)
 *   3. STREAM  — HTTP range-fetch of the resolved URL; container verified
 *                by magic bytes (EBML/WebM or MP4 'ftyp')
 *   4. RELATED — DHUN's OWN InnerTube /next (radio queue)
 *   5. WATCH   — NewPipeExtractor stream path health (non-fatal). Broken
 *                upstream as of 2026-09-01 (no fix on master); the rot
 *                drill tracks its recovery so it can re-enter as the
 *                Android in-JVM resolver when fixed.
 *
 * Exit 0 = pipeline healthy (rot-drill green).
 */

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

private const val MUSIC_BASE = "https://music.youtube.com"

/* ---------------- Downloader (kept for the NewPipe WATCH step) ---------- */

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
}

/* ---------------- HTTP helpers ------------------------------------------ */

private fun httpGet(url: String, extraHeaders: Map<String, String> = emptyMap()): Pair<Int, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 15_000
    conn.readTimeout = 25_000
    conn.setRequestProperty("User-Agent", USER_AGENT)
    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
    for ((k, v) in extraHeaders) conn.setRequestProperty(k, v)
    val code = conn.responseCode
    val body = (if (code < 400) conn.inputStream else conn.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    conn.disconnect()
    return code to body
}

private fun httpPostJson(url: String, json: String, extraHeaders: Map<String, String> = emptyMap())
        : Pair<Int, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.connectTimeout = 15_000
    conn.readTimeout = 25_000
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("User-Agent", USER_AGENT)
    for ((k, v) in extraHeaders) conn.setRequestProperty(k, v)
    conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    val code = conn.responseCode
    val body = (if (code < 400) conn.inputStream else conn.errorStream)
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    conn.disconnect()
    return code to body
}

/* ---------------- JSON helpers (nanojson) ------------------------------- */

private fun collectByKey(node: Any?, key: String, out: MutableList<JsonObject>) {
    when (node) {
        is JsonObject -> {
            val hit = node[key]
            if (hit is JsonObject) out.add(hit)
            for (v in node.values) collectByKey(v, key, out)
        }
        is JsonArray -> for (v in node) collectByKey(v, key, out)
    }
}

private fun parse(body: String): Any = JsonParser.any().from(body)

private fun firstRunText(obj: JsonObject?, path: List<String>): String? {
    var cur: Any? = obj
    for (p in path) {
        val next = (cur as? JsonObject)?.get(p) ?: return null
        // YTM pattern: arrays on these paths are single-element wrappers —
        // descend into the first element and continue walking.
        cur = if (next is JsonArray) next.firstOrNull() ?: return null else next
    }
    return when (cur) {
        is JsonObject -> cur.getString("text")
        is JsonArray -> (cur.firstOrNull() as? JsonObject)?.getString("text")
        else -> null
    }
}

/* ---------------- InnerTube (DHUN's own metadata client) ---------------- */

private fun currentMusicClientVersion(): String {
    val (_, html) = httpGet(MUSIC_BASE)
    val regex = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"")
    return regex.find(html)?.groupValues?.get(1)
        ?: error("could not scrape INNERTUBE_CLIENT_VERSION from homepage HTML")
}

private fun innertubeContext(clientVersion: String): String =
    """{"client":{"clientName":"WEB_REMIX","clientVersion":"$clientVersion","hl":"en","gl":"US"}}"""

/* ---------------- yt-dlp locator ---------------------------------------- */

private fun ytdlpCommand(): List<String> {
    System.getenv("DHUN_YTDLP")?.let { return listOf(it) }
    val which = try {
        ProcessBuilder("which", "yt-dlp").start().inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) { "" }
    return if (which.isNotEmpty()) listOf(which) else listOf("python3", "-m", "yt_dlp")
}

/* ---------------- Steps -------------------------------------------------- */

private fun step(name: String, block: () -> String): Boolean = try {
    println(block())
    true
} catch (t: Throwable) {
    println("PROBE|$name|FAIL|${t.javaClass.simpleName}: ${t.message?.take(300)}")
    false
}

private fun hex(bytes: ByteArray, n: Int) = bytes.take(n).joinToString(" ") {
    String.format("%02X", it)
}

private fun containerVerdict(bytes: ByteArray): String = when {
    bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
        bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "WEBM/EBML container"
    bytes.size >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "MP4/M4A container (ftyp)"
    else -> "UNKNOWN container"
}

fun main() {
    var pass = true

    // ---- STEP 0: fresh client version (the step NewPipe broke on) --------
    val clientVersion = runCatching { currentMusicClientVersion() }
        .onFailure { println("PROBE|version|FAIL|${it.message?.take(200)}") }
        .getOrNull()
    pass = pass && (clientVersion != null)
    clientVersion?.let { println("PROBE|version|PASS|WEB_REMIX $it (scraped from homepage HTML)") } ?: run {
        println("PROBE|verdict|FAIL|no client version — metadata path broken")
        kotlin.system.exitProcess(1)
    }
    val context = innertubeContext(clientVersion)

    // ---- STEP 1: SEARCH (own InnerTube) ----------------------------------
    var topVideoId: String? = null
    var topTitle: String? = null
    pass = step("search") {
        val body = """{"context":$context,"query":"bohemian rhapsody",""" +
            """"params":"EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"}"""
        val (code, resp) = httpPostJson("$MUSIC_BASE/youtubei/v1/search?prettyPrint=false", body,
            mapOf(
                "Origin" to MUSIC_BASE,
                "Referer" to "$MUSIC_BASE/",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to clientVersion,
            ))
        require(code == 200) { "HTTP $code" }
        val items = mutableListOf<JsonObject>()
        collectByKey(parse(resp), "musicResponsiveListItemRenderer", items)
        require(items.isNotEmpty()) { "0 search items parsed" }
        val sb = StringBuilder("PROBE|search|PASS|${items.size} music-song results\n")
        items.take(10).forEachIndexed { i, item ->
            val title = firstRunText(item,
                listOf("flexColumns", "musicResponsiveListItemFlexColumnRenderer", "text", "runs")) ?: "?"
            val videoId = (item["playlistItemData"] as? JsonObject)?.getString("videoId")
                ?: firstRunText(item,
                    listOf("flexColumns", "musicResponsiveListItemFlexColumnRenderer", "text", "runs"))
                    ?.let { "?" }
            sb.append("SEARCH|${i + 1}|$title | videoId=${videoId ?: "?"}\n")
            if (i == 0 && videoId != null && videoId != "?") {
                topVideoId = videoId
                topTitle = title
            }
        }
        require(topVideoId != null) { "no videoId on top search result" }
        sb.removeSuffix("\n").toString()
    } && pass
    if (topVideoId == null) {
        println("PROBE|verdict|FAIL|search broken — cannot continue pipeline")
        kotlin.system.exitProcess(1)
    }

    // ---- STEP 2+3: RESOLVE (yt-dlp) + STREAM (bytes verified) ------------
    pass = step("resolve+stream") {
        val watchUrl = "https://www.youtube.com/watch?v=$topVideoId"
        val cmd = ytdlpCommand() + listOf("--no-warnings", "-f", "bestaudio", "-g", watchUrl)
        val proc = ProcessBuilder(cmd).start()
        val stdout = proc.inputStream.bufferedReader().readText().trim()
        val stderr = proc.errorStream.bufferedReader().readText().trim()
        val done = proc.waitFor()
        require(done == 0 && stdout.isNotEmpty()) {
            "yt-dlp exit $done: ${(stderr.ifBlank { stdout }).lineSequence().lastOrNull()?.take(200)}"
        }
        val audioUrl = stdout.lineSequence().firstOrNull { it.startsWith("http") }
            ?: error("no URL in yt-dlp output")
        val resolver = cmd.first()

        val conn = URL(audioUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Range", "bytes=0-8191")
        conn.connectTimeout = 15_000
        conn.readTimeout = 25_000
        val code = conn.responseCode
        val ctype = conn.contentType ?: "?"
        val bytes = conn.inputStream.use { inp ->
            val buf = ByteArray(8_192)
            var off = 0
            while (off < buf.size) {
                val r = inp.read(buf, off, buf.size - off)
                if (r < 0) break
                off += r
            }
            buf.copyOf(off)
        }
        conn.disconnect()
        require(code == 200 || code == 206) { "HTTP $code fetching audio bytes" }
        require(bytes.size >= 1024) { "only ${bytes.size} bytes returned" }
        require(ctype.startsWith("audio")) { "content-type '$ctype' is not audio" }
        val verdict = containerVerdict(bytes)
        require(!verdict.startsWith("UNKNOWN")) { "unrecognized container: ${hex(bytes, 8)}" }
        "PROBE|resolve|PASS|via $resolver (\"$topTitle\")\n" +
            "PROBE|stream|PASS|HTTP $code | $ctype | ${bytes.size}B | ${hex(bytes, 8)} | $verdict"
    } && pass

    // ---- STEP 4: RELATED (own InnerTube /next) ----------------------------
    pass = step("related") {
        val videoId = topVideoId!!
        val body = """{"context":$context,"videoId":"$videoId","playlistId":"RDAMVM$videoId"}"""
        val (code, resp) = httpPostJson("$MUSIC_BASE/youtubei/v1/next?prettyPrint=false", body,
            mapOf(
                "Origin" to MUSIC_BASE,
                "Referer" to "$MUSIC_BASE/",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to clientVersion,
            ))
        require(code == 200) { "HTTP $code" }
        val panels = mutableListOf<JsonObject>()
        collectByKey(parse(resp), "playlistPanelVideoRenderer", panels)
        require(panels.isNotEmpty()) { "0 related tracks parsed" }
        val sb = StringBuilder("PROBE|related|PASS|${panels.size} related tracks\n")
        panels.take(5).forEachIndexed { i, p ->
            val t = firstRunText(p, listOf("title", "runs")) ?: "?"
            val by = firstRunText(p, listOf("longBylineText", "runs")) ?: "?"
            sb.append("RELATED|${i + 1}|$t | $by\n")
        }
        sb.removeSuffix("\n").toString()
    } && pass

    // ---- STEP 5: WATCH — NewPipeExtractor health (non-fatal) --------------
    runCatching {
        NewPipe.init(SimpleDownloader())
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$topVideoId")
        println("WATCH|newpipe-stream|OK|${info.audioStreams.size} audio streams")
    }.onFailure {
        println("WATCH|newpipe-stream|BROKEN|${it.javaClass.simpleName}: ${it.message?.take(160)}")
    }

    println("PROBE|verdict|${if (pass) "PASS" else "FAIL"}|extraction-pipeline-" +
        if (pass) "healthy" else "broken")
    kotlin.system.exitProcess(if (pass) 0 else 1)
}
