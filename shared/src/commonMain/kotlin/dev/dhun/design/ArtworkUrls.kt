package dev.dhun.design

/**
 * Artwork URL resolution tiers (pure Kotlin — unit-tested).
 *
 * YouTube list feeds serve tiny art (`w60-h60` / `w120-h120` thumbnails,
 * smallest-first arrays), and the old parser kept the smallest entry, so
 * the Now Playing stage — rendering at ~0.82 of the screen width —
 * upscaled a postage stamp. Two fixes combine here:
 *
 * - parsers keep the LARGEST thumbnail entry, pinned to the list tier;
 * - [nowPlaying] re-requests the same image at the 1024 tier for the
 *   FullPlayer stage + backdrop (one Coil key, fetched once).
 *
 * The `googleusercontent`/`ggpht` image proxy honors arbitrary `w/h`
 * sizes, so the rewrite is safe there. Non-proxy hosts (`i.ytimg.com`
 * `hqdefault` etc.) are returned untouched — blindly rewriting those
 * risks 404s for videos without maxres art, and Coil's error fallback
 * would then show a placeholder instead of a playable thumbnail.
 */
object ArtworkUrls {
    /** Card / row / mini-player tier: crisp at 160–280dp, bounded bytes. */
    const val LIST_SIZE_PX = 544

    /** Now Playing stage + backdrop tier. */
    const val NOW_PLAYING_SIZE_PX = 1024

    private val sizedParam = Regex("w\\d+-h\\d+")
    private val avatarParam = Regex("=s\\d+(-c)?")

    /**
     * Normalizes [url] for image loading: `//` → `https://`, and proxy-host
     * size params rewritten to [sizePx]. Null/blank pass through so callers
     * keep their existing placeholder behavior.
     */
    fun list(url: String?, sizePx: Int = LIST_SIZE_PX): String? {
        if (url.isNullOrBlank()) return url
        var out = url.trim()
        if (out.startsWith("//")) out = "https:$out"
        if (!isProxyHost(out)) return out
        if (sizedParam.containsMatchIn(out)) {
            out = sizedParam.replace(out, "w${sizePx}-h${sizePx}")
        }
        avatarParam.find(out)?.let { match ->
            // Preserve the crop flag (`=s176-c-k…` → `=s544-c-k…`).
            val crop = match.groupValues.getOrNull(1).orEmpty()
            out = avatarParam.replace(out, "=s${sizePx}$crop")
        }
        return out
    }

    /** Full-resolution tier for the Now Playing artwork + backdrop. */
    fun nowPlaying(url: String?): String? = list(url, NOW_PLAYING_SIZE_PX)

    private fun isProxyHost(url: String): Boolean =
        url.contains("googleusercontent.com") || url.contains("ggpht.com")
}
