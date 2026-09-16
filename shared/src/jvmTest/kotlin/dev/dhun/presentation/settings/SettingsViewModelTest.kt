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
import kotlinx.coroutines.runBlocking
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

    @Test
    fun freshDbExposesDocumentedDefaults(): Unit = runBlocking {
        val v = loaded(vm(data(), this))
        assertEquals(SettingsKeys.THEME_DEFAULT, v.themeId.value)
        assertEquals(SettingsKeys.ACCENT_DEFAULT, v.accentId.value)
        assertEquals(SettingsKeys.CACHE_SIZE_MB_DEFAULT, v.cacheSizeMb.value)
        assertEquals(SettingsKeys.RESUME_ON_LAUNCH_DEFAULT, v.resumeOnLaunch.value)
        assertEquals(SettingsKeys.CLOSE_TO_TRAY_DEFAULT, v.closeToTray.value)
    }

    @Test
    fun themeRoundTripsAndRejectsUnknownIds(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setThemeId("light")
        assertEquals("light", v.themeId.value)
        v.setThemeId("system") // storable in the DB schema, but not a real mode
        assertEquals("light", v.themeId.value)
        v.setThemeId("amoled???")
        assertEquals("light", v.themeId.value)
        // A second instance reads what the first wrote.
        assertEquals("light", loaded(vm(d, this)).themeId.value)
    }

    @Test
    fun accentRoundTripsAndRejectsBlanks(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setAccentId("violet")
        assertEquals("violet", v.accentId.value)
        v.setAccentId("  ")
        assertEquals("violet", v.accentId.value)
        assertEquals("violet", loaded(vm(d, this)).accentId.value)
    }

    @Test
    fun cacheBudgetRoundTripsAndRejectsOffLadderValues(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setCacheSizeMb(512)
        assertEquals(512, v.cacheSizeMb.value)
        v.setCacheSizeMb(123) // not on the ladder
        assertEquals(512, v.cacheSizeMb.value)
        v.setCacheSizeMb(0) // the documented unlimited sentinel
        assertEquals(0, v.cacheSizeMb.value)
        assertEquals(0, loaded(vm(d, this)).cacheSizeMb.value)
    }

    @Test
    fun corruptPersistedBudgetLoadsClamped(): Unit = runBlocking {
        val d = data()
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, 999_999)
        assertEquals(AudioCacheBudget.MAX_MB, loaded(vm(d, this)).cacheSizeMb.value)
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, -5)
        assertEquals(AudioCacheBudget.DEFAULT_MB, loaded(vm(d, this)).cacheSizeMb.value)
    }

    @Test
    fun switchesRoundTrip(): Unit = runBlocking {
        val d = data()
        val v = loaded(vm(d, this))
        v.setResumeOnLaunch(false)
        v.setCloseToTray(false)
        assertFalse(v.resumeOnLaunch.value)
        assertFalse(v.closeToTray.value)
        val v2 = loaded(vm(d, this))
        assertFalse(v2.resumeOnLaunch.value)
        assertFalse(v2.closeToTray.value)
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
    }
}
