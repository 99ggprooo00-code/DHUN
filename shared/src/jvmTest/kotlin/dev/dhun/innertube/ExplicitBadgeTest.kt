package dev.dhun.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitBadgeTest {
    private fun row(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun onlyTrackBadgesFlagExplicitContent() {
        assertTrue(row("""{"badges":[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}]}""").hasExplicitBadge())
        assertFalse(row("""{"title":"Explicit","menu":{"icon":{"iconType":"MUSIC_EXPLICIT_BADGE"}}}""").hasExplicitBadge())
        assertFalse(row("""{"badges":[{"musicInlineBadgeRenderer":{"icon":{"iconType":"MUSIC_PREMIUM_BADGE"}}}]}""").hasExplicitBadge())
    }
}
