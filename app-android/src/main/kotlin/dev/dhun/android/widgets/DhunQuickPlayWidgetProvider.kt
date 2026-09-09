package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
 * Compact **Quick Play** widget — 1×1 / 2×1 variant showing title + single
 * play/pause. Shares the same [DhunWidgetUpdater] / [MediaController] data
 * path as [DhunNowPlayingWidgetProvider] but uses [R.layout.widget_quick_play].
 *
 * The small form omits prev/next to stay legible; the large Now Playing
 * widget remains the full transport surface. Both are registered in the
 * manifest and appear as separate entries in the launcher's widget picker.
 */
class DhunQuickPlayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DhunWidgetUpdater.requestUpdate(context)
        if (appWidgetIds.isNotEmpty()) {
            val placeholder = DhunWidgetState.idle()
            for (id in appWidgetIds) {
                val views = DhunWidgetUpdater.buildQuickPlayViewsForId(context, placeholder, id)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetIntents.ACTION_PLAY_PAUSE) {
            handlePlayPause(context)
        }
    }

    private fun handlePlayPause(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { handlePlayPause(context) }
            return
        }
        val appContext = context.applicationContext
        val token = try {
            SessionToken(appContext, AndroidComponentName(appContext, DhunPlaybackService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "quick-play: no session token", e)
            DhunWidgetUpdater.requestUpdate(appContext)
            return
        }
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        try {
            future = MediaController.Builder(appContext, token).buildAsync()
        } catch (e: Exception) {
            Log.w(TAG, "quick-play: controller build failed", e)
            DhunWidgetUpdater.requestUpdate(appContext)
            return
        }
        val pending = future
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
                    if (controller.isPlaying) controller.pause() else controller.play()
                    handler.postDelayed({ DhunWidgetUpdater.requestUpdate(appContext) }, 400L)
                    handler.postDelayed({ runCatching { controller.release() } }, 2000L)
                } catch (e: Exception) {
                    Log.w(TAG, "quick-play transport failed", e)
                    DhunWidgetUpdater.requestUpdate(appContext)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    companion object {
        private const val TAG = "DHUN_WIDGET"
    }
}
