package dev.dhun.innertube

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.HomeFeed
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
        as? JsonPrimitive)?.contentOrNull

internal fun trailingDuration(item: JsonObject): Int? {
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

internal fun thumbnailOf(item: JsonObject): String? =
    ((descend(item, listOf("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        as? JsonArray ?: descend(item, listOf("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        as? JsonArray ?: descend(item, listOf("thumbnail", "thumbnails"))
        as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.str("url")
        ?.replace("w60-h60", "w544-h544")
        ?.replace("w120-h120", "w544-h544")

internal fun parseContinuationToken(root: JsonObject): String? {
    val continuations = mutableListOf<JsonObject>()
    root.collectObjects("nextContinuationData", continuations)
    for (c in continuations) {
        val token = c.str("continuation")
        if (!token.isNullOrBlank()) return token
    }
    return null
}

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
            videoId != null -> {
                val idsArtist = browseIdsOf(item.searchColumn(1))
                val idsAlbum = browseIdsOf(item.searchColumn(2))
                songs += Track(
                    id = videoId,
                    title = title,
                    artistName = parts.firstOrNull() ?: "Unknown artist",
                    artistId = idsArtist.first,
                    albumName = parts.getOrNull(1),
                    albumId = idsAlbum.second ?: idsArtist.second,
                    durationSeconds = trailingDuration(item),
                    thumbnailUrl = thumbnailOf(item),
                )
            }
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
    val continuationToken = parseContinuationToken(root)
    return SearchResults(
        query = query,
        songs = songs,
        videos = videos,
        artists = artists,
        albums = albums,
        playlists = playlists,
        continuationToken = continuationToken,
    )
}

/* ---------------- Home / Browse sections -------------------------------- */

internal fun parseHomeSections(root: JsonObject): List<HomeSection> {
    val shelves = mutableListOf<JsonObject>()
    root.collectObjects("musicCarouselShelfRenderer", shelves)
    root.collectObjects("musicImmersiveCarouselShelfRenderer", shelves)

    val sections = mutableListOf<HomeSection>()

    for (shelf in shelves) {
        val header = shelf.obj("header")?.obj("musicCarouselShelfBasicHeaderRenderer")
            ?: shelf.obj("header")?.obj("musicImmersiveCarouselShelfBasicHeaderRenderer")
        val title = header?.firstRunText("title", "runs")
            ?: header?.allRunsText("title", "runs")
            ?: ""
        val strapline = header?.firstRunText("strapline", "runs")
            ?: header?.allRunsText("strapline", "runs")

        val contents = shelf.arr("contents") ?: continue
        val items = mutableListOf<HomeItem>()

        for (el in contents) {
            val itemObj = el as? JsonObject ?: continue
            val responsive = itemObj.obj("musicResponsiveListItemRenderer")
            val twoRow = itemObj.obj("musicTwoRowItemRenderer")

            when {
                responsive != null -> {
                    val trackTitle = responsive.columnText(0) ?: continue
                    val subtitle = responsive.columnText(1)
                    val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    val videoId = responsive.searchVideoId()
                        ?: (descend(responsive, listOf("navigationEndpoint", "watchEndpoint", "videoId")) as? JsonPrimitive)?.contentOrNull
                    val browseId = responsive.searchBrowseId()
                    val thumb = thumbnailOf(responsive)

                    when {
                        videoId != null -> {
                            val idsArtist = browseIdsOf(responsive.searchColumn(1))
                            val idsAlbum = browseIdsOf(responsive.searchColumn(2))
                            items += HomeItem.TrackItem(
                                Track(
                                    id = videoId,
                                    title = trackTitle,
                                    artistName = parts.firstOrNull() ?: "Unknown artist",
                                    artistId = idsArtist.first,
                                    albumName = parts.getOrNull(1),
                                    albumId = idsAlbum.second ?: idsArtist.second,
                                    durationSeconds = trailingDuration(responsive),
                                    thumbnailUrl = thumb,
                                )
                            )
                        }
                        browseId != null && browseId.startsWith("MPREb") -> items += HomeItem.AlbumItem(
                            Album(
                                id = browseId,
                                title = trackTitle,
                                artistName = parts.firstOrNull(),
                                year = parts.lastOrNull()?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                                thumbnailUrl = thumb,
                            )
                        )
                        browseId != null && browseId.startsWith("VL") -> items += HomeItem.PlaylistItem(
                            Playlist(
                                id = browseId,
                                title = trackTitle,
                                authorName = parts.firstOrNull(),
                                trackCountText = parts.getOrNull(1),
                                thumbnailUrl = thumb,
                            )
                        )
                        browseId != null && browseId.startsWith("UC") -> items += HomeItem.ArtistItem(
                            Artist(
                                id = browseId,
                                name = trackTitle,
                                subscriberCountText = parts.firstOrNull(),
                                thumbnailUrl = thumb,
                            )
                        )
                    }
                }
                twoRow != null -> {
                    val itemTitle = twoRow.firstRunText("title", "runs")
                        ?: twoRow.allRunsText("title", "runs")
                        ?: continue
                    val subtitle = twoRow.allRunsText("subtitle", "runs")
                    val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    val videoId = (descend(twoRow, listOf("navigationEndpoint", "watchEndpoint", "videoId")) as? JsonPrimitive)?.contentOrNull
                        ?: (descend(twoRow, listOf("onTap", "watchEndpoint", "videoId")) as? JsonPrimitive)?.contentOrNull
                    val browseId = (descend(twoRow, listOf("navigationEndpoint", "browseEndpoint", "browseId")) as? JsonPrimitive)?.contentOrNull
                        ?: (descend(twoRow, listOf("onTap", "browseEndpoint", "browseId")) as? JsonPrimitive)?.contentOrNull
                    val thumb = thumbnailOf(twoRow)

                    when {
                        videoId != null -> {
                            val ids = browseIdsOf(twoRow.obj("subtitle"))
                            items += HomeItem.TrackItem(
                                Track(
                                    id = videoId,
                                    title = itemTitle,
                                    artistName = parts.firstOrNull() ?: "Unknown artist",
                                    artistId = ids.first,
                                    albumName = parts.getOrNull(1),
                                    albumId = ids.second,
                                    thumbnailUrl = thumb,
                                )
                            )
                        }
                        browseId != null && browseId.startsWith("MPREb") -> items += HomeItem.AlbumItem(
                            Album(
                                id = browseId,
                                title = itemTitle,
                                artistName = parts.firstOrNull(),
                                year = parts.lastOrNull()?.takeIf { it.length == 4 && it.all(Char::isDigit) },
                                thumbnailUrl = thumb,
                            )
                        )
                        browseId != null && browseId.startsWith("UC") -> items += HomeItem.ArtistItem(
                            Artist(
                                id = browseId,
                                name = itemTitle,
                                subscriberCountText = parts.firstOrNull(),
                                thumbnailUrl = thumb,
                            )
                        )
                        browseId != null -> items += HomeItem.PlaylistItem(
                            Playlist(
                                id = browseId,
                                title = itemTitle,
                                authorName = parts.firstOrNull(),
                                trackCountText = parts.getOrNull(1),
                                thumbnailUrl = thumb,
                            )
                        )
                    }
                }
            }
        }

        if (items.isNotEmpty()) {
            sections += HomeSection(
                title = title.ifBlank { "Recommended" },
                subtitle = strapline,
                items = items,
            )
        }
    }

    return sections
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
        val bylineIds = browseIdsOf(descend(p, listOf("longBylineText", "runs")))
        Track(
            id = id,
            title = title,
            artistName = byline?.firstOrNull() ?: "Unknown artist",
            artistId = bylineIds.first,
            albumName = byline?.getOrNull(1),
            albumId = bylineIds.second,
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

/* ---------------- lyrics ------------------------------------------------- */

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
