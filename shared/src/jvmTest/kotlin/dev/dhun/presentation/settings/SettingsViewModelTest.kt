package dev.dhun.presentation.settings

import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.EpochClock
import dev.dhun.data.SettingsKeys
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.player.AudioCacheBudget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S4 settings model, exercised through the real settings repository on an
 * in-memory DB (same pattern as `UseCasesTest`).
 */
class SettingsViewModelTest {

    private class FakeClock(var now: Long = 1_700_000_000_000L) : EpochClock {
        override fun nowMs(): Long = now
    }

    private fun data() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()), FakeClock())

    private fun vm(d: DataLayer, scope: kotlinx.coroutines.CoroutineScope) =
        SettingsViewModel(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings), scope)

    private suspend fun loaded(vm: SettingsViewModel): SettingsViewModel {
        vm.loaded.first { it }
        return vm
    }

    private suspend fun reloaded(d: DataLayer, scope: CoroutineScope): SettingsViewModel {
        // UI-facing flows are optimistic. A fresh ViewModel must not read the
        // DB until the asynchronous preference writes under test have finished.
        scope.coroutineContext[Job]!!.children.toList().joinAll()
        return loaded(vm(d, scope))
    }

    @Test
    fun freshDbExposesDocumentedDefaults(): Unit = runBlocking {
        val v = loaded(vm(data(), this))
        assertEquals(SettingsKeys.THEME_DEFAULT, v.themeId.value)
        assertEquals(SettingsKeys.ACCENT_DEFAULT, v.accentId.value)
        assertEquals(SettingsKeys.CACHE_SIZE_MB_DEFAULT, v.cacheSizeMb.value)
        assertEquals(SettingsKeys.RESUME_ON_LAUNCH_DEFAULT, v.resumeOnLaunch.value)
        assertEquals(SettingsKeys.CLOSE_TO_TRAY_DEFAULT, v.closeToTray.value)
        assertEquals(SettingsKeys.BACKDROP_BLUR_DEFAULT, v.backdropBlurPercent.value)
        assertEquals(SettingsKeys.BACKDROP_BRIGHTNESS_DEFAULT, v.backdropBrightnessPercent.value)
    }

    @Test
    fun themeRoundTripsAndRejectsUnknownIds(): Unit = runBlocking {
        val d = data()
        val v = reloaded(d, this)
        v.setThemeId("light")
        assertEquals("light", v.themeId.value)
        v.setThemeId("system")
        assertEquals("system", v.themeId.value)
        v.setThemeId("amoled???")
        assertEquals("system", v.themeId.value)
        assertEquals("system", reloaded(d, this).themeId.value)
    }

    @Test
    fun accentRoundTripsAndRejectsBlanks(): Unit = runBlocking {
        val d = data()
        val v = reloaded(d, this)
        v.setAccentId("violet")
        assertEquals("violet", v.accentId.value)
        v.setAccentId("  ")
        assertEquals("violet", v.accentId.value)
        assertEquals("violet", reloaded(d, this).accentId.value)
    }

    @Test
    fun rapidEditsCannotOverwriteResetAndNewEditsFollowReset(): Unit = runBlocking {
        val d = data()
        val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val v = loaded(vm(d, background))
            repeat(12) {
                v.setBackdropBrightnessPercent(it)
                v.setThemeId("light")
            }
            v.reset()
            background.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(null, d.settings.getString(SettingsKeys.THEME))
            assertEquals(null, d.settings.getString(SettingsKeys.BACKDROP_BRIGHTNESS))
            v.setThemeId("light")
            background.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals("light", d.settings.getString(SettingsKeys.THEME))
        } finally { background.cancel() }
    }

    @Test
    fun resetAlsoRemovesPersistedEqualizerCurve(): Unit = runBlocking {
        val d = data()
        d.settings.putBoolean(SettingsKeys.EQUALIZER_ENABLED, true)
        d.settings.putString(SettingsKeys.EQUALIZER_GAINS_DB, List(10) { "3" }.joinToString(","))
        val v = reloaded(d, this)
        v.reset()
        // Reset dispatches its DB writes asynchronously; observe completion.
        withTimeout(3_000) {
            while (d.settings.getString(SettingsKeys.EQUALIZER_ENABLED) != null ||
                d.settings.getString(SettingsKeys.EQUALIZER_GAINS_DB) != null) delay(10)
        }
        assertEquals(null, d.settings.getString(SettingsKeys.EQUALIZER_ENABLED))
        assertEquals(null, d.settings.getString(SettingsKeys.EQUALIZER_GAINS_DB))
    }

    @Test
    fun newPreferencesPersistAndReset(): Unit = runBlocking {
        val d = data()
        val v = reloaded(d, this)
        v.setAudioQuality("low")
        v.setSeekStepSeconds(15)
        v.setAutoplayRadio(false)
        v.setLyricsEnabled(false)
        v.setAccentMode("static")
        var providerAppliedAfterPersist = false
        v.setPreferredProvider("kugou") { providerAppliedAfterPersist = true }
        v.setProviderEnabled("ytm", false)
        v.setCountryCode("np")
        v.setCountryCode("ZZ") // unsupported region is ignored
        assertEquals("low", reloaded(d, this).audioQuality.value)
        assertEquals(15, reloaded(d, this).seekStepSeconds.value)
        assertFalse(reloaded(d, this).autoplayRadio.value)
        assertFalse(reloaded(d, this).lyricsEnabled.value)
        assertEquals("static", reloaded(d, this).accentMode.value)
        assertEquals("kugou", reloaded(d, this).preferredProvider.value)
        assertTrue(providerAppliedAfterPersist)
        assertEquals("NP", v.countryCode.value)
        assertEquals("NP", reloaded(d, this).countryCode.value)
        assertFalse("ytm" in reloaded(d, this).providerOrder.value.split(','))
        v.reset()
        assertEquals(SettingsKeys.AUDIO_QUALITY_DEFAULT, reloaded(d, this).audioQuality.value)
    }

    @Test
    fun cacheBudgetRoundTripsAndRejectsOffLadderValues(): Unit = runBlocking {
        val d = data()
        val v = reloaded(d, this)
        v.setCacheSizeMb(512)
        assertEquals(512, v.cacheSizeMb.value)
        v.setCacheSizeMb(123) // not on the ladder
        assertEquals(512, v.cacheSizeMb.value)
        v.setCacheSizeMb(0) // the documented unlimited sentinel
        assertEquals(0, v.cacheSizeMb.value)
        assertEquals(0, reloaded(d, this).cacheSizeMb.value)
    }

    @Test
    fun corruptPersistedBudgetLoadsClamped(): Unit = runBlocking {
        val d = data()
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, 999_999)
        assertEquals(AudioCacheBudget.MAX_MB, reloaded(d, this).cacheSizeMb.value)
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, -5)
        assertEquals(AudioCacheBudget.DEFAULT_MB, reloaded(d, this).cacheSizeMb.value)
    }

    @Test
    fun switchesRoundTrip(): Unit = runBlocking {
        val d = data()
        val v = reloaded(d, this)
        v.setResumeOnLaunch(false)
        v.setCloseToTray(false)
        assertFalse(v.resumeOnLaunch.value)
        assertFalse(v.closeToTray.value)
        val v2 = reloaded(d, this)
        assertFalse(v2.resumeOnLaunch.value)
        assertFalse(v2.closeToTray.value)
    }

    @Test
    fun countryRegionPickerHasFullNamesAndAllIsoCodes(): Unit = runBlocking {
        assertEquals(249, CountryRegions.all.size)
        assertEquals(249, CountryRegions.all.map { it.code }.distinct().size)
        assertEquals("Nepal", CountryRegions.find("NP")?.name)
        assertEquals("NP", CountryRegions.search("nepal").single().code)
        assertTrue(CountryRegions.search("nz").any { it.code == "NZ" })

        val d = data()
        d.settings.putString(SettingsKeys.COUNTRY_CODE, "ZZ")
        val v = reloaded(d, this)
        assertEquals(SettingsKeys.COUNTRY_CODE_DEFAULT, v.countryCode.value)
        v.setCountryCode("np")
        assertEquals("NP", reloaded(d, this).countryCode.value)
        v.setCountryCode("ZZ")
        assertEquals("NP", reloaded(d, this).countryCode.value)
    }

    @Test
    fun cacheLadderSurvivesTheBudgetClampUnchanged(): Unit {
        SettingsViewModel.CACHE_OPTIONS_MB.forEach { mb ->
            assertEquals(mb, AudioCacheBudget.clampMb(mb), "ladder entry $mb must not clamp")
        }
        assertEquals("Unlimited", SettingsViewModel.cacheOptionLabel(0))
        assertEquals("512 MB", SettingsViewModel.cacheOptionLabel(512))
        assertEquals("1 GB", SettingsViewModel.cacheOptionLabel(1024))
        assertEquals("4 GB", SettingsViewModel.cacheOptionLabel(4096))
        assertTrue(SettingsKeys.ACCENT in SettingsKeys.all)
        assertTrue(SettingsKeys.BACKDROP_BLUR in SettingsKeys.all)
        assertTrue(SettingsKeys.BACKDROP_BRIGHTNESS in SettingsKeys.all)
    }

    @Test
    fun backdropPrefsRoundTripAndClamp(): Unit = runBlocking {
        val d = data()
        val v = reloaded(d, this)
        v.setBackdropBlurPercent(40)
        v.setBackdropBrightnessPercent(20)
        assertEquals(40, v.backdropBlurPercent.value)
        assertEquals(20, v.backdropBrightnessPercent.value)
        v.setBackdropBlurPercent(250)
        v.setBackdropBrightnessPercent(-4)
        assertEquals(100, v.backdropBlurPercent.value)
        assertEquals(0, v.backdropBrightnessPercent.value)
        val v2 = reloaded(d, this)
        assertEquals(100, v2.backdropBlurPercent.value)
        assertEquals(0, v2.backdropBrightnessPercent.value)
    }
}
