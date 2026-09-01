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
}

application {
    // `gradle :tools:playback-probe:run -PmainClass=...` switches entry points:
    //  - MainKt        = Phase 01 extraction kill-switch probe (default)
    //  - SmokeMainKt   = Phase 02 provider-level live smoke
    mainClass.set(
        providers.gradleProperty("mainClass")
            .getOrElse("dev.dhun.tools.playbackprobe.MainKt")
    )
}

tasks.register<Copy>("resolveRuntime") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime-libs"))
}
