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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.dhun.desktop"
            // WHY 1.x: Compose Desktop's DMG/MSI packagers reject MAJOR == 0
            // ("'0.1.4' is not a valid version"). Configuration of THIS project
            // then fails, which takes down every Gradle task in the build —
            // that was the "desktop CI blocker" (docs/verification/04-desktop.md).
            // Installer versions map DHUN 0.x -> 1.0.x until v1.0.0 ships.
            packageVersion = "1.0.4"
        }
    }
}
