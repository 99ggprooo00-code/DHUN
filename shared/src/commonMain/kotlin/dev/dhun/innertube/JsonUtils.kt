package dev.dhun.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * kotlinx-serialization JSON traversal helpers.
 * InnerTube responses are deep, shape-shifting trees; these walkers are
 * deliberately forgiving (return null, never throw) because YouTube adds
 * keys freely — strict paths belong in tests, not in production walkers.
 */

internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject

internal fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray

internal fun JsonObject?.str(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonObject?.long(key: String): Long? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

/** Walks [path] through objects and (single-element) arrays. */
internal fun descend(root: JsonElement?, path: List<String>): JsonElement? {
    var cur: JsonElement? = root
    for (key in path) {
        cur = when (cur) {
            is JsonObject -> cur[key]
            is JsonArray -> (cur.firstOrNull() as? JsonObject)?.get(key)
            else -> return null
        } ?: return null
    }
    return cur
}

/** runs[0].text at the end of [path]. */
internal fun JsonObject?.firstRunText(vararg path: String): String? =
    ((descend(this, path.toList()) as? JsonArray)?.firstOrNull() as? JsonObject)
        ?.str("text")

/** All runs joined (subtitle strings like "Queen • A Night at the Opera • 1975"). */
internal fun JsonObject?.allRunsText(vararg path: String): String? =
    (descend(this, path.toList()) as? JsonArray)
        ?.joinToString("") { run ->
            ((run as? JsonObject)?.str("text") ?: "")
        }?.trim()
        ?.takeIf { it.isNotEmpty() }

/** Depth-first collection of every object stored under [key] anywhere below. */
internal fun JsonElement.collectObjects(key: String, out: MutableList<JsonObject>) {
    when (this) {
        is JsonObject -> {
            (this[key] as? JsonObject)?.let(out::add)
            for (v in values) v.collectObjects(key, out)
        }
        is JsonArray -> for (v in this) v.collectObjects(key, out)
        else -> {}
    }
}

/** Depth-first collection of every primitive string matching [regex]. */
internal fun JsonElement.collectStringsMatching(regex: Regex, out: MutableList<String>) {
    when (this) {
        is JsonObject -> for (v in values) v.collectStringsMatching(regex, out)
        is JsonArray -> for (v in this) v.collectStringsMatching(regex, out)
        is JsonPrimitive -> contentOrNull?.let { if (regex.containsMatchIn(it)) out.add(it) }
        else -> {}
    }
}
