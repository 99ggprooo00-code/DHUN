package dev.dhun.android.widgets

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import dev.dhun.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AMOLED palette contract (device feedback, 2026-09-15): the card is deep
 * black in EVERY mode, and the wallpaper tint survives on the accent only.
 *
 * Light and dark are separate classes because `@Config(qualifiers=…)` is
 * class-scoped in Robolectric — a night switch mid-test would not re-resolve
 * the app context the way a fresh configuration does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetPaletteLightModeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `card is amoled black in light mode`() {
        // The whole point: no wallpaper-tinted surface even when Material You
        // is on and the system is in light mode.
        assertEquals(0xFF000000.toInt(), ContextCompat.getColor(context, R.color.widget_background))
    }

    @Test
    fun `only the accent pair is dynamic`() {
        assertEquals(
            ContextCompat.getColor(context, android.R.color.system_accent1_200),
            ContextCompat.getColor(context, R.color.widget_accent),
        )
        assertEquals(
            ContextCompat.getColor(context, android.R.color.system_accent1_900),
            ContextCompat.getColor(context, R.color.widget_on_accent),
        )
    }

    @Test
    fun `every other role stays on the static amoled ramp`() {
        // No @android:color/system_* leakage into text, tracks or scrim —
        // those would go dark-on-dark in light mode.
        assertEquals(0xFFE6E1E9.toInt(), ContextCompat.getColor(context, R.color.widget_on_background))
        assertEquals(0xFF9D99A9.toInt(), ContextCompat.getColor(context, R.color.widget_secondary))
        assertEquals(0xFF3E3D47.toInt(), ContextCompat.getColor(context, R.color.widget_track))
        assertEquals(0x29FFFFFF, ContextCompat.getColor(context, R.color.widget_outline))
        assertEquals(0xFF2C2B34.toInt(), ContextCompat.getColor(context, R.color.widget_artwork_scrim))
    }

    @Test
    fun `text contrast survives a black card`() {
        val onBackground = ContextCompat.getColor(context, R.color.widget_on_background)
        // Luminance of the text color must sit far above the card's.
        val luminance = (0.2126 * android.graphics.Color.red(onBackground) +
            0.7152 * android.graphics.Color.green(onBackground) +
            0.0722 * android.graphics.Color.blue(onBackground)) / 255.0
        assertTrue("on_background luminance too low for a black card: $luminance", luminance > 0.6)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "night")
class WidgetPaletteDarkModeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `card is amoled black in dark mode too`() {
        assertEquals(0xFF000000.toInt(), ContextCompat.getColor(context, R.color.widget_background))
    }

    @Test
    fun `palette is mode-invariant — accent tint is the only dynamic role`() {
        // Same accent source in both modes (a light tint reads on black
        // regardless), and no night-only overrides left behind.
        assertEquals(
            ContextCompat.getColor(context, android.R.color.system_accent1_200),
            ContextCompat.getColor(context, R.color.widget_accent),
        )
        assertEquals(0xFFE6E1E9.toInt(), ContextCompat.getColor(context, R.color.widget_on_background))
        assertEquals(0xFF000000.toInt(), ContextCompat.getColor(context, R.color.widget_background))
    }
}
