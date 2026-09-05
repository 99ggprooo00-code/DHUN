package dev.dhun.player

/**
 * Phase 14 bounded audio-segment cache budget helpers.
 *
 * [dev.dhun.data.SettingsKeys.CACHE_SIZE_MB] is the user-facing setting
 * (default 1024). Platform players map that to a byte ceiling for the
 * on-disk segment store (Android: Media3 [SimpleCache] LRU).
 *
 * - `0` = effectively unlimited (capped to a sane internal max so LRU math
 *   does not overflow).
 * - Negative values clamp to the default.
 * - Values above [MAX_MB] clamp down (protect low-end storage).
 */
object AudioCacheBudget {
    const val DEFAULT_MB: Int = 1024
    const val MAX_MB: Int = 8 * 1024 // 8 GiB hard ceiling
    /** Internal stand-in for "unlimited" — large but safe for long math. */
    const val UNLIMITED_BYTES: Long = 512L * 1024L * 1024L * 1024L // 512 GiB

    fun clampMb(mb: Int): Int = when {
        mb < 0 -> DEFAULT_MB
        mb == 0 -> 0
        else -> mb.coerceAtMost(MAX_MB)
    }

    fun bytesForMb(mb: Int): Long {
        val clamped = clampMb(mb)
        if (clamped == 0) return UNLIMITED_BYTES
        return clamped.toLong() * 1024L * 1024L
    }
}
