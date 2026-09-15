package dev.dhun.android.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure action routing — no framework needed. */
class WidgetTransportTest {

    @Test
    fun `all five transport actions route to the shared handler`() {
        assertTrue(WidgetTransport.isTransportAction(WidgetIntents.ACTION_PLAY_PAUSE))
        assertTrue(WidgetTransport.isTransportAction(WidgetIntents.ACTION_NEXT))
        assertTrue(WidgetTransport.isTransportAction(WidgetIntents.ACTION_PREV))
        assertTrue(WidgetTransport.isTransportAction(WidgetIntents.ACTION_SHUFFLE))
        assertTrue(WidgetTransport.isTransportAction(WidgetIntents.ACTION_REPEAT))
    }

    @Test
    fun `non-transport actions are ignored`() {
        assertFalse(WidgetTransport.isTransportAction(WidgetIntents.ACTION_OPEN_APP))
        assertFalse(WidgetTransport.isTransportAction("android.appwidget.action.APPWIDGET_UPDATE"))
        assertFalse(WidgetTransport.isTransportAction(null))
        assertFalse(WidgetTransport.isTransportAction(""))
    }
}
