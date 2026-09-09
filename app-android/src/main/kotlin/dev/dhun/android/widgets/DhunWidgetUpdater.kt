package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName as AndroidComponentName
import dev.dhun.android.R
import dev.dhun.android.playback.DhunPlaybackService
import kotlinx.coroutines.guava.await

/**
 * Pushes DHUN widget RemoteViews for every widget instance.
 *
 * **Design — additive, no DhunPlaybackService edits:**
 * The updater queries the *existing* [DhunPlaybackService] [androidx.media3.session.MediaSession]
 * via a short-lived [MediaController]. If the service is unreachable (idle,
 * crashed, not yet started, or controller timeout) it renders the idle
 * placeholder from [DhunWidgetState.idle] rather than crashing or showing
 * stale data. Transport actions (play/pause/next/prev) are handled in each
 * provider's `onReceive` which re-uses this same controller pattern.
 *
 * **Threading:** [MediaController] requires the main thread. Every call here
 * posts to the main looper; controller callbacks also arrive on main.
 *
 * **Honest limits:** CI green = compile + unit tests. Widget renders on a
 * real launcher, launcher auto-refresh rate, artwork bitmap loading, and
 * periodic track-change polling are **not verified** by CI — they require
 * a device. The updater is triggered on `onUpdate`, on every transport
 * `ACTION_*`, and when the app calls [requestUpdate] explicitly.
 */
object DhunWidgetUpdater {

    private const val TAG = "DHUN_WIDGET"
    private const val CONTROLLER_TIMEOUT_MS = 3000L

    /** Request an update for *all* DHUN widget instances (both providers). */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val nowPlayingIds = manager.getAppWidgetIds(
            ComponentName(appContext, DhunNowPlayingWidgetProvider::class.java),
        )
        val quickPlayIds = manager.getAppWidgetIds(
            ComponentName(appContext, DhunQuickPlayWidgetProvider::class.java),
        )
        if (nowPlayingIds.isEmpty() && quickPlayIds.isEmpty()) return
        // Try to read live session; fall back to idle if not available.
        fetchStateAndPush(appContext, manager, nowPlayingIds, quickPlayIds)
    }

    /** Push a specific state to a set of widget ids (used by tests and fallback). */
    internal fun pushState(
        context: Context,
        manager: AppWidgetManager,
        state: DhunWidgetState,
        nowPlayingIds: IntArray,
        quickPlayIds: IntArray,
    ) {
        if (nowPlayingIds.isNotEmpty()) {
            val views = buildNowPlayingViews(context, state)
            for (id in nowPlayingIds) {
                // Each instance needs its own PendingIntents (widgetId-scoped).
                val perInstance = buildNowPlayingViewsForId(context, state, id)
                manager.updateAppWidget(id, perInstance)
            }
            // Also broadcast update for the view prototype (keeps preview in picker correct)
            if (nowPlayingIds.size == 1) {
                // already handled per-id
            } else {
                // no batch—RemoteViews are per-id due to PendingIntent scoping
                // This branch is intentionally no-op; per-id loop above did the work.
            }
            // Keep local var used so lint understands intent
            @Suppress("UNUSED_VARIABLE") val ignored = views
        }
        if (quickPlayIds.isNotEmpty()) {
            for (id in quickPlayIds) {
                val perInstance = buildQuickPlayViewsForId(context, state, id)
                manager.updateAppWidget(id, perInstance)
            }
        }
    }

    private fun fetchStateAndPush(
        context: Context,
        manager: AppWidgetManager,
        nowPlayingIds: IntArray,
        quickPlayIds: IntArray,
    ) {
        // Ensure main thread for MediaController.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                fetchStateAndPush(context, manager, nowPlayingIds, quickPlayIds)
            }
            return
        }
        val token = try {
            SessionToken(context, AndroidComponentName(context, DhunPlaybackService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "session token unavailable — showing idle", e)
            pushState(context, manager, DhunWidgetState.idle(), nowPlayingIds, quickPlayIds)
            return
        }
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            future = MediaController.Builder(context, token).buildAsync()
        } catch (e: Exception) {
            Log.w(TAG, "controller build failed — showing idle", e)
            pushState(context, manager, DhunWidgetState.idle(), nowPlayingIds, quickPlayIds)
            return
        }

        val pending = future
        // Timeout guard — don't leave widget blank if service doesn't answer.
        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!pending.isDone) {
                pending.cancel(true)
                Log.w(TAG, "controller connect timeout — showing idle")
                pushState(context, manager, DhunWidgetState.idle(), nowPlayingIds, quickPlayIds)
            }
        }
        handler.postDelayed(timeoutRunnable, CONTROLLER_TIMEOUT_MS)

        pending.addListener(
            {
                handler.removeCallbacks(timeoutRunnable)
                try {
                    val controller = pending.get()
                    val metadata = controller.currentMediaItem?.mediaMetadata
                    val title = metadata?.title
                    val artist = metadata?.artist
                    val isPlaying = try { controller.isPlaying } catch (_: Exception) { false }
                    val state = DhunWidgetState.fromMetadata(title, artist, isPlaying)
                    pushState(context, manager, state, nowPlayingIds, quickPlayIds)
                    // Delay release so RemoteViews have settled; controller is short-lived.
                    handler.postDelayed({ runCatching { controller.release() } }, 1000L)
                } catch (e: Exception) {
                    Log.w(TAG, "controller unavailable — showing idle", e)
                    pushState(context, manager, DhunWidgetState.idle(), nowPlayingIds, quickPlayIds)
                    runCatching { pending.cancel(true) }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    // ------------------------------------------------------------------ RemoteViews

    internal fun buildNowPlayingViews(context: Context, state: DhunWidgetState): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
            applyCommon(context, state, this)
        }

    internal fun buildNowPlayingViewsForId(context: Context, state: DhunWidgetState, appWidgetId: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
            applyCommon(context, state, this)
            // Wire per-instance PendingIntents (distinct request codes per widget)
            setOnClickPendingIntent(R.id.widget_prev, WidgetIntents.prevIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId))
            setOnClickPendingIntent(R.id.widget_play_pause, WidgetIntents.playPauseIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId))
            setOnClickPendingIntent(R.id.widget_next, WidgetIntents.nextIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId))
            setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openAppIntent(context, appWidgetId))
            // For idle, also make the empty state tap target whole widget; transport still works (will be no-op in service).
        }

    internal fun buildQuickPlayViewsForId(context: Context, state: DhunWidgetState, appWidgetId: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_quick_play).apply {
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            // Quick-play shows single play/pause; hide artist when idle to avoid double placeholder.
            if (state.isIdle) {
                setTextViewText(R.id.widget_artist, "")
            }
            val playPauseRes = if (state.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
            setImageViewResource(R.id.widget_play_pause, playPauseRes)
            // Content descriptions for accessibility (TalkBack) — RemoteViews supports it via setContentDescription.
            setContentDescription(R.id.widget_play_pause, if (state.isPlaying) "Pause" else "Play")
            setOnClickPendingIntent(R.id.widget_play_pause, WidgetIntents.playPauseIntent(context, DhunQuickPlayWidgetProvider::class.java, appWidgetId))
            setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openAppIntent(context, appWidgetId))
            // Quick-play's prev/next are omitted in 1x1; if layout ever adds them, wire them here.
        }

    private fun RemoteViews.applyCommon(context: Context, state: DhunWidgetState, views: RemoteViews) {
        // Title / artist
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_artist, state.artist)
        // Play/pause glyph reflects isPlaying; idle shows play.
        val playPauseRes = if (state.isPlaying && state.hasTrack) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
        views.setImageViewResource(R.id.widget_play_pause, playPauseRes)
        views.setContentDescription(R.id.widget_play_pause, if (state.isPlaying && state.hasTrack) "Pause" else "Play")
        views.setContentDescription(R.id.widget_prev, "Previous")
        views.setContentDescription(R.id.widget_next, "Next")
        // Dim transport when idle — user sees controls but they become a gentle open-app hint.
        val alpha = if (state.isIdle) 0.5f else 1f
        // RemoteViews has no setAlpha for ImageView directly, but we can use setInt with alpha via view property.
        // Use setFloat if available (API 31+); fallback to no-op on older — idle still shows placeholder text.
        try {
            views.setFloat(R.id.widget_prev, "setAlpha", alpha)
            views.setFloat(R.id.widget_play_pause, "setAlpha", alpha)
            views.setFloat(R.id.widget_next, "setAlpha", alpha)
        } catch (_: Exception) {
        }
        // Artwork placeholder — actual bitmap fetch would require Coil/network and is out of scope for CI;
        // we keep the static note icon and note that launcher hardware verification would show real artwork
        // via MediaMetadata.artworkData when available (future enhancement, documented as limitation).
        views.setImageViewResource(R.id.widget_artwork, R.drawable.widget_ic_music_note)
    }
}
