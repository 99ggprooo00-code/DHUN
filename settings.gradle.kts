pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // NewPipe Extractor lives on JitPack
    }
}

rootProject.name = "dhun"

include(":shared")
include(":app-android")
include(":app-desktop") // Phase 04 desktop (Compose Desktop + vlcj); CI compiles it
include(":tools:playback-probe")

// ---------------------------------------------------------------------------
// CI diagnostics without log access.
// WHY: the agent sandboxes that maintain this repo cannot download GitHub
// Actions log archives (results-receiver host is network-blocked) and cannot
// edit workflow files (token lacks `workflows` permission). Check-run
// ANNOTATIONS are reachable via the REST API, so when a build fails under
// GitHub Actions we print the failure cause chain as `::error::` workflow
// commands. This turns "step 4 failed" into the actual Gradle exception.
// ---------------------------------------------------------------------------
if (System.getenv("GITHUB_ACTIONS") == "true") {
    @Suppress("DEPRECATION")
    gradle.buildFinished {
        val root = failure ?: return@buildFinished
        var cause: Throwable? = root
        var depth = 0
        val seen = HashSet<String>()
        while (cause != null && depth < 12) {
            val line = (cause.javaClass.name + ": " + (cause.message ?: ""))
                .replace("\r", " ").replace("\n", " ⏎ ").take(900)
            if (seen.add(line)) println("::error title=Gradle failure ($depth)::$line")
            cause = cause.cause
            depth++
        }
        root.stackTrace.take(8).forEach { println("::error title=Gradle failure (frame)::at $it") }
    }
}
