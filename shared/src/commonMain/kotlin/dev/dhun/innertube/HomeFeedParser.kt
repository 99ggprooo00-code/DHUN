package dev.dhun.innertube

import dev.dhun.core.DhunError
import dev.dhun.core.DhunException
import dev.dhun.core.HomeFeedPage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A Home continuation belongs to the vertical section list, NOT to one of its
 * horizontal shelves. Neither a recursive token search nor flattening every
 * response action preserves that ownership.
 */
internal fun parseHomeFeedPage(root: JsonObject): HomeFeedPage {
    val continuationContents = root.obj("continuationContents")
    continuationContents.obj("sectionListContinuation")?.let { return homePage(it) }

    // YouTube Music sometimes returns a shelf-specific continuation instead
    // of the generic section-list continuation. These objects have the same
    // contents/continuations contract as their renderer counterparts, but the
    // wrapper key is different. Normalize them before parsing; otherwise a
    // valid Home page falls through to the action error below.
    continuationContents.obj("musicShelfContinuation")?.let {
        return homePage(JsonObject(mapOf("musicShelfRenderer" to it)))
    }
    continuationContents.obj("musicPlaylistShelfContinuation")?.let {
        return homePage(JsonObject(mapOf("musicPlaylistShelfRenderer" to it)))
    }
    continuationContents.obj("musicCarouselShelfContinuation")?.let {
        return homePage(JsonObject(mapOf("musicCarouselShelfRenderer" to it)))
    }
    continuationContents.obj("musicImmersiveCarouselShelfContinuation")?.let {
        return homePage(JsonObject(mapOf("musicImmersiveCarouselShelfRenderer" to it)))
    }

    // A full page may also contain unrelated sidebar/shelf update commands.
    // Prefer its actual section list before looking at incremental actions.
    val contents = root.obj("contents")
    contents.obj("sectionListRenderer")?.let { return homePage(it) }
    val browse = contents.obj("singleColumnBrowseResultsRenderer")
        ?: contents.obj("twoColumnBrowseResultsRenderer")
    val tabs = browse.arr("tabs").orEmpty().mapNotNull { (it as? JsonObject).obj("tabRenderer") }
    val selected = tabs.firstOrNull { it.str("selected") == "true" } ?: tabs.firstOrNull()
    selected.obj("content").obj("sectionListRenderer")?.let { return homePage(it) }
    root.obj("sectionListRenderer")?.let { return homePage(it) }

    return homeActionPage(root)
}

private data class HomeAppend(val target: String?, val items: JsonArray)

private fun isHomeSectionRenderer(renderer: JsonObject?): Boolean =
    renderer.obj("musicCarouselShelfRenderer") != null ||
        renderer.obj("musicImmersiveCarouselShelfRenderer") != null ||
        renderer.obj("musicShelfRenderer") != null ||
        renderer.obj("musicPlaylistShelfRenderer") != null

/**
 * Shape-only diagnostics for a response that did not match the Home contract.
 * Keys are safe to report; values are intentionally never included because
 * continuation responses contain opaque cursors and tracking data.
 */
private fun homeShapeSummary(root: JsonObject): String {
    fun keys(objects: List<JsonObject>): String = objects
        .flatMap { it.keys }
        .distinct()
        .sorted()
        .joinToString(",")
        .ifEmpty { "-" }

    val actionEntries = listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands")
        .flatMap { root.arr(it).orEmpty() }
        .mapNotNull { it as? JsonObject }
    val commands = actionEntries.flatMap { action ->
        listOfNotNull(
            action.obj("appendContinuationItemsAction"),
            action.obj("reloadContinuationItemsCommand"),
        )
    }
    val continuationItems = commands.flatMap { it.arr("continuationItems").orEmpty() }
        .mapNotNull { it as? JsonObject }
    return "shape=top[${keys(listOf(root))}]" +
        ";continuation[${keys(listOfNotNull(root.obj("continuationContents")))}]" +
        ";actions[${keys(actionEntries)}]" +
        ";commands[${keys(commands)}]" +
        ";items[${keys(continuationItems)}]"
}

private fun homeActionPage(root: JsonObject): HomeFeedPage {
    val appends = listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands")
        .flatMap { root.arr(it).orEmpty() }
        .mapNotNull { entry ->
            val action = entry as? JsonObject ?: return@mapNotNull null
            val command = action.obj("appendContinuationItemsAction")
                ?: action.obj("reloadContinuationItemsCommand")
                ?: return@mapNotNull null
            val items = command.arr("continuationItems") ?: return@mapNotNull null
            HomeAppend(command.str("targetId")?.takeIf { it.isNotBlank() }, items)
        }.distinct()

    // Multiple commands for the SAME non-null target can split shelves and
    // their final cursor. Never merge anonymous commands or different targets:
    // that would let a horizontal shelf's continuation become Home's cursor.
    val groups = appends.filter { it.target != null }.groupBy { it.target }.values.toList() +
        appends.filter { it.target == null }.map { listOf(it) }
    val feedGroups = groups.filter { group ->
        group.any { append -> append.items.any { item ->
            isHomeSectionRenderer(item as? JsonObject)
        } }
    }
    val candidates = feedGroups.ifEmpty {
        // An empty page may legitimately advance its cursor, or end the feed.
        // Accept that only when its action group is unambiguous. Track rows
        // alone describe a horizontal shelf, not a vertical Home page.
        groups.filter { group -> group.all { append ->
            append.items.all { (it as? JsonObject).obj("continuationItemRenderer") != null }
        } }
    }
    if (candidates.size != 1) {
        val reason = if (candidates.isEmpty()) "no section list or Home continuation action" else "ambiguous Home continuation targets"
        throw DhunException(
            DhunError.Parse("Home response contained $reason; ${homeShapeSummary(root)}"),
        )
    }
    val pages = candidates.single().map { append -> homePage(JsonObject(mapOf("contents" to append.items))) }
    return HomeFeedPage(
        sections = pages.flatMap { it.sections },
        continuationToken = pages.last().continuationToken,
    )
}

private fun homePage(list: JsonObject): HomeFeedPage = HomeFeedPage(
    sections = parseHomeSections(list),
    continuationToken = homeListContinuation(list),
)

/** Look only at the list's own continuations / direct continuation items. */
private fun homeListContinuation(list: JsonObject): String? {
    list.arr("continuations").orEmpty().forEach { entry ->
        (entry as? JsonObject).obj("nextContinuationData").str("continuation")
            ?.takeIf { it.isNotBlank() }?.let { return it }
    }
    list.arr("contents").orEmpty().forEach { entry ->
        val item = (entry as? JsonObject).obj("continuationItemRenderer") ?: return@forEach
        val token = item.obj("continuationEndpoint").obj("continuationCommand").str("token")
            ?: item.obj("button").obj("buttonRenderer").obj("command").obj("continuationCommand").str("token")
        token?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}
