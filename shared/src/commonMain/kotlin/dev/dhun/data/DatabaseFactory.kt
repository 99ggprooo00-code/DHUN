package dev.dhun.data

import app.cash.sqldelight.db.SqlDriver
import dev.dhun.database.DhunDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Builds the shared [DhunDatabase]. Platforms supply the driver (Android:
 * AndroidSqliteDriver, JVM: JdbcSqliteDriver) via [DatabaseDriverFactory].
 * Migrations are owned by SQLDelight (`src/commonMain/sqldelight/migrations`);
 * the schema version is `DhunDatabase.Schema.version`.
 */
object DatabaseFactory {
    const val FILE_NAME = "dhun.db"

    /** Drivers from [DatabaseDriverFactory] already have foreign keys enabled. */
    fun create(driver: SqlDriver): DhunDatabase = DhunDatabase(driver)
}

/** Platform driver — see androidMain / jvmMain actuals. */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Everything the app layers need, built from one database.
 *
 * All repositories share a single-threaded [dbIo] dispatcher. The JVM
 * `JdbcSqliteDriver` is not multi-thread safe on one connection (and the
 * in-memory test driver is a single shared connection): concurrent
 * `history.recordPlay` + `nowPlaying.saveQueue` from
 * [dev.dhun.player.NowPlayingPersistence] can hang or drop rows under
 * load — CI flake `NowPlayingPersistenceTest` timed out waiting 15s for
 * positionMs==30000 (run 33967027211). Serializing DB work at the
 * DataLayer boundary is the correct contract for one-connection SQLite.
 */
class DataLayer(val db: DhunDatabase, clock: EpochClock = EpochClock.System) {
    private val dbIo = Dispatchers.Default.limitedParallelism(1)

    val tracks: TrackRepository = SqlDelightTrackRepository(db, clock, dbIo)
    val library: LibraryRepository = SqlDelightLibraryRepository(db, clock, dbIo)
    val playlists: PlaylistRepository = SqlDelightPlaylistRepository(db, clock, dbIo)
    val history: HistoryRepository = SqlDelightHistoryRepository(db, clock, dbIo)
    val settings: SettingsRepository = SqlDelightSettingsRepository(db, dbIo)
    val search: SearchRepository = SqlDelightSearchRepository(db, clock, dbIo)
    val nowPlaying: NowPlayingRepository = SqlDelightNowPlayingRepository(db, clock, dbIo)
    val lyricsCache: LyricsCacheRepository = SqlDelightLyricsCacheRepository(db, clock, dbIo)
}
