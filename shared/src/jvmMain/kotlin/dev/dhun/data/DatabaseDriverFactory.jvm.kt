package dev.dhun.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/**
 * JVM/desktop driver. [file] == null → in-memory (tests). The JDBC driver
 * runs `Schema.create`/`migrate` itself when a schema is passed, keyed on
 * SQLite's `user_version`.
 */
actual class DatabaseDriverFactory(private val file: File? = defaultFile()) {
    actual fun createDriver(): SqlDriver {
        file?.parentFile?.mkdirs()
        val url = file?.let { "jdbc:sqlite:${it.absolutePath}" } ?: JdbcSqliteDriver.IN_MEMORY
        val props = Properties().apply { setProperty("foreign_keys", "true") } // sqlite-jdbc pragma
        return JdbcSqliteDriver(url, props, dev.dhun.database.DhunDatabase.Schema)
    }

    companion object {
        fun inMemory(): DatabaseDriverFactory = DatabaseDriverFactory(file = null)

        /**
         * Packaged install: `<installDir>/userdata/dhun.db` (removed with the
         * app). Unpackaged: OS user-data dir (see [DhunUserDirs]).
         */
        fun defaultFile(): File = File(DhunUserDirs.dataDir(), DatabaseFactory.FILE_NAME)
    }
}
