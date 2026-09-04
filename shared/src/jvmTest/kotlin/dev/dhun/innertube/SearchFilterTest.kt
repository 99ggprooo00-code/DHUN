package dev.dhun.innertube

import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 07: tests for the SearchFilter display name and the home item type safety.
 */
class SearchFilterTest {

    @Test
    fun searchFilter_displayNames() {
        assertEquals("All", SearchFilter.ALL.displayName())
        assertEquals("Songs", SearchFilter.SONGS.displayName())
        assertEquals("Videos", SearchFilter.VIDEOS.displayName())
        assertEquals("Artists", SearchFilter.ARTISTS.displayName())
        assertEquals("Albums", SearchFilter.ALBUMS.displayName())
        assertEquals("Playlists", SearchFilter.PLAYLISTS.displayName())
    }

    @Test
    fun searchFilter_params() {
        assertNull(SearchFilter.ALL.params)
        assertNotNull(SearchFilter.SONGS.params)
        assertNotNull(SearchFilter.VIDEOS.params)
        assertNotNull(SearchFilter.ARTISTS.params)
        assertNotNull(SearchFilter.ALBUMS.params)
        assertNotNull(SearchFilter.PLAYLISTS.params)
    }

    @Test
    fun homeItem_trackItem() {
        val track = Track("vid", "Title", "Artist")
        val item: HomeItem = HomeItem.TrackItem(track)
        assertTrue(item is HomeItem.TrackItem)
        assertEquals("vid", item.track.id)
        assertEquals("Title", item.track.title)
    }

    @Test
    fun homeSection_creation() {
        val track = Track("v1", "Song", "Artist")
        val section = HomeSection(
            title = "Test Section",
            browseId = "browse123",
            items = listOf(HomeItem.TrackItem(track)),
        )
        assertEquals("Test Section", section.title)
        assertEquals("browse123", section.browseId)
        assertEquals(1, section.items.size)
        assertTrue(section.items[0] is HomeItem.TrackItem)
    }
}
