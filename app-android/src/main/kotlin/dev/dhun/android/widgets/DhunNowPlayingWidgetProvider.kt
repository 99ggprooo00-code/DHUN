package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName as AndroidComponentName
import dev.dhun.android.playback.DhunPlaybackService

/**
 * Home-screen **Now Playing** widget — artwork placeholder, title, artist,
 * and transport (prev / play·pause / next). Tapping the body opens DHUN.
 *
 * Data source: the existing [DhunPlaybackService] `MediaSession` via a
 * short-lived [MediaController]. No playback engine changes; the widget
 * observes read-only. When the service is unreachable or the queue is empty
 * it shows the idle placeholder ("Nothing playing") and transport is dimmed
 * but still safe to tap (the provider swallows failures so the launcher
 * never sees a crash).
 *
 * Updates are triggered from:
 *  - [onUpdate] (system-initiated: widget added, `updatePeriodMillis`, or
 *    explicit [AppWidgetManager] broadcast)
 *  - every transport action ([WidgetIntents.ACTION_PLAY_PAUSE]/[ACTION_NEXT]/[ACTION_PREV])
 *    handled in [onReceive]
 *  - [DhunWidgetUpdater.requestUpdate] (callable from anywhere, e.g. future
 *    app-side hooks, without touching [DhunPlaybackService.kt])
 *
 * CI only proves compilation + unit tests; launcher rendering / real
 * artwork bitmap / live track-change push requires a device.
 */
class DhunNowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // System requested refresh for these ids — fetch live state.
        DhunWidgetUpdater.requestUpdate(context)
        // Also push an immediate idle placeholder so the widget never appears blank
        // during the async controller connect (the updater will overwrite it).
        if (appWidgetIds.isNotEmpty()) {
            val placeholder = DhunWidgetState.idle()
            for (id in appWidgetIds) {
                val views = DhunWidgetUpdater.buildNowPlayingViewsForId(context, placeholder, id)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WidgetIntents.ACTION_PLAY_PAUSE,
            WidgetIntents.ACTION_NEXT,
            WidgetIntents.ACTION_PREV -> handleTransport(context, intent.action!!)
            // ACTION_OPEN_APP is handled via PendingIntent.getActivity (MainActivity); no broadcast needed.
        }
    }

    private fun handleTransport(context: Context, action: String) {
        // MediaController must be used on main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { handleTransport(context, action) }
            return
        }
        val appContext = context.applicationContext
        val token = try {
            SessionToken(appContext, AndroidComponentName(appContext, DhunPlaybackService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "transport $action: no session token", e)
            DhunWidgetUpdater.requestUpdate(appContext)
            return
        }
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            future = MediaController.Builder(appContext, token).buildAsync()
        } catch (e: Exception) {
            Log.w(TAG, "transport $action: controller build failed", e)
            DhunWidgetUpdater.requestUpdate(appContext)
            return
        }
        val pending = future
        // Guard: if service doesn't answer, leave widget as-is and don't block.
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            if (!pending.isDone) {
                pending.cancel(true)
                DhunWidgetUpdater.requestUpdate(appContext)
            }
        }
        handler.postDelayed(timeout, 2500L)
        pending.addListener(
            {
                handler.removeCallbacks(timeout)
                try {
                    val controller = pending.get()
                    when (action) {
                        WidgetIntents.ACTION_PLAY_PAUSE -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        WidgetIntents.ACTION_NEXT -> {
                            if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                            else controller.seekToNext()
                        }
                        WidgetIntents.ACTION_PREV -> {
                            if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
                            else controller.seekToPrevious()
                        }
                    }
                    // Let the command propagate, then refresh widget visuals.
                    handler.postDelayed({ DhunWidgetUpdater.requestUpdate(appContext) }, 400L)
                    handler.postDelayed({ runCatching { controller.release() } }, 2000L)
                } catch (e: Exception) {
                    Log.w(TAG, "transport $action failed", e)
                    DhunWidgetUpdater.requestUpdate(appContext)
                    runCatching { pending.cancel(true) }
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    companion object {
        private const val TAG = "DHUN_WIDGET"
    }
}
