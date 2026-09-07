package dev.dhun.android.shortcuts

import android.content.Intent

/**
 * Launcher shortcut contract for DHUN (Phase 13 static shortcuts + Phase 15
 * dynamic shortcut).
 *
 * Static shortcuts are declared in `res/xml/shortcuts.xml`; they target
 * `MainActivity` carrying one string extra ([EXTRA_SHORTCUT_ACTION]). The
 * mapping extra-value -> [ShortcutAction] lives here — in pure code — so it
 * can be unit-tested without an emulator (the XML side is pinned by
 * `StaticShortcutsXmlTest`, which fails if the two ever drift apart).
 *
 * [NOW_PLAYING] is dynamic-only (published by [NowPlayingShortcutSync] as the
 * queue advances) and intentionally has NO static declaration — a static and
 * a dynamic shortcut with the same id would shadow each other.
 */
enum class ShortcutAction { SEARCH, RESUME, LIBRARY, NOW_PLAYING }

object ShortcutIntents {

    /** Extra key on the launch Intent delivered to `MainActivity`. */
    const val EXTRA_SHORTCUT_ACTION = "dev.dhun.android.extra.SHORTCUT_ACTION"

    // Extra values — ALSO referenced by res/xml/shortcuts.xml. Renaming one
    // here without the XML (or vice versa) is a regression the tests catch.
    const val VALUE_SEARCH = "search"
    const val VALUE_RESUME = "resume"
    const val VALUE_LIBRARY = "library"

    /** Dynamic-only extra value + shortcut id ("Now playing"). */
    const val VALUE_NOW_PLAYING = "now-playing"
    const val DYNAMIC_ID_NOW_PLAYING = "now-playing"

    /**
     * Maps the raw extra string to an action.
     * @return null for absent/unknown values (unknown values are ignored
     *   rather than crashing — a misbehaving launcher must not break launch).
     */
    fun actionFromExtra(value: String?): ShortcutAction? = when (value) {
        VALUE_SEARCH -> ShortcutAction.SEARCH
        VALUE_RESUME -> ShortcutAction.RESUME
        VALUE_LIBRARY -> ShortcutAction.LIBRARY
        else -> null
    }

    /** Extracts and maps the shortcut extra from a launch Intent. */
    fun actionFrom(intent: Intent?): ShortcutAction? =
        actionFromExtra(intent?.getStringExtra(EXTRA_SHORTCUT_ACTION))
}
