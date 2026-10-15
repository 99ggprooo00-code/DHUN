package dev.dhun.lyrics

import dev.dhun.core.DhunResult
import dev.dhun.core.Lyrics
import dev.dhun.core.Track
import dev.dhun.data.LyricsCacheRepository
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Stable IDs are persisted in settings; adding adapters must not change existing source order. */
data class LyricsProviderAdapter(val id: String, val source: LyricsSource)

/** Single lyrics entry point: per-song choice → cache → bounded sequential source fallback.
 * Candidate fan-out only occurs when the user explicitly opens/searches the match picker.
 */
class LyricsRepository(
    private val cache: LyricsCacheRepository,
    ytm: LyricsSource,
    lrcLib: LyricsSource,
    private val settings: SettingsRepository? = null,
    search: LyricsSource? = null,
    kugou: LyricsSource? = null,
    private val providerTimeoutMs: Long = 5_000,
) {
    init { require(providerTimeoutMs > 0) }
    private val registry = listOfNotNull(
        LyricsProviderAdapter("ytm", ytm), LyricsProviderAdapter("lrclib", lrcLib),
        search?.let { LyricsProviderAdapter("lrclib_search", it) },
        kugou?.let { LyricsProviderAdapter("kugou", it) },
    ).associateBy { it.id }

    private suspend fun orderedProviders(): List<LyricsProviderAdapter> {
        val preferred = settings?.getString(SettingsKeys.LYRICS_PREFERRED_PROVIDER) ?: "auto"
        val enabled = (settings?.getString(SettingsKeys.LYRICS_PROVIDER_ORDER)
            ?: if (settings == null) "ytm,lrclib" else SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT)
            .split(',').map { it.trim() }.filter { it in registry }.distinct()
        val ids = if (preferred == "auto") enabled else (listOf(preferred) + enabled).distinct()
        return ids.mapNotNull { registry[it] }
    }

    suspend fun getLyrics(track: Track): DhunResult<Lyrics> {
        if (settings?.getBoolean(SettingsKeys.LYRICS_ENABLED, true) == false)
            return DhunResult.Success(Lyrics.NotAvailable)
        selectedMatch(track.id)?.let { return DhunResult.Success(it.lyrics) }
        bestEffort { cache.get(track.id) }?.takeUnless { it == Lyrics.NotAvailable }?.let {
            return DhunResult.Success(it)
        }
        val preferred = settings?.getString(SettingsKeys.LYRICS_PREFERRED_PROVIDER) ?: "auto"
        val syncedFirst = settings?.getBoolean(SettingsKeys.LYRICS_PREFER_SYNCED, true) ?: false
        var plain: Lyrics.Unsynced? = null
        for (adapter in orderedProviders()) {
            val result = bounded { adapter.source.fetch(track) }
            val lyrics = (result as? DhunResult.Success)?.value
            if (lyrics != null && (lyrics is Lyrics.Synced || (lyrics is Lyrics.Unsynced && (!syncedFirst || preferred != "auto")))) {
                bestEffort { cache.put(track.id, lyrics) }
                return DhunResult.Success(lyrics)
            }
            if (lyrics is Lyrics.Unsynced && plain == null) plain = lyrics
        }
        plain?.let {
            bestEffort { cache.put(track.id, it) }
            return DhunResult.Success(it)
        }
        return DhunResult.Success(Lyrics.NotAvailable)
    }

    /** Existing automatic source fallback is not used to filter manual search results. */
    suspend fun searchMatches(track: Track, query: String): LyricsMatchSearch {
        if (settings?.getBoolean(SettingsKeys.LYRICS_ENABLED, true) == false) return LyricsMatchSearch(emptyList())
        val providers = orderedProviders()
        return coroutineScope {
            val results = providers.map { adapter -> async {
                val result = bounded {
                    val source = adapter.source
                    if (source is LyricsCandidateSource) source.candidates(track, query.trim().take(240))
                    else when (val fetched = source.fetch(track)) {
                        is DhunResult.Failure -> fetched
                        is DhunResult.Success -> DhunResult.Success(
                            if (fetched.value == Lyrics.NotAvailable) emptyList() else listOf(
                                // These legacy adapters don't expose their record metadata. Do not
                                // falsely advertise requested title/artist as a verified source match.
                                LyricsMatch("current-track", adapter.id, track.title, track.artistName,
                                    fetched.value, metadataVerified = false)
                            )
                        )
                    }
                }
                adapter.id to when (result) {
                    is DhunResult.Success -> DhunResult.Success(result.value.map { it.copy(provider = adapter.id) })
                    else -> result
                }
            } }.awaitAll()
            val matches = results.flatMap { (_, result) -> (result as? DhunResult.Success)?.value.orEmpty() }
                .filter { it.lyrics != Lyrics.NotAvailable }.distinctBy { it.provider to it.id }.take(60)
            LyricsMatchSearch(matches, results.filter { it.second !is DhunResult.Success }.map { it.first })
        }
    }

    suspend fun selectMatch(trackId: String, match: LyricsMatch) {
        val store = checkNotNull(settings) { "Persistent song choices are unavailable" }
        require(match.provider in registry) { "Unknown lyrics provider" }
        store.putString(SettingsKeys.lyricsMatchKey(trackId), LyricsMatchCodec.encode(match))
    }

    suspend fun selectedMatch(trackId: String): LyricsMatch? = bestEffort {
        LyricsMatchCodec.decode(settings?.getString(SettingsKeys.lyricsMatchKey(trackId)))
    }

    suspend fun resetMatch(trackId: String) { settings?.remove(SettingsKeys.lyricsMatchKey(trackId)) }
    suspend fun clearCache() = cache.clear()
    suspend fun cached(trackId: String): Lyrics? = cache.get(trackId)

    private suspend fun <T> bounded(block: suspend () -> T): T? = bestEffort {
        withTimeoutOrNull(providerTimeoutMs) { block().also { currentCoroutineContext().ensureActive() } }
    }

    private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
        block().also { currentCoroutineContext().ensureActive() }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
}
