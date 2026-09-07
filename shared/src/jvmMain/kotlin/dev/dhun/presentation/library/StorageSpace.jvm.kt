package dev.dhun.presentation.library

import java.io.File

/**
 * Desktop (JVM) actual: ADR-006 stores downloads under
 * `<user home>/.dhun/downloads`, so the user-home volume is the one that
 * fills. [File.usableSpace] reports the bytes a normal process can actually
 * write (already accounting for permissions/reserved blocks).
 */
actual fun deviceStorageSpace(): StorageSpace? = try {
    val volume = File(System.getProperty("user.home") ?: ".")
    val total = volume.totalSpace
    val free = volume.usableSpace
    if (total > 0) StorageSpace(totalBytes = total, freeBytes = free) else null
} catch (_: Exception) {
    null
}
