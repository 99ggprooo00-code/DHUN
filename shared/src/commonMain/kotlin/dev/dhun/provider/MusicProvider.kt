package dev.dhun.provider

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeSection
import dev.dhun.core.HomeItem
import dev.dhun.data.SettingsRepository
import dev.dhun.data.SettingsKeys
import dev.dhun.core.Lyrics
import dev.dhun.core.PlaylistTracksPage
import dev.dhun.core.RadioQueuePage
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.core.map
import dev.dhun.innertube.SearchFilter

/**
 * Existing native metadata/playback seam; currently implemented only by
 * YouTubeMusicProvider. It is NOT source-neutral yet: SearchFilter is imported
 * from InnerTube, arguments/ids are video/browse-shaped, and callers/storage
 * assume that identity. Provider-scoped ids/capabilities and auth/player adapters
 * need a separately reviewed design/migration before adding another source.
 * See docs/PRD.md §1 and ADR-010 D5; Spotify is planned, not implemented.
 * Resolver fallback within YouTube is distinct from adding a music provider.
 */
interface MusicProvider {
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): DhunResult<SearchResults>
    suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults>
    suspend fun searchSuggestions(query: String): DhunResult<List<String>>
    /**
     * First page of home shelves plus the continuation token for the next
     * one. Prefer this over [homeFeed] when the caller can scroll.
     */
    suspend fun homeFeedPage(): DhunResult<HomeFeedPage>

    /** Next page of home shelves, or an empty page when the feed is done. */
    suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage>

    /** Convenience: first page, sections only. */
    suspend fun homeFeed(): DhunResult<List<HomeSection>>
    suspend fun relatedTracks(videoId: String): DhunResult<List<Track>>
    /**
     * First page of the radio queue for a track + the next-page token
     * (endless radio). Defaults to [relatedTracks] without a token so
     * pre-existing fakes keep working; real providers override.
     */
    suspend fun radioQueuePage(videoId: String): DhunResult<dev.dhun.core.RadioQueuePage> =
        relatedTracks(videoId).map { RadioQueuePage(tracks = it) }

    /**
     * Next page of a radio queue (endless-radio refill). Default =
     * unsupported, which the refill treats like any other failure: the
     * queue is left exactly as it was.
     */
    suspend fun radioQueueContinuation(continuationToken: String): DhunResult<dev.dhun.core.RadioQueuePage> =
        DhunResult.Failure(DhunError.Unavailable("radio continuation unsupported"))
    suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo>
    suspend fun getLyrics(videoId: String): DhunResult<Lyrics>

    /** Phase 09 browse pages. Ids are YTM browse ids (UC… / MPREb… / VL…). */
    suspend fun artistPage(browseId: String): DhunResult<dev.dhun.core.ArtistPage>
    suspend fun albumPage(browseId: String): DhunResult<dev.dhun.core.AlbumDetail>
    suspend fun playlistPage(browseId: String): DhunResult<dev.dhun.core.PlaylistDetail>

    /** Next ordered track page; unsupported keeps older provider fakes/source ports source-compatible. */
    suspend fun playlistContinuation(continuationToken: String): DhunResult<PlaylistTracksPage> =
        DhunResult.Failure(DhunError.Unavailable("playlist continuation unsupported"))
}

/**
 * YouTube Music implementation: own InnerTube client for metadata,
 * a [dev.dhun.extraction.StreamResolver] chain for playback URLs (ADR-001).
 * Platform factories (which engines are primary/fallback) live in the
 * platform source sets — see jvmMain ProviderFactories.
 */
class YouTubeMusicProvider(
    private val client: dev.dhun.innertube.InnerTubeClient,
    private val streamResolver: dev.dhun.extraction.StreamResolver,
    private val settings: SettingsRepository? = null,
) : MusicProvider {

    private suspend fun allowExplicit(): Boolean =
        settings?.getBoolean(SettingsKeys.EXPLICIT_CONTENT, SettingsKeys.EXPLICIT_CONTENT_DEFAULT) ?: true

    private suspend fun filterSections(sections: List<HomeSection>): List<HomeSection> =
        if (allowExplicit()) sections else sections.map { section ->
            section.copy(items = section.items.filterNot {
                it is HomeItem.TrackItem && it.track.explicit
            })
        }

    override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> =
        client.search(query, filter).map { results ->
            if (allowExplicit()) results else results.copy(
                songs = results.songs.filterNot { it.explicit },
                videos = results.videos.filterNot { it.explicit },
            )
        }

    override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> =
        client.searchContinuation(continuationToken).map { results ->
            if (allowExplicit()) results else results.copy(
                songs = results.songs.filterNot { it.explicit },
                videos = results.videos.filterNot { it.explicit },
            )
        }

    override suspend fun searchSuggestions(query: String): DhunResult<List<String>> =
        client.searchSuggestions(query)

    override suspend fun homeFeedPage(): DhunResult<HomeFeedPage> =
        client.homeFeedPage().map { it.copy(sections = filterSections(it.sections)) }

    override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage> =
        client.homeFeedContinuation(continuationToken).map { it.copy(sections = filterSections(it.sections)) }

    override suspend fun homeFeed(): DhunResult<List<HomeSection>> =
        homeFeedPage().map { it.sections }

    override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
        client.relatedTracks(videoId).map { tracks -> if (allowExplicit()) tracks else tracks.filterNot { it.explicit } }

    override suspend fun radioQueuePage(videoId: String): DhunResult<RadioQueuePage> =
        client.radioQueuePage(videoId).map { page ->
            if (allowExplicit()) page else page.copy(tracks = page.tracks.filterNot { it.explicit })
        }

    override suspend fun radioQueueContinuation(continuationToken: String): DhunResult<RadioQueuePage> =
        client.radioQueueContinuation(continuationToken).map { page ->
            if (allowExplicit()) page else page.copy(tracks = page.tracks.filterNot { it.explicit })
        }

    override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> =
        streamResolver.resolve(videoId)

    override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> =
        client.getLyrics(videoId)

    override suspend fun artistPage(browseId: String): DhunResult<dev.dhun.core.ArtistPage> =
        client.artistPage(browseId).map { page ->
            if (allowExplicit()) page else page.copy(topSongs = page.topSongs.filterNot { it.explicit })
        }

    override suspend fun albumPage(browseId: String): DhunResult<dev.dhun.core.AlbumDetail> =
        client.albumPage(browseId).map { page ->
            if (allowExplicit()) page else page.copy(tracks = page.tracks.filterNot { it.explicit })
        }

    override suspend fun playlistPage(browseId: String): DhunResult<dev.dhun.core.PlaylistDetail> =
        client.playlistPage(browseId).map { page ->
            if (allowExplicit()) page else page.copy(tracks = page.tracks.filterNot { it.explicit })
        }

    override suspend fun playlistContinuation(continuationToken: String): DhunResult<PlaylistTracksPage> =
        client.playlistContinuation(continuationToken).map { page ->
            if (allowExplicit()) page else page.copy(tracks = page.tracks.filterNot { it.explicit })
        }

    companion object
}
