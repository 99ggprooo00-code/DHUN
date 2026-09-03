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

        /** Per-OS user data dir: %APPDATA%\DHUN, ~/Library/Application Support/DHUN, ~/.local/share/dhun. */
        fun defaultFile(): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val dir = when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "DHUN")
                os.contains("mac") -> File(home, "Library/Application Support/DHUN")
                else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "dhun")
            }
            return File(dir, DatabaseFactory.FILE_NAME)
        }
    }
}
