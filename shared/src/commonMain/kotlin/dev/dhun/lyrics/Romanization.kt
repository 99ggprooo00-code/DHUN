package dev.dhun.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** These are transliteration modes, not claims of language detection or translation. */
enum class RomanizationMode(val id: String, val label: String, internal val transformId: String?) {
    AUTO("auto", "Auto (conservative)", null),
    JAPANESE_KANA("ja", "Japanese kana (Kanji unchanged)", "Hiragana-Latin; Katakana-Latin"),
    KOREAN("ko", "Korean", "Hangul-Latin"),
    CHINESE("zh", "Chinese (Mandarin pinyin)", "Han-Latin"),
    DEVANAGARI("deva", "Devanagari (script transliteration)", "Devanagari-Latin"),
    GURMUKHI("guru", "Gurmukhi (script transliteration)", "Gurmukhi-Latin"),
    RUSSIAN("ru", "Russian (BGN)", "Russian-Latin/BGN"),
    UKRAINIAN("uk", "Ukrainian (BGN)", "Ukrainian-Latin/BGN");

    companion object {
        fun fromId(id: String?): RomanizationMode = entries.firstOrNull { it.id == id } ?: AUTO
        fun detect(text: String): RomanizationMode? {
            val scripts = mutableSetOf<String>()
            for (char in text) {
                if (!char.isLetter()) continue
                scripts += when (char.code) {
                    in 0x3040..0x30ff -> "kana"
                    in 0x3400..0x9fff, in 0xf900..0xfaff -> "han"
                    in 0x1100..0x11ff, in 0x3130..0x318f, in 0xac00..0xd7af -> "hangul"
                    in 0x0900..0x097f -> "deva"
                    in 0x0a00..0x0a7f -> "guru"
                    in 0x0041..0x024f, in 0x1e00..0x1eff -> "latin"
                    else -> "unknown"
                }
            }
            scripts.remove("latin")
            return when (scripts) {
                setOf("kana"), setOf("kana", "han") -> JAPANESE_KANA
                setOf("hangul") -> KOREAN
                setOf("deva") -> DEVANAGARI
                setOf("guru") -> GURMUKHI
                // Han alone and Cyrillic are language-ambiguous: require explicit override.
                else -> null
            }
        }
    }
}

internal expect fun platformTransliterate(text: String, transformId: String): String

/** One bounded engine per player; ICU instances never escape a worker invocation.
 * Source strings/models are immutable. Unknown, oversized or failed text is left unchanged.
 */
class RomanizationEngine(
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val transform: (String, String) -> String = ::platformTransliterate,
    private val capacity: Int = 256,
) {
    init { require(capacity in 1..1024) }
    private val mutex = Mutex()
    private val cache = linkedMapOf<Pair<String, RomanizationMode>, String>()

    suspend fun romanize(text: String, mode: RomanizationMode): String = withContext(worker) {
        if (text.length > 4_096 || text.isBlank()) return@withContext text
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            val key = text to mode
            cache.remove(key)?.let { cached -> cache[key] = cached; return@withLock cached }
            val selected = if (mode == RomanizationMode.AUTO) RomanizationMode.detect(text) else mode
            val converted = try {
                selected?.transformId?.let { transform(text, it) } ?: text
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { text }
            currentCoroutineContext().ensureActive()
            // Also bound output expansion; a malformed transform cannot fill memory/cache.
            val value = converted.takeIf { it.length <= 16_384 && it.isNotBlank() } ?: text
            if (cache.size >= capacity) cache.remove(cache.keys.first())
            cache[key] = value
            value
        }
    }
}
