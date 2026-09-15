package dev.dhun.android.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName as AndroidComponentName
import dev.dhun.android.R
import dev.dhun.android.playback.DhunPlaybackService

/**
 * Pushes DHUN widget RemoteViews for every widget instance.
 *
 * Two data paths feed the same renderer:
 * - **Service push** ([pushFromPlayer]) — [DhunPlaybackService] calls this
 *   from its player listener on every track/play/shuffle/repeat change plus
 *   a 10 s progress tick while playing. No controller round-trip, so track
 *   changes land on the launcher within a frame or two.
 * - **Controller pull** ([requestUpdate]) — providers, transport actions and
 *   the system `updatePeriodMillis` safety net read the *existing* session
 *   via a short-lived [MediaController]. Covers the app-dead case (binding
 *   wakes the service) and instances the service never saw.
 *
 * Both paths push in two phases: text/controls immediately (with a cached
 * artwork bitmap when warm), then again when [WidgetArtworkLoader] delivers
 * the decoded bitmap. If the service is unreachable the idle placeholder
 * from [DhunWidgetState.idle] renders instead of stale data.
 *
 * Layouts are responsive per instance ([layoutForNowPlaying],
 * [layoutForQuickPlay]) from the host's reported size, and re-selected on
 * every push plus `onAppWidgetOptionsChanged` (resize).
 */
object DhunWidgetUpdater {

    private const val TAG = "DHUN_WIDGET"
    private const val CONTROLLER_TIMEOUT_MS = 3000L

    /** Now Playing shows the compact tier below this width. */
    const val COMPACT_MAX_WIDTH_DP = 200

    /** Now Playing shows the tall tier (times + shuffle/repeat) at/above this height. */
    const val TALL_MIN_HEIGHT_DP = 180

    /** Quick Play shows the wide tier (artwork + next) at/above this width. */
    const val QUICK_WIDE_MIN_WIDTH_DP = 200

    /** Service progress-tick cadence while playing. */
    const val PROGRESS_TICK_MS = 10_000L

    /** Read-only snapshot of whatever the player exposes to widgets. */
    data class PlaybackSnapshot(
        val title: CharSequence?,
        val artist: CharSequence?,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val shuffleEnabled: Boolean,
        /** [DhunWidgetState.REPEAT_OFF]/[DhunWidgetState.REPEAT_ALL]/[DhunWidgetState.REPEAT_ONE]. */
        val repeatMode: Int,
        val hasNext: Boolean,
        val hasPrevious: Boolean,
        val artworkUri: String?,
        val artworkData: ByteArray?,
        val artworkKey: String?,
    )

    /** Latest state pushed (any path) — guards async artwork against overwriting a newer track. */
    @Volatile
    private var lastPushed: DhunWidgetState? = null

    // ------------------------------------------------------------------ entry

    /** Request an update for *all* DHUN widget instances (both providers). */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val nowPlayingIds = idsFor(manager, appContext, DhunNowPlayingWidgetProvider::class.java)
        val quickPlayIds = idsFor(manager, appContext, DhunQuickPlayWidgetProvider::class.java)
        if (nowPlayingIds.isEmpty() && quickPlayIds.isEmpty()) return
        fetchStateAndPush(appContext, manager, nowPlayingIds, quickPlayIds)
    }

    /**
     * Fast path for [DhunPlaybackService]: the player is in-process, so read
     * it directly instead of binding a controller. Safe to call from any
     * thread; the read itself is marshalled to main (ExoPlayer affinity).
     */
    fun pushFromPlayer(context: Context, player: Player) {
        val appContext = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { pushFromPlayer(appContext, player) }
            return
        }
        val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return
        val nowPlayingIds = idsFor(manager, appContext, DhunNowPlayingWidgetProvider::class.java)
        val quickPlayIds = idsFor(manager, appContext, DhunQuickPlayWidgetProvider::class.java)
        if (nowPlayingIds.isEmpty() && quickPlayIds.isEmpty()) return
        val snapshot = runCatching { snapshotOfPlayer(player) }.getOrNull()
        if (snapshot == null) {
            pushState(appContext, manager, DhunWidgetState.idle(), null, nowPlayingIds, quickPlayIds)
            return
        }
        pushSnapshot(appContext, manager, snapshot, nowPlayingIds, quickPlayIds)
    }

    /** Push a specific state to a set of widget ids (used by tests and fallback). */
    internal fun pushState(
        context: Context,
        manager: AppWidgetManager,
        state: DhunWidgetState,
        artwork: Bitmap? = null,
        nowPlayingIds: IntArray,
        quickPlayIds: IntArray,
    ) {
        lastPushed = state
        for (id in nowPlayingIds) {
            runCatching {
                val views = buildNowPlayingViewsForId(context, state, id, artwork)
                manager.updateAppWidget(id, views)
            }
        }
        for (id in quickPlayIds) {
            runCatching {
                val views = buildQuickPlayViewsForId(context, state, id, artwork)
                manager.updateAppWidget(id, views)
            }
        }
    }

    // ------------------------------------------------------------- controller

    private fun fetchStateAndPush(
        context: Context,
        manager: AppWidgetManager,
        nowPlayingIds: IntArray,
        quickPlayIds: IntArray,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                fetchStateAndPush(context, manager, nowPlayingIds, quickPlayIds)
            }
            return
        }
        val token = try {
            SessionToken(context, AndroidComponentName(context, DhunPlaybackService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "session token unavailable — showing idle", e)
            pushState(context, manager, DhunWidgetState.idle(), null, nowPlayingIds, quickPlayIds)
            return
        }
        val pending = try {
            MediaController.Builder(context, token).buildAsync()
        } catch (e: Exception) {
            Log.w(TAG, "controller build failed — showing idle", e)
            pushState(context, manager, DhunWidgetState.idle(), null, nowPlayingIds, quickPlayIds)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!pending.isDone) {
                pending.cancel(true)
                Log.w(TAG, "controller connect timeout — showing idle")
                pushState(context, manager, DhunWidgetState.idle(), null, nowPlayingIds, quickPlayIds)
            }
        }
        handler.postDelayed(timeoutRunnable, CONTROLLER_TIMEOUT_MS)

        pending.addListener(
            {
                handler.removeCallbacks(timeoutRunnable)
                try {
                    val controller = pending.get()
                    val snapshot = runCatching { snapshotOfController(controller) }.getOrNull()
                    if (snapshot == null) {
                        pushState(context, manager, DhunWidgetState.idle(), null, nowPlayingIds, quickPlayIds)
                    } else {
                        pushSnapshot(context, manager, snapshot, nowPlayingIds, quickPlayIds)
                    }
                    handler.postDelayed({ runCatching { controller.release() } }, 1000L)
                } catch (e: Exception) {
                    Log.w(TAG, "controller unavailable — showing idle", e)
                    pushState(context, manager, DhunWidgetState.idle(), null, nowPlayingIds, quickPlayIds)
                    runCatching { pending.cancel(true) }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Two-phase push: instant text/controls, then artwork when decoded. */
    private fun pushSnapshot(
        context: Context,
        manager: AppWidgetManager,
        snapshot: PlaybackSnapshot,
        nowPlayingIds: IntArray,
        quickPlayIds: IntArray,
    ) {
        val state = stateOf(snapshot)
        pushState(context, manager, state, WidgetArtworkLoader.cached(state.artworkKey), nowPlayingIds, quickPlayIds)
        val key = state.artworkKey ?: return
        if (WidgetArtworkLoader.cached(key) != null) return
        val deliver: (Bitmap?) -> Unit = { bitmap ->
            // Drop late arrivals for a track we already moved past.
            if (bitmap != null && lastPushed?.artworkKey == key) {
                val current = lastPushed ?: state
                pushState(context, manager, current, bitmap, nowPlayingIdsFor(context), quickPlayIdsFor(context))
            }
        }
        if (snapshot.artworkData != null) {
            WidgetArtworkLoader.loadBytes(snapshot.artworkData, key, deliver)
        } else {
            WidgetArtworkLoader.loadUri(snapshot.artworkUri, key, deliver)
        }
    }

    // --------------------------------------------------------------- snapshot

    internal fun snapshotOfController(controller: MediaController): PlaybackSnapshot {
        val metadata = runCatching { controller.currentMediaItem?.mediaMetadata }.getOrNull()
        return buildSnapshot(
            metadataTitle = metadata?.title,
            metadataArtist = metadata?.artist,
            metadataArtworkUri = runCatching { metadata?.artworkUri?.toString() }.getOrNull(),
            metadataArtworkData = runCatching { metadata?.artworkData }.getOrNull(),
            isPlaying = runCatching { controller.isPlaying }.getOrDefault(false),
            positionMs = runCatching { controller.contentPosition }.getOrDefault(0L),
            durationMs = runCatching { controller.contentDuration }.getOrDefault(C.TIME_UNSET),
            shuffleEnabled = runCatching { controller.shuffleModeEnabled }.getOrDefault(false),
            playerRepeatMode = runCatching { controller.repeatMode }.getOrDefault(Player.REPEAT_MODE_OFF),
            hasNext = runCatching { controller.hasNextMediaItem() }.getOrDefault(false),
            hasPrevious = runCatching { controller.hasPreviousMediaItem() }.getOrDefault(false),
        )
    }

    internal fun snapshotOfPlayer(player: Player): PlaybackSnapshot {
        val metadata = runCatching { player.currentMediaItem?.mediaMetadata }.getOrNull()
        return buildSnapshot(
            metadataTitle = metadata?.title,
            metadataArtist = metadata?.artist,
            metadataArtworkUri = runCatching { metadata?.artworkUri?.toString() }.getOrNull(),
            metadataArtworkData = runCatching { metadata?.artworkData }.getOrNull(),
            isPlaying = runCatching { player.isPlaying }.getOrDefault(false),
            positionMs = runCatching { player.currentPosition }.getOrDefault(0L),
            durationMs = runCatching { player.duration }.getOrDefault(C.TIME_UNSET),
            shuffleEnabled = runCatching { player.shuffleModeEnabled }.getOrDefault(false),
            playerRepeatMode = runCatching { player.repeatMode }.getOrDefault(Player.REPEAT_MODE_OFF),
            hasNext = runCatching { player.hasNextMediaItem() }.getOrDefault(false),
            hasPrevious = runCatching { player.hasPreviousMediaItem() }.getOrDefault(false),
        )
    }

    private fun buildSnapshot(
        metadataTitle: CharSequence?,
        metadataArtist: CharSequence?,
        metadataArtworkUri: String?,
        metadataArtworkData: ByteArray?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        shuffleEnabled: Boolean,
        playerRepeatMode: Int,
        hasNext: Boolean,
        hasPrevious: Boolean,
    ): PlaybackSnapshot {
        val uri = metadataArtworkUri?.takeIf { it.isNotBlank() }
        val key = when {
            uri != null -> uri
            metadataArtworkData != null && metadataArtworkData.isNotEmpty() ->
                "bytes:${metadataArtworkData.contentHashCode()}"
            else -> null
        }
        return PlaybackSnapshot(
            title = metadataTitle,
            artist = metadataArtist,
            isPlaying = isPlaying,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = if (durationMs == C.TIME_UNSET || durationMs < 0L) 0L else durationMs,
            shuffleEnabled = shuffleEnabled,
            repeatMode = when (playerRepeatMode) {
                Player.REPEAT_MODE_ALL -> DhunWidgetState.REPEAT_ALL
                Player.REPEAT_MODE_ONE -> DhunWidgetState.REPEAT_ONE
                else -> DhunWidgetState.REPEAT_OFF
            },
            hasNext = hasNext,
            hasPrevious = hasPrevious,
            artworkUri = uri,
            artworkData = metadataArtworkData?.takeIf { it.isNotEmpty() },
            artworkKey = key,
        )
    }

    internal fun stateOf(snapshot: PlaybackSnapshot): DhunWidgetState =
        DhunWidgetState.fromPlayback(
            rawTitle = snapshot.title,
            rawArtist = snapshot.artist,
            isPlaying = snapshot.isPlaying,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = snapshot.repeatMode,
            hasNext = snapshot.hasNext,
            hasPrevious = snapshot.hasPrevious,
            artworkKey = snapshot.artworkKey,
        )

    // ------------------------------------------------------------ responsive

    /**
     * Tier selection is pure (dp in, layout out) so resize behavior is
     * unit-testable without a launcher.
     */
    internal fun layoutForNowPlaying(minWidthDp: Int, maxHeightDp: Int): Int = when {
        minWidthDp in 1 until COMPACT_MAX_WIDTH_DP -> R.layout.widget_now_playing_compact
        maxHeightDp >= TALL_MIN_HEIGHT_DP -> R.layout.widget_now_playing_tall
        else -> R.layout.widget_now_playing
    }

    internal fun layoutForQuickPlay(minWidthDp: Int): Int = when {
        minWidthDp >= QUICK_WIDE_MIN_WIDTH_DP -> R.layout.widget_quick_play_wide
        else -> R.layout.widget_quick_play
    }

    // ------------------------------------------------------------- RemoteViews

    internal fun buildNowPlayingViews(
        context: Context,
        state: DhunWidgetState,
        artwork: Bitmap? = null,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
            applyCommon(context, state, artwork, this)
        }

    internal fun buildNowPlayingViewsForId(
        context: Context,
        state: DhunWidgetState,
        appWidgetId: Int,
        artwork: Bitmap? = null,
    ): RemoteViews {
        val (minWidthDp, maxHeightDp) = liveOptions(context, appWidgetId)
        return buildNowPlayingViewsForId(context, state, appWidgetId, artwork, minWidthDp, maxHeightDp)
    }

    internal fun buildNowPlayingViewsForId(
        context: Context,
        state: DhunWidgetState,
        appWidgetId: Int,
        artwork: Bitmap?,
        minWidthDp: Int,
        maxHeightDp: Int,
    ): RemoteViews {
        val layout = layoutForNowPlaying(minWidthDp, maxHeightDp)
        return RemoteViews(context.packageName, layout).apply {
            applyCommon(context, state, artwork, this)
            setOnClickPendingIntent(
                R.id.widget_prev,
                WidgetIntents.prevIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId),
            )
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                WidgetIntents.playPauseIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                WidgetIntents.nextIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId),
            )
            if (layout == R.layout.widget_now_playing_tall) {
                setOnClickPendingIntent(
                    R.id.widget_shuffle,
                    WidgetIntents.shuffleIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId),
                )
                setOnClickPendingIntent(
                    R.id.widget_repeat,
                    WidgetIntents.repeatIntent(context, DhunNowPlayingWidgetProvider::class.java, appWidgetId),
                )
            }
            setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openAppIntent(context, appWidgetId))
        }
    }

    internal fun buildQuickPlayViewsForId(
        context: Context,
        state: DhunWidgetState,
        appWidgetId: Int,
        artwork: Bitmap? = null,
    ): RemoteViews {
        val (minWidthDp, _) = liveOptions(context, appWidgetId)
        return buildQuickPlayViewsForId(context, state, appWidgetId, artwork, minWidthDp)
    }

    internal fun buildQuickPlayViewsForId(
        context: Context,
        state: DhunWidgetState,
        appWidgetId: Int,
        artwork: Bitmap?,
        minWidthDp: Int,
    ): RemoteViews {
        val layout = layoutForQuickPlay(minWidthDp)
        return RemoteViews(context.packageName, layout).apply {
            applyCommon(context, state, artwork, this)
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                WidgetIntents.playPauseIntent(context, DhunQuickPlayWidgetProvider::class.java, appWidgetId),
            )
            if (layout == R.layout.widget_quick_play_wide) {
                setOnClickPendingIntent(
                    R.id.widget_next,
                    WidgetIntents.nextIntent(context, DhunQuickPlayWidgetProvider::class.java, appWidgetId),
                )
            }
            setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openAppIntent(context, appWidgetId))
        }
    }

    /**
     * Shared binding for every tier. RemoteViews actions are null-safe at
     * apply time — ids absent from a tier's layout are silently skipped —
     * so one binding serves all five layouts.
     */
    private fun RemoteViews.applyCommon(
        context: Context,
        state: DhunWidgetState,
        artwork: Bitmap?,
        views: RemoteViews,
    ) {
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_artist, state.artist)

        val playing = state.isPlaying && state.hasTrack
        views.setImageViewResource(
            R.id.widget_play_pause_icon,
            if (playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (playing) R.string.widget_cd_pause else R.string.widget_cd_play),
        )
        views.setContentDescription(R.id.widget_prev, context.getString(R.string.widget_cd_previous))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.widget_cd_next))
        views.setContentDescription(R.id.widget_artwork, context.getString(R.string.widget_cd_artwork))

        // Progress + times.
        views.setProgressBar(R.id.widget_progress, 1000, state.progressPermille, false)
        views.setTextViewText(R.id.widget_position, state.positionText)
        views.setTextViewText(R.id.widget_duration, state.durationText)
        views.setViewVisibility(
            R.id.widget_times_row,
            if (state.hasProgress) View.VISIBLE else View.GONE,
        )

        // Shuffle / repeat toggles (tall tier). Active = accent, idle = secondary.
        val accent = ContextCompat.getColor(context, R.color.widget_accent)
        val secondary = ContextCompat.getColor(context, R.color.widget_secondary)
        views.setImageViewResource(
            R.id.widget_repeat,
            if (state.repeatMode == DhunWidgetState.REPEAT_ONE) {
                R.drawable.widget_ic_repeat_one
            } else {
                R.drawable.widget_ic_repeat
            },
        )
        runCatching {
            views.setInt(R.id.widget_shuffle, "setColorFilter", if (state.shuffleEnabled) accent else secondary)
            val repeatActive = state.repeatMode != DhunWidgetState.REPEAT_OFF
            views.setInt(R.id.widget_repeat, "setColorFilter", if (repeatActive) accent else secondary)
        }
        views.setContentDescription(
            R.id.widget_shuffle,
            context.getString(
                if (state.shuffleEnabled) R.string.widget_cd_shuffle_on else R.string.widget_cd_shuffle_off,
            ),
        )
        views.setContentDescription(
            R.id.widget_repeat,
            context.getString(
                when (state.repeatMode) {
                    DhunWidgetState.REPEAT_ALL -> R.string.widget_cd_repeat_all
                    DhunWidgetState.REPEAT_ONE -> R.string.widget_cd_repeat_one
                    else -> R.string.widget_cd_repeat_off
                },
            ),
        )

        // Dim skip buttons at queue edges / when idle; play stays full-strength.
        runCatching {
            val prevAlpha = edgeAlpha(state, state.hasPrevious)
            val nextAlpha = edgeAlpha(state, state.hasNext)
            views.setFloat(R.id.widget_prev, "setAlpha", prevAlpha)
            views.setFloat(R.id.widget_next, "setAlpha", nextAlpha)
        }

        // Artwork: decoded bitmap wins, else the note placeholder.
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_artwork, artwork)
        } else {
            views.setImageViewResource(R.id.widget_artwork, R.drawable.widget_ic_music_note)
        }
    }

    private fun edgeAlpha(state: DhunWidgetState, hasEdge: Boolean): Float = when {
        state.isIdle -> 0.5f
        hasEdge -> 1f
        else -> 0.35f
    }

    // ---------------------------------------------------------------- helpers

    private fun idsFor(manager: AppWidgetManager, context: Context, provider: Class<*>): IntArray =
        runCatching { manager.getAppWidgetIds(ComponentName(context, provider)) }.getOrDefault(intArrayOf())

    private fun nowPlayingIdsFor(context: Context): IntArray =
        runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DhunNowPlayingWidgetProvider::class.java))
        }.getOrDefault(intArrayOf())

    private fun quickPlayIdsFor(context: Context): IntArray =
        runCatching {
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, DhunQuickPlayWidgetProvider::class.java))
        }.getOrDefault(intArrayOf())

    /** (minWidthDp, maxHeightDp); 0/0 when the host reports nothing (tests, old launchers). */
    private fun liveOptions(context: Context, appWidgetId: Int): Pair<Int, Int> {
        val options: Bundle? = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        }.getOrNull()
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val maxHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return minWidth to maxHeight
    }
}
