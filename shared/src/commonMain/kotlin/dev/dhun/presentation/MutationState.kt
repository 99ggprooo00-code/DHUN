package dev.dhun.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Observable state of the mutating actions a screen model runs.
 *
 * **Why this exists.** After playlist deletion was made truthful (PR #210/#211)
 * the native audit found the same defect class in the remaining mutations: a
 * `scope.launch { runCatching { repo.remove(id) } }` swallowed every failure, so
 * a Like, a history-row removal, a download or a playlist rename could do
 * nothing at all with no message — and, where `runCatching` was missing, the
 * exception escaped an unguarded `scope.launch` and reached the platform's
 * uncaught-exception handler (an app crash on Android). This class gives those
 * actions one contract:
 *
 * - **No duplicate work.** [run] claims `key` before it launches, so a second
 *   tap that lands before recomposition is ignored rather than queued.
 * - **No silent failure.** A thrown exception becomes [MutationState.errorMessage]
 *   for the screen to render; it never escapes to the thread's uncaught handler.
 * - **Honest success.** Callers only run their success callback when the
 *   suspend block returned normally, so "Added to playlist" cannot be shown for
 *   a write that failed.
 * - **Retryable.** The UI keeps the calling control available and clears the
 *   message with [dismissError] once the retry succeeds.
 */
data class MutationState(
    /** Keys of mutations currently in flight. Empty when idle. */
    val runningKeys: Set<String> = emptySet(),
    /** Message for the last failure that has not been dismissed or retried. */
    val errorMessage: String? = null,
    /**
     * The key whose action failed, so a dialog shows only its own failure and
     * a screen-level banner can ignore messages a dialog already owns.
     * `null` for failures that belong to the screen (e.g. a preference write).
     */
    val errorKey: String? = null,
) {
    fun isRunning(key: String): Boolean = key in runningKeys

    /** The failure message belonging to [key], or null. */
    fun errorFor(key: String): String? = if (errorKey == key) errorMessage else null

    /** Failures that no dialog owns — what a screen banner renders. */
    val screenErrorMessage: String? get() = if (errorKey == null) errorMessage else null

    /** True while at least one mutation runs (used for dialog confirm gating). */
    val isBusy: Boolean get() = runningKeys.isNotEmpty()
}

/**
 * Runs screen-model mutations with the [MutationState] contract above.
 *
 * One instance per screen model; keys are chosen by that model (they are its
 * private vocabulary, e.g. `"history:$entryId"`) and are exposed as constants
 * only where the UI needs to gate a dialog's confirm button.
 */
class MutationTracker(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(MutationState())
    val state: StateFlow<MutationState> = _state.asStateFlow()

    /**
     * Starts [block] unless [key] is already running.
     *
     * The pending state is published **before** this method returns, so the
     * caller can gate a second submission synchronously. The coroutine starts
     * [CoroutineStart.UNDISPATCHED] for the same reason every other write in
     * this app does: the first suspension point (normally the repository's
     * `withContext`) is reached in call order, which keeps serialized write
     * queues deterministic.
     *
     * @param failureMessage shown verbatim when [block] throws.
     * @param onSuccess runs only after [block] completed normally.
     * @param onFailure optional rollback, run before [failureMessage] is
     *   published. Screens that show the result of a mutation before it is
     *   stored (a settings reset flips every control immediately) use it to put
     *   back what is actually stored, so a failure cannot leave the UI claiming
     *   a state the database never reached. Its own failure is swallowed: the
     *   action's message still stands and the caller's data is unchanged.
     * @return true when the mutation was started, false when it was a duplicate.
     */
    fun run(
        key: String,
        failureMessage: String,
        onSuccess: (() -> Unit)? = null,
        onFailure: (suspend () -> Unit)? = null,
        block: suspend () -> Unit,
    ): Boolean {
        // Atomic claim: a second tap before recomposition must not queue a
        // second write (duplicate delete/rename/unlike requests).
        while (true) {
            val current = _state.value
            if (key in current.runningKeys) return false
            val next = current.copy(
                runningKeys = current.runningKeys + key,
                errorMessage = null,
                errorKey = null,
            )
            if (_state.compareAndSet(current, next)) break
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
                finish(key)
                onSuccess?.invoke()
            } catch (cancelled: CancellationException) {
                finish(key)
                throw cancelled
            } catch (_: Exception) {
                if (onFailure != null) {
                    try {
                        onFailure()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The rollback reads the same store that just failed.
                        // Keep the message; the next screen read shows the
                        // stored state regardless.
                    }
                }
                // Keep the mutation's failure in the model instead of the
                // thread's uncaught-exception handler.
                _state.update {
                    it.copy(
                        runningKeys = it.runningKeys - key,
                        errorMessage = failureMessage,
                        errorKey = key,
                    )
                }
            }
        }
        return true
    }

    /**
     * Records a failure that happened outside a [run] block (e.g. a preference
     * write). [key] is null for screen-level messages.
     */
    fun report(failureMessage: String, key: String? = null) {
        _state.update { it.copy(errorMessage = failureMessage, errorKey = key) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null, errorKey = null) }
    }

    private fun finish(key: String) {
        _state.update { it.copy(runningKeys = it.runningKeys - key) }
    }
}
