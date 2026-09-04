package dev.dhun.innertube

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.HomeItem
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.Playlist
import dev.dhun.core.SearchResults
import dev.dhun.core.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * InnerTube response -> core entity parsers. Grounded in the fixtures in
 * /tests/fixtures (captured live) — see docs/research/01-extraction-spike.md
 * for the JSON shapes.
 */

private val durationRegex = Regex("(\\d{1,3}):(\\d{2})")

/* ---------------- search items ------------------------------------------ */

/** Search items carry their fields in flexColumns, one column each. */
internal fun JsonObject.searchColumn(index: Int): JsonObject? =
    (arr("flexColumns")?.getOrNull(index)) as? JsonObject

internal fun JsonObject.columnText(index: Int): String? =
    searchColumn(index)?.allRunsText("musicResponsiveListItemFlexColumnRenderer", "text", "runs")

internal fun JsonObject.searchVideoId(): String? = obj("playlistItemData")?.str("videoId")

internal fun JsonObject.searchBrowseId(): String? =
    (descend(this, listOf("navigationEndpoint", "browseEndpoint", "browseId"))
        as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

private fun trailingDuration(item: JsonObject): Int? {
    val cols = item.arr("flexColumns") ?: return null
    for (i in cols.indices.reversed()) {
        val col = cols[i] as? JsonObject ?: continue
        val text = col.allRunsText("musicResponsiveListItemFlexColumnRenderer", "text", "runs") ?: continue
        val m = durationRegex.find(text) ?: continue
        val (mm, ss) = m.destructured
        return mm.toInt() * 60 + ss.toInt()
    }
    return null
}

private fun thumbnailOf(item: JsonObject): String? =
    ((descend(item, listOf("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.str("url")
        ?.replace("w60-h60", "w544-h544")
        ?.replace("w120-h120", "w544-h544")

internal fun parseSearchResults(query: String, root: JsonObject): SearchResults {
    val items = mutableListOf<JsonObject>()
    root.collectObjects("musicResponsiveListItemRenderer", items)

    val songs = mutableListOf<Track>()
    val videos = mutableListOf<Track>()
    val artists = mutableListOf<Artist>()
    val albums = mutableListOf<Album>()
    val playlists = mutableListOf<Playlist>()

    for (item in items) {
        val title = item.columnText(0) ?: continue
        val subtitle = item.columnText(1)
        val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val videoId = item.searchVideoId()
        val browseId = item.searchBrowseId()

        when {
            videoId != null -> songs += Track(
                id = videoId,
                title = title,
                artistName = parts.firstOrNull() ?: "Unknown artist",
                albumName = parts.getOrNull(1),
                durationSeconds = trailingDuration(item),
                thumbnailUrl = thumbnailOf(item),
            )
            browseId != null && browseId.startsWith("MPREb") -> albums += Album(
                id = browseId,
                title = title,
                artistName = parts.firstOrNull(),
                year = parts.lastOrNull()?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                thumbnailUrl = thumbnailOf(item),
            )
            browseId != null && browseId.startsWith("VL") -> playlists += Playlist(
                id = browseId,
                title = title,
                authorName = parts.firstOrNull(),
                trackCountText = parts.getOrNull(1),
                thumbnailUrl = thumbnailOf(item),
            )
            browseId != null && browseId.startsWith("UC") -> artists += Artist(
                id = browseId,
                name = title,
                subscriberCountText = parts.firstOrNull(),
                thumbnailUrl = thumbnailOf(item),
            )
        }
    }
    return SearchResults(query, songs, videos, artists, albums, playlists)
}

/* ---------------- /next (radio queue) ----------------------------------- */

internal fun parseRelatedTracks(root: JsonObject): List<Track> {
    val panels = mutableListOf<JsonObject>()
    root.collectObjects("playlistPanelVideoRenderer", panels)
    return panels.mapNotNull { p ->
        val id = p.str("videoId") ?: return@mapNotNull null
        val title = p.firstRunText("title", "runs") ?: return@mapNotNull null
        val byline = p.allRunsText("longBylineText", "runs")
            ?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() }
        val duration = p.firstRunText("lengthText", "runs")
            ?.let { durationRegex.find(it)?.destructured }
            ?.let { (mm, ss) -> mm.toInt() * 60 + ss.toInt() }
        Track(
            id = id,
            title = title,
            artistName = byline?.firstOrNull() ?: "Unknown artist",
            albumName = byline?.getOrNull(1),
            durationSeconds = duration,
            thumbnailUrl = (descend(p, listOf("thumbnail", "thumbnails"))
                as? JsonArray)?.firstOrNull()?.let { (it as? JsonObject)?.str("url") },
        )
    }
}

/* ---------------- suggestions ------------------------------------------- */

internal fun parseSuggestions(root: JsonObject): List<String> {
    val suggestions = mutableListOf<JsonObject>()
    root.collectObjects("searchSuggestionRenderer", suggestions)
    return suggestions.mapNotNull { it.allRunsText("suggestion", "runs") }
}

/* ---------------- search continuation ------------------------------------ */

/**
 * Parses a search continuation response. YTM returns `continuationContents
 * .musicShelfContinuation` with the same item renderers as the initial response.
 * Returns a data class with the incremental results + the next token (or null).
 */
data class SearchContinuation(
    val songs: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val continuation: String? = null,
)

internal fun parseSearchContinuation(root: JsonObject): SearchContinuation {
    val shelf = root.obj("continuationContents")?.obj("musicShelfContinuation")
        ?: return SearchContinuation()
    val items = mutableListOf<JsonObject>()
    shelf.arr("contents")?.forEach { elem ->
        (elem as? JsonObject)?.let(items::add)
    }
    // Next continuation token
    val nextCont = shelf.arr("continuations")
        ?.firstOrNull()
        ?.let { (it as? JsonObject)?.obj("nextContinuationData")?.str("continuation") }
        ?: shelf.arr("continuations")
            ?.firstOrNull()
            ?.let { (it as? JsonObject)?.obj("nextRadioData")?.str("continuation") }

    val songs = mutableListOf<Track>()
    val artists = mutableListOf<Artist>()
    val albums = mutableListOf<Album>()
    val playlists = mutableListOf<Playlist>()

    for (item in items) {
        item.obj("musicResponsiveListItemRenderer")?.let { renderer ->
            val title = renderer.columnText(0) ?: return@let
            val subtitle = renderer.columnText(1)
            val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            val videoId = renderer.searchVideoId()
            val browseId = renderer.searchBrowseId()

            when {
                videoId != null -> songs += Track(
                    id = videoId,
                    title = title,
                    artistName = parts.firstOrNull() ?: "Unknown artist",
                    albumName = parts.getOrNull(1),
                    durationSeconds = trailingDuration(renderer),
                    thumbnailUrl = thumbnailOf(renderer),
                )
                browseId != null && browseId.startsWith("MPREb") -> albums += Album(
                    id = browseId,
                    title = title,
                    artistName = parts.firstOrNull(),
                    thumbnailUrl = thumbnailOf(renderer),
                )
                browseId != null && browseId.startsWith("VL") -> playlists += Playlist(
                    id = browseId,
                    title = title,
                    authorName = parts.firstOrNull(),
                    trackCountText = parts.getOrNull(1),
                    thumbnailUrl = thumbnailOf(renderer),
                )
                browseId != null && browseId.startsWith("UC") -> artists += Artist(
                    id = browseId,
                    name = title,
                    subscriberCountText = parts.firstOrNull(),
                    thumbnailUrl = thumbnailOf(renderer),
                )
            }
        }
        item.obj("musicTwoRowItemRenderer")?.let { twoRow ->
            parseTwoRowItem(twoRow)?.let { item2 ->
                when (item2) {
                    is HomeItem.TrackItem -> songs += item2.track
                    is HomeItem.AlbumItem -> albums += item2.album
                    is HomeItem.ArtistItem -> artists += item2.artist
                    is HomeItem.PlaylistItem -> playlists += item2.playlist
                }
            }
        }
    }
    return SearchContinuation(songs, artists, albums, playlists, nextCont)
}

private val lyricsBrowseIdRegex = Regex("^MPLYt_")

/** The lyrics browseId is buried somewhere in /next — find it by prefix. */
internal fun parseLyricsBrowseId(root: JsonObject): String? {
    val found = mutableListOf<String>()
    root.collectStringsMatching(lyricsBrowseIdRegex, found)
    return found.firstOrNull()
}

internal fun parseLyricsBrowse(root: JsonObject): Lyrics {
    val shelves = mutableListOf<JsonObject>()
    root.collectObjects("musicDescriptionShelfRenderer", shelves)
    val text = shelves.firstNotNullOfOrNull { it.allRunsText("description", "runs") }
    return if (text.isNullOrBlank()) Lyrics.NotAvailable else Lyrics.Unsynced(text)
}

/* ---------------- home sections ------------------------------------------ */

/**
 * Parses the YTM home feed (sectionListRenderer).
 * Each shelf is a `musicShelfRenderer` inside `sectionListRenderer.contents[]`.
 * Items are either `musicTwoRowItemRenderer` (albums/artist/playlists) or
 * `musicResponsiveListItemRenderer` (tracks).
 */
internal fun parseHomeSections(root: JsonObject): List<HomeSection> {
    val sections = mutableListOf<HomeSection>()
    val listContents = mutableListOf<JsonObject>()
    root.collectObjects("sectionListRenderer", listContents)

    for (sectionList in listContents) {
        val shelfContents = mutableListOf<JsonObject>()
        sectionList.arr("contents")?.forEach { elem ->
            (elem as? JsonObject)?.let(shelfContents::add)
        }
        for (shelf in shelfContents) {
            val renderer = shelf.obj("musicShelfRenderer") ?: continue
            val title = renderer.firstRunText("title", "runs") ?: continue
            if (title == "Listen again" || title == "Your morning music") continue // skip ephemeral banners
            val browseId = renderer.obj("bottomEndpoint")?.obj("browseEndpoint")?.str("browseId")
            val items = mutableListOf<HomeItem>()

            renderer.arr("contents")?.forEach { elem ->
                (elem as? JsonObject)?.let { content ->
                    content.obj("musicTwoRowItemRenderer")?.let { twoRow ->
                        parseTwoRowItem(twoRow)?.let(items::add)
                    }
                    content.obj("musicResponsiveListItemRenderer")?.let { rowItem ->
                        parseResponsiveListItem(rowItem)?.let(items::add)
                    }
                }
            }

            if (items.isNotEmpty()) {
                sections.add(HomeSection(title = title, browseId = browseId, items = items))
            }
        }
    }
    return sections
}

/** Album / artist / playlist in the two-row grid format. */
private fun parseTwoRowItem(renderer: JsonObject): HomeItem? {
    val title = renderer.firstRunText("title", "runs") ?: return null
    val subtitle = renderer.allRunsText("subtitle", "runs")
    val thumbnailUrl = ((descend(renderer, listOf("thumbnail", "thumbnails"))
        as? JsonArray)?.firstOrNull() as? JsonObject)?.str("url")
        ?.replace("w120-h120", "w544-h544")
    val browseId = renderer.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")
    val secondSubtitle = renderer.allRunsText("secondSubtitle", "runs")

    return when {
        browseId?.startsWith("MPREb") == true -> HomeItem.AlbumItem(
            Album(
                id = browseId,
                title = title,
                artistName = subtitle,
                thumbnailUrl = thumbnailUrl,
            )
        )
        browseId?.startsWith("UC") == true -> HomeItem.ArtistItem(
            Artist(
                id = browseId,
                name = title,
                thumbnailUrl = thumbnailUrl,
            )
        )
        browseId?.startsWith("VL") == true -> HomeItem.PlaylistItem(
            Playlist(
                id = browseId,
                title = title,
                authorName = subtitle,
                trackCountText = secondSubtitle,
                thumbnailUrl = thumbnailUrl,
            )
        )
        // Video / track items have a watch endpoint with videoId
        renderer.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId") != null -> {
            val videoId = renderer.obj("navigationEndpoint")?.obj("watchEndpoint")?.str("videoId")!!
            HomeItem.TrackItem(
                Track(
                    id = videoId,
                    title = title,
                    artistName = subtitle ?: "Unknown artist",
                    thumbnailUrl = thumbnailUrl,
                )
            )
        }
        else -> null
    }
}

/** Track in the responsive list format (common in radio sections on home). */
private fun parseResponsiveListItem(renderer: JsonObject): HomeItem? {
    val videoId = renderer.obj("playlistItemData")?.str("videoId") ?: return null
    val title = renderer.columnText(0) ?: return null
    val subtitle = renderer.columnText(1)
    val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    return HomeItem.TrackItem(
        Track(
            id = videoId,
            title = title,
            artistName = parts.firstOrNull() ?: "Unknown artist",
            albumName = parts.getOrNull(1),
            durationSeconds = trailingDuration(renderer),
            thumbnailUrl = thumbnailOf(renderer),
        )
    )
}
