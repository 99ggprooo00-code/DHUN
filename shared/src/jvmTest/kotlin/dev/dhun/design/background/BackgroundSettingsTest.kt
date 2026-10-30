package dev.dhun.design.background

import androidx.compose.ui.graphics.Color
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.EpochClock
import dev.dhun.data.SettingsKeys
import dev.dhun.design.ArtworkColors
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunTokens
import dev.dhun.design.luminance
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.presentation.settings.BackgroundPreferences
import dev.dhun.presentation.settings.SettingsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking

/**
 * The background feature: precedence, the two blur switches, manual palettes,
 * persistence/restore and the colour-safety rules.
 *
 * The policy is pure, so most of this needs no composition; the persistence
 * half runs against the real settings repository on an in-memory database, the
 * same pattern `SettingsViewModelTest` uses, so a regression in the key names or
 * the migration is caught rather than mocked away.
 */
class BackgroundSettingsTest {

    private val dark = DhunTokens.dark
    private val light = DhunTokens.light

    @BeforeTest
    fun setUp() {
        DhunBackgrounds.clear()
    }

    @AfterTest
    fun tearDown() {
        DhunBackgrounds.clear()
        DhunAppearance.applyPersistedAppearance("dark", "brand")
    }

    /* ------------------------------------------------ palette precedence */

    private fun resolve(
        section: BackgroundSection = BackgroundSection.HOME,
        global: Boolean,
        sectionOn: Boolean,
        manual: BackgroundPalette? = null,
        artwork: BackgroundPalette? = null,
        tokens: DhunTokens = dark,
    ) = BackgroundPalettePolicy.resolve(section, tokens, global, sectionOn, manual, artwork)

    @Test
    fun theTwoSwitchesTogetherDecideWhichPaletteAPaints() {
        val manual = BackgroundPalette(Color(0xFF202020), Color(0xFF3366CC), Color(0xFF33CC99))
        val artwork = BackgroundPalette(Color(0xFF151515), Color(0xFFAA4488), Color(0xFF44AAAA))

        // 1. Global off → manual, whatever the section says.
        for (sectionOn in listOf(true, false)) {
            assertEquals(manual, resolve(global = false, sectionOn = sectionOn, manual = manual, artwork = artwork))
        }
        // 2. Global on, section off → manual. This is the "my own colours in
        //    Search" case, and it must not depend on artwork being absent.
        assertEquals(manual, resolve(global = true, sectionOn = false, manual = manual, artwork = artwork))
        // 3. Both on → artwork.
        assertEquals(artwork, resolve(global = true, sectionOn = true, manual = manual, artwork = artwork))
    }

    @Test
    fun aSectionWithNoManualPaletteFallsBackToItsDefault() {
        val fallback = BackgroundPalettePolicy.defaultPalette(BackgroundSection.LIBRARY, dark)
        assertEquals(fallback, resolve(global = false, sectionOn = true, manual = null, artwork = null))
        assertEquals(fallback, resolve(global = true, sectionOn = false, manual = null, artwork = null))
        // Both on but nothing is playing: still a deliberate background, never
        // a transparent hole.
        assertEquals(fallback, resolve(global = true, sectionOn = true, manual = null, artwork = null))
    }

    @Test
    fun everySectionResolvesWithTheSameRules() {
        val manual = BackgroundPalette(Color(0xFF1A1A1A), Color(0xFF557799), Color(0xFF775599))
        val artwork = BackgroundPalette(Color(0xFF161616), Color(0xFF994477), Color(0xFF447799))
        for (section in BackgroundSection.entries) {
            assertEquals(
                manual,
                resolve(section = section, global = false, sectionOn = true, manual = manual, artwork = artwork),
                "$section must respect the global switch",
            )
            assertEquals(
                manual,
                resolve(section = section, global = true, sectionOn = false, manual = manual, artwork = artwork),
                "$section must respect its own switch",
            )
            assertEquals(
                artwork,
                resolve(section = section, global = true, sectionOn = true, manual = manual, artwork = artwork),
                "$section must follow the artwork when both switches allow it",
            )
        }
    }

    @Test
    fun theSettingsCaptionMatchesTheResolution() {
        assertEquals(
            PaletteSource.ARTWORK,
            BackgroundPalettePolicy.source(globalDynamicColors = true, sectionDynamicColors = true, hasManual = false, hasArtwork = true),
        )
        assertEquals(
            PaletteSource.MANUAL,
            BackgroundPalettePolicy.source(globalDynamicColors = true, sectionDynamicColors = false, hasManual = true, hasArtwork = true),
        )
        assertEquals(
            PaletteSource.DEFAULT,
            BackgroundPalettePolicy.source(globalDynamicColors = true, sectionDynamicColors = false, hasManual = false, hasArtwork = true),
        )
        // Global off is *overridden* even when the section itself is on: that
        // is what the settings caption has to say out loud.
        for (sectionOn in listOf(true, false)) {
            val source = BackgroundPalettePolicy.source(
                globalDynamicColors = false,
                sectionDynamicColors = sectionOn,
                hasManual = true,
                hasArtwork = true,
            )
            assertEquals(PaletteSource.MANUAL_OVERRIDDEN, source)
            assertTrue(source.isOverridden)
        }
    }

    /* ---------------------------------------------------- blur precedence */

    @Test
    fun theMasterSwitchBeatsEverySectionPreference() {
        for (sectionBlur in listOf(true, false)) {
            for (dynamic in listOf(true, false)) {
                for (sectionDynamic in listOf(true, false)) {
                    assertEquals(
                        0,
                        BackgroundBlurPolicy.percentFor(
                            globalBlurEnabled = false,
                            sectionBlurEnabled = sectionBlur,
                            dynamicBlur = dynamic,
                            sectionDynamicBlur = sectionDynamic,
                            artworkLuminance = 0.9f,
                            artworkSaturation = 0.9f,
                        ),
                        "master off must mean no blur anywhere",
                    )
                }
            }
        }
    }

    @Test
    fun aSectionCanStaySharpWhileTheRestOfTheAppIsBlurred() {
        assertEquals(
            0,
            BackgroundBlurPolicy.percentFor(
                globalBlurEnabled = true,
                sectionBlurEnabled = false,
                dynamicBlur = true,
                sectionDynamicBlur = true,
            ),
        )
        assertTrue(
            BackgroundBlurPolicy.percentFor(
                globalBlurEnabled = true,
                sectionBlurEnabled = true,
                dynamicBlur = true,
                sectionDynamicBlur = true,
            ) > 0,
        )
    }

    @Test
    fun staticBlurIsOneSteadyRadiusAndDynamicBlurFollowsTheArtwork() {
        val static = BackgroundBlurPolicy.percentFor(
            globalBlurEnabled = true,
            sectionBlurEnabled = true,
            dynamicBlur = false,
            sectionDynamicBlur = true,
            artworkLuminance = 0.95f,
            artworkSaturation = 1f,
        )
        assertEquals(BackgroundBlurPolicy.STATIC_PERCENT, static)
        // A section opting out of dynamic blur gets the same steady radius even
        // when the global switch is dynamic.
        assertEquals(
            BackgroundBlurPolicy.STATIC_PERCENT,
            BackgroundBlurPolicy.percentFor(
                globalBlurEnabled = true,
                sectionBlurEnabled = true,
                dynamicBlur = true,
                sectionDynamicBlur = false,
            ),
        )

        val bright = BackgroundBlurPolicy.adaptivePercent(0.95f, 0.95f)
        val dark = BackgroundBlurPolicy.adaptivePercent(0.05f, 0.05f)
        assertTrue(bright > static, "pale, busy artwork needs more frost: $bright vs $static")
        assertTrue(dark < static, "a dark, moody cover keeps its detail: $dark vs $static")
        assertNotEquals(bright, dark)
    }

    @Test
    fun dynamicBlurStaysInsideItsBand() {
        for (luminance in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (saturation in listOf(0f, 0.5f, 1f)) {
                val percent = BackgroundBlurPolicy.adaptivePercent(luminance, saturation)
                assertTrue(
                    percent in BackgroundBlurPolicy.DYNAMIC_MIN_PERCENT..BackgroundBlurPolicy.DYNAMIC_MAX_PERCENT,
                    "adaptive percent $percent out of band for lum=$luminance sat=$saturation",
                )
            }
        }
        // No artwork statistics yet: the neutral case is the static radius.
        assertEquals(
            BackgroundBlurPolicy.STATIC_PERCENT,
            BackgroundBlurPolicy.adaptivePercent(null, null),
        )
    }

    @Test
    fun theStrengthSliderCanOnlyReduceABlurItCannotSwitchOneOn() {
        assertEquals(0, BackgroundBlurPolicy.applyStrength(percent = 0, strengthPercent = 100))
        assertEquals(50, BackgroundBlurPolicy.applyStrength(percent = 100, strengthPercent = 50))
        assertEquals(0, BackgroundBlurPolicy.applyStrength(percent = 80, strengthPercent = 0))
        assertEquals(80, BackgroundBlurPolicy.applyStrength(percent = 80, strengthPercent = 100))
    }

    /* --------------------------------------------------- palette safety */

    @Test
    fun theArtworkBaseStaysInsideTheThemeContrastBand() {
        // A violent artwork colour must not repaint the page base.
        for (hue in listOf(Color(0xFFFF0000), Color(0xFF00FF00), Color(0xFF0000FF), Color(0xFFFF00FF))) {
            for (tokens in listOf(dark, light)) {
                val palette = BackgroundPalettePolicy.artworkPalette(
                    BackgroundSection.HOME,
                    ArtworkColors.fromPrimary(hue),
                    tokens,
                )!!
                val drift = kotlin.math.abs(palette.primary.luminance() - tokens.background.luminance())
                assertTrue(
                    drift <= BackgroundPalettePolicy.BASE_LUMINANCE_DRIFT + 0.001f,
                    "base drifted $drift past the contrast band for $hue",
                )
            }
        }
    }

    @Test
    fun aManualPaletteIsClampedBeforeItIsPainted() {
        val white = BackgroundPalette(Color.White, Color.White, Color.White)
        val safe = BackgroundPalettePolicy.sanitize(white, dark)
        val drift = kotlin.math.abs(safe.primary.luminance() - dark.background.luminance())
        assertTrue(drift <= BackgroundPalettePolicy.BASE_LUMINANCE_DRIFT + 0.001f)
        // The glows may stay bright, but never fully transparent.
        assertTrue(safe.secondary.alpha >= BackgroundPalettePolicy.MIN_LAYER_ALPHA)
        assertTrue(safe.tertiary.alpha >= BackgroundPalettePolicy.MIN_LAYER_ALPHA)
    }

    @Test
    fun theArtworkPaletteKeepsTheThreeLayersRelated() {
        val art = ArtworkColors.fromPrimary(Color(0xFF8A4FBF))
        val palette = BackgroundPalettePolicy.artworkPalette(BackgroundSection.HOME, art, dark)!!
        assertNotEquals(palette.secondary, palette.tertiary, "the companion layer must be a different layer")
        assertTrue(palette.secondary.luminance() > palette.primary.luminance(), "the glow reads over the base")
        assertNull(BackgroundPalettePolicy.artworkPalette(BackgroundSection.HOME, null, dark))
    }

    /* ---------------------------------------------------- live holder */

    @Test
    fun theHolderResolvesPerSectionAndSurvivesGlobalOverrides() {
        val manual = BackgroundPalette(Color(0xFF1C1C1C), Color(0xFF446688), Color(0xFF664488))
        DhunBackgrounds.setSectionManualPalette(BackgroundSection.SEARCH, manual)
        DhunBackgrounds.setSectionDynamicColors(BackgroundSection.SEARCH, false)

        // Global on, Search off → Search uses its own colours, Home follows art.
        DhunBackgrounds.setArtworkSeed("https://example.test/cover.jpg")
        assertEquals(PaletteSource.ARTWORK, DhunBackgrounds.sourceFor(BackgroundSection.HOME))
        // The manual palette is sanitised on the way in, so that is what the
        // section paints. The blooms are compared exactly; the base is compared
        // by luminance because sanitising twice must not drift it.
        val painted = DhunBackgrounds.paletteFor(BackgroundSection.SEARCH)
        val expected = BackgroundPalettePolicy.sanitize(manual, DhunAppearance.tokens)
        assertEquals(expected.secondary, painted.secondary)
        assertEquals(expected.tertiary, painted.tertiary)
        assertTrue(
            kotlin.math.abs(painted.primary.luminance() - expected.primary.luminance()) <= 0.001f,
            "sanitising an already-safe palette must be idempotent",
        )

        // Global off → Search keeps its colours and Home now uses its default,
        // and **the saved section preference is still there**.
        DhunBackgrounds.setDynamicColors(false)
        assertEquals(manual.secondary, DhunBackgrounds.paletteFor(BackgroundSection.SEARCH).secondary)
        assertFalse(DhunBackgrounds.usesArtworkColors(BackgroundSection.HOME))
        DhunBackgrounds.setDynamicColors(true)
        assertEquals(PaletteSource.MANUAL, DhunBackgrounds.sourceFor(BackgroundSection.SEARCH))
        assertEquals(PaletteSource.ARTWORK, DhunBackgrounds.sourceFor(BackgroundSection.HOME))
    }

    @Test
    fun turningTheMasterBlurOffSuspendsDynamicBlurWithoutForgettingIt() {
        DhunBackgrounds.setDynamicBlur(true)
        DhunBackgrounds.setBlurEnabled(false)
        for (section in BackgroundSection.entries) {
            assertEquals(0, DhunBackgrounds.blurPercentFor(section))
        }
        // The saved preference is untouched…
        assertTrue(DhunBackgrounds.current.dynamicBlur)
        // …and comes straight back with the master switch.
        DhunBackgrounds.setBlurEnabled(true)
        assertTrue(DhunBackgrounds.blurPercentFor(BackgroundSection.HOME, strengthPercent = 100) > 0)
        DhunBackgrounds.setDynamicBlur(false)
        assertEquals(
            BackgroundBlurPolicy.STATIC_PERCENT,
            DhunBackgrounds.blurPercentFor(BackgroundSection.HOME, strengthPercent = 100),
        )
    }

    @Test
    fun aMissingArtworkFallsBackToManualOrDefault() {
        val manual = BackgroundPalette(Color(0xFF1A1A1A), Color(0xFF5588AA), Color(0xFFAA5588))
        DhunBackgrounds.setSectionManualPalette(BackgroundSection.LIBRARY, manual)
        DhunBackgrounds.setArtworkSeed("https://example.test/a.jpg")
        DhunBackgrounds.setArtworkSeed(null)
        assertNull(DhunBackgrounds.current.artwork)
        assertEquals(manual.secondary, DhunBackgrounds.paletteFor(BackgroundSection.LIBRARY).secondary)
        assertEquals(
            BackgroundPalettePolicy.defaultPalette(BackgroundSection.HOME, DhunAppearance.tokens).secondary,
            DhunBackgrounds.paletteFor(BackgroundSection.HOME).secondary,
        )
        // Blur is unaffected by a missing palette: it is a separate feature.
        assertTrue(DhunBackgrounds.blurPercentFor(BackgroundSection.HOME, strengthPercent = 100) > 0)
    }

    @Test
    fun resettingASectionRestoresEveryOneOfItsPreferences() {
        DhunBackgrounds.setSectionBlurEnabled(BackgroundSection.FULL_PLAYER, false)
        DhunBackgrounds.setSectionDynamicBlur(BackgroundSection.FULL_PLAYER, false)
        DhunBackgrounds.setSectionDynamicColors(BackgroundSection.FULL_PLAYER, false)
        DhunBackgrounds.setSectionManualPalette(
            BackgroundSection.FULL_PLAYER,
            BackgroundPalette(Color(0xFF111111), Color(0xFF445566), Color(0xFF665544)),
        )
        DhunBackgrounds.resetSection(BackgroundSection.FULL_PLAYER)
        assertEquals(SectionBackgrounds.default, DhunBackgrounds.current.section(BackgroundSection.FULL_PLAYER))
        // A different section is untouched.
        DhunBackgrounds.setSectionBlurEnabled(BackgroundSection.HOME, false)
        DhunBackgrounds.resetSection(BackgroundSection.FULL_PLAYER)
        assertFalse(DhunBackgrounds.current.section(BackgroundSection.HOME).blurEnabled)
    }

    @Test
    fun switchingTracksReusesTheCachedExtraction() {
        DhunBackgrounds.setArtworkSeed("https://example.test/one.jpg")
        val first = DhunBackgrounds.current
        DhunBackgrounds.setArtworkSeed("https://example.test/one.jpg")
        // Same seed → same revision: no second extraction, no repaint.
        assertEquals(first.revision, DhunBackgrounds.current.revision)
        DhunBackgrounds.setArtworkSeed("https://example.test/two.jpg")
        assertTrue(DhunBackgrounds.current.revision > first.revision)
    }

    /* --------------------------------------------------------- encoding */

    @Test
    fun palettesRoundTripThroughTheSettingsEncoding() {
        val palette = BackgroundPalette(Color(0xFF16213A), Color(0xFFBB86FC), Color(0xFF4FC3A1))
        val encoded = BackgroundPaletteCodec.encodePalette(palette)
        assertEquals(3, encoded.size)
        val decoded = BackgroundPaletteCodec.decodePalette(encoded[0], encoded[1], encoded[2])
        assertEquals(palette, decoded)
        // Nothing saved is three empty strings, and that is *not* black.
        assertEquals(listOf("", "", ""), BackgroundPaletteCodec.encodePalette(null))
        assertNull(BackgroundPaletteCodec.decodePalette("", "", ""))
        // A half-written or hand-edited row is unset, never a crash.
        assertNull(BackgroundPaletteCodec.decodePalette(encoded[0], encoded[1], "nonsense"))
    }

    /* ------------------------------------------------------ persistence */

    private class FakeClock(var now: Long = 1_700_000_000_000L) : EpochClock {
        override fun nowMs(): Long = now
    }

    private fun data() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()), FakeClock())

    private fun vm(d: DataLayer, scope: CoroutineScope) =
        SettingsViewModel(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings), scope)

    private suspend fun loaded(vm: SettingsViewModel): SettingsViewModel {
        vm.loaded.first { it }
        return vm
    }

    /** Waits for every pending preference write the ViewModel launched. */
    private suspend fun settled(scope: CoroutineScope) {
        scope.coroutineContext[Job]!!.children.toList().joinAll()
    }

    private suspend fun reloaded(d: DataLayer, scope: CoroutineScope): SettingsViewModel {
        settled(scope)
        return loaded(vm(d, scope))
    }

    @Test
    fun everyBackgroundPreferenceSurvivesARestart(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        val manual = BackgroundPalette(Color(0xFF1A1E2A), Color(0xFF5577AA), Color(0xFFAA7755))

        v.setBackgroundBlurEnabled(false)
        v.setBackgroundDynamicBlur(false)
        v.setBackgroundDynamicColors(false)
        v.setSectionBlurEnabled(BackgroundSection.MINI_PLAYER, false)
        v.setSectionDynamicColors(BackgroundSection.LIBRARY, false)
        v.setSectionManualPalette(BackgroundSection.LIBRARY, manual)

        val restarted = reloaded(d, this)
        val state = restarted.backgrounds.value
        assertFalse(state.blurEnabled)
        assertFalse(state.dynamicBlur)
        assertFalse(state.dynamicColors)
        assertFalse(state.section(BackgroundSection.MINI_PLAYER).blurEnabled)
        assertFalse(state.section(BackgroundSection.LIBRARY).dynamicColors)
        assertEquals(manual, state.section(BackgroundSection.LIBRARY).manual)
        // And the live holder was restored too, not just the settings copy.
        assertEquals(PaletteSource.MANUAL_OVERRIDDEN, DhunBackgrounds.sourceFor(BackgroundSection.LIBRARY))
        assertEquals(0, DhunBackgrounds.blurPercentFor(BackgroundSection.HOME, strengthPercent = 100))
    }

    @Test
    fun aFreshInstallGetsTheDocumentedDefaults(): Unit = runBlocking {
        val d = data()
        val state = loaded(vm(d, this)).backgrounds.value
        assertTrue(state.blurEnabled)
        assertTrue(state.dynamicBlur)
        assertTrue(state.dynamicColors)
        for (section in BackgroundSection.entries) {
            assertEquals(SectionBackgrounds.default, state.section(section))
        }
    }

    @Test
    fun anOlderInstallWithBlurOffKeepsItOffOnce(): Unit = runBlocking {
        val d = data()
        val update = UpdateSettingUseCase(d.settings)
        // A database from before the feature: only the legacy slider exists.
        update.int(SettingsKeys.BACKDROP_BLUR, 0)

        val first = loaded(vm(d, this)).backgrounds.value
        assertFalse(first.blurEnabled, "a stored 0 meant no blur and must be imported")

        // The user turns blur on; a later launch must not import 0 again.
        val v = loaded(vm(d, this))
        v.setBackgroundBlurEnabled(true)
        assertEquals(SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT, d.settings.getInt(SettingsKeys.BACKGROUND_PROFILE_VERSION, 0))
        val restarted = reloaded(d, this).backgrounds.value
        assertTrue(restarted.blurEnabled)
    }

    @Test
    fun theLegacySliderIsLeftAloneForUsersWhoNeverTouchedIt(): Unit = runBlocking {
        val d = data()
        val state = loaded(vm(d, this)).backgrounds.value
        assertTrue(state.blurEnabled)
        // Defaults are the pre-feature behaviour: blurred, dynamic, artwork colours.
        assertEquals(SettingsKeys.BACKGROUND_BLUR_ENABLED_DEFAULT, state.blurEnabled)
    }

    @Test
    fun sectionResetWritesDefaultsWithoutTouchingTheGlobalSwitches(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setBackgroundDynamicColors(false)
        v.setSectionBlurEnabled(BackgroundSection.HOME, false)
        v.setSectionManualPalette(BackgroundSection.HOME, BackgroundPalette(Color(0xFF101010), Color(0xFF334455), Color(0xFF554433)))

        v.resetSectionBackgrounds(BackgroundSection.HOME)
        assertEquals(SectionBackgrounds.default, v.backgrounds.value.section(BackgroundSection.HOME))
        assertFalse(v.backgrounds.value.dynamicColors, "a section reset must not re-enable a global switch")

        val restarted = reloaded(d, this).backgrounds.value
        assertEquals(SectionBackgrounds.default, restarted.section(BackgroundSection.HOME))
        assertFalse(restarted.dynamicColors)
    }

    @Test
    fun restoreAllSectionsClearsEveryManualPalette(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        for (section in BackgroundSection.entries) {
            v.setSectionDynamicColors(section, false)
            v.setSectionManualPalette(section, BackgroundPalette(Color(0xFF111111), Color(0xFF334455), Color(0xFF554433)))
        }
        v.resetAllSectionBackgrounds()
        v.backgrounds.value.resolvedSections().forEach { (section, prefs) ->
            assertEquals(SectionBackgrounds.default, prefs, "$section must be back to its defaults")
        }
        val restarted = reloaded(d, this).backgrounds.value
        restarted.resolvedSections().forEach { (section, prefs) ->
            assertEquals(SectionBackgrounds.default, prefs, "$section must stay reset after a restart")
        }
    }

    @Test
    fun resettingEverySettingAlsoClearsTheBackgroundRows(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setBackgroundBlurEnabled(false)
        v.setSectionManualPalette(BackgroundSection.HOME, BackgroundPalette(Color(0xFF111111), Color(0xFF334455), Color(0xFF554433)))
        v.reset()
        // Writes are asynchronous: observe the database only once they have all
        // landed, otherwise this asserts on a race instead of on the reset.
        settled(this)
        // Every background row is gone, including the per-section ones that are
        // not in SettingsKeys.all.
        for (key in BackgroundPreferences.allKeys()) {
            assertNull(d.settings.getString(key), "$key should have been removed")
        }
        val restarted = reloaded(d, this).backgrounds.value
        assertTrue(restarted.blurEnabled)
        assertEquals(SectionBackgrounds.default, restarted.section(BackgroundSection.HOME))
    }

    @Test
    fun anOptimisticSwitchIsTheValueThatWasPersisted(): Unit = runBlocking {
        // The switch is published the instant it is tapped (the app behind the
        // settings page follows it), and the persisted row has to end up
        // matching it — a mismatch is exactly the "screen lies" bug the
        // rollback contract exists to prevent.
        val d = data()
        val v = loaded(vm(d, this))
        v.setBackgroundDynamicColors(false)
        assertFalse(v.backgrounds.value.dynamicColors)
        reloaded(d, this)
        assertEquals("false", d.settings.getString(SettingsKeys.BACKGROUND_DYNAMIC_COLORS))
        assertFalse(v.backgrounds.value.dynamicColors)
    }

    @Test
    fun theLightThemeGetsItsOwnPaletteDefaults() {
        val darkDefault = BackgroundPalettePolicy.defaultPalette(BackgroundSection.HOME, dark)
        val lightDefault = BackgroundPalettePolicy.defaultPalette(BackgroundSection.HOME, light)
        assertNotEquals(darkDefault.primary, lightDefault.primary)
        // The artwork base follows the theme it will be painted on.
        val art = ArtworkColors.fromPrimary(Color(0xFFB060A0))
        val darkArt = BackgroundPalettePolicy.artworkPalette(BackgroundSection.HOME, art, dark)!!
        val lightArt = BackgroundPalettePolicy.artworkPalette(BackgroundSection.HOME, art, light)!!
        assertTrue(lightArt.primary.luminance() > darkArt.primary.luminance())
    }
}
