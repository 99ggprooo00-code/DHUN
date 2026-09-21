package dev.dhun.domain

import kotlin.concurrent.Volatile

/**
 * Session-scoped bookkeeping for the endless radio: which station is
 * active (seeded from [seedTrackId]) and the continuation token that pages
 * through it.
 *
 * One instance per app process (Koin `single`), shared by the view models
 * that can start a radio ([dev.dhun.presentation.player.PlayerViewModel]
 * and [dev.dhun.presentation/browse.ArtistViewModel]). The refill monitor
 * (PlayerViewModel) reads it on every queue/state change: when a radio has
 * a few songs left it fetches the next /next page via [continuationToken]
 * (or re-seeds from the playing track when the token is gone) and swaps
 * the queue tail around the sounding item — same song, same position,
 * no gap.
 *
 * Deliberately in-memory: a process restart drops the token, so a restored
 * radio session simply ends when its saved queue plays out (documented in
 * KNOWN_LIMITATIONS). The seed is only kept to document the station; the
 * re-seed fallback always uses the CURRENT track.
 */
class RadioSession {
    /** Track the station was started from; null = no radio active. */
    @Volatile
    var seedTrackId: String? = null
        private set

    /**
     * Token for the next page of the station. May be null: the server sent
     * no continuation, or a restart dropped it — the refill then re-seeds
     * a fresh /next from the playing track instead.
     */
    @Volatile
    var continuationToken: String? = null
        private set

    /** True while a radio started this session is still the queue. */
    val isActive: Boolean get() = seedTrackId != null

    fun start(seedTrackId: String, continuationToken: String? = null) {
        this.seedTrackId = seedTrackId
        this.continuationToken = continuationToken
    }

    /** A non-radio queue took over (search/album/playlist/history/…). */
    fun stop() {
        seedTrackId = null
        continuationToken = null
    }

    /** Advance the paging chain after a consumed page (null = re-seed next). */
    fun tokenConsumed(nextToken: String?) {
        continuationToken = nextToken
    }
}
