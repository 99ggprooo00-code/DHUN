package dev.dhun.presentation.settings

import dev.dhun.data.SettingsKeys
import dev.dhun.design.background.BackgroundColorCodec
import dev.dhun.design.background.BackgroundComponent
import dev.dhun.design.background.BackgroundState
import dev.dhun.design.background.ComponentBackgrounds
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase

/**
 * Reads, writes and migrates the background preferences.
 *
 * This is the only place that knows how a [BackgroundState] maps onto settings
 * rows, so the Settings screen and the two host entry points (which must apply
 * the state *before the first frame*) cannot drift apart. It lives in the
 * presentation layer because that is the lowest layer allowed to see both the
 * data layer's key strings and the design layer's state.
 *
 * Two switches and four per-surface colours, nothing else. The two earlier
 * shapes of this feature — a bare 0–100 slider, then five sections with a
 * three-layer palette each — are imported once each and then never read again.
 */
object BackgroundPreferences {

    /**
     * Every persisted key that belongs to the background feature, in the order
     * Reset removes them (globals, then current component rows, then the legacy
     * rows an older install may still carry).
     */
    fun allKeys(): List<String> = buildList {
        add(SettingsKeys.BACKGROUND_BLUR_ENABLED)
        add(SettingsKeys.BACKGROUND_DYNAMIC_BLUR)
        add(SettingsKeys.BACKGROUND_DYNAMIC_COLORS)
        add(SettingsKeys.BACKGROUND_PROFILE_VERSION)
        for (component in BackgroundComponent.entries) {
            for (field in FIELDS) add(SettingsKeys.backgroundComponentKey(component.id, field))
        }
        // Legacy version-1 rows: cleared too, so "reset everything" cannot leave
        // a future import reading a stale per-section palette.
        for (section in LEGACY_SECTIONS) {
            for (field in LEGACY_FIELDS) add(SettingsKeys.backgroundKey(section, field))
        }
    }

    private val FIELDS = listOf(
        SettingsKeys.BACKGROUND_FIELD_BLUR,
        SettingsKeys.BACKGROUND_FIELD_FOLLOW_ARTWORK,
        SettingsKeys.BACKGROUND_FIELD_COLOR,
    )

    /** Version-1 section ids that owned a palette. */
    private val LEGACY_SECTIONS = listOf("home", "search", "library", "mini", "player")

    /**
     * The version-1 sections that are now one surface. Home/Search/Library each
     * had their own rows, but they are the same page background physically, so
     * the first one the user actually saved becomes *Main pages*.
     */
    private val LEGACY_PAGE_SECTIONS = listOf("home", "search", "library")

    /** The version-1 rows that could carry a colour a user picked. */
    private val LEGACY_COLOUR_FIELDS = listOf(
        SettingsKeys.BACKGROUND_FIELD_SECONDARY,
        SettingsKeys.BACKGROUND_FIELD_PRIMARY,
    )

    private val LEGACY_FIELDS = listOf(
        SettingsKeys.BACKGROUND_FIELD_BLUR,
        SettingsKeys.BACKGROUND_FIELD_DYNAMIC_BLUR,
        SettingsKeys.BACKGROUND_FIELD_DYNAMIC_COLORS,
        SettingsKeys.BACKGROUND_FIELD_PRIMARY,
        SettingsKeys.BACKGROUND_FIELD_SECONDARY,
        SettingsKeys.BACKGROUND_FIELD_TERTIARY,
    )

    /**
     * Read the whole feature: migrate once, then return one coherent snapshot.
     *
     * The values are read into locals and returned as a single object on
     * purpose. Publishing as each key arrives meant a read that failed halfway
     * left the app on a mixture of stored values and defaults that the user
     * never chose — a state that then silently reverted on the next launch.
     */
    suspend fun read(get: GetSettingUseCase, update: UpdateSettingUseCase): BackgroundState {
        val version = get.int(SettingsKeys.BACKGROUND_PROFILE_VERSION, 0)
        // A migration is a *write*, and a write is not allowed to decide whether
        // the stored preferences can be read: on a full disk the old build failed
        // the entire load here, so every setting came back as its default and the
        // screen reported that nothing could be read. Every imported value is
        // therefore derived from rows that are left in place, and persisted on a
        // best-effort basis — if the marker write fails the import simply runs
        // again next launch.
        val importedBlur = if (version < SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT) {
            migrate(get, update, version)
        } else {
            null
        }

        val blurEnabled = importedBlur
            ?: get.boolean(SettingsKeys.BACKGROUND_BLUR_ENABLED, SettingsKeys.BACKGROUND_BLUR_ENABLED_DEFAULT)
        val adaptiveBlur = get.boolean(SettingsKeys.BACKGROUND_DYNAMIC_BLUR, SettingsKeys.BACKGROUND_DYNAMIC_BLUR_DEFAULT)
        val dynamicColors = get.boolean(SettingsKeys.BACKGROUND_DYNAMIC_COLORS, SettingsKeys.BACKGROUND_DYNAMIC_COLORS_DEFAULT)

        val components = BackgroundComponent.entries.associateWith { component ->
            ComponentBackgrounds(
                blurEnabled = get.boolean(
                    SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_BLUR),
                    true,
                ),
                followArtwork = get.boolean(
                    SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_FOLLOW_ARTWORK),
                    true,
                ),
                color = BackgroundColorCodec.decode(
                    get.string(
                        SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_COLOR),
                        "",
                    ),
                ),
            )
        }

        return BackgroundState(
            blurEnabled = blurEnabled,
            adaptiveBlur = adaptiveBlur,
            dynamicColors = dynamicColors,
            components = components,
        )
    }

    /**
     * Import the older shapes of this feature, once.
     *
     * Version 0 is the slider era: `backdrop_blur = 0` was the only way to say
     * "no blur", so it becomes Background blur off — and the slider itself is
     * reset to the shipped strength, otherwise turning blur back on would hand
     * the user the 20% floor instead of the frost they had before.
     *
     * Version 1 is the five-section, three-layer era. Its rows are read into the
     * component model: Home/Search/Library collapse into *Main pages* (the
     * surfaces share one page background; the first section that has a saved
     * setting wins, Home first), the dock and the player map one to one, and the
     * navigation bar gets nothing because version 1 had no navigation colour.
     * The version-1 `secondary` layer is imported as the surface colour: it is
     * the hue the user actually chose, whereas `primary` was already the derived
     * wash and re-deriving it would mute it twice.
     *
     * Returns the imported global blur value, or `null` when nothing was
     * imported — see [read].
     */
    private suspend fun migrate(
        get: GetSettingUseCase,
        update: UpdateSettingUseCase,
        version: Int,
    ): Boolean? {
        val legacyPercent = if (version < 1 && !get.has(SettingsKeys.BACKGROUND_BLUR_ENABLED)) {
            get.int(SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT)
        } else {
            null
        }
        val importedBlur = legacyPercent?.let { it > 0 }
        val legacySections = if (version < 2) legacySections(get) else emptyMap()

        bestEffort {
            importedBlur?.let { update.boolean(SettingsKeys.BACKGROUND_BLUR_ENABLED, it) }
            if (legacyPercent != null && legacyPercent <= 0) {
                // The old slider was also the off switch, so a stored 0 says
                // "off" — imported above. Reset the slider itself to the shipped
                // strength so the switch is not left pointing at the 20% floor.
                update.int(SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT)
            }
            for ((component, prefs) in legacySections) {
                update.boolean(
                    SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_BLUR),
                    prefs.blurEnabled,
                )
                update.boolean(
                    SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_FOLLOW_ARTWORK),
                    prefs.followArtwork,
                )
                prefs.color?.let { color ->
                    update.string(
                        SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_COLOR),
                        BackgroundColorCodec.encode(color),
                    )
                }
            }
            update.int(SettingsKeys.BACKGROUND_PROFILE_VERSION, SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT)
        }
        return importedBlur
    }

    /**
     * Read the version-1 section rows into component preferences.
     *
     * A section counts as "saved" when any of its rows exists. Sections that were
     * never touched are skipped, so a fresh install is not given a colour it did
     * not choose.
     */
    private suspend fun legacySections(
        get: GetSettingUseCase,
    ): Map<BackgroundComponent, ComponentBackgrounds> {
        suspend fun saved(section: String): Boolean =
            LEGACY_FIELDS.any { field -> get.has(SettingsKeys.backgroundKey(section, field)) }

        suspend fun prefs(section: String) = ComponentBackgrounds(
            blurEnabled = get.boolean(SettingsKeys.backgroundKey(section, SettingsKeys.BACKGROUND_FIELD_BLUR), true),
            followArtwork = get.boolean(
                SettingsKeys.backgroundKey(section, SettingsKeys.BACKGROUND_FIELD_DYNAMIC_COLORS),
                true,
            ),
            // Version 1 stored three layers per section. The one the user
            // actually chose is `secondary` (the main bloom); `primary` was the
            // derived wash sitting over their colour. So `secondary` becomes the
            // surface colour, and `primary` is only a fallback for a section that
            // somehow saved a wash but no bloom — a colour is never dropped just
            // because it was stored in the other row.
            color = BackgroundColorCodec.decode(
                get.string(SettingsKeys.backgroundKey(section, SettingsKeys.BACKGROUND_FIELD_SECONDARY), ""),
            ) ?: BackgroundColorCodec.decode(
                get.string(SettingsKeys.backgroundKey(section, SettingsKeys.BACKGROUND_FIELD_PRIMARY), ""),
            ),
        )

        // Home/Search/Library become one surface, so at most one of their
        // colours can survive. Two rules, in this order:
        //
        //  1. the surface's *flags* (does it blur, does it follow artwork) come
        //     from the first section the user saved - Home if it was ever
        //     touched, which is the common case;
        //  2. its *colour* comes from the first saved section that actually has
        //     one, so a user whose only colour lived under Search does not lose
        //     it to a Home row that merely carried a blur toggle.
        //
        // The version-1 rows themselves are left untouched, so nothing is
        // destroyed - see `allKeys()` and the "legacy rows survive" test.
        val savedPages = LEGACY_PAGE_SECTIONS.filter { saved(it) }
        val colourPage = savedPages.firstOrNull { section ->
            LEGACY_COLOUR_FIELDS.any { field -> get.has(SettingsKeys.backgroundKey(section, field)) }
        }
        val components = mutableMapOf<BackgroundComponent, ComponentBackgrounds>()
        savedPages.firstOrNull()?.let { flags ->
            val flagsPrefs = prefs(flags)
            val colour = colourPage?.let { prefs(it).color }
            components[BackgroundComponent.PAGES] =
                if (flagsPrefs.color == null && colour != null) flagsPrefs.copy(color = colour) else flagsPrefs
        }
        if (saved("mini")) components[BackgroundComponent.MINI_PLAYER] = prefs("mini")
        if (saved("player")) components[BackgroundComponent.FULL_PLAYER] = prefs("player")
        return components
    }

    /**
     * Runs a migration write, ignoring a storage failure.
     *
     * Deliberately narrow: it swallows a failed *write*, never a cancellation,
     * and never a failed read. A migration that cannot be saved is not a reason
     * to show the user defaults for settings that are sitting in the database.
     */
    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next launch retries: every import is derived from the same rows.
        }
    }

    /**
     * Applies the stored state to the live holder from a platform entry point,
     * before the first frame.
     *
     * Never throws. These call sites run while the Activity/window is still
     * being created, so a database that is briefly unavailable must not take the
     * launch down; the documented defaults stay in place, which is exactly what
     * a fresh install shows.
     */
    suspend fun restoreBeforeFirstFrame(get: GetSettingUseCase, update: UpdateSettingUseCase) {
        try {
            apply(read(get, update))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DhunBackgrounds.clear()
        }
    }

    /* ------------------------------------------------------------- writes */

    suspend fun writeGlobalBlur(update: UpdateSettingUseCase, enabled: Boolean) =
        update.boolean(SettingsKeys.BACKGROUND_BLUR_ENABLED, enabled)

    suspend fun writeGlobalAdaptiveBlur(update: UpdateSettingUseCase, enabled: Boolean) =
        update.boolean(SettingsKeys.BACKGROUND_DYNAMIC_BLUR, enabled)

    suspend fun writeGlobalDynamicColors(update: UpdateSettingUseCase, enabled: Boolean) =
        update.boolean(SettingsKeys.BACKGROUND_DYNAMIC_COLORS, enabled)

    suspend fun writeComponentBlur(
        update: UpdateSettingUseCase,
        component: BackgroundComponent,
        enabled: Boolean,
    ) = update.boolean(
        SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_BLUR),
        enabled,
    )

    suspend fun writeComponentFollowArtwork(
        update: UpdateSettingUseCase,
        component: BackgroundComponent,
        enabled: Boolean,
    ) = update.boolean(
        SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_FOLLOW_ARTWORK),
        enabled,
    )

    /** Writes the surface colour, or clears it when the user restores the default. */
    suspend fun writeComponentColor(
        update: UpdateSettingUseCase,
        component: BackgroundComponent,
        color: androidx.compose.ui.graphics.Color?,
    ) = update.string(
        SettingsKeys.backgroundComponentKey(component.id, SettingsKeys.BACKGROUND_FIELD_COLOR),
        BackgroundColorCodec.encode(color),
    )

    /** Removes every background row so the next launch starts from the defaults. */
    suspend fun clearAll(update: UpdateSettingUseCase) {
        for (key in allKeys()) update.remove(key)
    }

    /**
     * Applies a snapshot to the live holder. Called after every change and from
     * the host before the first frame, so what the settings screen previews is
     * exactly what the app paints.
     *
     * The snapshot is the *settings* half of the state; [DhunBackgrounds.restore]
     * keeps the runtime artwork colour (re-deriving it from the playing track)
     * so saving a switch never blanks the background for a frame.
     */
    fun apply(state: BackgroundState) {
        DhunBackgrounds.restore(state)
    }
}
