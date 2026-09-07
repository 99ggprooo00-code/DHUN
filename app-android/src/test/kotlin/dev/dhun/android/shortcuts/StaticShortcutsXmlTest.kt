package dev.dhun.android.shortcuts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.dhun.android.MainActivity
import dev.dhun.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Pins res/xml/shortcuts.xml (the static launcher shortcuts) to the code
 * contract in [ShortcutIntents]:
 *  - exactly the three shortcuts with stable ids,
 *  - labels that resolve to real, non-blank strings,
 *  - intents that land on MainActivity with a mappable action extra.
 *
 * This is the contract a launcher sees; a silent rename in either the XML or
 * the Kotlin breaks long-press shortcuts on real devices — CI cannot catch
 * that without these tests (the module had no test source set before).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StaticShortcutsXmlTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val resources get() = context.resources

    @Test
    fun `manifest declares exactly the three static shortcuts with stable ids`() {
        val shortcuts = parseShortcuts()

        assertEquals(3, shortcuts.size)
        assertEquals(
            setOf("search", "resume", "library"),
            shortcuts.mapNotNull { it.id }.toSet(),
        )
        assertTrue(shortcuts.all { it.enabled })
    }

    @Test
    fun `static ids never collide with the dynamic now-playing shortcut id`() {
        // A static shortcut with the same id would permanently shadow the
        // dynamic one (and ShortcutManager ignores updates for it).
        val ids = parseShortcuts().mapNotNull { it.id }

        assertTrue(
            "static shortcuts must not use the dynamic id",
            ShortcutIntents.DYNAMIC_ID_NOW_PLAYING !in ids,
        )
    }

    @Test
    fun `short and long labels resolve to non-blank strings`() {
        for (shortcut in parseShortcuts()) {
            assertTrue("icon missing for ${shortcut.id}", shortcut.iconRes != 0)

            assertTrue(
                "shortcutShortLabel must be a string resource (${shortcut.id})",
                shortcut.shortLabelRes != 0,
            )
            val short = resources.getString(shortcut.shortLabelRes)
            assertTrue("short label blank for ${shortcut.id}", short.isNotBlank())

            assertTrue(
                "shortcutLongLabel must be a string resource (${shortcut.id})",
                shortcut.longLabelRes != 0,
            )
            val long = resources.getString(shortcut.longLabelRes)
            assertTrue("long label blank for ${shortcut.id}", long.isNotBlank())
            assertTrue(
                "long label should extend the short label (${shortcut.id})",
                long.length >= short.length,
            )
        }
    }

    @Test
    fun `every shortcut icon points at an existing, distinct drawable resource`() {
        val shortcuts = parseShortcuts()
        for (shortcut in shortcuts) {
            assertTrue("icon attribute missing for ${shortcut.id}", shortcut.iconRes != 0)
            assertEquals(
                "icon for ${shortcut.id} must be a drawable",
                "drawable",
                resources.getResourceTypeName(shortcut.iconRes),
            )
        }
        // Phase 15: each shortcut has its own glyph (search / play / library).
        // Distinct resource IDs = distinct icons on the long-press surface.
        assertEquals(
            "every static shortcut must have a distinct icon",
            shortcuts.size,
            shortcuts.map { it.iconRes }.toSet().size,
        )
    }

    @Test
    fun `every shortcut launches MainActivity with an extra the code can map`() {
        val mapped = parseShortcuts().mapNotNull { shortcut ->
            assertNotNull("intent element missing for ${shortcut.id}", shortcut.intentAction)
            assertEquals(
                "targetPackage for ${shortcut.id}",
                context.packageName,
                shortcut.targetPackage,
            )
            assertEquals(
                "targetClass for ${shortcut.id}",
                MainActivity::class.java.name,
                shortcut.targetClass,
            )
            assertEquals(
                "extra name for ${shortcut.id}",
                ShortcutIntents.EXTRA_SHORTCUT_ACTION,
                shortcut.extraName,
            )
            ShortcutIntents.actionFromExtra(shortcut.extraValue)
        }

        // All three extras must map, and to distinct actions — otherwise the
        // launcher shows a shortcut that does nothing on tap.
        assertEquals(
            setOf(ShortcutAction.SEARCH, ShortcutAction.RESUME, ShortcutAction.LIBRARY),
            mapped.toSet(),
        )
    }

    @Test
    fun `ShortcutIntents maps a real Intent carrying the extra`() {
        val intent = android.content.Intent()
            .putExtra(ShortcutIntents.EXTRA_SHORTCUT_ACTION, ShortcutIntents.VALUE_LIBRARY)

        assertEquals(ShortcutAction.LIBRARY, ShortcutIntents.actionFrom(intent))
        assertEquals(null, ShortcutIntents.actionFrom(android.content.Intent()))
        assertEquals(null, ShortcutIntents.actionFrom(null))
    }

    /* ---------------- parsing helper ---------------- */

    private class ParsedShortcut {
        var id: String? = null
        var enabled = true
        var iconRes = 0
        var shortLabelRes = 0
        var longLabelRes = 0
        var intentAction: String? = null
        var targetPackage: String? = null
        var targetClass: String? = null
        var extraName: String? = null
        var extraValue: String? = null
    }

    private fun parseShortcuts(): List<ParsedShortcut> {
        val parser = resources.getXml(R.xml.shortcuts)
        val result = mutableListOf<ParsedShortcut>()
        var current: ParsedShortcut? = null
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "shortcut" -> current = ParsedShortcut().apply {
                            id = parser.getAttributeValue(ANDROID_NS, "shortcutId")
                            enabled = parser.getAttributeBooleanValue(ANDROID_NS, "enabled", true)
                            iconRes = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0)
                            shortLabelRes =
                                parser.getAttributeResourceValue(ANDROID_NS, "shortcutShortLabel", 0)
                            longLabelRes =
                                parser.getAttributeResourceValue(ANDROID_NS, "shortcutLongLabel", 0)
                        }
                        "intent" -> current?.let {
                            it.intentAction = parser.getAttributeValue(ANDROID_NS, "action")
                            it.targetPackage = parser.getAttributeValue(ANDROID_NS, "targetPackage")
                            it.targetClass = parser.getAttributeValue(ANDROID_NS, "targetClass")
                        }
                        "extra" -> current?.let {
                            it.extraName = parser.getAttributeValue(ANDROID_NS, "name")
                            it.extraValue = parser.getAttributeValue(ANDROID_NS, "value")
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "shortcut") {
                        current?.let(result::add)
                        current = null
                    }
                }
            }
        } finally {
            parser.close()
        }
        return result
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
