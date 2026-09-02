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

    // Playback (Media3): ExoPlayer engine, session service, resolving data source
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")

    implementation("io.insert-koin:koin-android:4.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
