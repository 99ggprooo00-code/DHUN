import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    // Desktop app = JVM-only multiplatform target (src/jvmMain).
    // Uses kotlin("multiplatform") like :shared — see NOTE below.
    jvm {
        withJava()
    }
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
            packageVersion = "0.1.4"
        }
    }
}
