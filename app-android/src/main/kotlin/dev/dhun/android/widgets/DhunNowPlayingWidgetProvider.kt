package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Home-screen **Now Playing** widget — artwork, title, artist, progress and
 * transport, in three responsive tiers (compact / standard / tall) selected
 * per instance from the host's reported size.
 *
 * Live updates arrive as service pushes ([DhunWidgetUpdater.pushFromPlayer],
 * wired in [dev.dhun.android.playback.DhunPlaybackService]); this provider
 * only handles system callbacks and transport taps, which delegate to the
 * shared [WidgetTransport].
 */
class DhunNowPlayingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DhunWidgetUpdater.requestUpdate(context)
        // Immediate placeholder so the widget never appears blank during the
        // async controller connect (the updater overwrites it).
        if (appWidgetIds.isNotEmpty()) {
            val placeholder = DhunWidgetState.idle()
            for (id in appWidgetIds) {
                runCatching {
                    val views = DhunWidgetUpdater.buildNowPlayingViewsForId(context, placeholder, id)
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Resize — re-select the responsive tier for this instance.
        DhunWidgetUpdater.requestUpdate(context)
    }

    override fun onEnabled(context: Context) {
        DhunWidgetUpdater.requestUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (WidgetTransport.isTransportAction(action)) {
            WidgetTransport.handle(context, action!!)
        }
        // ACTION_OPEN_APP is handled via PendingIntent.getActivity (MainActivity); no broadcast needed.
    }
}
