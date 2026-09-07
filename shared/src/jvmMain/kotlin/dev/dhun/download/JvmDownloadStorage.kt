package dev.dhun.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop (JVM) [DownloadStorage]. Downloads live under
 * `<root>/audio/<trackId>.<ext>` and artwork under `<root>/art/<trackId>.jpg`.
 * ADR-006 uses `<user home>/.dhun/downloads` on desktop; the caller supplies
 * [root]. Atomic commit is a rename (cross-device fallback to copy+delete).
 */
class JvmDownloadStorage(override val root: String) : DownloadStorage {

    override fun audioPath(trackId: String, mimeType: String): String =
        File(File(root, "audio"), "${safeTrackId(trackId)}.${extensionForMimeType(mimeType)}").absolutePath

    override fun artPath(trackId: String): String =
        File(File(root, "art"), "${safeTrackId(trackId)}.jpg").absolutePath

    override suspend fun size(path: String): Long = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.isFile) f.length() else 0L
    }

    override suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).exists() }

    override suspend fun append(path: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.appendBytes(bytes)
    }

    override suspend fun commit(part: String, final: String): Unit = withContext(Dispatchers.IO) {
        val p = File(part)
        val f = File(final)
        if (!p.exists()) return@withContext
        f.parentFile?.mkdirs()
        if (f.exists()) f.delete()
        if (!p.renameTo(f)) {
            p.copyTo(f, overwrite = true)
            p.delete()
        }
    }

    override suspend fun delete(path: String): Unit = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        val f = File(path)
        if (f.exists()) f.delete()
    }

    override suspend fun ensureDirExists(path: String): Unit = withContext(Dispatchers.IO) {
        File(path).parentFile?.mkdirs()
    }
}
