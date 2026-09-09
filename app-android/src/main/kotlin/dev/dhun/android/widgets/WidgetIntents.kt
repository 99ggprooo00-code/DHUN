package dev.dhun.android.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.dhun.android.MainActivity

/**
 * Intent contract for DHUN home-screen widgets (candidate 26).
 *
 * Both widget providers ([DhunNowPlayingWidgetProvider] and
 * [DhunQuickPlayWidgetProvider]) use these actions for transport.
 * The provider's [onReceive] handles them by connecting a short-lived
 * [androidx.media3.session.MediaController] to [dev.dhun.android.playback.DhunPlaybackService]
 * and issuing the corresponding player command — the widget never talks
 * to ExoPlayer directly. All actions are `PendingIntent.getBroadcast`
 * targeting the provider that owns the widget id.
 *
 * Adding a new transport requires a new [ACTION_*] constant, a new
 * [PendingIntent] factory below, and a new `when` branch in each
 * provider — pinned by [WidgetIntentsTest].
 */
object WidgetIntents {

    const val ACTION_PLAY_PAUSE = "dev.dhun.android.widgets.PLAY_PAUSE"
    const val ACTION_NEXT = "dev.dhun.android.widgets.NEXT"
    const val ACTION_PREV = "dev.dhun.android.widgets.PREV"
    const val ACTION_OPEN_APP = "dev.dhun.android.widgets.OPEN_APP"

    /** Extra carrying the originating widget id (used for scoped updates). */
    const val EXTRA_WIDGET_ID = "widgetId"

    /** Request-code bases — offset by widget id to keep PendingIntents distinct per instance. */
    private const val RC_PLAY_PAUSE = 1000
    private const val RC_NEXT = 2000
    private const val RC_PREV = 3000
    private const val RC_OPEN = 4000

    fun playPauseIntent(context: Context, providerClass: Class<*>, appWidgetId: Int): PendingIntent =
        broadcastFor(context, providerClass, ACTION_PLAY_PAUSE, appWidgetId, RC_PLAY_PAUSE)

    fun nextIntent(context: Context, providerClass: Class<*>, appWidgetId: Int): PendingIntent =
        broadcastFor(context, providerClass, ACTION_NEXT, appWidgetId, RC_NEXT)

    fun prevIntent(context: Context, providerClass: Class<*>, appWidgetId: Int): PendingIntent =
        broadcastFor(context, providerClass, ACTION_PREV, appWidgetId, RC_PREV)

    /** Tap on the widget body — opens the app (expanded FullPlayer if a queue exists). */
    fun openAppIntent(context: Context, appWidgetId: Int): PendingIntent {
        // MainActivity already handles ShortcutAction.NOW_PLAYING for the dynamic shortcut.
        // We reuse the same extra so a widget tap lands in the expanded player when possible.
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_APP
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            RC_OPEN + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcastFor(
        context: Context,
        providerClass: Class<*>,
        action: String,
        appWidgetId: Int,
        base: Int,
    ): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            this.action = action
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            base + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
