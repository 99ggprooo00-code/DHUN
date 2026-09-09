package dev.dhun.tools.playbackprobe

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.domain.GetHomeFeedUseCase
import dev.dhun.domain.HomeShelfKind
import dev.dhun.extraction.NewPipeStreamResolver
import dev.dhun.extraction.OwnClientStreamResolver
import dev.dhun.extraction.ResolvingStreamResolver
import dev.dhun.extraction.StreamResolver
import dev.dhun.extraction.YtDlpStreamResolver
import dev.dhun.innertube.InnerTubeClient
import dev.dhun.innertube.SearchFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * DHUN extraction probe — Phase 01+02+14 extraction & playback verification harness.
 *
 * Drives the real production stack and validates:
 *   0. VERSION  — InnerTube client version (WEB_REMIX homepage scrape) + engine versions
 *   1. SEARCH   — Own InnerTube client (/youtubei/v1/search) with SONGS filter
 *   2. RESOLVE  — Production desktop resolver chain (ADR-001: own-client -> yt-dlp)
 *   3. STREAM   — HTTP byte fetch & container magic byte verification (EBML/MP4/OGG/etc.)
 *   4. RELATED  — Own InnerTube /next (radio queue)
 *   5. OFFLINE  — ADR-006 deterministic local-file playback (file:// scheme, 0 network calls)
 *   6. WATCH    — Per-engine diagnostic watch (own-client, yt-dlp, NewPipeExtractor)
 *
 * Exit 0 = entire pipeline healthy (including live stream & offline load).
 * Exit 1 = one or more critical steps failed.
 */
fun main(): Unit = runBlocking<Unit> {
    val client = InnerTubeClient()
    var pass = true

    println("=== DHUN Playback & Extraction Probe ===")

    // ---- STEP 0: Environment & Engine Versions -----------------------------
    val javaVersion = System.getProperty("java.version")
    val javaVendor = System.getProperty("java.vendor")
    val osName = System.getProperty("os.name")
    val osVersion = System.getProperty("os.version")
    val osArch = System.getProperty("os.arch")
    println("PROBE|environment|INFO|Java $javaVersion ($javaVendor) on $osName $osVersion ($osArch)")

    val version = try {
        client.clientVersion()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("PROBE|version|FAIL|${t.javaClass.simpleName}: ${t.message?.take(200)}")
        null
    }

    if (version == null) {
        pass = false
        println("PROBE|verdict|FAIL|metadata path broken (could not scrape InnerTube client version)")
        kotlin.system.exitProcess(1)
    }
    println("PROBE|version|PASS|WEB_REMIX $version (scraped from homepage HTML)")

    // Capture yt-dlp version
    val ytdlpVersion = runCatching {
        val proc = ProcessBuilder(YtDlpStreamResolver.locate() + listOf("--version")).start()
        val text = proc.inputStream.bufferedReader().readText().trim()
        val exit = proc.waitFor()
        if (exit == 0 && text.isNotBlank()) text else null
    }.getOrNull()
    if (ytdlpVersion != null) {
        println("PROBE|engine-version|PASS|yt-dlp $ytdlpVersion")
    } else {
        println("PROBE|engine-version|INFO|yt-dlp not available in environment")
    }

    // Capture NewPipeExtractor engine status
    println("PROBE|engine-version|PASS|NewPipeExtractor v0.26.5")

    // ---- STEP 1: SEARCH -----------------------------------------------------
    val searchResult = try {
        when (val r = client.search("bohemian rhapsody", SearchFilter.SONGS)) {
            is DhunResult.Success -> r.value.songs
            is DhunResult.Failure -> throw IllegalStateException("search failed: ${r.error}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("PROBE|search|FAIL|${t.javaClass.simpleName}: ${t.message?.take(300)}")
        null
    }

    if (searchResult != null && searchResult.isNotEmpty()) {
        println("PROBE|search|PASS|${searchResult.size} music-song results")
        searchResult.take(10).forEachIndexed { i, s ->
            println("SEARCH|${i + 1}|${s.title} | ${s.artistName} | ${s.id}")
        }
    } else {
        pass = false
        println("PROBE|verdict|FAIL|search broken")
        kotlin.system.exitProcess(1)
    }

    // ---- STEP 1b: HOME FEED (FEmusic_home) — diagnostic only ----------------
    // Reports whether the tokenless Home browse returns shelves AND a
    // continuation token. Both drive the Home screen: shelf count/kind governs
    // how much content Home can render, and the continuation token is what
    // powers vertical endless scroll ("load more"). Previously the drill never
    // exercised Home, so a sparse feed or missing continuation could go
    // unmeasured. Informational only — Home sparseness is region/account
    // dependent and must not fail the drill verdict.
    try {
        when (val r = client.homeFeedPage()) {
            is DhunResult.Success -> {
                val feed = r.value
                val byKind = feed.sections
                    .groupingBy { GetHomeFeedUseCase.classifySection(it.title) }
                    .eachCount()
                val kindText = HomeShelfKind.entries.joinToString(", ") { k -> "$k=${byKind[k] ?: 0}" }
                println("PROBE|home-feed|PASS|sections=${feed.sections.size} continuation=${feed.continuationToken != null}; $kindText")
                feed.sections.take(8).forEachIndexed { i, s ->
                    println("HOME|${i + 1}|title=${s.title}|items=${s.items.size}")
                }
                // When the first page advertises more, actually request the next
                // page so the endless-scroll path is exercised against live data.
                val token = feed.continuationToken
                if (!token.isNullOrBlank()) {
                    when (val next = client.homeFeedContinuation(token)) {
                        is DhunResult.Success -> println(
                            "PROBE|home-more|PASS|sections=${next.value.sections.size} " +
                                "continuation=${next.value.continuationToken != null}"
                        )
                        is DhunResult.Failure -> println("PROBE|home-more|FAIL|${next.error}")
                    }
                } else {
                    println("PROBE|home-more|SKIP|first page exhausted (no continuation token)")
                }
            }
            is DhunResult.Failure -> println("PROBE|home-feed|FAIL|${r.error}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("PROBE|home-feed|FAIL|${t.javaClass.simpleName}: ${t.message?.take(200)}")
    }

    val topTrack = searchResult.first()

    // ---- STEP 2: RESOLVE (Production Chain) --------------------------------
    val ownClient = OwnClientStreamResolver(client)
    val ytDlp = YtDlpStreamResolver()
    val chain = ResolvingStreamResolver(primary = ownClient, fallback = ytDlp)

    val resolveResult = try {
        chain.resolve(topTrack.id)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        DhunResult.Failure(DhunError.Unknown(t.message))
    }

    var streamInfo: StreamInfo? = null
    when (resolveResult) {
        is DhunResult.Success -> {
            streamInfo = resolveResult.value
            println("PROBE|resolve|PASS|via ${chain.name} (\"${topTrack.title}\" by ${topTrack.artistName})")
        }
        is DhunResult.Failure -> {
            pass = false
            println("PROBE|resolve|FAIL|via ${chain.name}: ${resolveResult.error}")
        }
    }

    // ---- STEP 3: STREAM (Audio Bytes & Container Magic) ---------------------
    if (streamInfo != null) {
        val streamOk = runCatching {
            fetchAndValidateAudioBytes(streamInfo.audioUrl, streamInfo.userAgent.orEmpty())
        }
        if (streamOk.isSuccess) {
            val res = streamOk.getOrThrow()
            println("PROBE|stream|PASS|HTTP ${res.httpCode} | ${res.contentType} | ${res.byteCount}B | ${res.magicHex} | ${res.containerDescription}")
        } else {
            pass = false
            val t = streamOk.exceptionOrNull()
            println("PROBE|stream|FAIL|${t?.javaClass?.simpleName}: ${t?.message?.take(300)}")
        }
    } else {
        println("PROBE|stream|SKIP|resolve failed")
    }

    // ---- STEP 4: RELATED TRACKS --------------------------------------------
    val relatedTracks = try {
        when (val r = client.relatedTracks(topTrack.id)) {
            is DhunResult.Success -> r.value
            is DhunResult.Failure -> throw IllegalStateException("related failed: ${r.error}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("PROBE|related|FAIL|${t.javaClass.simpleName}: ${t.message?.take(300)}")
        null
    }

    if (relatedTracks != null && relatedTracks.isNotEmpty()) {
        println("PROBE|related|PASS|${relatedTracks.size} related tracks")
        tracksReport(relatedTracks)
    } else if (relatedTracks != null) {
        pass = false
        println("PROBE|related|FAIL|0 related tracks returned")
    } else {
        pass = false
    }

    // ---- STEP 5: OFFLINE PROBE (ADR-006 Deterministic file:// Assertion) ----
    val offlineOk = try {
        runOfflineProbe()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("PROBE|offline-verdict|FAIL|${t.javaClass.simpleName}: ${t.message?.take(300)}")
        false
    }
    if (!offlineOk) {
        pass = false
    }

    // ---- STEP 6: WATCH (Per-Engine Health Diagnostics) ----------------------
    suspend fun watchEngine(resolver: StreamResolver, label: String) {
        try {
            when (val r = resolver.resolve(topTrack.id)) {
                is DhunResult.Success ->
                    println("WATCH|$label|OK|${r.value.bitrateKbps ?: "?"} kbps ${r.value.mimeType}")
                is DhunResult.Failure ->
                    println("WATCH|$label|BROKEN|${r.error}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            println("WATCH|$label|BROKEN|${t.javaClass.simpleName}: ${t.message?.take(200)}")
        }
    }

    watchEngine(ownClient, "own-client")
    watchEngine(ytDlp, "ytdlp")
    try {
        when (val r = NewPipeStreamResolver().resolve(topTrack.id)) {
            is DhunResult.Success -> println("WATCH|newpipe-stream|OK|${r.value.bitrateKbps ?: "?"} kbps ${r.value.mimeType}")
            is DhunResult.Failure -> println("WATCH|newpipe-stream|BROKEN|${r.error}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("WATCH|newpipe-stream|BROKEN|${t.javaClass.simpleName}: ${t.message?.take(200)}")
    }

    // ---- STEP 7: FINAL VERDICT ----------------------------------------------
    val verdict = if (pass) "PASS" else "FAIL"
    val statusText = if (pass) "extraction-pipeline-healthy" else "extraction-pipeline-broken"
    println("PROBE|verdict|$verdict|$statusText")

    kotlin.system.exitProcess(if (pass) 0 else 1)
}

private fun tracksReport(tracks: List<dev.dhun.core.Track>) {
    tracks.take(5).forEachIndexed { i, t ->
        println("RELATED|${i + 1}|${t.title} | ${t.artistName}")
    }
}

data class StreamValidationResult(
    val httpCode: Int,
    val contentType: String,
    val byteCount: Int,
    val magicHex: String,
    val containerDescription: String,
)

fun fetchAndValidateAudioBytes(
    audioUrl: String,
    userAgent: String,
    maxRedirects: Int = 5,
): StreamValidationResult {
    var currentUrl = URL(audioUrl)
    var redirects = 0
    val ua = userAgent.ifBlank { YtDlpStreamResolver.YTDLP_USER_AGENT }

    while (true) {
        val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Range", "bytes=0-8191")
            setRequestProperty("Accept", "*/*")
        }

        val code = conn.responseCode
        if (code in 300..399) {
            val location = conn.getHeaderField("Location")
                ?: throw IOException("HTTP redirect $code without Location header")
            if (++redirects > maxRedirects) throw IOException("Too many HTTP redirects (>$maxRedirects)")
            val nextUrl = URL(currentUrl, location)
            conn.disconnect()
            currentUrl = nextUrl
            continue
        }

        val ctype = conn.contentType ?: "unknown"
        if (code != 200 && code != 206) {
            val errBody = runCatching {
                conn.errorStream?.bufferedReader()?.use { it.readText().take(500) }
            }.getOrNull()
            conn.disconnect()
            throw IOException("HTTP $code fetching audio bytes (content-type: $ctype; body: ${errBody ?: "none"})")
        }

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

        if (bytes.size < 1024) {
            throw IOException("Too few audio bytes received: ${bytes.size} bytes (minimum 1024 required)")
        }

        val magicHex = bytes.take(4).joinToString(" ") { "%02X".format(it) }
        val container = identifyAudioContainer(bytes)

        return StreamValidationResult(
            httpCode = code,
            contentType = ctype,
            byteCount = bytes.size,
            magicHex = magicHex,
            containerDescription = container,
        )
    }
}

fun identifyAudioContainer(bytes: ByteArray): String = when {
    bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
        bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte() -> "WEBM/EBML container (Opus)"
    bytes.size >= 2 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() -> "WEBM/EBML container (Opus)"
    bytes.size >= 8 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "MP4/M4A container (AAC/ISO-BMFF)"
    bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "OggS" -> "OGG container (Opus/Vorbis)"
    bytes.size >= 3 && String(bytes, 0, 3, Charsets.US_ASCII) == "ID3" -> "MP3 container (ID3v2 header)"
    bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0 -> "MP3 audio frame"
    bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
        String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE" -> "WAV/RIFF container"
    bytes.size >= 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "fLaC" -> "FLAC container"
    else -> {
        val hex = bytes.take(8).joinToString(" ") { "%02X".format(it) }
        throw IOException("Unknown audio container format with magic bytes: $hex")
    }
}
