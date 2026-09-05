package dev.dhun.tools.playbackprobe

import dev.dhun.core.DhunResult
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.ResolvingStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.extraction.NewPipeStreamResolver
import dev.dhun.extraction.YtDlpStreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.SearchFilter
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL

/**
 * DHUN extraction probe — Phase 01+02 combined harness. Thin on purpose:
 * it drives the REAL shared-module stack (InnerTubeClient, resolvers) the
 * apps will use, so a green drill means the shipped code path is healthy.
 *
 *   1. SEARCH   — own InnerTube client (WEB_REMIX, fresh-scraped version)
 *   2. RESOLVE  — the PRODUCTION desktop chain (ADR-001: own-client
 *                 primary, yt-dlp failover) — identical to forDesktop()
 *   3. STREAM   — HTTP range-fetch, container verified by magic bytes
 *   4. RELATED  — own InnerTube /next (radio queue)
 *   5. WATCH    — per-engine health (own-client, yt-dlp, NewPipeExtractor):
 *                 non-fatal evidence lines; the rot drill feeds them into
 *                 ADR-001's evidence-driven engine priority. Added after
 *                 run 33961533965 gated the verdict on the fallback engine
 *                 alone while the production primary went untested.
 *
 * Exit 0 = pipeline healthy. Run with `PYTHONPATH` pointing at yt-dlp when
 * installed via `pip --target`.
 */
fun main(): Unit = runBlocking<Unit> {
    val client = InnerTubeClient()
    var pass = true

    suspend fun step(name: String, block: suspend () -> String): Boolean = try {
        println(block())
        true
    } catch (t: Throwable) {
        println("PROBE|$name|FAIL|${t.javaClass.simpleName}: ${t.message?.take(300)}")
        false
    }

    // ---- STEP 0: fresh client version --------------------------------------
    val version = runCatching { client.clientVersion() }
        .onFailure { println("PROBE|version|FAIL|${it.message?.take(200)}") }
        .getOrNull()
    pass = pass && (version != null)
    if (version == null) {
        println("PROBE|verdict|FAIL|metadata path broken")
        kotlin.system.exitProcess(1)
    }
    println("PROBE|version|PASS|WEB_REMIX $version (scraped from homepage HTML)")

    // ---- STEP 1: SEARCH -----------------------------------------------------
    val top = runCatching {
        when (val r = client.search("bohemian rhapsody", SearchFilter.SONGS)) {
            is DhunResult.Success -> r.value.songs
            is DhunResult.Failure -> throw IllegalStateException("search: ${r.error}")
        }
    }
    pass = step("search") {
        val songs = top.getOrThrow()
        require(songs.isNotEmpty()) { "0 results" }
        val sb = StringBuilder("PROBE|search|PASS|${songs.size} music-song results\n")
        songs.take(10).forEachIndexed { i, s ->
            sb.append("SEARCH|${i + 1}|${s.title} | ${s.artistName} | ${s.id}\n")
        }
        sb.removeSuffix("\n").toString()
    } && pass
    val topTrack = top.getOrNull()?.firstOrNull()
    if (topTrack == null) {
        println("PROBE|verdict|FAIL|search broken")
        kotlin.system.exitProcess(1)
    }

    // ---- STEP 2+3: RESOLVE (production chain) + STREAM (bytes verified) ----
    // The fatal gate is the chain a shipped desktop app actually wires
    // (ADR-001: own-client primary -> yt-dlp failover, same as
    // YouTubeMusicProvider.forDesktop). NOT a weakening vs the old
    // yt-dlp-only gate: the URL must still come from production code and
    // real audio bytes must still stream (HTTP 200/206 + container magic).
    // If every engine is gated (typical CI datacenter-IP case), the chain
    // fails and the verdict stays FAIL — kill switch preserved.
    val ownClient = OwnClientStreamResolver(client)
    val ytDlp = YtDlpStreamResolver()
    val chain = ResolvingStreamResolver(primary = ownClient, fallback = ytDlp)

    suspend fun watchEngine(resolver: StreamResolver, label: String) {
        runCatching {
            when (val r = resolver.resolve(topTrack.id)) {
                is DhunResult.Success ->
                    println("WATCH|$label|OK|${r.value.bitrateKbps ?: "?"} kbps ${r.value.mimeType}")
                is DhunResult.Failure -> println("WATCH|$label|BROKEN|$r.error")
            }
        }.onFailure {
            println("WATCH|$label|BROKEN|${it.javaClass.simpleName}: ${it.message?.take(200)}")
        }
    }
    watchEngine(ownClient, "own-client")
    watchEngine(ytDlp, "ytdlp")

    pass = step("resolve+stream") {
        val info = when (val r = chain.resolve(topTrack.id)) {
            is DhunResult.Success -> r.value
            is DhunResult.Failure -> throw IllegalStateException("resolve via ${chain.name}: $r.error")
        }
        val conn = URL(info.audioUrl).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
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
        val magic = bytes.take(4).joinToString(" ") { String.format("%02X", it) }
        val verdict = when {
            bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() -> "WEBM/EBML container"
            String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "MP4/M4A container"
            else -> error("unknown container: $magic")
        }
            "PROBE|resolve|PASS|via ${chain.name} (\"${topTrack.title}\" by ${topTrack.artistName})\n" +
            "PROBE|stream|PASS|HTTP $code | $ctype | ${bytes.size}B | $magic | $verdict"
    } && pass

    // ---- STEP 4: RELATED ----------------------------------------------------
    pass = step("related") {
        when (val r = client.relatedTracks(topTrack.id)) {
            is DhunResult.Success -> {
                val tracks = r.value
                require(tracks.isNotEmpty()) { "0 related tracks" }
                val sb = StringBuilder("PROBE|related|PASS|${tracks.size} related tracks\n")
                tracks.take(5).forEachIndexed { i, t ->
                    sb.append("RELATED|${i + 1}|${t.title} | ${t.artistName}\n")
                }
                sb.removeSuffix("\n").toString()
            }
            is DhunResult.Failure -> throw IllegalStateException("related: ${r.error}")
        }
    } && pass

    // ---- STEP 5: WATCH — NewPipeExtractor health (non-fatal) ----------------
    runCatching {
        when (val r = NewPipeStreamResolver().resolve(topTrack.id)) {
            is DhunResult.Success -> println("WATCH|newpipe-stream|OK|${r.value.bitrateKbps}kbps ${r.value.mimeType}")
            is DhunResult.Failure -> println("WATCH|newpipe-stream|BROKEN|${r.error}")
        }
    }

    println("PROBE|verdict|${if (pass) "PASS" else "FAIL"}|extraction-pipeline-" +
        if (pass) "healthy" else "broken")
    kotlin.system.exitProcess(if (pass) 0 else 1)
}
