package dev.dhun.data

import app.cash.sqldelight.db.SqlDriver
import dev.dhun.database.DhunDatabase

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

/** Everything the app layers need, built from one database. */
class DataLayer(val db: DhunDatabase, clock: EpochClock = EpochClock.System) {
    val tracks: TrackRepository = SqlDelightTrackRepository(db, clock)
    val library: LibraryRepository = SqlDelightLibraryRepository(db, clock)
    val playlists: PlaylistRepository = SqlDelightPlaylistRepository(db, clock)
    val history: HistoryRepository = SqlDelightHistoryRepository(db, clock)
    val settings: SettingsRepository = SqlDelightSettingsRepository(db)
    val search: SearchRepository = SqlDelightSearchRepository(db, clock)
    val nowPlaying: NowPlayingRepository = SqlDelightNowPlayingRepository(db, clock)
    val lyricsCache: LyricsCacheRepository = SqlDelightLyricsCacheRepository(db, clock)
}
