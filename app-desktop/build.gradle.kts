import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // Desktop app = JVM-only multiplatform target (src/jvmMain).
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                // shared's InnerTubeClient exposes Ktor types in its public signature
                implementation("io.ktor:ktor-client-core:3.1.3")
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                // Desktop playback (ADR-001): vlcj wraps a system libVLC (VLC install)
                implementation("uk.co.caprica:vlcj:4.8.2")
                implementation("io.insert-koin:koin-core:4.0.2")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // Phase 12: SMTC spike (JNA WinRT interop, Windows-only paths).
                // Base jna only — Smct.kt declares its own Structure/Library
                // types over user32.dll/combase.dll (the platform artifact's
                // win32 helpers proved version-fragile; see DEBUG_LOG r6).
                implementation("net.java.dev.jna:jna:5.17.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.dhun.desktop.MainKt"

        nativeDistributions {
            // Phase 14 Windows JVM launch fix: the MSI bundles a jlink-slimmed
            // JDK. The Compose plugin does NOT auto-detect required JDK modules;
            // missing java.sql (needed by SQLDelight/sqlite-jdbc) makes the
            // launcher show "Failed to launch JVM" after a successful install.
            // See: https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#including-jdk-modules
            // StackOverflow cases with H2/sqlite on Compose Desktop required
            // explicit java.sql or includeAllModules. Keep both: explicit list
            // documents the minimum, includeAllModules guarantees launch even
            // if the list drifts (size cost acceptable for test builds). Run
            // :app-desktop:suggestModules on a full JDK to tighten later.
            modules(
                "java.sql",
                "java.sql.rowset",
                "java.naming",
                "jdk.unsupported",
                "java.management",
                "java.instrument",
                "java.desktop",
                "java.logging",
                "java.net.http",
            )
            includeAllModules = true
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DHUN"
            description = "DHUN — YouTube Music player (test build)"
            vendor = "DHUN"
            copyright = "© DHUN contributors. GPL-3.0."
            // WHY 1.x: Compose Desktop's DMG/MSI packagers reject MAJOR == 0
            // ("'0.1.4' is not a valid version"). Configuration of THIS project
            // then fails, which takes down every Gradle task in the build —
            // that was the "desktop CI blocker" (docs/verification/04-desktop.md).
            // Installer versions map DHUN 0.x -> 1.0.x until v1.0.0 ships.
            // 1.0.5: Phase 14 ruggedization — bundles java.sql etc. and makes
            // VLC init fault-tolerant (fixes Windows "Failed to launch JVM").
            packageVersion = "1.0.5"
            windows {
                // Per-user = no admin UAC, install under %LOCALAPPDATA%\DHUN,
                // uninstall from Settings → Apps. Runtime data lives in
                // <installDir>/userdata (see DhunUserDirs) so uninstall
                // removes the DB + audio cache — no leftover %APPDATA%\DHUN.
                perUserInstall = true
                dirChooser = false
                menuGroup = "DHUN"
                // Stable across every test/release MSI. Changing it orphans
                // the previous install in Apps & Features.
                upgradeUuid = "31ddb86b-9666-4071-b11c-45f16fa4682d"
            }
        }
    }
}
