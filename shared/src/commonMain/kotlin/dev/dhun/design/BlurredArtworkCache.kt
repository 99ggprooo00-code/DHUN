package dev.dhun.design

/**
 * Track-keyed cache for the FullPlayer background pipeline (ADR-002 P4).
 *
 * Contract: blur / tint work runs **once per track (or artwork URL) change**,
 * never every frame. Compose currently applies [androidx.compose.ui.draw.blur]
 * on the artwork layer; this holder records which key was last prepared so
 * future bitmap-pipeline work (downscale → offscreen blur → tint) can skip
 * redundant work and so tests can assert the once-per-track rule.
 *
 * Material 3 only — no Liquid Glass, no continuous full-res reblur.
 */
object BlurredArtworkCache {
    @Volatile
    private var preparedKey: String? = null

    /** True when [key] was the last value passed to [markPrepared]. */
    fun isPrepared(key: String): Boolean = preparedKey == key

    fun markPrepared(key: String) {
        if (key.isBlank()) return
        preparedKey = key
    }

    fun clear() {
        preparedKey = null
    }

    /** Stable cache key from a track's artwork URL or id. */
    fun keyFor(thumbnailUrl: String?, trackId: String?): String =
        thumbnailUrl?.takeIf { it.isNotBlank() } ?: trackId.orEmpty()
}
