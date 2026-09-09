package dev.dhun.domain

import dev.dhun.core.DhunError
import dev.dhun.core.DhunResult
import dev.dhun.core.Track
import dev.dhun.data.HistoryRepository
import dev.dhun.data.LibraryRepository
import dev.dhun.provider.MusicProvider
import kotlinx.coroutines.flow.first

/**
 * History-seeded recommendations for the Home "Recommended songs" section.
 *
 * Two complementary strategies, both driven by the user's own listening data
 * so the section is meaningful without ever blocking Home's first paint:
 *
 *  1. [localPicks] — an offline-safe, near-instant list of the user's saved
 *     songs that they have not just replayed (so it never repeats Home's
 *     "Listen again" row). Used as immediately-rendered content and as the
 *     graceful fallback when the network pass returns nothing.
 *  2. [historySeeded] — best-effort network enrichment: fresh tracks similar
 *     to the user's recent plays, seeded through
 *     [MusicProvider.relatedTracks]. It never throws and never blocks; on any
 *     failure it simply returns empty so the caller keeps [localPicks].
 *
 * Everything already visible on Home (quick picks, the full feed shelves and
 * the "Listen again" row) is passed in as [excludeIds] so a recommendation is
 * never a duplicate of a shelf the user is already looking at.
 */
class GetRecommendationsUseCase(
    private val provider: MusicProvider,
    private val history: HistoryRepository,
    private val library: LibraryRepository,
) {

    /**
     * Offline-safe local picks: favourites the user has NOT just replayed.
     * Recent plays are deliberately left out because the whole recent window
     * is already shown in Home's "Listen again" row — repeating it here would
     * duplicate that shelf. Saved-but-not-recently-played songs are the
     * closest offline proxy for "music I liked that isn't on screen now".
     */
    suspend fun localPicks(
        excludeIds: Set<String>,
        historyWindow: Int = HISTORY_WINDOW,
        limit: Int = LOCAL_LIMIT,
    ): List<Track> {
        val recent = history.observeRecentlyPlayed(historyWindow).first()
        val favorites = library.observeFavorites().first()
        val recentIds = recent.mapTo(LinkedHashSet()) { it.id }
        val excluded = excludeIds + recentIds
        return favorites.filterNot { it.id in excluded }.take(limit)
    }

    /**
     * Fresh related tracks seeded from the user's most recent distinct plays.
     * Sequential and bounded so a single slow/failed hop cannot stall the rest;
     * stops early once enough unique candidates are gathered.
     */
    suspend fun historySeeded(
        excludeIds: Set<String>,
        seedCount: Int = SEED_COUNT,
        limit: Int = REMOTE_LIMIT,
    ): List<Track> {
        val recent = history.observeRecentlyPlayed(HISTORY_WINDOW).first()
        val seeds = distinctIds(recent).filterNot { it in excludeIds }.take(seedCount)
        if (seeds.isEmpty()) return emptyList()

        val excluded = excludeIds + seeds
        val seen = LinkedHashSet<String>()
        val result = ArrayList<Track>()
        for (seedId in seeds) {
            if (result.size >= limit) break
            val related = try {
                provider.relatedTracks(seedId)
            } catch (_: Exception) {
                // A transient extraction/network failure on one seed must not
                // surface as a Home error — fall back to the next seed.
                DhunResult.Failure(DhunError.Unknown())
            }
            if (related is DhunResult.Success) {
                for (track in related.value) {
                    if (track.id.isBlank() || track.id in excluded || track.id in seen) continue
                    seen.add(track.id)
                    result.add(track)
                    if (result.size >= limit) break
                }
            }
        }
        return result
    }

    private fun distinctIds(tracks: List<Track>): List<String> {
        val seen = LinkedHashSet<String>()
        val ids = ArrayList<String>(tracks.size)
        for (t in tracks) {
            if (t.id.isBlank() || t.id in seen) continue
            seen.add(t.id)
            ids.add(t.id)
        }
        return ids
    }

    internal companion object {
        const val HISTORY_WINDOW = 30
        const val SEED_COUNT = 3
        const val LOCAL_LIMIT = 24
        const val REMOTE_LIMIT = 24
    }
}
