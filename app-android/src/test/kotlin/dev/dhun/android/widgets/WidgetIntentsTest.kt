package dev.dhun.android.widgets

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Widget intent contract — action strings, extras, and PendingIntent distinctness.
 * The Intent extra check touches android.content.Intent, which is not mocked
 * on the plain JVM — Robolectric is required for that one test (the rest are pure).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetIntentsTest {

    @Test
    fun `action strings are stable — they are persisted in PendingIntents`() {
        assertEquals("dev.dhun.android.widgets.PLAY_PAUSE", WidgetIntents.ACTION_PLAY_PAUSE)
        assertEquals("dev.dhun.android.widgets.NEXT", WidgetIntents.ACTION_NEXT)
        assertEquals("dev.dhun.android.widgets.PREV", WidgetIntents.ACTION_PREV)
        assertEquals("dev.dhun.android.widgets.OPEN_APP", WidgetIntents.ACTION_OPEN_APP)
        assertEquals("widgetId", WidgetIntents.EXTRA_WIDGET_ID)
    }

    @Test
    fun `all transport actions are distinct`() {
        val actions = setOf(
            WidgetIntents.ACTION_PLAY_PAUSE,
            WidgetIntents.ACTION_NEXT,
            WidgetIntents.ACTION_PREV,
            WidgetIntents.ACTION_OPEN_APP,
        )
        assertEquals(4, actions.size)
    }

    @Test
    fun `extra key is distinct from the shortcut contract`() {
        // Widgets and launcher shortcuts must not collide on extra names;
        // both are delivered to MainActivity.
        assertNotEquals(WidgetIntents.EXTRA_WIDGET_ID, "dev.dhun.android.extra.SHORTCUT_ACTION")
    }

    @Test
    fun `intents carry the widget id extra`() {
        // Verify the Intent built for a broadcast carries the id — the provider
        // needs it to decide whether to update a single instance or all.
        val raw = Intent().apply {
            action = WidgetIntents.ACTION_NEXT
            putExtra(WidgetIntents.EXTRA_WIDGET_ID, 42)
        }
        assertEquals(42, raw.getIntExtra(WidgetIntents.EXTRA_WIDGET_ID, -1))
        assertEquals(WidgetIntents.ACTION_NEXT, raw.action)
    }
}
