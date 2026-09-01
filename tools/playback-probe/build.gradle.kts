plugins {
    kotlin("jvm") version "2.1.20"
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Stream extraction engine (GPL-3.0). Runs on any JVM — this same
    // dependency resolves streams for the Android and Desktop apps later.
    // NOTE: its POM scopes ALL dependencies to runtime-only, so anything the
    // probe needs at compile time must be declared here explicitly.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
}

application {
    mainClass.set("dev.dhun.tools.playbackprobe.MainKt")
}

// Copies the resolved runtime classpath to disk — used by the findings step
// to inspect extractor internals, and by rot-drill debugging.
tasks.register<Copy>("resolveRuntime") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime-libs"))
}
