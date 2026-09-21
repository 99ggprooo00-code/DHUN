package dev.dhun.desktop.player

import dev.dhun.core.PlaybackState
import dev.dhun.core.Track

/** A file URI or bare path is local; only HTTP(S) can use CDN recovery. */
internal fun isLocalMediaUrl(url: String?): Boolean =
    url == null || !(url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true))

/** Local file failures must not send the user chasing a CDN/network problem. */
internal fun localFilePlaybackError(track: Track?, mediaUrl: String?): PlaybackState.Error? {
    if (track == null || mediaUrl == null || !isLocalMediaUrl(mediaUrl)) return null
    return PlaybackState.Error(
        track,
        "The local audio file could not be played. Try opening it in VLC, " +
            "or remove the download and download it again.",
        "libVLC failed to play a local file. Network/CDN recovery was not attempted. " +
            "The file may be missing, unreadable, damaged or in an unsupported format.",
    )
}
