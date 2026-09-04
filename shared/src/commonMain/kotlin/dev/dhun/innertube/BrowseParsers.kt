package dev.dhun.innertube

import dev.dhun.core.Album
import dev.dhun.core.Artist
import dev.dhun.core.ArtistPage
import dev.dhun.core.AlbumDetail
import dev.dhun.core.Playlist
import dev.dhun.core.PlaylistDetail
import dev.dhun.core.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * InnerTube browse-page parsers (Phase 09): artist / album / playlist.
 *
 * YouTube changes browse layouts constantly, so these parsers are
 * deliberately shape-tolerant: they walk the response collecting known
 * renderers instead of depending on exact layout paths. Fixture-grounded
 * (tests/fixtures/browse-*.json); live re-capture rides the rot drill.
 */

/* ---------------- shared helpers ---------------------------------------- */

/** innerTitle for shelf-like renderers: title.runs[0] text. */
private fun JsonObject.shelfTitle(): String? =
    firstRunText("title", "runs") ?: allRunsText("title", "runs")

/** (artistId UC…, albumId MPRE…) extracted from any subtitle/byline subtree. */
internal fun browseIdsOf(node: JsonElement?): Pair<String?, String?> {
    val runs = mutableListOf<Pair<String, String>>()
    collectBrowseRuns(node, runs)
    val artist = runs.firstOrNull { it.second.startsWith("UC") }?.second
    val album = runs.firstOrNull { it.second.startsWith("MPRE") }?.second
    return artist to album
}

/** Largest thumbnail URL from any nearby thumbnail renderer. */
private fun thumbnailsLastUrl(node: JsonObject?): String? {
    if (node == null) return null
    val paths = listOf(
        listOf("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"),
        listOf("thumbnail", "croppedSquareThumbnailRenderer", "thumbnail", "thumbnails"),
        listOf("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"),
        listOf("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"),
        listOf("thumbnail", "thumbnails"),
    )
    for (path in paths) {
        val arr = descend(node, path) as? JsonArray ?: continue
        val last = arr.lastOrNull() as? JsonObject
        val url = last?.str("url")
        if (!url.isNullOrBlank()) return normalizeRemoteUrl(url)
    }
    return null
}

private fun normalizeRemoteUrl(url: String): String {
    val https = if (url.startsWith("//")) "https:$url" else url
    return https
        .replace("w60-h60", "w544-h544")
        .replace("w120-h120", "w544-h544")
}

/**
 * Browsable sections of any page layout, normalized so each entry is a
 * single-key wrapper: {"musicShelfRenderer": …} / {"musicCarouselShelfRenderer": …}
 * / {"musicDescriptionShelfRenderer": …} — regardless of which outer layout
 * (single/two column) the page used.
 */
private fun browseSectionContents(root: JsonObject): List<JsonObject> {
    val paths = listOf(
        listOf("contents", "singleColumnBrowseResultsRenderer", "tabs"),
        listOf("contents", "twoColumnBrowseResultsRenderer", "tabs"),
    )
    for (path in paths) {
        val tabs = descend(root, path) as? JsonArray ?: continue
        for (tab in tabs) {
            val renderer = (tab as? JsonObject)?.obj("tabRenderer") ?: continue
            val contents = descend(renderer, listOf("content", "sectionListRenderer", "contents"))
                as? JsonArray ?: continue
            val sections = contents.mapNotNull { it as? JsonObject }
            if (sections.isNotEmpty()) return sections
        }
    }
    // twoColumn keeps sections under secondaryContents on album/playlist pages.
    val secondary = descend(
        root,
        listOf("contents", "twoColumnBrowseResultsRenderer", "secondaryContents", "sectionListRenderer", "contents"),
    ) as? JsonArray
    if (secondary != null && secondary.isNotEmpty()) {
        return secondary.mapNotNull { it as? JsonObject }
    }

    // Last resort: collect known shelf renderers anywhere in the tree and
    // wrap them so callers see the same single-key shape.
    val wrapped = mutableListOf<JsonObject>()
    fun wrapAll(key: String) {
        val found = mutableListOf<JsonObject>()
        root.collectObjects(key, found)
        found.forEach { wrapped += buildJsonObject { put(key, it) } }
    }
    wrapAll("musicShelfRenderer")
    wrapAll("musicCarouselShelfRenderer")
    wrapAll("musicPlaylistShelfRenderer")
    return wrapped
}

private fun carouselHeaderTitle(shelf: JsonObject): String? =
    shelf.obj("header")?.obj("musicCarouselShelfBasicHeaderRenderer")?.shelfTitle()
        ?: shelf.obj("header")?.obj("musicImmersiveCarouselShelfBasicHeaderRenderer")?.shelfTitle()

/** A browse song row (musicResponsiveListItemRenderer with playlistItemData). */
private fun parseBrowseSongRow(item: JsonObject, fallbackArtist: String? = null): Track? {
    val videoId = item.searchVideoId()
        ?: (descend(item, listOf("navigationEndpoint", "watchEndpoint", "videoId")) as? JsonPrimitive)
            ?.contentOrNull
        ?: (descend(item, listOf("navigationEndpoint", "watchPlaylistEndpoint", "videoId")) as? JsonPrimitive)
            ?.contentOrNull
        ?: return null
    val title = item.columnText(0) ?: return null
    val subtitle = item.columnText(1)
    val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val idsCol1 = browseIdsOf(item.searchColumn(1))
    val idsCol2 = browseIdsOf(item.searchColumn(2))
    return Track(
        id = videoId,
        title = title,
        artistName = parts.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: fallbackArtist
            ?: "Unknown artist",
        artistId = idsCol1.first ?: idsCol2.first,
        albumName = parts.getOrNull(1),
        albumId = idsCol2.second ?: idsCol1.second,
        durationSeconds = trailingDuration(item),
        thumbnailUrl = thumbnailOf(item),
    )
}

/** one two-row carousel card → Album / Playlist / Artist by browse id. */
private fun parseCarouselCard(twoRow: JsonObject): Any? {
    val title = twoRow.firstRunText("title", "runs") ?: twoRow.allRunsText("title", "runs") ?: return null
    val subtitle = twoRow.allRunsText("subtitle", "runs")
    val parts = subtitle?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val browseId = (
        descend(twoRow, listOf("navigationEndpoint", "browseEndpoint", "browseId")) as? JsonPrimitive
            ?: descend(twoRow, listOf("onTap", "browseEndpoint", "browseId")) as? JsonPrimitive
        )?.contentOrNull ?: return null
    val thumb = thumbnailOf(twoRow) ?: thumbnailsLastUrl(twoRow)
    return when {
        browseId.startsWith("MPREb") || browseId.startsWith("MPRE") -> Album(
            id = browseId,
            title = title,
            artistName = parts.firstOrNull(),
            year = parts.lastOrNull()?.takeIf { it.length == 4 && it.all(Char::isDigit) },
            thumbnailUrl = thumb,
        )
        browseId.startsWith("VL") || browseId.startsWith("PL") -> Playlist(
            id = browseId,
            title = title,
            authorName = parts.firstOrNull(),
            trackCountText = parts.getOrNull(1),
            thumbnailUrl = thumb,
        )
        browseId.startsWith("UC") -> Artist(
            id = browseId,
            name = title,
            subscriberCountText = parts.firstOrNull(),
            thumbnailUrl = thumb,
        )
        else -> null
    }
}

/** page header (any of the header renderer families YTM has shipped). */
private fun pageHeader(root: JsonObject): JsonObject? {
    val header = root.obj("header") ?: return null
    return header.obj("musicImmersiveHeaderRenderer")
        ?: header.obj("musicVisualHeaderRenderer")
        ?: header.obj("musicResponsiveHeaderRenderer")
        ?: header.obj("musicDetailHeaderRenderer")
        ?: header.obj("musicEditablePlaylistDetailHeaderRenderer")?.firstNotNullOfOrNull {
            (it.value as? JsonObject)?.obj("musicResponsiveHeaderRenderer")
        }
        ?: header.obj("musicPlaylistHeaderRenderer")
}

/** Collect every text run that carries a browse endpoint in a subtree. */
internal fun collectBrowseRuns(node: JsonElement?, out: MutableList<Pair<String, String>>) {
    when (node) {
        is JsonObject -> {
            val text = node.str("text")
            val browseId = (
                descend(node, listOf("navigationEndpoint", "browseEndpoint", "browseId")) as? JsonPrimitive
                )?.contentOrNull
            if (text != null && browseId != null) out += text to browseId
            for (v in node.values) collectBrowseRuns(v, out)
        }
        is JsonArray -> for (v in node) collectBrowseRuns(v, out)
        else -> {}
    }
}

/* ---------------- artist ------------------------------------------------- */

internal fun parseArtistPage(root: JsonObject, browseId: String): ArtistPage {
    val header = pageHeader(root)
    val name = header?.firstRunText("title", "runs") ?: "Artist"
    val thumb = thumbnailsLastUrl(header)
    val secondSubtitle = header?.allRunsText("secondSubtitle", "runs")
    val monthlyListeners =
        secondSubtitle?.takeIf { it.contains("monthly listeners", ignoreCase = true) }

    val description = mutableListOf<JsonObject>()
    root.collectObjects("musicDescriptionShelfRenderer", description)
    val about = description.firstNotNullOfOrNull { it.allRunsText("description", "runs") }

    val topSongs = mutableListOf<Track>()
    var topSongsPlaylistId: String? = null
    val albums = mutableListOf<Album>()
    val singles = mutableListOf<Album>()
    val featured = mutableListOf<Playlist>()
    val related = mutableListOf<Artist>()

    for (section in browseSectionContents(root)) {
        val shelf = section.obj("musicShelfRenderer")
        if (shelf != null) {
            val rows = shelf.arr("contents").orEmpty()
            val songs = rows.mapNotNull { (it as? JsonObject)?.obj("musicResponsiveListItemRenderer") }
                .mapNotNull { parseBrowseSongRow(it, fallbackArtist = name) }
            if (songs.isNotEmpty()) {
                topSongs += songs
                if (topSongsPlaylistId == null) {
                    topSongsPlaylistId = (
                        descend(shelf, listOf("bottomEndpoint", "browseEndpoint", "browseId"))
                            as? JsonPrimitive
                        )?.contentOrNull
                }
            }
            // A shelf without song rows (e.g. "Videos") is skipped deliberately.
            continue
        }

        val carousel = section.obj("musicCarouselShelfRenderer") ?: continue
        val title = carouselHeaderTitle(carousel) ?: ""
        val cards = carousel.arr("contents").orEmpty()
            .mapNotNull { (it as? JsonObject)?.obj("musicTwoRowItemRenderer") }
            .mapNotNull { parseCarouselCard(it) }
        when {
            title.contains("single", ignoreCase = true) || title.contains("ep", ignoreCase = true) ->
                singles += cards.filterIsInstance<Album>()
            title.contains("album", ignoreCase = true) ->
                albums += cards.filterIsInstance<Album>()
            title.contains("fans", ignoreCase = true) || title.contains("similar", ignoreCase = true) ->
                related += cards.filterIsInstance<Artist>()
            title.contains("featured", ignoreCase = true) || title.contains("playlist", ignoreCase = true) ->
                featured += cards.filterIsInstance<Playlist>()
            else -> {
                // Untitled/"More from" carousels: sort by card type.
                albums += cards.filterIsInstance<Album>()
                related += cards.filterIsInstance<Artist>()
                featured += cards.filterIsInstance<Playlist>()
            }
        }
    }

    return ArtistPage(
        artist = Artist(id = browseId, name = name, thumbnailUrl = thumb),
        monthlyListeners = monthlyListeners,
        description = about,
        topSongs = topSongs,
        topSongsPlaylistId = topSongsPlaylistId,
        albums = albums.distinctBy { it.id },
        singles = singles.distinctBy { it.id },
        featuredPlaylists = featured.distinctBy { it.id },
        relatedArtists = related.distinctBy { it.id },
    )
}

/* ---------------- album -------------------------------------------------- */

internal fun parseAlbumPage(root: JsonObject, browseId: String): AlbumDetail {
    val header = pageHeader(root)
    val title = header?.firstRunText("title", "runs") ?: "Album"

    val subtitleRuns = mutableListOf<Pair<String, String>>()
    collectBrowseRuns(header?.obj("subtitle"), subtitleRuns)
    val artistRun = subtitleRuns.firstOrNull { it.second.startsWith("UC") }
    val artistName = artistRun?.first ?: header.allRunsText("subtitle", "runs")
        ?.split("•")?.map { it.trim() }?.firstOrNull()
    val year = header.allRunsText("subtitle", "runs")
        ?.split("•")?.map { it.trim() }
        ?.lastOrNull { it.length == 4 && it.all(Char::isDigit) }

    val secondSubtitle = header.allRunsText("secondSubtitle", "runs")
    val secondParts = secondSubtitle?.split("•")?.map { it.trim() }
    val trackCount = secondParts?.firstOrNull { it.contains("song", ignoreCase = true) }
    val durationText = secondParts?.lastOrNull()?.takeIf { secondParts.size > 1 }

    val description = header.obj("description")?.allRunsText("description", "runs")
        ?: header.obj("description")?.allRunsText("musicDescriptionShelfRenderer", "description", "runs")
        ?: header.obj("description")?.allRunsText("runs")
        ?: run {
            val descShelves = mutableListOf<JsonObject>()
            root.collectObjects("musicDescriptionShelfRenderer", descShelves)
            descShelves.firstNotNullOfOrNull { it.allRunsText("description", "runs") }
        }

    // Track rows: the album "Songs" shelf (playlistItemData-bearing rows),
    // falling back to any matching row anywhere in the tree.
    val sectionSongs = mutableListOf<Track>()
    for (section in browseSectionContents(root)) {
        val shelf = section.obj("musicShelfRenderer") ?: continue
        val rows = shelf.arr("contents").orEmpty()
        sectionSongs += rows.mapNotNull { (it as? JsonObject)?.obj("musicResponsiveListItemRenderer") }
            .mapNotNull { parseBrowseSongRow(it, fallbackArtist = artistName) }
    }
    if (sectionSongs.isEmpty()) {
        val allRows = mutableListOf<JsonObject>()
        root.collectObjects("musicResponsiveListItemRenderer", allRows)
        sectionSongs += allRows.mapNotNull { parseBrowseSongRow(it, fallbackArtist = artistName) }
    }
    val tracks = sectionSongs.distinctBy { it.id }

    // "More by …" browse target from a carousel under the track shelf.
    var moreBy: String? = null
    for (section in browseSectionContents(root)) {
        val carousel = section.obj("musicCarouselShelfRenderer") ?: continue
        val title = carouselHeaderTitle(carousel)?.lowercase() ?: ""
        if ("more by" in title || "more from" in title) {
            moreBy = (
                descend(carousel, listOf("header", "musicCarouselShelfBasicHeaderRenderer", "moreContentButton", "buttonRenderer", "command", "browseEndpoint", "browseId"))
                    as? JsonPrimitive
                )?.contentOrNull ?: moreBy
            if (moreBy == null) {
                // fall back to the first card's artist-ish browse id in that carousel
                val runs = mutableListOf<Pair<String, String>>()
                collectBrowseRuns(carousel.obj("header"), runs)
                moreBy = runs.firstOrNull { it.second.startsWith("UC") }?.second
            }
        }
    }

    return AlbumDetail(
        id = browseId,
        title = title,
        artistName = artistName,
        artistId = artistRun?.second,
        year = year,
        trackCountText = trackCount,
        durationText = durationText,
        thumbnailUrl = thumbnailsLastUrl(header),
        description = description,
        tracks = tracks,
        moreByArtistBrowseId = moreBy,
    )
}

/* ---------------- playlist ----------------------------------------------- */

internal fun parsePlaylistPage(root: JsonObject, browseId: String): PlaylistDetail {
    val header = pageHeader(root)
    val title = header?.firstRunText("title", "runs") ?: "Playlist"

    val subtitleText = header?.allRunsText("subtitle", "runs")
    val parts = subtitleText?.split("•")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val author = parts.firstOrNull()
    val countText = parts.firstOrNull { it.contains("song", ignoreCase = true) || it.contains("track", ignoreCase = true) }
        ?: header?.allRunsText("secondSubtitle", "runs")

    val description = header?.obj("description")?.allRunsText("description", "runs")
        ?: header?.obj("description")?.allRunsText("musicDescriptionShelfRenderer", "description", "runs")
        ?: header?.obj("description")?.allRunsText("runs")
        ?: run {
            val descShelves = mutableListOf<JsonObject>()
            root.collectObjects("musicDescriptionShelfRenderer", descShelves)
            descShelves.firstNotNullOfOrNull { it.allRunsText("description", "runs") }
        }

    // musicPlaylistShelfRenderer contents carry the ordered track list.
    val shelfRows = mutableListOf<JsonObject>()
    root.collectObjects("musicPlaylistShelfRenderer", shelfRows)
    val tracks = shelfRows.flatMap { shelf ->
        shelf.arr("contents").orEmpty()
            .mapNotNull { (it as? JsonObject)?.obj("musicResponsiveListItemRenderer") }
            .mapNotNull { parseBrowseSongRow(it) }
    }.distinctBy { it.id }

    return PlaylistDetail(
        id = browseId,
        title = title,
        authorName = author,
        trackCountText = countText,
        description = description,
        thumbnailUrl = thumbnailsLastUrl(header),
        tracks = tracks,
    )
}
