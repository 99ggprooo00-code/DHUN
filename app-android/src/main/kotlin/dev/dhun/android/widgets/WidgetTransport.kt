package dev.dhun.android.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName as AndroidComponentName
import dev.dhun.android.playback.DhunPlaybackService

/**
 * Shared transport executor for the widget providers.
 *
 * Each action connects a short-lived [MediaController] to the existing
 * [DhunPlaybackService] session, applies one player command, then refreshes
 * the widgets. Failures are swallowed (with a log) so the launcher never
 * sees a crash — a tap during a dead service just re-renders idle.
 */
object WidgetTransport {

    private const val TAG = "DHUN_WIDGET"
    private const val TIMEOUT_MS = 2500L
    private const val REFRESH_DELAY_MS = 400L
    private const val RELEASE_DELAY_MS = 2000L

    fun isTransportAction(action: String?): Boolean = when (action) {
        WidgetIntents.ACTION_PLAY_PAUSE,
        WidgetIntents.ACTION_NEXT,
        WidgetIntents.ACTION_PREV,
        WidgetIntents.ACTION_SHUFFLE,
        WidgetIntents.ACTION_REPEAT,
        -> true
        else -> false
    }

    fun handle(context: Context, action: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { handle(context, action) }
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
        val pending = try {
            MediaController.Builder(appContext, token).buildAsync()
        } catch (e: Exception) {
            Log.w(TAG, "transport $action: controller build failed", e)
            DhunWidgetUpdater.requestUpdate(appContext)
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable {
            if (!pending.isDone) {
                pending.cancel(true)
                DhunWidgetUpdater.requestUpdate(appContext)
            }
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
        pending.addListener(
            {
                handler.removeCallbacks(timeout)
                try {
                    val controller = pending.get()
                    runCatching { applyAction(controller, action) }
                    // The service's widget listener usually beats this refresh;
                    // keep it as the fallback for races and the app-dead path.
                    handler.postDelayed({ DhunWidgetUpdater.requestUpdate(appContext) }, REFRESH_DELAY_MS)
                    handler.postDelayed({ runCatching { controller.release() } }, RELEASE_DELAY_MS)
                } catch (e: Exception) {
                    Log.w(TAG, "transport $action failed", e)
                    DhunWidgetUpdater.requestUpdate(appContext)
                    runCatching { pending.cancel(true) }
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun applyAction(controller: MediaController, action: String) {
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
            WidgetIntents.ACTION_SHUFFLE -> {
                controller.shuffleModeEnabled = !controller.shuffleModeEnabled
            }
            WidgetIntents.ACTION_REPEAT -> {
                controller.repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
    }
}
