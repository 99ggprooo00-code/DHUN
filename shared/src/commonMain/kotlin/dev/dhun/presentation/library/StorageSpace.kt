package dev.dhun.presentation.library

/**
 * Snapshot of the storage volume that holds DHUN downloads, in bytes.
 *
 * [freeBytes] is the space still available to a caller without privilege
 * escalation (Android `availableBytes` / JVM `usableSpace`); [totalBytes] is
 * the volume's full capacity. The pair drives the Library storage-management
 * view ("X used by downloads • Y free of Z").
 */
data class StorageSpace(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    companion object {
        /** Shown when a platform cannot report volume statistics. */
        val UNAVAILABLE: StorageSpace? = null
    }
}

/**
 * Best-effort capacity probe for the volume downloads live on.
 *
 * Context-free on purpose: Android downloads are internal (`filesDir`, on the
 * data partition — `Environment.getDataDirectory()`), desktop downloads live
 * under the user home, so neither target needs a platform handle to locate
 * the right filesystem. Returns `null` if the OS does not expose the figure.
 * CommonMain has no filesystem API, hence the `expect`/`actual`.
 */
expect fun deviceStorageSpace(): StorageSpace?
