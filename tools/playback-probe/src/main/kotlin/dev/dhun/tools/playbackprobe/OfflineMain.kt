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
    val fixture = Thread.currentThread().contextClassLoader
        .getResourceAsStream("fixtures/offline-track.wav")
        ?: error("offline fixture is missing from the probe runtime classpath")
    val localFile = Files.createTempFile("dhun-offline-probe-", ".wav")
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
        is DhunResult.Failure -> error("offline resolver failed: ${result.error}")
    }

    val uri = URI.create(info.audioUrl)
    require(uri.scheme == "file") { "expected file:// URI, got ${info.audioUrl}" }
    val resolvedPath = Path.of(uri).toAbsolutePath().normalize()
    require(resolvedPath == localFile.toAbsolutePath().normalize()) {
        "resolver selected $resolvedPath instead of $localFile"
    }
    require(network.calls == 0) {
        "offline resolver called the network resolver ${network.calls} time(s)"
    }
    require(info.contentLengthBytes == fileSize) {
        "resolver reported ${info.contentLengthBytes} bytes, fixture is $fileSize bytes"
    }

    val header = Files.newInputStream(resolvedPath).use { input -> input.readNBytes(44) }
    require(header.size == 44) { "local file yielded only ${header.size} header bytes" }
    require(header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray())) {
        "local file is not a RIFF/WAV container"
    }
    require(header.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) {
        "local file is not a WAVE container"
    }

    println("PROBE|offline-resolve|PASS|${info.audioUrl} | network-calls=${network.calls}")
    println("PROBE|offline-load|PASS|${header.size}B WAV header | ${fileSize}B local file")
    println("PROBE|offline-verdict|PASS|completed-download-resolves-and-loads-without-network")

    Files.deleteIfExists(localFile)
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
