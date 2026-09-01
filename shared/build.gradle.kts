plugins {
    kotlin("multiplatform") version "2.1.20"
}

kotlin {
    // Phase 02: JVM target (Desktop + the probe). The Android target is added
    // in Phase 03 together with the AGP/SDK setup it requires — commonMain is
    // written target-agnostic (no JVM APIs) so nothing is rewritten then.
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

tasks.withType<Test> {
    testLogging {
        events("passed", "failed", "skipped")
    }
}
