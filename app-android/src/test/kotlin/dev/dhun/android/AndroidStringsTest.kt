package dev.dhun.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The user-facing strings the Android shell surfaces through launcher
 * shortcuts and accessibility services. These are referenced by resource ID
 * from code and XML — a rename/deletion breaks shortcuts and TalkBack output
 * at RUNTIME only (it still compiles), so pin them here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidStringsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resources get() = context.resources

    @Test
    fun `shortcut labels are short, non-blank and long labels extend them`() {
        val pairs = listOf(
            R.string.shortcut_search to R.string.shortcut_search_long,
            R.string.shortcut_resume to R.string.shortcut_resume_long,
            R.string.shortcut_library to R.string.shortcut_library_long,
            R.string.shortcut_now_playing_short to null,
        )
        for ((shortRes, longRes) in pairs) {
            val short = resources.getString(shortRes)
            assertTrue("blank short label ${resources.getResourceEntryName(shortRes)}", short.isNotBlank())
            assertTrue("short label too long for a launcher surface: '$short'", short.length <= 10)
            longRes?.let {
                val long = resources.getString(it)
                assertTrue("blank long label", long.isNotBlank())
                assertTrue("long label must extend short: '$long' vs '$short'", long.length >= short.length)
            }
        }
    }

    @Test
    fun `accessibility description is non-blank`() {
        val a11y = resources.getString(R.string.a11y_connecting)
        assertTrue(a11y.isNotBlank())
    }

    @Test
    fun `app name is stable`() {
        assertEquals("DHUN", resources.getString(R.string.app_name))
    }
}
