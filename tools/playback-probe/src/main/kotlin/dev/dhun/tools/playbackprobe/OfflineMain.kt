package dev.dhun.tools.playbackprobe

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.DownloadState
import dev.dhun.core.DownloadedTrack
import dev.dhun.core.StreamInfo
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SqlDelightDownloadRepository
import dev.dhun.extraction.OfflineFirstStreamResolver
import dev.dhun.extraction.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Deterministic ADR-006 verification probe.
 *
 * This is deliberately separate from the live extraction kill-switch. It
 * persists a COMPLETED download row in the real JVM SQLDelight repository,
 * resolves it through OfflineFirstStreamResolver, and opens the returned
 * file:// URI without permitting the network resolver to run.
 *
 * It verifies shared resolver/repository and JVM file loading only. Android
 * Media3 FileDataSource, Desktop vlcj decoding, and audible playback still
 * require real-device/PC verification.
 */
fun main(): Unit = runBlocking {
    val success = runOfflineProbe()
    if (!success) {
        kotlin.system.exitProcess(1)
    }
}

/**
 * Executes the deterministic offline playback assertion:
 * 1. Copies bundled WAV fixture to a temporary file.
 * 2. Persists a COMPLETED DownloadedTrack into an in-memory SQLDelight repository.
 * 3. Resolves the track using [OfflineFirstStreamResolver] backed by a failing network resolver.
 * 4. Asserts the resolved URI is `file://`, points to the local file, and triggered zero network calls.
 * 5. Reads and validates the 44-byte RIFF/WAVE header directly from the resolved local file.
 *
 * Returns `true` if all assertions pass, `false` otherwise.
 */
suspend fun runOfflineProbe(logPrefix: String = "PROBE"): Boolean {
    val fixture = Thread.currentThread().contextClassLoader
        .getResourceAsStream("fixtures/offline-track.wav")
    if (fixture == null) {
        println("$logPrefix|offline-verdict|FAIL|offline fixture 'fixtures/offline-track.wav' is missing from probe classpath")
        return false
    }

    val localFile = Files.createTempFile("dhun-offline-probe-", ".wav")
    try {
        fixture.use { input ->
            Files.copy(input, localFile, StandardCopyOption.REPLACE_EXISTING)
        }

        val repository = SqlDelightDownloadRepository(
            DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()),
            Dispatchers.Unconfined,
        )
        val trackId = "offline-fixture-track"
        val fileSize = Files.size(localFile)
        repository.upsert(
            DownloadedTrack(
                trackId = trackId,
                title = "DHUN offline probe fixture",
                artistName = "DHUN verification",
                localAudioPath = localFile.toString(),
                fileSizeBytes = fileSize,
                mimeType = "audio/wav",
                downloadState = DownloadState.COMPLETED,
                downloadedAtEpochMs = 1L,
            ),
        )

        val network = NetworkMustNotRun()
        val resolver = OfflineFirstStreamResolver(
            downloads = repository,
            primary = network,
            fileExists = { path -> Files.isRegularFile(Path.of(path)) },
        )

        val info = when (val result = resolver.resolve(trackId)) {
            is DhunResult.Success -> result.value
            is DhunResult.Failure -> {
                println("$logPrefix|offline-resolve|FAIL|${result.error}")
                println("$logPrefix|offline-verdict|FAIL|offline resolver failed")
                return false
            }
        }

        val uri = URI.create(info.audioUrl)
        if (uri.scheme != "file") {
            println("$logPrefix|offline-resolve|FAIL|expected file:// URI, got ${info.audioUrl}")
            println("$logPrefix|offline-verdict|FAIL|URI scheme mismatch")
            return false
        }

        val resolvedPath = Path.of(uri).toAbsolutePath().normalize()
        if (resolvedPath != localFile.toAbsolutePath().normalize()) {
            println("$logPrefix|offline-resolve|FAIL|selected $resolvedPath instead of $localFile")
            println("$logPrefix|offline-verdict|FAIL|resolved path mismatch")
            return false
        }

        if (network.calls != 0) {
            println("$logPrefix|offline-resolve|FAIL|network resolver was called ${network.calls} time(s)")
            println("$logPrefix|offline-verdict|FAIL|network leakage in offline mode")
            return false
        }

        if (info.contentLengthBytes != fileSize) {
            println("$logPrefix|offline-load|FAIL|reported ${info.contentLengthBytes}B != actual ${fileSize}B")
            println("$logPrefix|offline-verdict|FAIL|file size mismatch")
            return false
        }

        val header = Files.newInputStream(resolvedPath).use { input -> input.readNBytes(44) }
        if (header.size != 44) {
            println("$logPrefix|offline-load|FAIL|yielded only ${header.size} header bytes")
            println("$logPrefix|offline-verdict|FAIL|incomplete WAV header")
            return false
        }

        val riff = String(header.copyOfRange(0, 4), Charsets.US_ASCII)
        val wave = String(header.copyOfRange(8, 12), Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") {
            println("$logPrefix|offline-load|FAIL|invalid WAV header magic: $riff/$wave")
            println("$logPrefix|offline-verdict|FAIL|corrupt WAV container")
            return false
        }

        println("$logPrefix|offline-resolve|PASS|${info.audioUrl} | network-calls=0")
        println("$logPrefix|offline-load|PASS|${header.size}B WAV header | ${fileSize}B local file")
        println("$logPrefix|offline-verdict|PASS|completed-download-resolves-and-loads-without-network")
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        println("$logPrefix|offline-verdict|FAIL|${t.javaClass.simpleName}: ${t.message?.take(300)}")
        return false
    } finally {
        Files.deleteIfExists(localFile)
    }
}

private class NetworkMustNotRun : StreamResolver {
    override val name: String = "network-disabled"
    var calls: Int = 0

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
        calls++
        return DhunResult.Failure(
            DhunError.Unavailable("network resolver was called for $videoId"),
        )
    }
}
