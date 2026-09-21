package dev.dhun.provider

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.HomeFeedPage
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.RadioQueuePage
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
import dev.dhun.core.map
import dev.dhun.innertube.SearchFilter

/**
 * The provider abstraction — the ONLY music-source API the application and
 * UI layers are allowed to see. YouTube-specific types never cross this
 * boundary.
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
) : MusicProvider {

    override suspend fun search(query: String, filter: SearchFilter): DhunResult<SearchResults> =
        client.search(query, filter)

    override suspend fun searchContinuation(continuationToken: String): DhunResult<SearchResults> =
        client.searchContinuation(continuationToken)

    override suspend fun searchSuggestions(query: String): DhunResult<List<String>> =
        client.searchSuggestions(query)

    override suspend fun homeFeedPage(): DhunResult<HomeFeedPage> =
        client.homeFeedPage()

    override suspend fun homeFeedContinuation(continuationToken: String): DhunResult<HomeFeedPage> =
        client.homeFeedContinuation(continuationToken)

    override suspend fun homeFeed(): DhunResult<List<HomeSection>> =
        when (val page = client.homeFeedPage()) {
            is DhunResult.Success -> DhunResult.Success(page.value.sections)
            is DhunResult.Failure -> DhunResult.Failure(page.error)
        }

    override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
        client.relatedTracks(videoId)

    override suspend fun radioQueuePage(videoId: String): DhunResult<RadioQueuePage> =
        client.radioQueuePage(videoId)

    override suspend fun radioQueueContinuation(continuationToken: String): DhunResult<RadioQueuePage> =
        client.radioQueueContinuation(continuationToken)

    override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> =
        streamResolver.resolve(videoId)

    override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> =
        client.getLyrics(videoId)

    override suspend fun artistPage(browseId: String): DhunResult<dev.dhun.core.ArtistPage> =
        client.artistPage(browseId)

    override suspend fun albumPage(browseId: String): DhunResult<dev.dhun.core.AlbumDetail> =
        client.albumPage(browseId)

    override suspend fun playlistPage(browseId: String): DhunResult<dev.dhun.core.PlaylistDetail> =
        client.playlistPage(browseId)

    companion object
}
