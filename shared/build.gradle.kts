plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
    id("app.cash.sqldelight")
}

kotlin {
    // Desktop (JVM) + Android. commonMain stays target-agnostic: no JVM APIs.
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
            implementation("io.ktor:ktor-client-core:3.1.3")
            implementation("io.ktor:ktor-client-cio:3.1.3")
            // Phase 05 data layer
            // `api`: DataLayer/DhunDatabase types appear in shared's public API
            api("app.cash.sqldelight:runtime:2.1.0")
            api("app.cash.sqldelight:coroutines-extensions:2.1.0")
            // Phase 06 design system — Compose Multiplatform (android+jvm)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            // Coil 3 — CMP artwork loading (common, no platform file needed)
            // Keep the version in sync with THIRD_PARTY.md.
            implementation("io.coil-kt.coil3:coil-compose:3.1.0")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.1.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api("app.cash.sqldelight:android-driver:2.1.0")
        }
        jvmTest.dependencies {
            implementation("io.ktor:ktor-client-mock:3.1.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            // Koin core (no Android) for the KoinDownloadStackTest regression
            // that guards the ADR-006 Android download DI graph against the
            // self-recursion bug class. The Android-side graph (appModule)
            // itself needs Robolectric to exercise with androidContext() —
            // that is a coordinator/CI follow-up. Versioned to match
            // app-android's io.insert-koin:koin-android:4.0.2.
            implementation("io.insert-koin:koin-core-jvm:4.0.2")
            // Headless icon raster regressions need the matching Skiko runtime.
            implementation(compose.desktop.currentOs)
        }
        jvmMain.dependencies {
            implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
            api("app.cash.sqldelight:sqlite-driver:2.1.0")
        }
        // NOTE: NewPipeExtractor scopes its deps to runtime — declare compile-
        // time needs explicitly if common/jvm code references them directly.
    }
}

sqldelight {
    databases {
        create("DhunDatabase") {
            packageName.set("dev.dhun.database")
            // Schema v3: v1 base + migrations/1.sqm (LyricsCache, v2) +
            // migrations/2.sqm (DownloadedTrack, v3). The version is derived
            // from the highest migration; both Android and JVM drivers run
            // Schema.create/migrate automatically. Derive the schema .db files
            // and enable verifyMigrations before any further schema change.
        }
    }
}

android {
    namespace = "dev.dhun.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    lint {
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test> {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
