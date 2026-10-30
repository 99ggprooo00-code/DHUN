package dev.dhun.presentation.settings

import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.design.VisualPart
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
import dev.dhun.player.AudioCacheBudget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings persistence, in both directions.
 *
 * Two defects drove this file:
 *
 * 1. **A partial load published a mixed snapshot.** The load assigned each
 *    flow as it read, so a read that failed halfway left earlier keys applied
 *    beside defaults for the rest — a combination the user never chose, which
 *    then reverted on the next launch. The load now reads everything first and
 *    commits once.
 * 2. **A failed write left the screen lying.** Controls publish immediately (a
 *    slider must follow the finger), and the failure was reported, but the
 *    unsaved value stayed on screen until the next launch. The stored value is
 *    read back and republished now.
 *
 * Each test below fails on the previous implementation.
 */
class SettingsPersistenceTest {

    private fun data() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    /**
     * A store whose writes all fail, the way a full disk fails. Every typed
     * write is overridden: [SettingsRepository.putBoolean] and `putInt`
     * delegate to [SettingsRepository.putString] *inside the real object*, so
     * overriding only `putString` would let `UpdateSettingUseCase.boolean/int`
     * succeed through the delegate and hide the rollback under test.
     */
    private fun readOnly(settings: SettingsRepository) = object : SettingsRepository by settings {
        override suspend fun putString(key: String, value: String) = error("disk full")
        override suspend fun putBoolean(key: String, value: Boolean) = error("disk full")
        override suspend fun putInt(key: String, value: Int) = error("disk full")
    }

    /** A store whose deletes all fail (a read-only database). */
    private fun immutable(settings: SettingsRepository) = object : SettingsRepository by settings {
        override suspend fun remove(key: String): Unit = error("database is read-only")
    }

    private class Harness {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, error -> uncaught += error },
        )

        fun close() = scope.cancel()
    }

    private suspend fun eventually(timeoutMs: Long = 10_000, check: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(10) }
    }

    @Test
    fun aMidLoadFailurePublishesNothingAtAllNotEvenTheKeysThatSucceeded(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        d.settings.putString(SettingsKeys.THEME, "light")
        d.settings.putString(SettingsKeys.ACCENT, "violet")
        val reachedAccent = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // The first key reads fine; the second one blocks and then fails, the
        // shape of a database that becomes unavailable mid-load.
        val brokenSettings = object : SettingsRepository by d.settings {
            override suspend fun getString(key: String): String? {
                if (key == SettingsKeys.ACCENT) {
                    reachedAccent.complete(Unit)
                    release.await()
                    error("database became unavailable")
                }
                return d.settings.getString(key)
            }
        }
        val vm = SettingsViewModel(
            GetSettingUseCase(brokenSettings),
            UpdateSettingUseCase(brokenSettings),
            h.scope,
        )
        try {
            reachedAccent.await()
            // THEME has already been read successfully at this point. Nothing
            // may be published yet: the load is one snapshot or none.
            assertEquals(
                SettingsKeys.THEME_DEFAULT,
                vm.themeId.value,
                "a value read before a later failure must never reach the UI",
            )
            assertEquals(SettingsKeys.ACCENT_DEFAULT, vm.accentId.value)
            assertFalse(vm.loaded.value, "loaded is only published with a complete snapshot")

            release.complete(Unit)
            eventually { vm.loaded.value }
            assertEquals(SettingsKeys.THEME_DEFAULT, vm.themeId.value)
            assertEquals(SettingsKeys.ACCENT_DEFAULT, vm.accentId.value)
            assertEquals(
                SettingsViewModel.SETTINGS_LOAD_FAILED,
                vm.maintenanceState.value.screenErrorMessage,
            )
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }

    @Test
    fun aCompleteLoadAppliesEveryStoredValue(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        d.settings.putString(SettingsKeys.THEME, "light")
        d.settings.putString(SettingsKeys.AUDIO_QUALITY, "low")
        d.settings.putBoolean(SettingsKeys.RESUME_ON_LAUNCH, false)
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, 1024)
        d.settings.putBoolean(SettingsKeys.PAUSE_ON_MUTE_OR_ZERO_VOLUME, false)
        val vm = SettingsViewModel(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings), h.scope)
        try {
            eventually { vm.loaded.value }
            assertEquals("light", vm.themeId.value)
            assertEquals("low", vm.audioQuality.value)
            assertFalse(vm.resumeOnLaunch.value)
            assertEquals(1024, vm.cacheSizeMb.value)
            assertFalse(vm.pauseOnMute.value)
            assertNull(vm.maintenanceState.value.errorMessage, "a clean load reports nothing")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedWriteRepublishesTheValueThatIsActuallyStored(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        d.settings.putString(SettingsKeys.THEME, "black")
        val readOnlySettings = readOnly(d.settings)
        val vm = SettingsViewModel(GetSettingUseCase(readOnlySettings), UpdateSettingUseCase(readOnlySettings), h.scope)
        try {
            eventually { vm.loaded.value }
            assertEquals("black", vm.themeId.value)

            vm.setThemeId("light")
            eventually { vm.maintenanceState.value.errorMessage != null }
            // The control went back to what the database actually holds, so the
            // screen never claims a value that was not saved.
            assertEquals("black", vm.themeId.value)
            assertEquals(SettingsViewModel.SAVE_FAILED, vm.maintenanceState.value.screenErrorMessage)
            assertEquals("black", d.settings.getString(SettingsKeys.THEME))
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedWriteWithNothingStoredFallsBackToTheDocumentedDefault(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val readOnlySettings = readOnly(d.settings)
        val vm = SettingsViewModel(GetSettingUseCase(readOnlySettings), UpdateSettingUseCase(readOnlySettings), h.scope)
        try {
            eventually { vm.loaded.value }
            vm.setAudioQuality("low")
            eventually { vm.maintenanceState.value.errorMessage != null }
            assertEquals(
                SettingsKeys.AUDIO_QUALITY_DEFAULT,
                vm.audioQuality.value,
                "no stored value means the documented default, not the unsaved tap",
            )
        } finally {
            h.close()
        }
    }

    @Test
    fun failedIntAndBooleanWritesRollBackToo(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, 512)
        d.settings.putBoolean(SettingsKeys.RESUME_ON_LAUNCH, true)
        val readOnlySettings = readOnly(d.settings)
        val vm = SettingsViewModel(GetSettingUseCase(readOnlySettings), UpdateSettingUseCase(readOnlySettings), h.scope)
        try {
            eventually { vm.loaded.value }
            assertEquals(512, vm.cacheSizeMb.value)

            vm.setCacheSizeMb(4096)
            eventually { vm.maintenanceState.value.errorMessage != null }
            assertEquals(512, vm.cacheSizeMb.value)
            vm.dismissMaintenanceError()

            vm.setResumeOnLaunch(false)
            eventually { vm.maintenanceState.value.errorMessage != null }
            assertTrue(vm.resumeOnLaunch.value, "the stored true is what survives a failed write")
        } finally {
            h.close()
        }
    }

    @Test
    fun aSuccessfulWriteKeepsTheNewValueAndPersistsIt(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val vm = SettingsViewModel(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings), h.scope)
        try {
            eventually { vm.loaded.value }
            vm.setCacheSizeMb(2048)
            // Wait for the queued write to reach the database.
            eventually { d.settings.getInt(SettingsKeys.CACHE_SIZE_MB, -1) == 2048 }
            assertEquals(2048, vm.cacheSizeMb.value)
            assertNull(vm.maintenanceState.value.errorMessage)

            vm.setThemeId("light")
            eventually { d.settings.getString(SettingsKeys.THEME) == "light" }
            assertEquals("light", vm.themeId.value)
        } finally {
            h.close()
        }
    }

    @Test
    fun resetRestoresEveryDocumentedDefault(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val vm = SettingsViewModel(GetSettingUseCase(d.settings), UpdateSettingUseCase(d.settings), h.scope)
        try {
            eventually { vm.loaded.value }
            vm.setThemeId("light")
            vm.setCacheSizeMb(4096)
            vm.setResumeOnLaunch(false)
            vm.setAudioQuality("low")
            eventually { d.settings.getString(SettingsKeys.THEME) == "light" }
            eventually { d.settings.getInt(SettingsKeys.CACHE_SIZE_MB, -1) == 4096 }
            eventually { d.settings.getBoolean(SettingsKeys.RESUME_ON_LAUNCH, true).not() }
            eventually { d.settings.getString(SettingsKeys.AUDIO_QUALITY) == "low" }

            vm.reset()
            eventually { d.settings.getString(SettingsKeys.THEME) == null }
            assertEquals(SettingsKeys.THEME_DEFAULT, vm.themeId.value)
            assertEquals(SettingsKeys.CACHE_SIZE_MB_DEFAULT, vm.cacheSizeMb.value)
            assertEquals(SettingsKeys.RESUME_ON_LAUNCH_DEFAULT, vm.resumeOnLaunch.value)
            assertEquals(SettingsKeys.AUDIO_QUALITY_DEFAULT, vm.audioQuality.value)
            assertEquals(SettingsKeys.LYRICS_PROVIDER_ORDER_DEFAULT, vm.providerOrder.value)
            assertEquals(SettingsKeys.COUNTRY_CODE_DEFAULT, vm.countryCode.value)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedResetPutsBackTheValuesThatAreStillStored(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        d.settings.putString(SettingsKeys.THEME, "light")
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, 4096)
        val immutableSettings = immutable(d.settings)
        val vm = SettingsViewModel(GetSettingUseCase(immutableSettings), UpdateSettingUseCase(immutableSettings), h.scope)
        try {
            eventually { vm.loaded.value }
            assertEquals("light", vm.themeId.value)
            assertEquals(4096, vm.cacheSizeMb.value)

            vm.reset()
            eventually { vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_RESET) != null }
            // Reset flips every control at once, but nothing was removed: the
            // screen must show what is really stored, not the defaults it
            // briefly pretended to have applied.
            assertEquals("light", vm.themeId.value)
            assertEquals(4096, vm.cacheSizeMb.value)
            assertEquals("light", d.settings.getString(SettingsKeys.THEME))
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }

    @Test
    fun resetAlsoRestoresTheLiveEqualizerAfterTheRowsAreGone(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        d.settings.putBoolean(SettingsKeys.EQUALIZER_ENABLED, true)
        d.settings.putString(SettingsKeys.EQUALIZER_PRESET, "bass_boost")
        var engineResets = 0
        var presetStillStoredWhenEngineReset: Boolean? = null
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            resetEqualizerAction = {
                engineResets++
                presetStillStoredWhenEngineReset = d.settings.getString(SettingsKeys.EQUALIZER_PRESET) != null
            },
        )
        try {
            eventually { vm.loaded.value }
            vm.reset()
            eventually { vm.lastCompletedKey.value == SettingsViewModel.KEY_RESET }
            // The EQ rows are settings, but the engine keeps its own state: a
            // reset that only deleted the rows left the panel on the old preset
            // and the next slider move wrote it straight back.
            assertEquals(1, engineResets, "reset must restore the live engine exactly once")
            assertEquals(
                false,
                presetStillStoredWhenEngineReset,
                "the saved rows are gone before the engine is reset, so its defaults are what persists",
            )
            assertNull(d.settings.getString(SettingsKeys.EQUALIZER_PRESET))
            assertNull(vm.maintenanceState.value.errorMessage)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedEqualizerResetIsReportedAndNotCountedAsDone(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            resetEqualizerAction = { error("equalizer engine unavailable") },
        )
        try {
            eventually { vm.loaded.value }
            vm.reset()
            eventually { vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_RESET) != null }
            assertEquals(
                SettingsViewModel.RESET_FAILED,
                vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_RESET),
            )
            assertNull(vm.lastCompletedKey.value, "a half-applied reset must not close its dialog")
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedVisualTuningSavePutsBackTheStoredExperiment(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val part = VisualPart.LIST_ROWS
        d.settings.putInt(part.key, 70)
        val readOnlySettings = readOnly(d.settings)
        val vm = SettingsViewModel(GetSettingUseCase(readOnlySettings), UpdateSettingUseCase(readOnlySettings), h.scope)
        try {
            eventually { vm.loaded.value }
            assertEquals(70, vm.visualTuning.value[part])

            vm.previewVisualTuning(part, 95)
            assertEquals(95, vm.visualTuning.value[part], "the slider follows the drag")
            vm.saveVisualTuning(part)
            eventually { vm.maintenanceState.value.errorMessage != null }
            assertEquals(70, vm.visualTuning.value[part], "an unsaved experiment must not stay applied")
            assertEquals(70, d.settings.getInt(part.key, -1))
        } finally {
            h.close()
        }
    }

    @Test
    fun anInvalidStoredCacheBudgetIsClampedOnLoadAndOnRollback(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        // A value written by an older build (or a corrupt row) must not reach
        // the UI as-is; the documented budget ladder clamps it.
        d.settings.putInt(SettingsKeys.CACHE_SIZE_MB, 999_999)
        val readOnlySettings = readOnly(d.settings)
        val vm = SettingsViewModel(GetSettingUseCase(readOnlySettings), UpdateSettingUseCase(readOnlySettings), h.scope)
        try {
            eventually { vm.loaded.value }
            assertEquals(AudioCacheBudget.clampMb(999_999), vm.cacheSizeMb.value)

            vm.setCacheSizeMb(256)
            eventually { vm.maintenanceState.value.errorMessage != null }
            assertEquals(
                AudioCacheBudget.clampMb(999_999),
                vm.cacheSizeMb.value,
                "the rollback uses the same clamp as the load",
            )
        } finally {
            h.close()
        }
    }
}
