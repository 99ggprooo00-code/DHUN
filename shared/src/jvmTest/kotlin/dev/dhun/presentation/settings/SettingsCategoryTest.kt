package dev.dhun.presentation.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsCategoryTest {
    @Test fun hubOnlyExposesPlatformSupportedCategoriesWithStableIds() {
        val android = SettingsCategory.search("", false)
        assertFalse(SettingsCategory.DESKTOP in android)
        assertEquals(SettingsCategory.entries.toList(), SettingsCategory.search("", true))
        assertEquals(SettingsCategory.entries.size, SettingsCategory.entries.map { it.id }.toSet().size)
        assertTrue(android.all { it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test fun localCategorySearchHandlesKeywordsCaseWhitespaceAndMissingFeatures() {
        assertEquals(listOf(SettingsCategory.APPEARANCE), SettingsCategory.search("  OLED  ", false))
        assertEquals(listOf(SettingsCategory.APPEARANCE), SettingsCategory.search("dynamic THEME", false))
        assertEquals(listOf(SettingsCategory.LYRICS), SettingsCategory.search("romanization", false))
        assertEquals(listOf(SettingsCategory.PRIVACY), SettingsCategory.search("pause history", true))
        assertTrue(SettingsCategory.search("desktop", false).isEmpty())
        assertEquals(listOf(SettingsCategory.DESKTOP), SettingsCategory.search("tray", true))
        // Don't advertise non-working credentials/backends as settings controls.
        assertTrue(SettingsCategory.search("scrobbling", true).isEmpty())
        assertTrue(SettingsCategory.search("recognition", false).isEmpty())
    }
}
