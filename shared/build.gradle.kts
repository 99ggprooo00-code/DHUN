plugins {
    kotlin("multiplatform")
    id("com.android.library")
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
        }
        // NOTE: NewPipeExtractor scopes its deps to runtime — declare compile-
        // time needs explicitly if common/jvm code references them directly.
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
