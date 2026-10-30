package dev.dhun.design.background

import androidx.compose.ui.graphics.Color
import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.EpochClock
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.design.DhunAppearance
import dev.dhun.design.DhunTokens
import dev.dhun.design.luminance
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.presentation.settings.BackgroundPreferences
import dev.dhun.presentation.settings.SettingsViewModel
import kotlin.math.abs
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
 * The background feature, in the terms the user meets it:
 *
 * - **Background blur** is one decision (is the artwork behind the app?),
 * - **Dynamic colours** is a second, independent one (does the colour come from
 *   the artwork?),
 * - each of the four surfaces keeps its own colour and its own blur switch.
 *
 * The policy is pure, so most of this needs no composition; the persistence half
 * runs against the real settings repository on an in-memory database, the same
 * pattern `SettingsViewModelTest` uses, so a regression in the key names or the
 * migrations is caught rather than mocked away.
 */
class BackgroundSettingsTest {

    private val dark = DhunTokens.dark
    private val light = DhunTokens.light

    /** A representative artwork colour: saturated, mid-luminance. */
    private val artColor = Color(0xFFB060A0)

    /** A representative user pick that is nothing like the artwork. */
    private val pickColor = Color(0xFF4FC3A1)

    @BeforeTest
    fun setUp() {
        DhunBackgrounds.clear()
    }

    @AfterTest
    fun tearDown() {
        DhunBackgrounds.clear()
        DhunAppearance.applyPersistedAppearance("dark", "brand")
    }

    /* ------------------------------------------------ colour precedence */

    private fun resolve(
        component: BackgroundComponent = BackgroundComponent.PAGES,
        dynamicColors: Boolean,
        followArtwork: Boolean,
        manual: Color? = null,
        artwork: Color? = null,
        solid: Boolean = false,
        tokens: DhunTokens = dark,
    ) = BackgroundPalettePolicy.resolve(
        component = component,
        tokens = tokens,
        dynamicColors = dynamicColors,
        followArtwork = followArtwork,
        manualColor = manual,
        artworkColor = artwork,
        solid = solid,
    )

    @Test
    fun dynamicColoursOffAlwaysUsesThePickedColour() {
        val expected = BackgroundPalettePolicy.paletteFrom(pickColor, dark, solid = false)
        for (follow in listOf(true, false)) {
            assertEquals(
                expected,
                resolve(dynamicColors = false, followArtwork = follow, manual = pickColor, artwork = artColor),
                "with Dynamic colours off no artwork colour may reach the screen",
            )
        }
    }

    @Test
    fun aSurfaceCanOptOutOfArtworkColoursOnItsOwn() {
        val expected = BackgroundPalettePolicy.paletteFrom(pickColor, dark, solid = false)
        assertEquals(
            expected,
            resolve(dynamicColors = true, followArtwork = false, manual = pickColor, artwork = artColor),
            "a surface set to its own colour keeps it even with Dynamic colours on",
        )
        // …and the neighbouring surface still follows the artwork.
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(artColor, dark, solid = false),
            resolve(dynamicColors = true, followArtwork = true, manual = pickColor, artwork = artColor),
        )
    }

    @Test
    fun bothOnUsesTheArtworkColour() {
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(artColor, dark, solid = false),
            resolve(dynamicColors = true, followArtwork = true, manual = pickColor, artwork = artColor),
        )
    }

    @Test
    fun aSurfaceWithNoColourFallsBackToItsDefault() {
        for (component in BackgroundComponent.entries) {
            val fallback = BackgroundPalettePolicy.defaultPalette(component, dark)
            for (dynamic in listOf(true, false)) {
                for (follow in listOf(true, false)) {
                    assertEquals(
                        fallback,
                        resolve(
                            component = component,
                            dynamicColors = dynamic,
                            followArtwork = follow,
                            manual = null,
                            artwork = null,
                        ),
                        "$component must never resolve to a transparent hole",
                    )
                }
            }
        }
    }

    @Test
    fun everySurfaceResolvesWithTheSameRules() {
        val artworkPalette = BackgroundPalettePolicy.paletteFrom(artColor, dark, solid = false)
        val pickPalette = BackgroundPalettePolicy.paletteFrom(pickColor, dark, solid = false)
        for (component in BackgroundComponent.entries) {
            assertEquals(
                pickPalette,
                resolve(component, dynamicColors = false, followArtwork = true, manual = pickColor, artwork = artColor),
                "$component must respect Dynamic colours",
            )
            assertEquals(
                pickPalette,
                resolve(component, dynamicColors = true, followArtwork = false, manual = pickColor, artwork = artColor),
                "$component must respect its own artwork-colour switch",
            )
            assertEquals(
                artworkPalette,
                resolve(component, dynamicColors = true, followArtwork = true, manual = pickColor, artwork = artColor),
                "$component must follow the artwork when both allow it",
            )
        }
    }

    @Test
    fun theSettingsCaptionMatchesTheResolution() {
        assertEquals(
            PaletteSource.ARTWORK,
            BackgroundPalettePolicy.source(dynamicColors = true, followArtwork = true, hasManual = true, hasArtwork = true),
        )
        assertEquals(
            PaletteSource.MANUAL,
            BackgroundPalettePolicy.source(dynamicColors = true, followArtwork = false, hasManual = true, hasArtwork = true),
        )
        assertEquals(
            PaletteSource.DEFAULT,
            BackgroundPalettePolicy.source(dynamicColors = true, followArtwork = false, hasManual = false, hasArtwork = true),
        )
        assertEquals(
            PaletteSource.MANUAL_OVERRIDDEN,
            BackgroundPalettePolicy.source(dynamicColors = false, followArtwork = true, hasManual = true, hasArtwork = true),
        )
        assertEquals(
            PaletteSource.DEFAULT_OVERRIDDEN,
            BackgroundPalettePolicy.source(dynamicColors = false, followArtwork = true, hasManual = false, hasArtwork = true),
        )
        // Something is playing but this surface opted out: not an override of the
        // global switch, so the caption must not blame it.
        assertEquals(
            PaletteSource.MANUAL,
            BackgroundPalettePolicy.source(dynamicColors = true, followArtwork = false, hasManual = true, hasArtwork = true),
        )
    }

    /* ------------------------------------------------------- colour safety */

    @Test
    fun everySurfaceColourStaysInsideTheContrastBand() {
        val accents = listOf(
            Color(0xFFBB86FC), Color(0xFF7FB3FF), Color(0xFF79E0B4), Color(0xFFFFC46B),
            Color(0xFFF08FB0), Color(0xFF8AD8E8), Color(0xFFFFFFFF), Color(0xFF000000),
        )
        for (tokens in listOf(dark, light)) {
            val themeBase = tokens.background.luminance()
            for (color in accents) {
                for (solid in listOf(true, false)) {
                    val palette = BackgroundPalettePolicy.paletteFrom(color, tokens, solid)
                    val drift = abs(palette.base.luminance() - themeBase)
                    assertTrue(
                        drift <= BackgroundPalettePolicy.BASE_LUMINANCE_DRIFT + 1e-3f,
                        "base drift $drift for $color (solid=$solid) exceeds the measured band",
                    )
                }
            }
        }
    }

    @Test
    fun aSolidSurfaceIsMoreColouredThanAWashOverArtwork() {
        // The whole point of "blur off = my colours": with nothing behind it a
        // 30% wash is invisible, so the solid base has to carry more of the hue.
        val solid = BackgroundPalettePolicy.paletteFrom(pickColor, dark, solid = true)
        val washed = BackgroundPalettePolicy.paletteFrom(pickColor, dark, solid = false)
        assertNotEquals(solid.base, washed.base)
        val solidTint = BackgroundPalettePolicy.SOLID_BASE_TINT_ALPHA
        val washTint = BackgroundPalettePolicy.BASE_TINT_ALPHA
        assertTrue(solidTint > washTint)
        // Both stay in the band: the extra is chroma, not brightness.
        val base = dark.background.luminance()
        assertTrue(abs(solid.base.luminance() - base) <= BackgroundPalettePolicy.BASE_LUMINANCE_DRIFT + 1e-3f)
    }

    @Test
    fun clampingIsIdempotentSoRepeatedReadsCannotDrift() {
        val palette = BackgroundPalettePolicy.paletteFrom(Color(0xFFFFFFFF), dark, solid = true)
        val once = BackgroundPalettePolicy.sanitize(palette, dark)
        val twice = BackgroundPalettePolicy.sanitize(once, dark)
        assertEquals(once, twice)
        assertEquals(once, BackgroundPalettePolicy.sanitize(twice, dark))
    }

    @Test
    fun glowsKeepAVisibleFloor() {
        val palette = BackgroundPalettePolicy.paletteFrom(Color(0x00FF00FF), dark, solid = true)
        assertTrue(palette.mainGlow.alpha >= BackgroundPalettePolicy.MIN_GLOW_ALPHA)
        assertTrue(palette.companionGlow.alpha >= BackgroundPalettePolicy.MIN_GLOW_ALPHA)
    }

    @Test
    fun theLightThemeGetsItsOwnPalettes() {
        val darkBase = BackgroundPalettePolicy.paletteFrom(pickColor, dark, solid = true).base
        val lightBase = BackgroundPalettePolicy.paletteFrom(pickColor, light, solid = true).base
        assertTrue(
            lightBase.luminance() > darkBase.luminance(),
            "the same pick must follow the theme it is painted on",
        )
    }

    /* ------------------------------------------------------------- blur */

    @Test
    fun theBlurSwitchDecidesWhetherArtworkIsPaintedAtAll() {
        // Blur off → no radius, on every surface, whatever the surface says.
        for (component in BackgroundComponent.entries) {
            assertEquals(
                0,
                BackgroundBlurPolicy.percentFor(
                    blurEnabled = false,
                    componentBlurEnabled = true,
                    adaptive = true,
                ),
            )
            assertFalse(
                BackgroundBlurPolicy.paintsArtwork(
                    BackgroundBlurPolicy.percentFor(false, true, true),
                ),
                "$component must not paint artwork with Background blur off",
            )
        }
        // Blur on + adaptive off → the shipped, steady frost.
        assertEquals(
            BackgroundBlurPolicy.STATIC_PERCENT,
            BackgroundBlurPolicy.percentFor(blurEnabled = true, componentBlurEnabled = true, adaptive = false),
        )
        assertTrue(
            BackgroundBlurPolicy.paintsArtwork(
                BackgroundBlurPolicy.percentFor(true, true, adaptive = false),
            ),
        )
    }

    @Test
    fun aSurfaceCanTurnBlurOffOnItsOwn() {
        assertEquals(
            0,
            BackgroundBlurPolicy.percentFor(blurEnabled = true, componentBlurEnabled = false, adaptive = true),
        )
        // …and that is not a colour decision.
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(artColor, dark, solid = true),
            resolve(dynamicColors = true, followArtwork = true, artwork = artColor, solid = true),
        )
    }

    @Test
    fun adaptiveBlurStaysInTheDocumentedBand() {
        assertEquals(BackgroundBlurPolicy.ADAPTIVE_BASE_PERCENT, BackgroundBlurPolicy.adaptivePercent(null, null))
        assertEquals(
            BackgroundBlurPolicy.ADAPTIVE_BASE_PERCENT,
            BackgroundBlurPolicy.adaptivePercent(0.5f, 0.5f),
            "neutral artwork keeps the shipped frost",
        )
        assertEquals(BackgroundBlurPolicy.ADAPTIVE_MAX_PERCENT, BackgroundBlurPolicy.adaptivePercent(1f, 1f))
        val darkCalm = BackgroundBlurPolicy.adaptivePercent(0f, 0f)
        assertEquals(BackgroundBlurPolicy.ADAPTIVE_MIN_PERCENT, darkCalm)
        assertTrue(darkCalm < BackgroundBlurPolicy.ADAPTIVE_MAX_PERCENT)
    }

    @Test
    fun strengthScalesButNeverSwitchesBlurOffOrOn() {
        val enabled = BackgroundBlurPolicy.STATIC_PERCENT
        assertEquals(0, BackgroundBlurPolicy.applyStrength(0, 100))
        // The slider cannot impersonate the off switch…
        assertTrue(BackgroundBlurPolicy.applyStrength(enabled, 0) >= 1)
        assertTrue(BackgroundBlurPolicy.applyStrength(enabled, 1) >= BackgroundBlurPolicy.MIN_EFFECTIVE_STRENGTH)
        // …and cannot exceed the radius it is scaling.
        assertEquals(enabled, BackgroundBlurPolicy.applyStrength(enabled, 100))
        assertTrue(BackgroundBlurPolicy.applyStrength(70, 100) <= 70)
    }

    /* ------------------------------------------------------- live holder */

    @Test
    fun blurOffRemovesTheArtworkWithoutTouchingTheColours() {
        DhunBackgrounds.restore(
            BackgroundState(
                dynamicColors = true,
                artworkPrimary = artColor,
                artworkLuminance = 0.6f,
                artworkSaturation = 0.7f,
            ),
        )
        val withBlur = DhunBackgrounds.paletteFor(BackgroundComponent.PAGES)

        DhunBackgrounds.setBlurEnabled(false)
        for (component in BackgroundComponent.entries) {
            assertFalse(
                DhunBackgrounds.showsArtwork(component),
                "$component must paint no artwork while Background blur is off",
            )
            // Colour still resolves — this is the "Dynamic colours on, blur off"
            // cell of the matrix, and it must not collapse to the default.
            assertEquals(
                BackgroundPalettePolicy.paletteFrom(artColor, DhunAppearance.tokens, solid = true),
                DhunBackgrounds.paletteFor(component),
            )
            assertTrue(DhunBackgrounds.usesArtworkColors(component))
            assertEquals(PaletteSource.ARTWORK, DhunBackgrounds.sourceFor(component))
        }
        assertNotEquals(
            withBlur.base,
            DhunBackgrounds.paletteFor(BackgroundComponent.PAGES).base,
            "with no artwork behind it the colour has to carry the surface",
        )
    }

    @Test
    fun theFourCombinationsResolveAsTheMatrixSays() {
        // Artwork playing first (restore replaces the whole snapshot), then a
        // solid pick on every surface.
        DhunBackgrounds.restore(BackgroundState(artworkPrimary = artColor))
        for (component in BackgroundComponent.entries) {
            DhunBackgrounds.setComponentColor(component, pickColor)
        }

        for (blur in listOf(false, true)) {
            for (dynamic in listOf(false, true)) {
                DhunBackgrounds.setBlurEnabled(blur)
                DhunBackgrounds.setDynamicColors(dynamic)
                for (component in BackgroundComponent.entries) {
                    assertEquals(
                        blur,
                        DhunBackgrounds.showsArtwork(component),
                        "$component: artwork must be painted exactly when blur is on",
                    )
                    val expected = if (dynamic) artColor else pickColor
                    assertEquals(
                        BackgroundPalettePolicy.paletteFrom(expected, DhunAppearance.tokens, solid = !blur),
                        DhunBackgrounds.paletteFor(component),
                        "$component: colour source for blur=$blur, dynamic=$dynamic",
                    )
                }
            }
        }
    }

    @Test
    fun everySurfaceKeepsItsOwnColour() {
        val pageColor = Color(0xFF7FB3FF)
        val navColor = Color(0xFF79E0B4)
        val miniColor = Color(0xFFFFC46B)
        val playerColor = Color(0xFFF08FB0)
        DhunBackgrounds.setComponentColor(BackgroundComponent.PAGES, pageColor)
        DhunBackgrounds.setComponentColor(BackgroundComponent.NAVIGATION, navColor)
        DhunBackgrounds.setComponentColor(BackgroundComponent.MINI_PLAYER, miniColor)
        DhunBackgrounds.setComponentColor(BackgroundComponent.FULL_PLAYER, playerColor)
        DhunBackgrounds.setDynamicColors(false)
        DhunBackgrounds.setBlurEnabled(false)

        val tokens = DhunAppearance.tokens
        assertEquals(BackgroundPalettePolicy.paletteFrom(pageColor, tokens, solid = true), DhunBackgrounds.paletteFor(BackgroundComponent.PAGES))
        assertEquals(BackgroundPalettePolicy.paletteFrom(navColor, tokens, solid = true), DhunBackgrounds.paletteFor(BackgroundComponent.NAVIGATION))
        assertEquals(BackgroundPalettePolicy.paletteFrom(miniColor, tokens, solid = true), DhunBackgrounds.paletteFor(BackgroundComponent.MINI_PLAYER))
        assertEquals(BackgroundPalettePolicy.paletteFrom(playerColor, tokens, solid = true), DhunBackgrounds.paletteFor(BackgroundComponent.FULL_PLAYER))

        // Changing one must not touch the others.
        DhunBackgrounds.setComponentColor(BackgroundComponent.NAVIGATION, null)
        assertEquals(
            BackgroundPalettePolicy.defaultPalette(BackgroundComponent.NAVIGATION, tokens),
            DhunBackgrounds.paletteFor(BackgroundComponent.NAVIGATION),
        )
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(pageColor, tokens, solid = true),
            DhunBackgrounds.paletteFor(BackgroundComponent.PAGES),
            "resetting the navigation bar must not repaint the pages",
        )
    }

    @Test
    fun oneSurfaceCanKeepArtworkColoursWhileAnotherDoesNot() {
        DhunBackgrounds.setDynamicColors(true)
        DhunBackgrounds.restore(BackgroundState(artworkPrimary = artColor))
        DhunBackgrounds.setComponentColor(BackgroundComponent.NAVIGATION, pickColor)
        DhunBackgrounds.setComponentFollowArtwork(BackgroundComponent.NAVIGATION, false)

        val tokens = DhunAppearance.tokens
        assertEquals(PaletteSource.MANUAL, DhunBackgrounds.sourceFor(BackgroundComponent.NAVIGATION))
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(pickColor, tokens, solid = false),
            DhunBackgrounds.paletteFor(BackgroundComponent.NAVIGATION),
        )
        assertEquals(PaletteSource.ARTWORK, DhunBackgrounds.sourceFor(BackgroundComponent.PAGES))
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(artColor, tokens, solid = false),
            DhunBackgrounds.paletteFor(BackgroundComponent.PAGES),
        )
    }

    @Test
    fun theSwitchOffSuspendsPerSurfaceChoicesInsteadOfClearingThem() {
        DhunBackgrounds.setComponentBlurEnabled(BackgroundComponent.MINI_PLAYER, false)
        DhunBackgrounds.setBlurEnabled(false)
        assertFalse(DhunBackgrounds.showsArtwork(BackgroundComponent.MINI_PLAYER))

        DhunBackgrounds.setBlurEnabled(true)
        assertFalse(
            DhunBackgrounds.showsArtwork(BackgroundComponent.MINI_PLAYER),
            "the dock's own switch must survive the global toggle",
        )
        assertTrue(DhunBackgrounds.showsArtwork(BackgroundComponent.PAGES))
    }

    @Test
    fun artworkChangesAndMissingArtworkAreHandled() {
        fun snapshot(artwork: Color?) = BackgroundState(
            dynamicColors = true,
            components = mapOf(BackgroundComponent.PAGES to ComponentBackgrounds(color = pickColor)),
            artworkPrimary = artwork,
        )

        DhunBackgrounds.restore(snapshot(artColor))
        val first = DhunBackgrounds.paletteFor(BackgroundComponent.PAGES)

        // A different track: a different colour, and the surface follows it.
        DhunBackgrounds.restore(snapshot(Color(0xFF3366CC)))
        val second = DhunBackgrounds.paletteFor(BackgroundComponent.PAGES)
        assertNotEquals(first.base, second.base)
        assertEquals(PaletteSource.ARTWORK, DhunBackgrounds.sourceFor(BackgroundComponent.PAGES))

        // No artwork (nothing playing, or an extraction that fell back to the
        // accent): the surface keeps the user's colour rather than a hole.
        DhunBackgrounds.restore(snapshot(null))
        assertEquals(PaletteSource.MANUAL, DhunBackgrounds.sourceFor(BackgroundComponent.PAGES))
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(pickColor, DhunAppearance.tokens, solid = false),
            DhunBackgrounds.paletteFor(BackgroundComponent.PAGES),
        )
        // …and with no colour either, the default.
        DhunBackgrounds.setComponentColor(BackgroundComponent.PAGES, null)
        assertEquals(
            BackgroundPalettePolicy.defaultPalette(BackgroundComponent.PAGES, DhunAppearance.tokens),
            DhunBackgrounds.paletteFor(BackgroundComponent.PAGES),
        )
    }

    @Test
    fun theSameSeedIsNeverReExtracted() {
        DhunBackgrounds.setArtworkSeed("https://example.test/one.jpg")
        val revision = DhunBackgrounds.current.revision
        DhunBackgrounds.setArtworkSeed("https://example.test/one.jpg")
        assertEquals(revision, DhunBackgrounds.current.revision, "a recomposition must not re-run extraction")
        DhunBackgrounds.setArtworkSeed(null)
        assertNull(DhunBackgrounds.current.artworkPrimary)
        assertNotEquals(revision, DhunBackgrounds.current.revision)
    }

    /* ---------------------------------------------------------- resets */

    @Test
    fun resettingOneSurfaceLeavesTheOthersAlone() {
        val manual = Color(0xFF334455)
        DhunBackgrounds.setComponentBlurEnabled(BackgroundComponent.PAGES, false)
        DhunBackgrounds.setComponentColor(BackgroundComponent.PAGES, manual)
        DhunBackgrounds.setComponentBlurEnabled(BackgroundComponent.FULL_PLAYER, false)
        DhunBackgrounds.setComponentColor(BackgroundComponent.FULL_PLAYER, manual)

        DhunBackgrounds.resetComponent(BackgroundComponent.PAGES)
        assertEquals(ComponentBackgrounds.default, DhunBackgrounds.current.component(BackgroundComponent.PAGES))
        assertEquals(manual, DhunBackgrounds.current.component(BackgroundComponent.FULL_PLAYER).color)
        assertFalse(DhunBackgrounds.current.component(BackgroundComponent.FULL_PLAYER).blurEnabled)
    }

    @Test
    fun resettingEverySurfaceKeepsTheGlobalSwitches() {
        DhunBackgrounds.setBlurEnabled(false)
        DhunBackgrounds.setDynamicColors(false)
        DhunBackgrounds.setComponentColor(BackgroundComponent.MINI_PLAYER, pickColor)
        DhunBackgrounds.resetComponents()
        assertFalse(DhunBackgrounds.current.blurEnabled)
        assertFalse(DhunBackgrounds.current.dynamicColors)
        assertEquals(ComponentBackgrounds.default, DhunBackgrounds.current.component(BackgroundComponent.MINI_PLAYER))
    }

    /* --------------------------------------------------------- encoding */

    @Test
    fun aColourRoundTripsThroughTheSettingsEncoding() {
        assertEquals("", BackgroundColorCodec.encode(null))
        assertNull(BackgroundColorCodec.decode(""))
        assertEquals(pickColor, BackgroundColorCodec.decode(BackgroundColorCodec.encode(pickColor)))
        assertEquals(Color(0xFF000000), BackgroundColorCodec.decode("#FF000000"), "a black pick is not 'unset'")
        // A hand-edited row is unset, never a crash.
        assertNull(BackgroundColorCodec.decode("not-a-colour"))
        assertNull(BackgroundColorCodec.decode("#12345"))
        // Six hex digits is a hand-edited row, not a colour: unset.
        assertNull(BackgroundColorCodec.decode("4fc3a1"))
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

    private suspend fun reloaded(d: DataLayer, scope: CoroutineScope): SettingsViewModel {
        val fresh = vm(d, scope)
        return loaded(fresh)
    }

    /** Waits for every pending preference write the ViewModel launched. */
    private suspend fun settled(scope: CoroutineScope) {
        scope.coroutineContext[Job]!!.children.toList().joinAll()
    }

    @Test
    fun aFreshInstallGetsTheDocumentedDefaults(): Unit = runBlocking {
        val d = data()
        val state = loaded(vm(d, this)).backgrounds.value
        assertTrue(state.blurEnabled)
        assertTrue(state.adaptiveBlur)
        assertTrue(state.dynamicColors)
        for (component in BackgroundComponent.entries) {
            assertEquals(ComponentBackgrounds.default, state.component(component))
        }
    }

    @Test
    fun everyBackgroundPreferenceSurvivesARestart(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))

        v.setBackgroundBlurEnabled(false)
        v.setBackgroundAdaptiveBlur(false)
        v.setBackgroundDynamicColors(false)
        v.setComponentBlurEnabled(BackgroundComponent.MINI_PLAYER, false)
        v.setComponentFollowArtwork(BackgroundComponent.FULL_PLAYER, false)
        v.setComponentColor(BackgroundComponent.NAVIGATION, pickColor)
        v.setComponentColor(BackgroundComponent.PAGES, Color(0xFF7FB3FF))
        settled(this)

        val restarted = reloaded(d, this)
        val state = restarted.backgrounds.value
        assertFalse(state.blurEnabled)
        assertFalse(state.adaptiveBlur)
        assertFalse(state.dynamicColors)
        assertFalse(state.component(BackgroundComponent.MINI_PLAYER).blurEnabled)
        assertFalse(state.component(BackgroundComponent.FULL_PLAYER).followArtwork)
        assertEquals(pickColor, state.component(BackgroundComponent.NAVIGATION).color)
        assertEquals(Color(0xFF7FB3FF), state.component(BackgroundComponent.PAGES).color)
        // The live holder was restored too, not just the settings copy.
        assertEquals(
            BackgroundPalettePolicy.paletteFrom(
                pickColor,
                DhunAppearance.tokens,
                solid = true,
            ),
            DhunBackgrounds.paletteFor(BackgroundComponent.NAVIGATION),
        )
        assertEquals(0, DhunBackgrounds.blurPercentFor(BackgroundComponent.PAGES, strengthPercent = 100))
    }

    @Test
    fun anOptimisticSwitchIsTheValueThatWasPersisted(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setBackgroundDynamicColors(false)
        assertFalse(v.backgrounds.value.dynamicColors)
        settled(this)
        reloaded(d, this)
        assertEquals("false", d.settings.getString(SettingsKeys.BACKGROUND_DYNAMIC_COLORS))
        assertFalse(v.backgrounds.value.dynamicColors)
    }

    @Test
    fun restoringBeforeTheFirstFrameNeverTakesTheLaunchDown(): Unit = runBlocking {
        val d = data()
        UpdateSettingUseCase(d.settings).boolean(SettingsKeys.BACKGROUND_DYNAMIC_COLORS, false)
        BackgroundPreferences.restoreBeforeFirstFrame(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
        )
        assertFalse(DhunBackgrounds.current.dynamicColors)

        DhunBackgrounds.clear()
        val broken = object : SettingsRepository by d.settings {
            override suspend fun getString(key: String): String? = error("database not ready")
        }
        BackgroundPreferences.restoreBeforeFirstFrame(
            GetSettingUseCase(broken),
            UpdateSettingUseCase(broken),
        )
        assertTrue(DhunBackgrounds.current.blurEnabled)
        assertTrue(DhunBackgrounds.current.dynamicColors)
    }

    @Test
    fun anUnwritableDatabaseStillLoadsTheStoredBackgrounds(): Unit = runBlocking {
        val d = data()
        UpdateSettingUseCase(d.settings).boolean(SettingsKeys.BACKGROUND_BLUR_ENABLED, false)
        val readOnly = object : SettingsRepository by d.settings {
            override suspend fun putString(key: String, value: String): Unit = error("disk full")
            override suspend fun putBoolean(key: String, value: Boolean): Unit = error("disk full")
            override suspend fun putInt(key: String, value: Int): Unit = error("disk full")
        }
        val state = BackgroundPreferences.read(
            GetSettingUseCase(readOnly),
            UpdateSettingUseCase(readOnly),
        )
        assertFalse(state.blurEnabled, "the stored value must survive an unwritable database")
        assertNull(
            d.settings.getString(SettingsKeys.BACKGROUND_PROFILE_VERSION),
            "nothing was written, so the import retries next launch",
        )
    }

    @Test
    fun resettingEverySettingAlsoClearsTheBackgroundRows(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setBackgroundBlurEnabled(false)
        v.setComponentColor(BackgroundComponent.PAGES, pickColor)
        v.reset()
        // Writes are asynchronous: observe the database only once they have all
        // landed, otherwise this asserts on a race instead of on the reset.
        settled(this)
        for (key in BackgroundPreferences.allKeys()) {
            assertNull(d.settings.getString(key), "$key should have been removed")
        }
        val restarted = reloaded(d, this).backgrounds.value
        assertTrue(restarted.blurEnabled)
        assertEquals(ComponentBackgrounds.default, restarted.component(BackgroundComponent.PAGES))
    }

    @Test
    fun resettingOneSurfacePersistsWithoutTouchingTheGlobalSwitches(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setBackgroundDynamicColors(false)
        v.setComponentBlurEnabled(BackgroundComponent.PAGES, false)
        v.setComponentColor(BackgroundComponent.PAGES, pickColor)

        v.resetComponentBackgrounds(BackgroundComponent.PAGES)
        assertEquals(ComponentBackgrounds.default, v.backgrounds.value.component(BackgroundComponent.PAGES))
        assertFalse(v.backgrounds.value.dynamicColors, "a surface reset must not re-enable a global switch")
        settled(this)

        val restarted = reloaded(d, this).backgrounds.value
        assertEquals(ComponentBackgrounds.default, restarted.component(BackgroundComponent.PAGES))
        assertFalse(restarted.dynamicColors)
    }

    @Test
    fun restoreAllClearsEverySurfaceColour(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        for (component in BackgroundComponent.entries) {
            v.setComponentFollowArtwork(component, false)
            v.setComponentColor(component, pickColor)
        }
        v.resetAllComponentBackgrounds()
        settled(this)
        v.backgrounds.value.resolvedComponents().forEach { (component, prefs) ->
            assertEquals(ComponentBackgrounds.default, prefs, "$component must be back to its defaults")
        }
        val restarted = reloaded(d, this).backgrounds.value
        restarted.resolvedComponents().forEach { (component, prefs) ->
            assertEquals(ComponentBackgrounds.default, prefs, "$component must stay reset after a restart")
        }
    }

    /* -------------------------------------------------------- migration */

    /**
     * Version 0: the slider era. `backdrop_blur = 0` was the only way to say "no
     * blur", so it becomes Background blur off — and the slider is reset, so
     * turning blur back on gives the real frost rather than the 20% floor.
     */
    @Test
    fun anOlderInstallWithBlurOffKeepsItOffOnce(): Unit = runBlocking {
        val d = data()
        val update = UpdateSettingUseCase(d.settings)
        update.int(SettingsKeys.BACKDROP_BLUR, 0)

        val first = loaded(vm(d, this)).backgrounds.value
        assertFalse(first.blurEnabled, "a stored 0 meant no blur and must be imported")
        assertEquals(
            SettingsKeys.BACKDROP_BLUR_DEFAULT,
            d.settings.getInt(SettingsKeys.BACKDROP_BLUR, -1),
        )

        // The user turns blur on; a later launch must not import 0 again.
        val v = loaded(vm(d, this))
        v.setBackgroundBlurEnabled(true)
        settled(this)
        assertEquals(
            SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT,
            d.settings.getInt(SettingsKeys.BACKGROUND_PROFILE_VERSION, 0),
        )
        val restarted = reloaded(d, this).backgrounds.value
        assertTrue(restarted.blurEnabled)
    }

    @Test
    fun theLegacySliderIsLeftAloneForUsersWhoNeverTouchedIt(): Unit = runBlocking {
        val d = data()
        val state = loaded(vm(d, this)).backgrounds.value
        // Defaults are the pre-feature behaviour: artwork blurred, artwork colours.
        assertEquals(SettingsKeys.BACKGROUND_BLUR_ENABLED_DEFAULT, state.blurEnabled)
        assertEquals(SettingsKeys.BACKGROUND_DYNAMIC_BLUR_DEFAULT, state.adaptiveBlur)
        assertEquals(SettingsKeys.BACKGROUND_DYNAMIC_COLORS_DEFAULT, state.dynamicColors)
    }

    /**
     * Version 1: five sections × three palette layers. The rows must land in the
     * component model without losing the two decisions the user made (blur on
     * this surface, artwork colours on this surface) or the hue they picked.
     */
    @Test
    fun versionOneSectionPalettesMigrateIntoTheSurfaceModel(): Unit = runBlocking {
        val d = data()
        val s = d.settings
        fun legacy(section: String, field: String, value: String) =
            s.putString(SettingsKeys.backgroundKey(section, field), value)

        // Home: artwork colours off, blur on, and a violet bloom the user chose.
        legacy("home", SettingsKeys.BACKGROUND_FIELD_BLUR, "true")
        legacy("home", SettingsKeys.BACKGROUND_FIELD_DYNAMIC_COLORS, "false")
        legacy("home", SettingsKeys.BACKGROUND_FIELD_PRIMARY, "#FF141118")
        legacy("home", SettingsKeys.BACKGROUND_FIELD_SECONDARY, "#FFBB86FC")
        legacy("home", SettingsKeys.BACKGROUND_FIELD_TERTIARY, "#FF9C7BE8")
        // The dock: blur off.
        legacy("mini", SettingsKeys.BACKGROUND_FIELD_BLUR, "false")
        legacy("mini", SettingsKeys.BACKGROUND_FIELD_SECONDARY, "#FF4FC3A1")
        // Search/Library were never touched, so they must not overwrite Home.
        s.putInt(SettingsKeys.BACKGROUND_PROFILE_VERSION, 1)

        val state = BackgroundPreferences.read(GetSettingUseCase(s), UpdateSettingUseCase(s))
        val pages = state.component(BackgroundComponent.PAGES)
        assertEquals(Color(0xFFBB86FC), pages.color, "the chosen hue (secondary) becomes the surface colour")
        assertFalse(pages.followArtwork)
        assertTrue(pages.blurEnabled)
        val mini = state.component(BackgroundComponent.MINI_PLAYER)
        assertFalse(mini.blurEnabled)
        assertEquals(Color(0xFF4FC3A1), mini.color)
        // The navigation bar had no version-1 equivalent: it starts at its default.
        assertEquals(ComponentBackgrounds.default, state.component(BackgroundComponent.NAVIGATION))
        // The import wrote the new shape and the marker, so it runs once.
        assertEquals(
            SettingsKeys.BACKGROUND_PROFILE_VERSION_CURRENT,
            s.getInt(SettingsKeys.BACKGROUND_PROFILE_VERSION, 0),
        )
        assertEquals(
            "#FF4FC3A1",
            s.getString(
                SettingsKeys.backgroundComponentKey(
                    BackgroundComponent.MINI_PLAYER.id,
                    SettingsKeys.BACKGROUND_FIELD_COLOR,
                ),
            ),
        )
    }

    @Test
    fun theVersionOneImportDoesNotOverwriteALaterChoice(): Unit = runBlocking {
        val d = data()
        d.settings.putString(
            SettingsKeys.backgroundKey("home", SettingsKeys.BACKGROUND_FIELD_SECONDARY),
            "#FFBB86FC",
        )
        d.settings.putInt(SettingsKeys.BACKGROUND_PROFILE_VERSION, 1)
        val v = loaded(vm(d, this))
        assertEquals(Color(0xFFBB86FC), v.backgrounds.value.component(BackgroundComponent.PAGES).color)

        v.setComponentColor(BackgroundComponent.PAGES, pickColor)
        settled(this)
        val restarted = reloaded(d, this).backgrounds.value
        assertEquals(pickColor, restarted.component(BackgroundComponent.PAGES).color, "the import must run once")
    }

    @Test
    fun theVersionOneImportIsSkippedOnAFreshInstall(): Unit = runBlocking {
        val d = data()
        val state = BackgroundPreferences.read(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings))
        for (component in BackgroundComponent.entries) {
            assertEquals(
                ComponentBackgrounds.default,
                state.component(component),
                "$component must not gain a colour nobody chose",
            )
        }
    }

    @Test
    fun aComponentColourRowThatIsNotAColourIsUnsetNotACrash(): Unit = runBlocking {
        val d = data()
        d.settings.putString(
            SettingsKeys.backgroundComponentKey(BackgroundComponent.PAGES.id, SettingsKeys.BACKGROUND_FIELD_COLOR),
            "not-a-colour",
        )
        val state = BackgroundPreferences.read(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings))
        assertNull(state.component(BackgroundComponent.PAGES).color)
        assertEquals(
            BackgroundPalettePolicy.defaultPalette(BackgroundComponent.PAGES, DhunAppearance.tokens),
            DhunBackgrounds.paletteFor(BackgroundComponent.PAGES),
        )
    }
}
