package dev.dhun.data

/**
 * Every persisted setting key, in one place. Values are strings in the DB;
 * [SettingsRepository] typed helpers do the conversion. Defaults live here
 * too so every screen agrees on them.
 */
object SettingsKeys {
    /** "low" | "medium" | "high" */
    const val AUDIO_QUALITY = "audio_quality"
    const val AUDIO_QUALITY_DEFAULT = "high"

    /** "dark" | "light" | "system" — v1 ships dark-first. */
    const val THEME = "theme"
    const val THEME_DEFAULT = "dark"

    /** "artwork" (dynamic palette) | "static" */
    const val ACCENT_MODE = "accent_mode"
    const val ACCENT_MODE_DEFAULT = "artwork"

    const val LYRICS_ENABLED = "lyrics_enabled"
    const val LYRICS_ENABLED_DEFAULT = true

    /** Audio/metadata cache budget in MB. 0 = unlimited. */
    const val CACHE_SIZE_MB = "cache_size_mb"
    const val CACHE_SIZE_MB_DEFAULT = 1024

    /** ISO-3166 alpha-2 for InnerTube `gl`. */
    const val COUNTRY_CODE = "country_code"
    const val COUNTRY_CODE_DEFAULT = "US"

    const val EXPLICIT_CONTENT = "explicit_content"
    const val EXPLICIT_CONTENT_DEFAULT = true

    /** Desktop: minimize to tray on close (Phase 12). */
    const val CLOSE_TO_TRAY = "close_to_tray"
    const val CLOSE_TO_TRAY_DEFAULT = true

    /** Whether to restore the last queue on cold start. */
    const val RESUME_ON_LAUNCH = "resume_on_launch"
    const val RESUME_ON_LAUNCH_DEFAULT = true

    /** Desktop window geometry, "x,y,w,h" — see Phase 12. */
    const val WINDOW_GEOMETRY = "window_geometry"

    val all: List<String> = listOf(
        AUDIO_QUALITY, THEME, ACCENT_MODE, LYRICS_ENABLED, CACHE_SIZE_MB, COUNTRY_CODE,
        EXPLICIT_CONTENT, CLOSE_TO_TRAY, RESUME_ON_LAUNCH, WINDOW_GEOMETRY,
    )
}
