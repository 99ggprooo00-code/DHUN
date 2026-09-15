package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Compact **Quick Play** widget — title, play disc and progress, with a wide
 * tier (artwork + skip) when resized past 200dp. Shares the
 * [DhunWidgetUpdater] data path and [WidgetTransport] actions with
 * [DhunNowPlayingWidgetProvider]; appears as a separate picker entry.
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
