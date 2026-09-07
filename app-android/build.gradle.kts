plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.dhun.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.dhun.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"
    }

    signingConfigs {
        // Stable signing for debug/test builds.
        //
        // WHY: with no explicit config, AGP signs debug builds with a
        // per-machine ~/.android/debug.keystore. CI runners are ephemeral, so
        // every CI-built dhun-test.apk had a DIFFERENT signature and Android
        // refused updates over an existing install ("package conflicts with an
        // existing package"). This committed key makes all debug/test builds —
        // CI or local — share one signature so updates install cleanly.
        //
        // It is a PUBLIC throwaway test key (passwords 'android', repo is
        // public, test builds only). Real release signing is Phase 14
        // (see ROADMAP.md); .gitignore keeps real *.jks/*.keystore out.
        create("testBuild") {
            storeFile = file("keystores/dhun-test.p12")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("testBuild")
        }
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests {
            // The shortcut-XML and nav-state tests read real app resources
            // (R.xml.shortcuts, strings) and real framework classes through
            // Robolectric.
            isIncludeAndroidResources = true
            // Robolectric loads the full framework jar per test class — the
            // Gradle default test heap is too small for it alongside the
            // Compose/Koin classpath.
            all { test -> test.maxHeapSize = "1024m" }
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    // shared's InnerTubeClient exposes Ktor types in its public signature
    implementation("io.ktor:ktor-client-core:3.1.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Playback (Media3): ExoPlayer engine, session service, resolving data source,
    // Phase 14 bounded audio-segment cache (SimpleCache + StandaloneDatabaseProvider).
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")
    implementation("androidx.media3:media3-database:1.5.1")

    implementation("io.insert-koin:koin-android:4.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Unit tests (Phase 15 scaffold) -------------------------------------
    // This module previously had NO test source set at all (known gap,
    // ROADMAP "CI follow-up": add :app-android:testDebugUnitTest + Robolectric
    // incl. a Koin graph test). Versions chosen for JDK 17 / AGP 8.7 / Kotlin
    // 2.1 compatibility; Robolectric 4.14.1 is the first line that supports
    // the API levels this app targets.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

// ---------------------------------------------------------------------------
// CI EXECUTION COUPLING — remove this block once a workflow runs
// `:app-android:testDebugUnitTest` explicitly.
//
// WHY: every existing CI Android path (ci.yml "Android debug build" and
// test-release.yml "Build debug APK") invokes ONLY :app-android:assembleDebug.
// Before this module had tests that was harmless; with a test suite it means
// broken/failing unit tests would ship silently, because assemble is a
// compile gate, not a test gate. The session that added the scaffold could
// not edit .github/workflows (frozen scope), so the minimal app-android-only
// way to make CI actually EXECUTE the suite on every PR and release build is
// this dependency. It adds no task to the APK itself.
// ---------------------------------------------------------------------------
tasks.named("assembleDebug") { dependsOn("testDebugUnitTest") }
