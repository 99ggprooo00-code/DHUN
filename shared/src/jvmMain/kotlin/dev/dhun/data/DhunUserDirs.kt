package dev.dhun.data

import java.io.File

/**
 * Desktop user-data location.
 *
 * **Packaged** (jpackage MSI / DMG / DEB): `<installDir>/userdata`.
 * The installer owns that folder; OS uninstall of the app removes the
 * SQLite DB and the audio cache with it. No leftover `%APPDATA%\DHUN`.
 *
 * **Unpackaged** (`./gradlew :app-desktop:run`): OS convention
 * (`%APPDATA%\DHUN`, `~/Library/Application Support/DHUN`,
 * `~/.local/share/dhun`).
 *
 * Detection: JDK jpackage sets `jpackage.app-path` to the launcher
 * executable. That property is absent under Gradle `run`.
 */
object DhunUserDirs {
    const val USERDATA_DIR_NAME = "userdata"
    const val APP_DIR_NAME = "DHUN"

    fun dataDir(
        osName: String = System.getProperty("os.name").orEmpty(),
        home: String = System.getProperty("user.home").orEmpty(),
        appdata: String? = System.getenv("APPDATA"),
        xdgDataHome: String? = System.getenv("XDG_DATA_HOME"),
        jpackageAppPath: String? = System.getProperty("jpackage.app-path"),
    ): File {
        packagedInstallDir(jpackageAppPath)?.let { return File(it, USERDATA_DIR_NAME) }
        return osConventionDir(osName, home, appdata, xdgDataHome)
    }

    fun packagedInstallDir(jpackageAppPath: String?): File? {
        if (jpackageAppPath.isNullOrBlank()) return null
        return fileParent(jpackageAppPath)
    }

    fun osConventionDir(
        osName: String,
        home: String,
        appdata: String?,
        xdgDataHome: String?,
    ): File {
        val os = osName.lowercase()
        return when {
            os.contains("win") -> File(appdata?.takeIf { it.isNotBlank() } ?: "$home${slash()}AppData${slash()}Roaming", APP_DIR_NAME)
            os.contains("mac") -> File(home, "Library${slash()}Application Support${slash()}$APP_DIR_NAME")
            else -> File(xdgDataHome?.takeIf { it.isNotBlank() } ?: "$home${slash()}.local${slash()}share", APP_DIR_NAME.lowercase())
        }
    }

    /**
     * Parent of [path] using both `/` and `\` so unit tests on Linux can
     * feed Windows-style launcher paths.
     */
    internal fun fileParent(path: String): File? {
        val normalized = path.replace('\\', '/')
        val cut = normalized.lastIndexOf('/')
        if (cut <= 0) return null
        return File(normalized.substring(0, cut))
    }

    private fun slash(): String = File.separator
}
