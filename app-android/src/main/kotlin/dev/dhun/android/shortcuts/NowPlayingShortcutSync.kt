package dev.dhun.android.shortcuts

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.dhun.android.MainActivity
import dev.dhun.android.R
import dev.dhun.core.PlaybackState
import dev.dhun.core.Track

/**
 * Phase 15 — the dynamic "Now playing" launcher shortcut.
 *
 * While music plays, the app keeps one dynamic shortcut whose long label is
 * the current track; tapping it opens DHUN with the full player expanded.
 * Label rules are pure ([NowPlayingShortcutLabels], unit-tested); the
 * publishing wrapper is deliberately thin (ShortcutManagerCompat only, no
 * shadow-dependent logic).
 */
object NowPlayingShortcutLabels {

    /** Launcher long labels get truncated by the UI anyway — bound it. */
    const val MAX_LONG_LABEL = 60

    fun longLabel(title: String?, artistName: String?): String {
        val t = title?.trim().orEmpty().ifEmpty { "Untitled" }
        val a = artistName?.trim().orEmpty()
        val base = if (a.isEmpty()) t else "$t — $a"
        return if (base.length <= MAX_LONG_LABEL) base else base.take(MAX_LONG_LABEL - 1) + "…"
    }
}

/**
 * The track "now playing", if any. `Error` deliberately maps to null: a
 * mid-track failure should keep the last published shortcut instead of
 * wiping it (recovering playback keeps the old surface stable).
 */
fun PlaybackState?.shortcutTrack(): Track? = when (this) {
    is PlaybackState.Resolving -> track
    is PlaybackState.Buffering -> track
    is PlaybackState.Recovering -> track
    is PlaybackState.Playing -> track
    is PlaybackState.Paused -> track
    is PlaybackState.Error -> null
    PlaybackState.Idle -> null
    null -> null
}

/** Publishes/updates the dynamic shortcut. Safe to call on any state. */
class NowPlayingShortcutSync(private val context: Context) {

    fun publish(state: PlaybackState?) {
        val track = state.shortcutTrack() ?: return
        val info = ShortcutInfoCompat.Builder(context, ShortcutIntents.DYNAMIC_ID_NOW_PLAYING)
            .setShortLabel(context.getString(R.string.shortcut_now_playing_short))
            .setLongLabel(NowPlayingShortcutLabels.longLabel(track.title, track.artistName))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_now_playing))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(
                        ShortcutIntents.EXTRA_SHORTCUT_ACTION,
                        ShortcutIntents.VALUE_NOW_PLAYING,
                    ),
            )
            .build()
        // Some OEM launchers reject dynamic shortcuts (or the rate limiter
        // kicks in) — a shortcut must NEVER disturb playback.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, info) }
            .onFailure { Log.w(TAG, "now-playing shortcut push failed", it) }
    }

    private companion object {
        const val TAG = "DHUN"
    }
}
