plugins {
    kotlin("multiplatform")
    id("com.android.library")
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            api("app.cash.sqldelight:android-driver:2.1.0")
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
            // Schema v1. When v2 arrives: add src/commonMain/sqldelight/migrations/1.sqm,
            // emit 1.db via generateCommonMainDhunDatabaseSchema, turn on verifyMigrations.
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
