package dev.dhun.presentation.settings

import dev.dhun.data.SettingsKeys
import dev.dhun.design.background.BackgroundPalette
import dev.dhun.design.background.BackgroundPaletteCodec
import dev.dhun.design.background.BackgroundSection
import dev.dhun.design.background.BackgroundState
import dev.dhun.design.background.DhunBackgrounds
import dev.dhun.design.background.SectionBackgrounds
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
 */
object BackgroundPreferences {

    /**
     * Every persisted key that belongs to the background feature, in the order
     * Reset removes them (globals first, then sections).
     */
    fun allKeys(): List<String> = buildList {
        add(SettingsKeys.BACKGROUND_BLUR_ENABLED)
        add(SettingsKeys.BACKGROUND_DYNAMIC_BLUR)
        add(SettingsKeys.BACKGROUND_DYNAMIC_COLORS)
        add(SettingsKeys.BACKGROUND_PROFILE_VERSION)
        for (section in BackgroundSection.entries) {
            for (field in FIELDS) add(SettingsKeys.backgroundKey(section.id, field))
        }
    }

    private val FIELDS = listOf(
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
     *
     * **Backward compatibility.** A database written before this feature has no
     * `background_*` rows at all, and its only blur vocabulary was
     * `backdrop_blur = 0..100`. Version 0 therefore imports that one fact — a
     * stored 0 means the user had turned blur off — and then writes the marker
     * so the import runs exactly once. Users who never touched the slider get
     * the new defaults, which are the old behaviour.
     */
    suspend fun read(get: GetSettingUseCase, update: UpdateSettingUseCase): BackgroundState {
        val version = get.int(SettingsKeys.BACKGROUND_PROFILE_VERSION, 0)
        // A migration is a *write*, and a write is not allowed to decide whether
        // the stored preferences can be read: on a full disk the old build failed
        // the entire load here, so every setting came back as its default and the
        // screen reported that nothing could be read. The imported value is
        // therefore computed first and persisted on a best-effort basis — if the
        // marker write fails the import simply runs again next launch, which is
        // safe because it is derived from the same legacy row.
        val importedBlur = if (version < SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT) {
            migrate(get, update)
        } else {
            null
        }

        val blurEnabled = importedBlur
            ?: get.boolean(SettingsKeys.BACKGROUND_BLUR_ENABLED, SettingsKeys.BACKGROUND_BLUR_ENABLED_DEFAULT)
        val dynamicBlur = get.boolean(SettingsKeys.BACKGROUND_DYNAMIC_BLUR, SettingsKeys.BACKGROUND_DYNAMIC_BLUR_DEFAULT)
        val dynamicColors = get.boolean(SettingsKeys.BACKGROUND_DYNAMIC_COLORS, SettingsKeys.BACKGROUND_DYNAMIC_COLORS_DEFAULT)

        val sections = BackgroundSection.entries.associateWith { section ->
            SectionBackgrounds(
                blurEnabled = get.boolean(
                    SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_BLUR),
                    true,
                ),
                dynamicBlur = get.boolean(
                    SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_DYNAMIC_BLUR),
                    true,
                ),
                dynamicColors = get.boolean(
                    SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_DYNAMIC_COLORS),
                    true,
                ),
                manual = BackgroundPaletteCodec.decodePalette(
                    primary = get.string(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_PRIMARY), ""),
                    secondary = get.string(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_SECONDARY), ""),
                    tertiary = get.string(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_TERTIARY), ""),
                ),
            )
        }

        return BackgroundState(
            blurEnabled = blurEnabled,
            dynamicBlur = dynamicBlur,
            dynamicColors = dynamicColors,
            sections = sections,
        )
    }

    /**
     * One-time import of the pre-switch blur setting. Idempotent by the marker,
     * so a user who deliberately turns blur off later keeps that choice.
     *
     * Returns the imported master-blur value (or `null` when there was nothing to
     * import), and never lets a failed write hide the stored preferences — see
     * [read].
     */
    private suspend fun migrate(get: GetSettingUseCase, update: UpdateSettingUseCase): Boolean? {
        val imported = if (!get.has(SettingsKeys.BACKGROUND_BLUR_ENABLED)) {
            val legacyPercent = get.int(SettingsKeys.BACKDROP_BLUR, SettingsKeys.BACKDROP_BLUR_DEFAULT)
            legacyPercent > 0
        } else {
            null
        }
        bestEffort {
            imported?.let { update.boolean(SettingsKeys.BACKGROUND_BLUR_ENABLED, it) }
            update.int(SettingsKeys.BACKGROUND_PROFILE_VERSION, SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT)
        }
        return imported
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
            // The next launch retries: the import is derived from the same row.
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

    suspend fun writeGlobalDynamicBlur(update: UpdateSettingUseCase, enabled: Boolean) =
        update.boolean(SettingsKeys.BACKGROUND_DYNAMIC_BLUR, enabled)

    suspend fun writeGlobalDynamicColors(update: UpdateSettingUseCase, enabled: Boolean) =
        update.boolean(SettingsKeys.BACKGROUND_DYNAMIC_COLORS, enabled)

    suspend fun writeSectionBlur(update: UpdateSettingUseCase, section: BackgroundSection, enabled: Boolean) =
        update.boolean(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_BLUR), enabled)

    suspend fun writeSectionDynamicBlur(update: UpdateSettingUseCase, section: BackgroundSection, enabled: Boolean) =
        update.boolean(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_DYNAMIC_BLUR), enabled)

    suspend fun writeSectionDynamicColors(update: UpdateSettingUseCase, section: BackgroundSection, enabled: Boolean) =
        update.boolean(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_DYNAMIC_COLORS), enabled)

    /** Writes the three layers, or clears them when the user restores defaults. */
    suspend fun writeSectionPalette(
        update: UpdateSettingUseCase,
        section: BackgroundSection,
        palette: BackgroundPalette?,
    ) {
        val encoded = BackgroundPaletteCodec.encodePalette(palette)
        update.string(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_PRIMARY), encoded[0])
        update.string(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_SECONDARY), encoded[1])
        update.string(SettingsKeys.backgroundKey(section.id, SettingsKeys.BACKGROUND_FIELD_TERTIARY), encoded[2])
    }

    /** Removes every background row so the next launch starts from the defaults. */
    suspend fun clearAll(update: UpdateSettingUseCase) {
        for (key in allKeys()) update.remove(key)
    }

    /**
     * Applies a snapshot to the live holder. Called after every change and from
     * the host before the first frame, so what the settings screen previews is
     * exactly what the app paints.
     */
    fun apply(state: BackgroundState) {
        DhunBackgrounds.restore(state)
    }
}
