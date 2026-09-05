package dev.dhun.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DhunUserDirsTest {

    @Test
    fun packagedPutsDataNextToTheLauncher() {
        val dir = DhunUserDirs.dataDir(
            osName = "Windows 11",
            home = "/home/sam",
            appdata = "/home/sam/AppData/Roaming",
            jpackageAppPath = "/home/sam/AppData/Local/DHUN/DHUN.exe",
        )
        assertEquals(File("/home/sam/AppData/Local/DHUN/userdata"), dir)
    }

    @Test
    fun packagedAcceptsWindowsSeparators() {
        val install = DhunUserDirs.packagedInstallDir(
            """C:\Users\sam\AppData\Local\DHUN\DHUN.exe""",
        )
        assertEquals(File("C:/Users/sam/AppData/Local/DHUN"), install)
        assertEquals(
            File("C:/Users/sam/AppData/Local/DHUN/userdata"),
            DhunUserDirs.dataDir(jpackageAppPath = """C:\Users\sam\AppData\Local\DHUN\DHUN.exe"""),
        )
    }

    @Test
    fun blankJpackagePathIsUnpackaged() {
        assertNull(DhunUserDirs.packagedInstallDir("  "))
        assertNull(DhunUserDirs.packagedInstallDir(null))
    }

    @Test
    fun unpackagedWindowsUsesRoamingAppData() {
        val dir = DhunUserDirs.dataDir(
            osName = "Windows 11",
            home = "/home/sam",
            appdata = "/home/sam/AppData/Roaming",
            jpackageAppPath = null,
        )
        assertEquals(File("/home/sam/AppData/Roaming", "DHUN"), dir)
    }

    @Test
    fun unpackagedMacUsesApplicationSupport() {
        val dir = DhunUserDirs.dataDir(
            osName = "Mac OS X",
            home = "/Users/sam",
            jpackageAppPath = null,
        )
        assertEquals(File("/Users/sam/Library/Application Support/DHUN"), dir)
    }

    @Test
    fun unpackagedLinuxPrefersXdgThenDotLocal() {
        assertEquals(
            File("/custom/share", "dhun"),
            DhunUserDirs.dataDir(
                osName = "Linux",
                home = "/home/sam",
                xdgDataHome = "/custom/share",
                jpackageAppPath = null,
            ),
        )
        assertEquals(
            File("/home/sam/.local/share", "dhun"),
            DhunUserDirs.dataDir(
                osName = "Linux",
                home = "/home/sam",
                xdgDataHome = null,
                jpackageAppPath = null,
            ),
        )
    }

    @Test
    fun defaultDbFileSitsInTheDataDir() {
        // DatabaseDriverFactory.defaultFile() reads real System properties;
        // the contract we care about here is the relative layout.
        val data = DhunUserDirs.dataDir(
            osName = "Linux",
            home = "/home/sam",
            jpackageAppPath = "/opt/DHUN/DHUN",
        )
        assertEquals(File("/opt/DHUN/userdata/dhun.db"), File(data, DatabaseFactory.FILE_NAME))
        assertEquals(File("/opt/DHUN/userdata/cache/audio"), File(data, "cache/audio"))
    }
}
