plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The whole point of this module: reuse DHUN's own extraction instead of
    // reimplementing it. `:shared`'s jvmMain wires the ADR-001 chain
    // (own InnerTube client primary, yt-dlp failover) via
    // YouTubeMusicProvider.Companion.forDesktop().
    implementation(project(":shared"))

    // shared exposes coroutines and kotlinx-serialization as `implementation`,
    // not `api`, so anything touching those types directly must redeclare them
    // (same pattern as tools/playback-probe).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // NOTE: deliberately no HTTP server or HTTP client dependency here. The
    // bridge runs on com.sun.net.httpserver + java.net.http, both in the JDK,
    // so this module adds nothing to the dependency graph and nothing to
    // THIRD_PARTY.md. Versions are also one less thing to rot.
}

application {
    mainClass.set("dev.dhun.tools.webbridge.WebBridgeServerKt")
}

tasks.named<JavaExec>("run") {
    // `./gradlew :tools:web-bridge:run --args="--port 8787 --allowed-origin https://example.github.io"`
    standardInput = System.`in`
}
