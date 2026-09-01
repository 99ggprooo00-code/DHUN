// DHUN root build file. Plugin versions are pinned HERE so subprojects apply
// them without repeating versions.
plugins {
    id("com.android.library") version "8.7.2" apply false
    id("com.android.application") version "8.7.2" apply false
    kotlin("android") version "2.1.20" apply false
    kotlin("multiplatform") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
