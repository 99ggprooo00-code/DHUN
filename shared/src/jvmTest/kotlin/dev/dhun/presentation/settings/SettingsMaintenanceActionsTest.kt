package dev.dhun.presentation.settings

import dev.dhun.data.DataLayer
import dev.dhun.data.DatabaseDriverFactory
import dev.dhun.data.DatabaseFactory
import dev.dhun.data.SettingsKeys
import dev.dhun.data.SettingsRepository
import dev.dhun.domain.GetSettingUseCase
import dev.dhun.domain.UpdateSettingUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The destructive Settings actions, verified as *result-driven* rather than
 * tap-driven.
 *
 * The audit's finding (recorded in `.ai/KNOWN_LIMITATIONS.md`): "Native Settings
 * history/lyrics/audio-cache clear controls exist, but some dismiss before
 * completion or lack uniform error feedback". `clearLyricsCache`,
 * `clearSearchHistory` and `clearPlayHistory` launched the action with no
 * failure handling at all, so a failed clear closed its confirmation exactly
 * like a successful one; reset and every preference write could let an
 * exception escape the coroutine.
 *
 * Each test here fails on that code and passes on the current one.
 */
class SettingsMaintenanceActionsTest {

    private fun data() =
        DataLayer(DatabaseFactory.create(DatabaseDriverFactory.inMemory().createDriver()))

    /** A scope that records anything escaping a coroutine instead of hiding it. */
    private class Harness {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, error -> uncaught += error },
        )

        fun close() = scope.cancel()
    }

    private suspend fun eventually(timeoutMs: Long = 10_000, check: () -> Boolean) {
        withTimeout(timeoutMs) { while (!check()) delay(10) }
    }

    @Test
    fun aFailedPlayHistoryClearKeepsItsDialogOpenAndOffersARetry(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var attempts = 0
        var failing = true
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            clearPlayHistoryAction = {
                attempts++
                if (failing) error("database is locked")
            },
        )
        try {
            vm.clearPlayHistory()
            eventually { vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_PLAY) != null }
            assertEquals(
                SettingsViewModel.PLAY_HISTORY_CLEAR_FAILED,
                vm.maintenanceState.value.errorMessage,
            )
            assertNull(vm.lastCompletedKey.value, "a failed clear must not tell the UI to close")
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")

            // Retry: the same control runs again and this time it reports success.
            failing = false
            vm.clearPlayHistory()
            eventually { vm.lastCompletedKey.value == SettingsViewModel.KEY_CLEAR_PLAY }
            assertEquals(2, attempts)
            assertNull(vm.maintenanceState.value.errorMessage)
            vm.consumeCompletedAction()
            assertNull(vm.lastCompletedKey.value)
        } finally {
            h.close()
        }
    }

    @Test
    fun aSecondTapWhileTheClearIsPendingIsIgnored(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            clearSearchHistoryAction = {
                attempts++
                gate.await()
            },
        )
        try {
            vm.clearSearchHistory()
            assertTrue(
                vm.maintenanceState.value.isRunning(SettingsViewModel.KEY_CLEAR_SEARCH),
                "pending is visible without waiting for a frame",
            )
            vm.clearSearchHistory()
            vm.clearSearchHistory()
            assertEquals(1, attempts, "repeat taps must not queue repeat clears")
            gate.complete(Unit)
            eventually { vm.lastCompletedKey.value == SettingsViewModel.KEY_CLEAR_SEARCH }
            assertEquals(1, attempts)
            assertTrue(h.uncaught.isEmpty())
        } finally {
            h.close()
        }
    }

    @Test
    fun aSuccessfulAudioClearReportsWhatItReclaimed(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            clearAudioCacheAction = { 4_194_304L },
        )
        try {
            assertTrue(vm.canClearAudioCache)
            assertNull(vm.freedAudioBytes.value)
            vm.clearAudioCache()
            eventually { vm.lastCompletedKey.value == SettingsViewModel.KEY_CLEAR_AUDIO }
            assertEquals(4_194_304L, vm.freedAudioBytes.value)
            assertNull(vm.maintenanceState.value.errorMessage)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedAudioClearIsReportedWithItsOwnMessage(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            clearAudioCacheAction = { error("file in use") },
        )
        try {
            vm.clearAudioCache()
            eventually { vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_AUDIO) != null }
            assertEquals(
                SettingsViewModel.AUDIO_CLEAR_FAILED,
                vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_AUDIO),
            )
            // One dialog's failure never leaks into another dialog.
            assertNull(vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_LYRICS))
            // ...and it is not a screen-level message either.
            assertNull(vm.maintenanceState.value.screenErrorMessage)
            assertNull(vm.lastCompletedKey.value)
            assertNull(vm.freedAudioBytes.value)
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedLyricsClearIsReportedAndRetryable(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        var failing = true
        val vm = SettingsViewModel(
            GetSettingUseCase(d.settings),
            UpdateSettingUseCase(d.settings),
            h.scope,
            clearLyricsCacheAction = { if (failing) error("cache busy") },
        )
        try {
            vm.clearLyricsCache()
            eventually { vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_LYRICS) != null }
            assertEquals(
                SettingsViewModel.LYRICS_CLEAR_FAILED,
                vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_LYRICS),
            )
            failing = false
            vm.clearLyricsCache()
            eventually { vm.lastCompletedKey.value == SettingsViewModel.KEY_CLEAR_LYRICS }
            assertNull(vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_CLEAR_LYRICS))
        } finally {
            h.close()
        }
    }

    /**
     * A preference write that cannot be saved is a screen-level message now. It
     * used to be an exception in an unguarded `scope.launch`, which on Android
     * reaches the uncaught-exception handler — the app dies for a failed write.
     */
    @Test
    fun aFailedPreferenceWriteIsReportedInsteadOfCrashing(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val readOnlySettings = object : SettingsRepository by d.settings {
            override suspend fun putString(key: String, value: String) {
                error("database is read-only")
            }
        }
        val vm = SettingsViewModel(
            GetSettingUseCase(readOnlySettings),
            UpdateSettingUseCase(readOnlySettings),
            h.scope,
        )
        try {
            vm.setThemeId("light")
            eventually { vm.maintenanceState.value.screenErrorMessage != null }
            assertEquals(SettingsViewModel.SAVE_FAILED, vm.maintenanceState.value.screenErrorMessage)
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")

            vm.dismissMaintenanceError()
            assertNull(vm.maintenanceState.value.screenErrorMessage)
        } finally {
            h.close()
        }
    }

    @Test
    fun aFailedResetIsReportedAndDoesNotClaimSuccess(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val brokenSettings = object : SettingsRepository by d.settings {
            override suspend fun remove(key: String) {
                error("database is read-only")
            }
        }
        val vm = SettingsViewModel(
            GetSettingUseCase(brokenSettings),
            UpdateSettingUseCase(brokenSettings),
            h.scope,
        )
        try {
            vm.reset()
            eventually { vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_RESET) != null }
            assertEquals(
                SettingsViewModel.RESET_FAILED,
                vm.maintenanceState.value.errorFor(SettingsViewModel.KEY_RESET),
            )
            assertNull(vm.lastCompletedKey.value, "a failed reset must not close its dialog")
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }

    /**
     * `init { scope.launch { …read every key… } }` had no handler. A failed read
     * killed that coroutine (an uncaught exception on Android) and left `loaded`
     * false forever, so Settings never rendered its controls and the rest of the
     * app never applied stored preferences. It now falls back to the documented
     * defaults, still reports loaded, and says what happened.
     */
    @Test
    fun aMidLoadFailureDiscardsEarlierValuesInsteadOfPublishingAMixedSnapshot(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        // The first persisted key succeeds, then a later read fails. A failed
        // load must not expose the earlier value alongside defaults for the
        // remaining keys.
        d.settings.putString(SettingsKeys.THEME, "light")
        d.settings.putString(SettingsKeys.ACCENT, "violet")
        val brokenSettings = object : SettingsRepository by d.settings {
            override suspend fun getString(key: String): String? {
                if (key == SettingsKeys.ACCENT) error("database became unavailable")
                return d.settings.getString(key)
            }
        }
        val vm = SettingsViewModel(
            GetSettingUseCase(brokenSettings),
            UpdateSettingUseCase(brokenSettings),
            h.scope,
        )
        try {
            eventually { vm.loaded.value }
            assertEquals(SettingsKeys.THEME_DEFAULT, vm.themeId.value,
                "a value read before the failure must be rolled back to its default")
            assertEquals(SettingsKeys.ACCENT_DEFAULT, vm.accentId.value)
            assertEquals(SettingsKeys.CACHE_SIZE_MB_DEFAULT, vm.cacheSizeMb.value)
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
    fun aFailedSettingsReadFallsBackToDefaultsAndStillLoads(): Unit = runBlocking {
        val d = data()
        val h = Harness()
        val brokenSettings = object : SettingsRepository by d.settings {
            override suspend fun getString(key: String): String? = error("database is corrupt")
        }
        val vm = SettingsViewModel(
            GetSettingUseCase(brokenSettings),
            UpdateSettingUseCase(brokenSettings),
            h.scope,
        )
        try {
            eventually { vm.loaded.value }
            assertEquals(SettingsKeys.THEME_DEFAULT, vm.themeId.value)
            assertEquals(SettingsKeys.CACHE_SIZE_MB_DEFAULT, vm.cacheSizeMb.value)
            assertEquals(
                SettingsViewModel.SETTINGS_LOAD_FAILED,
                vm.maintenanceState.value.screenErrorMessage,
            )
            assertTrue(h.uncaught.isEmpty(), "the failure must not escape the coroutine: ${h.uncaught}")
        } finally {
            h.close()
        }
    }
}
