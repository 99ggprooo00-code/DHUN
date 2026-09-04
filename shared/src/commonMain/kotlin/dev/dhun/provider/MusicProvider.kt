package dev.dhun.provider

import dev.dhun.core.DhunResult
import dev.dhun.core.HomeSection
import dev.dhun.core.Lyrics
import dev.dhun.core.SearchResults
import dev.dhun.core.StreamInfo
import dev.dhun.core.Track
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
    suspend fun homeFeed(): DhunResult<List<HomeSection>>
    suspend fun relatedTracks(videoId: String): DhunResult<List<Track>>
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

    override suspend fun homeFeed(): DhunResult<List<HomeSection>> =
        client.homeFeed()

    override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
        client.relatedTracks(videoId)

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
