// DHUN root build file. Plugin versions are pinned HERE so subprojects apply
// them without repeating versions.
plugins {
    id("com.android.library") version "8.7.2" apply false
    id("com.android.application") version "8.7.2" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("multiplatform") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Phase 04 desktop module (org.jetbrains.compose). NOTE: applied only
    // when :app-desktop is activated (see settings.gradle.kts). CI keeps it
    // OUT of the default build for now — see docs/verification/04-desktop.md
    // "CI configuration blocker".
    id("org.jetbrains.compose") version "1.8.2" apply false
}
