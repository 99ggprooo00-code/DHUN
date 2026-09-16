package dev.dhun.android.equalizer

import android.media.audiofx.Equalizer
import android.util.Log
import dev.dhun.player.equalizer.EqualizerEngine
import dev.dhun.player.equalizer.EqualizerState

/**
 * [EqualizerEngine] backed by `android.media.audiofx.Equalizer` (S4.3).
 *
 * Attaches to DHUN's own audio session (see [AndroidAudioSession]) — never
 * session 0, so enabling EQ can neither affect other apps' audio nor be
 * affected by it. The effect is created lazily on the first enabled apply
 * and re-created if the session id changes (player rebuild); every apply
 * re-reads the session supplier, so no listener plumbing is needed.
 *
 * Degradation is silent-but-logged, in this order:
 *
 * 1. EQ disabled or session [AndroidAudioSession.UNSET] → the effect (if any)
 *    is bypassed. No instance is created for a disabled state.
 * 2. The device has no EQ (constructor throws — some DSPs/HALs omit it) →
 *    logged once per session id; applies become no-ops until the session
 *    changes. There is deliberately no per-tick retry: a device that throws
 *    once throws always, and the UI must not jank on slider drags.
 * 3. A mid-apply failure (dead effect, released session) → the effect is
 *    released and re-created on the next apply.
 *
 * Threading: every method is `@Synchronized` — applies arrive from the
 * settings UI thread while [release] arrives from the service destroy path.
 * `Equalizer` calls are binder transactions; none of them touch the player.
 */
class AndroidEqualizerEngine(
    private val sessionId: () -> Int,
) : EqualizerEngine {

    private var effect: Equalizer? = null
    private var boundSession: Int = -1

    @Synchronized
    override fun apply(state: EqualizerState) {
        val current = sessionId()
        if (!state.enabled || current == AndroidAudioSession.UNSET) {
            runCatching { effect?.enabled = false }
            return
        }
        if (boundSession != current) {
            bind(current)
        }
        val fx = effect ?: return
        runCatching {
            val bands = fx.numberOfBands.toInt().coerceAtLeast(0)
            val centers = (0 until bands).map { fx.getCenterFreq(it.toShort()) }
            val range = fx.bandLevelRange
            val levels = EqualizerBandMapper.map(state, centers, range[0], range[1])
            levels.forEachIndexed { index, mb -> fx.setBandLevel(index.toShort(), mb) }
            fx.enabled = true
        }.onFailure { t ->
            Log.w(TAG, "equalizer apply failed — releasing, will rebind on next apply", t)
            releaseLocked()
            // Force a rebind attempt on the next apply (bind() itself records
            // the session even on failure, so a device that throws every time
            // still degrades to silent no-op rather than retry spam).
            boundSession = -1
        }
    }

    /**
     * Releases the native effect. Called from the service destroy path; safe
     * to call repeatedly and safe when nothing is bound. The next enabled
     * apply re-creates it (the session supplier is re-read every time).
     */
    @Synchronized
    fun release() {
        releaseLocked()
    }

    private fun bind(session: Int) {
        releaseLocked()
        boundSession = session
        effect = runCatching { Equalizer(0, session) }
            .onFailure { t -> Log.w(TAG, "no equalizer for audio session $session (${t.javaClass.simpleName})", t) }
            .getOrNull()
    }

    private fun releaseLocked() {
        runCatching { effect?.release() }
        effect = null
    }

    private companion object {
        const val TAG = "DhunEq"
    }
}
