import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// MSI ProductVersion is an installer sequence, not DHUN's semantic version.
// Rebuilding "1.0.5" for every main push prevented in-place Windows upgrades.
// Rolling CI supplies a strictly increasing value, including workflow reruns.
val installerVersion = providers.gradleProperty("dhunInstallerVersion").getOrElse("1.0.6")
val installerParts = installerVersion.split('.').map { it.toIntOrNull() ?: -1 }
require(installerParts.size == 3 &&
    installerParts[0] in 1..255 && installerParts[1] in 0..255 && installerParts[2] in 0..65535) {
    "dhunInstallerVersion must be a numeric MSI version: major 1..255, minor 0..255, build 0..65535"
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
        // Candidate 27 (jump lists / tray polish): the module's first test
        // source set covers the PURE jump-list cores (task model, args,
        // recents persistence, throttle decision, tray state) — no COM/AWT
        // on any test path, so they run green on any OS. NOTE: CI's desktop
        // gate is `:app-desktop:compileKotlinJvm` only; `:app-desktop:test`
        // is not a CI step yet (.github is outside this batch's scope), so
        // run `./gradlew :app-desktop:jvmTest` locally.
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.dhun.desktop.MainKt"
        jvmArgs += "-Ddhun.installer.version=$installerVersion"

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
            packageVersion = installerVersion
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
