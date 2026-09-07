package dev.dhun.android.download

import android.content.Context
import dev.dhun.download.DownloadStorage
import dev.dhun.download.extensionForMimeType
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [DownloadStorage] (ADR-006). Downloads live in the app's internal
 * storage under `<filesDir>/downloads/audio/` (and `art/`), so they survive
 * process restarts without runtime storage permissions. [root] is pinned to
 * `context.filesDir` — uninstalling the app removes downloads with it.
 */
class AndroidDownloadStorage(
    context: Context,
) : DownloadStorage {

    override val root: String = File(context.filesDir, "downloads").absolutePath

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

    private fun safeTrackId(id: String): String =
        id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80)
}
