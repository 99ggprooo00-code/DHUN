package dev.dhun.presentation.library

import android.os.Environment
import android.os.StatFs

/**
 * Android actual: downloads live in the app's internal `filesDir`, which sits
 * on the shared data partition. That partition is reachable without a
 * [android.content.Context], so this probe needs no DI wiring.
 */
actual fun deviceStorageSpace(): StorageSpace? = try {
    val stats = StatFs(Environment.getDataDirectory().path)
    val total = stats.blockCountLong * stats.blockSizeLong
    val free = stats.availableBlocksLong * stats.blockSizeLong
    if (total > 0) StorageSpace(totalBytes = total, freeBytes = free) else null
} catch (_: Exception) {
    // No volume (rare/headless) — the UI degrades to "used by downloads" only.
    null
}
