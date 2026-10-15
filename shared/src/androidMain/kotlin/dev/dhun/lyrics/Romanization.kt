package dev.dhun.lyrics

import com.ibm.icu.text.Transliterator

/** ICU4J on both platforms avoids Android API-level-dependent transform differences. */
internal actual fun platformTransliterate(text: String, transformId: String): String =
    Transliterator.getInstance(transformId).transliterate(text)
