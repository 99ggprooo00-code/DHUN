package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Compact **Quick Play** widget — the only home-screen widget DHUN ships.
 * Title/artist, an accent play disc and a progress bar; a wide tier
 * (artwork + skip) takes over when the instance is resized past
 * [DhunWidgetUpdater.QUICK_WIDE_MIN_WIDTH_DP].
 *
 * Live updates arrive as service pushes ([DhunWidgetUpdater.pushFromPlayer],
 * wired in [dev.dhun.android.playback.DhunPlaybackService]); this provider
 * only handles system callbacks and transport taps, which delegate to the
 * shared [WidgetTransport].
 *
 * This class name, both layouts, every view id and `@xml/widget_quick_play_info`
 * are a compatibility contract: an instance already placed on a launcher is
 * only revived by an app update while those identifiers are untouched.
 */
class DhunQuickPlayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DhunWidgetUpdater.requestUpdate(context)
        if (appWidgetIds.isNotEmpty()) {
            val placeholder = DhunWidgetState.idle()
            for (id in appWidgetIds) {
                runCatching {
                    val views = DhunWidgetUpdater.buildQuickPlayViewsForId(context, placeholder, id)
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
    }
}
