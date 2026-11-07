package dev.dhun.innertube

import dev.dhun.core.ArtistCredit
import kotlinx.serialization.json.JsonElement

/**
 * Multi-artist credits for a track row.
 *
 * ## The defect this exists to fix
 *
 * Providers describe a collaboration as ONE string — `artistName = "A & B"`,
 * `"A, B, C"`, `"A feat. B"` — alongside a single `artistId`, which is only the
 * **first** credited channel. The Full Player rendered that whole string as one
 * clickable target pointing at `artistId`, so opening a collaboration silently
 * landed on one artist and the other credits were unreachable.
 *
 * ## What this does, and what it refuses to do
 *
 * It pairs the *linked* names the response actually gave us (each run of the
 * byline carries its own `browseEndpoint.browseId`) with the display string.
 * When the byline yields several distinct channel ids, every credited name
 * becomes its own navigable credit.
 *
 * When it does not — a single linked id for a multi-name string, or a
 * byline with no links at all — it does **not** invent a target. Splitting
 * "A & B" into two credits that both point at A's channel would be a worse
 * defect than the one being fixed: it looks correct and navigates wrong.
 * Those rows fall back to a single credit, and the display name is left
 * exactly as the provider wrote it.
 */
internal object ArtistCredits {

    /** Separators providers use between co-artists in one display string. */
    private val separators = Regex(
        """\s*(?:,|;|/|&|\+|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\bvs\.?\b|\bx\b)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Distinct channel ids in source order from a byline/subtitle subtree.
     *
     * [browseIdsOf] answers "which ids appear anywhere"; this answers "which
     * ids appear, each once, in the order the reader sees them", which is what
     * a per-name credit list needs. Non-artist ids (albums `MPRE…`, playlists
     * `VL…`) are filtered out.
     */
    fun linkedArtistIds(node: JsonElement?): List<String> =
        linkedCredits(node).mapNotNull { it.id }

    fun linkedCredits(node: JsonElement?): List<ArtistCredit> {
        val runs = mutableListOf<Pair<String, String>>()
        collectBrowseRuns(node, runs)
        return runs
            .filter { (name, id) -> id.startsWith("UC") && name.isNotBlank() }
            .distinctBy { it.second }
            .map { (name, id) -> ArtistCredit(name = name, id = id) }
    }

    /** Names in a display string, in order, without inventing any. */
    fun splitNames(displayName: String): List<String> =
        displayName.split(separators)
            .map { it.trim().trim('-', '\u2013', '\u2014') }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Resolves the credits for one row.
     *
     * @param displayName the provider's own joined string (`Track.artistName`).
     * @param linked the credits mined from the row's byline subtree, if any.
     * @param fallbackId the single id the row already resolves
     *   (`Track.artistId`); used only to make a *single* credit navigable.
     */
    fun resolve(
        displayName: String,
        linked: List<ArtistCredit>,
        fallbackId: String?,
    ): List<ArtistCredit> {
        if (displayName.isBlank()) return emptyList()
        // Several distinct linked artists: trust the provider's own pairing.
        if (linked.size > 1) {
            // Only adopt the linked list when it accounts for the whole credit
            // string; a byline that links one name and mentions another in plain
            // text must not silently drop the plain-text artist.
            val names = splitNames(displayName)
            val covered = linked.all { l -> names.any { it.equals(l.name, ignoreCase = true) || it.contains(l.name, ignoreCase = true) } }
            if (covered) return linked
        }
        // One credit: the joined display name, navigable when an id is known.
        return listOf(ArtistCredit(name = displayName, id = fallbackId ?: linked.firstOrNull()?.id))
    }

    /** A browse id is only usable as an artist page when it is a channel id. */
    fun isArtistBrowseId(id: String?): Boolean = id != null && id.startsWith("UC")

    /** Convenience for parsers: the id of the first linked channel, if any. */
    fun firstLinkedId(node: JsonElement?): String? =
        linkedCredits(node).firstOrNull()?.id
}
