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

    /** "dark" | "light" | "black" | "system" — v1 ships dark-first. */
    const val THEME = "theme"
    const val THEME_DEFAULT = "dark"

    /**
     * Accent hue id — one of [dev.dhun.design.DhunAccent.id]
     * ("brand" default). Persisted by the S4 Settings screen; unknown ids
     * fall back to the default at read time rather than crashing.
     */
    const val ACCENT = "accent"
    const val ACCENT_DEFAULT = "brand"

    /** "artwork" (dynamic palette) | "static" */
    const val ACCENT_MODE = "accent_mode"
    const val ACCENT_MODE_DEFAULT = "artwork"

    const val LYRICS_ENABLED = "lyrics_enabled"
    const val LYRICS_ENABLED_DEFAULT = true

    /**
     * Phase 14 audio-segment cache budget in MB (Android Media3 SimpleCache
     * LRU). 0 = effectively unlimited (capped internally). Default 1024.
     * Applies on next process start if changed while running.
     */
    const val CACHE_SIZE_MB = "cache_size_mb"
    const val CACHE_SIZE_MB_DEFAULT = 1024

    /** ISO-3166 alpha-2 for InnerTube `gl`. */
    const val COUNTRY_CODE = "country_code"
    const val COUNTRY_CODE_DEFAULT = "US"

    const val RECORD_LISTENING_HISTORY = "record_listening_history"
    const val RECORD_LISTENING_HISTORY_DEFAULT = true

    const val EXPLICIT_CONTENT = "explicit_content"
    const val EXPLICIT_CONTENT_DEFAULT = true

    /** Hide the desktop window on X while keeping playback alive. */
    const val CLOSE_TO_TRAY = "close_to_tray"
    const val CLOSE_TO_TRAY_DEFAULT = true

    /** Whether to restore the last queue on cold start. */
    const val RESUME_ON_LAUNCH = "resume_on_launch"
    const val RESUME_ON_LAUNCH_DEFAULT = true

    /** 0–100 multiplier on the 64dp now-playing blur (API 31+ / desktop). */
    const val BACKDROP_BLUR = "backdrop_blur"
    const val BACKDROP_BLUR_DEFAULT = 100

    /** 0–100 artwork brightness on every platform. */
    const val BACKDROP_BRIGHTNESS = "backdrop_brightness"
    const val BACKDROP_BRIGHTNESS_DEFAULT = 55

    /**
     * Background blur, as three switches (see `dev.dhun.design.background`):
     * the master [BACKGROUND_BLUR_ENABLED], the *kind* of blur
     * [BACKGROUND_DYNAMIC_BLUR], and the independent colour switch
     * [BACKGROUND_DYNAMIC_COLORS]. They default to the behaviour the app had
     * before the switches existed: blurred, artwork-responsive, artwork-coloured.
     */
    const val BACKGROUND_BLUR_ENABLED = "background_blur_enabled"
    const val BACKGROUND_BLUR_ENABLED_DEFAULT = true
    const val BACKGROUND_DYNAMIC_BLUR = "background_dynamic_blur"
    const val BACKGROUND_DYNAMIC_BLUR_DEFAULT = true
    const val BACKGROUND_DYNAMIC_COLORS = "background_dynamic_colors"
    const val BACKGROUND_DYNAMIC_COLORS_DEFAULT = true

    /**
     * One-off migration marker for the background settings. Version 0 (absent)
     * means the build predates the switches, so the legacy `BACKDROP_BLUR = 0`
     * row means "no blur" and is imported once. Bumping this constant is how a
     * future default change is applied without overwriting a user's choice.
     */
    const val BACKGROUND_PROFILE_VERSION = "background_profile_version"
    const val BACKGROUND_PROFILE_VERSION_CURRENT = 1

    /** Per-section background keys: `background_<section>_<field>`. */
    fun backgroundKey(sectionId: String, field: String): String = "background_${sectionId}_$field"

    const val BACKGROUND_FIELD_BLUR = "blur"
    const val BACKGROUND_FIELD_DYNAMIC_BLUR = "dynamic_blur"
    const val BACKGROUND_FIELD_DYNAMIC_COLORS = "dynamic_colors"
    const val BACKGROUND_FIELD_PRIMARY = "primary"
    const val BACKGROUND_FIELD_SECONDARY = "secondary"
    const val BACKGROUND_FIELD_TERTIARY = "tertiary"

    private val BACKGROUND_SECTION_KEY = Regex(
        "^background_(home|search|library|mini|player)_(blur|dynamic_blur|dynamic_colors|primary|secondary|tertiary)$",
    )

    /**
     * Per-section background keys are built from a section id, so they cannot
     * live in [all]. [UpdateSettingUseCase] still has to refuse typos, so it
     * asks this instead: the shape is validated, and an id that is not one of
     * the five sections is rejected exactly like an unknown key.
     */
    fun isBackgroundSectionKey(key: String): Boolean = BACKGROUND_SECTION_KEY.matches(key)

    // Internal test-build visual tuning. The public build ignores persisted values.
    const val TUNE_PAGE_BACKDROP = "tune_page_backdrop"
    const val TUNE_LIST_ROWS = "tune_list_rows"
    const val TUNE_READING = "tune_reading"
    const val TUNE_CHIPS = "tune_chips"
    const val TUNE_INPUTS = "tune_inputs"
    const val TUNE_PLAYER = "tune_player"
    const val TUNE_LYRICS = "tune_lyrics"
    const val TUNE_MINI_PLAYER = "tune_mini_player"
    const val TUNE_NAVIGATION = "tune_navigation"
    const val TUNE_QUEUE_SHEET = "tune_queue_sheet"
    const val TUNE_QUEUE_ROWS = "tune_queue_rows"
    const val TUNE_TONAL_SURFACES = "tune_tonal_surfaces"
    const val TUNE_PROFILE_VERSION = "tune_profile_version"

    const val SEEK_STEP_SECONDS = "seek_step_seconds"
    const val SEEK_STEP_SECONDS_DEFAULT = 5
    const val AUTOPLAY_RADIO_ENABLED = "autoplay_radio_enabled"
    const val AUTOPLAY_RADIO_ENABLED_DEFAULT = true
    const val LYRICS_PREFER_SYNCED = "lyrics_prefer_synced"
    const val LYRICS_PREFER_SYNCED_DEFAULT = true
    const val LYRICS_ROMANIZATION_ENABLED = "lyrics_romanization_enabled"
    const val LYRICS_ROMANIZATION_MODE = "lyrics_romanization_mode"
    const val LYRICS_AUTO_SCROLL = "lyrics_auto_scroll"
    const val LYRICS_AUTO_SCROLL_DEFAULT = true
    /** Per-track timing corrections are dynamic keys, not a global playback preference. */
    const val LYRICS_MATCH_PREFIX = "lyrics_match:"
    fun lyricsMatchKey(trackId: String): String = LYRICS_MATCH_PREFIX + trackId

    const val LYRICS_OFFSET_PREFIX = "lyrics_offset_ms:"
    fun lyricsOffsetKey(trackId: String): String = LYRICS_OFFSET_PREFIX + trackId

    const val LYRICS_TEXT_SIZE = "lyrics_text_size"
    const val LYRICS_TEXT_SIZE_DEFAULT = "default"
    const val LYRICS_PREFERRED_PROVIDER = "lyrics_preferred_provider"
    const val LYRICS_PREFERRED_PROVIDER_DEFAULT = "auto"
    const val LYRICS_PROVIDER_ORDER = "lyrics_provider_order"
    const val LYRICS_PROVIDER_ORDER_DEFAULT = "lrclib,ytm,lrclib_search,kugou"
    const val PERSIST_SHUFFLE_REPEAT = "persist_shuffle_repeat"
    const val PERSIST_SHUFFLE_REPEAT_DEFAULT = true
    const val PAUSE_ON_MUTE_OR_ZERO_VOLUME = "pause_on_mute_or_zero_volume"
    const val PAUSE_ON_MUTE_OR_ZERO_VOLUME_DEFAULT = false

    const val EQUALIZER_ENABLED = "equalizer_enabled"
    const val EQUALIZER_PRESET = "equalizer_preset"
    const val EQUALIZER_PREAMP_DB = "equalizer_preamp_db"
    const val EQUALIZER_GAINS_DB = "equalizer_gains_db"

    /** Desktop window geometry, "x,y,w,h" — see Phase 12. */
    const val WINDOW_GEOMETRY = "window_geometry"

    val all: List<String> = listOf(
        AUDIO_QUALITY, THEME, ACCENT, ACCENT_MODE, LYRICS_ENABLED, CACHE_SIZE_MB, COUNTRY_CODE,
        RECORD_LISTENING_HISTORY, EXPLICIT_CONTENT, CLOSE_TO_TRAY, RESUME_ON_LAUNCH, WINDOW_GEOMETRY,
        BACKDROP_BLUR, BACKDROP_BRIGHTNESS, SEEK_STEP_SECONDS, AUTOPLAY_RADIO_ENABLED,
        BACKGROUND_BLUR_ENABLED, BACKGROUND_DYNAMIC_BLUR, BACKGROUND_DYNAMIC_COLORS,
        BACKGROUND_PROFILE_VERSION,
        TUNE_PAGE_BACKDROP, TUNE_LIST_ROWS, TUNE_READING, TUNE_CHIPS, TUNE_INPUTS,
        TUNE_PLAYER, TUNE_LYRICS, TUNE_MINI_PLAYER, TUNE_NAVIGATION, TUNE_QUEUE_SHEET, TUNE_QUEUE_ROWS, TUNE_TONAL_SURFACES, TUNE_PROFILE_VERSION,
        LYRICS_ROMANIZATION_ENABLED, LYRICS_ROMANIZATION_MODE,
        LYRICS_PREFER_SYNCED, LYRICS_AUTO_SCROLL, LYRICS_TEXT_SIZE, LYRICS_PREFERRED_PROVIDER,
        LYRICS_PROVIDER_ORDER, PERSIST_SHUFFLE_REPEAT, PAUSE_ON_MUTE_OR_ZERO_VOLUME,
        EQUALIZER_ENABLED, EQUALIZER_PRESET, EQUALIZER_PREAMP_DB, EQUALIZER_GAINS_DB,
    )
}
