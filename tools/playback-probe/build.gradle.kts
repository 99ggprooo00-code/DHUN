plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    // shared exposes coroutines/ktor as `implementation` (not `api`) — the
    // probe touches those types directly, so declare them explicitly.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    // Stream extraction engine (GPL-3.0). NOTE: its POM scopes ALL deps to
    // runtime-only — anything needed at compile time must be declared here.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    testImplementation(kotlin("test"))
}

application {
    // `gradle :tools:playback-probe:run -PmainClass=...` switches entry points:
    //  - MainKt        = Extraction kill-switch probe & full verification suite (default)
    //  - SmokeMainKt   = Phase 02 provider-level live smoke
    //  - OfflineMainKt = ADR-006 deterministic local-file/offline probe
    mainClass.set(
        providers.gradleProperty("mainClass")
            .getOrElse("dev.dhun.tools.playbackprobe.MainKt")
    )
}

// Deterministic ADR-006 check. It uses only a packaged WAV fixture, the real
// JVM SQLDelight download repository, and a network resolver that fails if
// called. `--offline` therefore checks the local playback route without any
// live YouTube or CDN dependency.
tasks.register<JavaExec>("offlineProbe") {
    group = "verification"
    description = "Assert a completed download resolves and loads through file:// without network"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.dhun.tools.playbackprobe.OfflineMainKt")
}

tasks.register<JavaExec>("smokeProbe") {
    group = "verification"
    description = "Live provider-level smoke check across home feed, search, suggestions, radio, lyrics, stream info"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.dhun.tools.smoke.SmokeMainKt")
}

tasks.register<Copy>("resolveRuntime") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime-libs"))
}

// CI compiles and tests this module explicitly. Until the repository keeps a
// separate desktop compile dependency in the main build job, retain the
// GITHUB_ACTIONS-only dependency so the probe compile also checks the Desktop
// JVM source set. Local builds are unaffected.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    tasks.named("compileKotlin") {
        dependsOn(":app-desktop:compileKotlinJvm")
    }
}
