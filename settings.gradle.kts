pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://jitpack.io") // NewPipe Extractor lives on JitPack
    }
}

rootProject.name = "dhun"

include(":shared")
include(":tools:playback-probe")
