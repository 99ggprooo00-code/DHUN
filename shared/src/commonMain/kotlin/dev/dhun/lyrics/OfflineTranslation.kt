package dev.dhun.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Local inference only. Implementations must never upload text or download models here.
 * Model acquisition/verification and explicit download consent belong outside this boundary.
 * A null result is an untranslated line, not permission to use a remote fallback.
 */
interface OfflineTranslationProvider {
    /** Stable runtime + model revision, NOT a credential or a filesystem path. */
    val modelId: String
    val languages: Set<String>
    suspend fun translate(source: String, target: String, lines: List<String>): List<String?>
}

/** Text-only overlay; no timestamps, word boundaries, agents or original text are rewritten. */
data class TranslationDocument(val trackId: String, val lines: List<String>)
data class TranslationOverlay(
    val document: TranslationDocument,
    val modelId: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    /** Exactly one slot per original line; null means unavailable. */
    val lines: List<String?>,
)

sealed interface OfflineTranslationUpdate {
    data object NeedsManualApproval : OfflineTranslationUpdate
    data object UnsupportedLanguage : OfflineTranslationUpdate
    data object InputTooLarge : OfflineTranslationUpdate
    data class Progress(val overlay: TranslationOverlay, val finished: Boolean) : OfflineTranslationUpdate
}

/** Cold, manual-only pipeline, not yet an enabled player feature.
 * The caller must cancel collection when track/source/target changes, and provide a current
 * snapshot predicate as a second stale-result guard. Cache is bounded, process-memory-only;
 * no lyrics or translations are added to settings, disk, diagnostics or network requests.
 */
class OfflineTranslationEngine(
    private val provider: OfflineTranslationProvider,
    private val cacheCapacity: Int = 8,
    private val batchSize: Int = 8,
    private val batchTimeoutMs: Long = 15_000,
) {
    init {
        require(cacheCapacity in 1..16)
        require(batchSize in 1..16)
        require(batchTimeoutMs in 1..60_000)
        require(provider.modelId.isNotBlank() && provider.modelId.length <= 200)
    }
    private data class Key(val document: TranslationDocument, val model: String, val source: String, val target: String)
    private val cacheLock = Mutex()
    private val inferenceLock = Mutex()
    private val cache = linkedMapOf<Key, TranslationOverlay>()
    private var generation = 0L

    /** Also invalidates in-flight work so it cannot repopulate a cleared cache. */
    suspend fun clearCache() = cacheLock.withLock { generation++; cache.clear() }

    fun translate(
        document: TranslationDocument,
        sourceLanguage: String,
        targetLanguage: String,
        manuallyApproved: Boolean = false,
        isCurrent: () -> Boolean,
    ): Flow<OfflineTranslationUpdate> {
        // Snapshot at request creation, not at collection: callers may own mutable lists.
        val snapshot = document.copy(lines = document.lines.take(513))
        val model = provider.modelId
        return flow {
            if (!manuallyApproved) {
                emit(OfflineTranslationUpdate.NeedsManualApproval)
                return@flow
            }
            val languagePattern = Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*")
            if (!languagePattern.matches(sourceLanguage) || !languagePattern.matches(targetLanguage) ||
                sourceLanguage !in provider.languages || targetLanguage !in provider.languages) {
                emit(OfflineTranslationUpdate.UnsupportedLanguage)
                return@flow
            }
            if (snapshot.trackId.isBlank() || snapshot.trackId.length > 512 || snapshot.lines.size > 512 ||
                snapshot.lines.any { it.length > 2_048 } || snapshot.lines.sumOf { it.length } > 32_000) {
                emit(OfflineTranslationUpdate.InputTooLarge)
                return@flow
            }
            val epoch = cacheLock.withLock { generation }
            suspend fun checkCurrent() {
                currentCoroutineContext().ensureActive()
                if (!isCurrent() || provider.modelId != model || cacheLock.withLock { generation != epoch }) {
                    throw CancellationException("Translation request superseded")
                }
            }
            checkCurrent()
            val key = Key(snapshot, model, sourceLanguage, targetLanguage)
            val cached = cacheLock.withLock {
                cache.remove(key)?.also { cache[key] = it }
            }
            if (cached != null) {
                checkCurrent()
                // Copies prevent a downstream mutable-list cast from poisoning cached overlays.
                emit(OfflineTranslationUpdate.Progress(cached.detached(), true))
                return@flow
            }
            val translated = MutableList<String?>(snapshot.lines.size) { null }
            val pending = snapshot.lines.indices.filter { snapshot.lines[it].isNotBlank() }
            var outputChars = 0
            for (indices in pending.chunked(batchSize)) {
                checkCurrent()
                val input = indices.map { snapshot.lines[it] }
                val output = if (sourceLanguage == targetLanguage) input else inferenceLock.withLock {
                    // Check again after waiting for another inference to release the runtime.
                    checkCurrent()
                    try {
                        withTimeoutOrNull(batchTimeoutMs) { provider.translate(sourceLanguage, targetLanguage, input) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null } // Never expose exception messages containing text/paths.
                }
                checkCurrent()
                if (output != null && output.size == indices.size) {
                    indices.forEachIndexed { offset, index ->
                        val text = output[offset]
                        if (text != null && text.isNotBlank() && text.length <= 4_096 && outputChars + text.length <= 64_000) {
                            translated[index] = text
                            outputChars += text.length
                        }
                    }
                }
                emit(OfflineTranslationUpdate.Progress(
                    TranslationOverlay(snapshot.copy(lines = snapshot.lines.toList()), model, sourceLanguage, targetLanguage, translated.toList()),
                    false,
                ))
            }
            checkCurrent()
            val overlay = TranslationOverlay(snapshot, model, sourceLanguage, targetLanguage, translated.toList())
            // Partial failures are deliberately not cached: a later manual request may recover.
            if (pending.all { translated[it] != null }) cacheLock.withLock {
                if (generation != epoch) throw CancellationException("Translation cache cleared")
                if (cache.size >= cacheCapacity) cache.remove(cache.keys.first())
                cache[key] = overlay.detached()
            }
            checkCurrent()
            emit(OfflineTranslationUpdate.Progress(overlay.detached(), true))
        }
    }

    private fun TranslationOverlay.detached() = copy(document = document.copy(lines = document.lines.toList()), lines = lines.toList())
}
