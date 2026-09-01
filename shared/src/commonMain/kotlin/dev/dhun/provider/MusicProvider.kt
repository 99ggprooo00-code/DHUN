package dev.dhun.provider

import dev.dhun.core.DhunResult
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
    suspend fun searchSuggestions(query: String): DhunResult<List<String>>
    suspend fun relatedTracks(videoId: String): DhunResult<List<Track>>
    suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo>
    suspend fun getLyrics(videoId: String): DhunResult<Lyrics>
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

    override suspend fun searchSuggestions(query: String): DhunResult<List<String>> =
        client.searchSuggestions(query)

    override suspend fun relatedTracks(videoId: String): DhunResult<List<Track>> =
        client.relatedTracks(videoId)

    override suspend fun getStreamInfo(videoId: String): DhunResult<StreamInfo> =
        streamResolver.resolve(videoId)

    override suspend fun getLyrics(videoId: String): DhunResult<Lyrics> =
        client.getLyrics(videoId)

    companion object
}
