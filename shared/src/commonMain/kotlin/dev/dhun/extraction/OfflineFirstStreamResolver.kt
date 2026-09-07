package dev.dhun.extraction

import dev.dhun.core.DhunResult
import dev.dhun.core.StreamInfo
import dev.dhun.download.DownloadRepository

/**
 * ADR-006 offline-first playback hook: if a track has a COMPLETED persistent
 * download whose local file is present, resolve to that local file instead of
 * hitting the network/tokenless InnerTube chain. Otherwise delegate to the
 * wrapped (network) [StreamResolver].
 *
 * The [StreamInfo.audioUrl] returned is a `file://` URI. Platform byte layers
 * must route `file://` to a local-file reader:
 *  - Android: [dev.dhun.android.playback.PlaybackGraph]'s data source handles
 *    the `file` scheme via a `FileDataSource` (not the HTTP User-Agent path).
 *  - Desktop: vlcj loads a `file://` MRL directly (local-path playback).
 *
 * [fileExists] is injected so the check is platform- and test-friendly; the
 * default trusts that a COMPLETED row means the file was committed (the
 * downloader renames `.part` → final only on success).
 */
class OfflineFirstStreamResolver(
    private val downloads: DownloadRepository,
    private val primary: StreamResolver,
    private val fileExists: suspend (String) -> Boolean = { true },
) : StreamResolver {

    override val name: String = "offline-first(${primary.name})"

    override suspend fun resolve(videoId: String): DhunResult<StreamInfo> {
        val downloaded = downloads.getCompleted(videoId)
        if (downloaded != null && fileExists(downloaded.localAudioPath)) {
            return DhunResult.Success(
                StreamInfo(
                    videoId = videoId,
                    audioUrl = toFileUri(downloaded.localAudioPath),
                    mimeType = downloaded.mimeType.ifBlank { "audio/webm" },
                    bitrateKbps = downloaded.bitrateKbps,
                    contentLengthBytes = downloaded.fileSizeBytes,
                    // A local file has no resolving identity to preserve.
                    userAgent = null,
                ),
            )
        }
        return primary.resolve(videoId)
    }

    private fun toFileUri(path: String): String = "file://$path"
}
