package dev.dhun.android.equalizer

/**
 * The live playback audio session id (S4.3).
 *
 * [PlaybackGraph.buildExoPlayer][dev.dhun.android.playback.PlaybackGraph.buildExoPlayer]
 * generates a dedicated session (`AudioManager.generateAudioSessionId`),
 * pins the new ExoPlayer to it, and publishes it here — so the equalizer
 * engine always attaches to DHUN's own output, never the global mix
 * (session 0) and never another app's session.
 *
 * Exactly one player is live at a time (the service player, or the
 * session-less fallback in `MainActivity`), and both are built through
 * `buildExoPlayer`, so last-write-wins is the correct semantics: the
 * published id is always the current player's.
 *
 * [UNSET] (0) means no player has published yet, or id generation failed —
 * the engine treats it as "bypass EQ" rather than touching session 0.
 */
object AndroidAudioSession {
    const val UNSET: Int = 0

    @Volatile
    var sessionId: Int = UNSET
}
