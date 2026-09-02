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
// Phase 04 (desktop, vlcj): module code is committed but deliberately NOT
// activated in CI yet. Uncomment to build it locally:
//   include(":app-desktop")
// and run `./gradlew :app-desktop:run`. Reason + CI bisection evidence:
// docs/verification/04-desktop.md ("CI configuration blocker").
include(":tools:playback-probe")
