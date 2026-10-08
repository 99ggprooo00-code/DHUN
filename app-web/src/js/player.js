/**
 * Playback.
 *
 * Mirror of shared/src/commonMain/kotlin/dev/dhun/player/ (QueueManager,
 * DhunPlayer, NowPlayingPersistence) with one honest difference: the browser
 * has no stream to play.
 *
 * Two backends implement the same interface:
 *
 *   "audio"  a real HTMLAudioElement (+ the Web Audio equaliser chain). Used
 *            whenever a track actually carries a `streamUrl`.
 *   "timing" a labelled clock. It advances position so the seek bar, the
 *            synced-lyrics highlight and the queue can be exercised, produces
 *            no sound, and is announced in the UI at all times. It is never
 *            described as playback.
 *
 * ADR-008 boundary 4 forbids implying Web support before playback is proven
 * from the deployed origin, so which backend is running is part of the
 * player's public state, not an implementation detail.
 */

export const BACKEND = Object.freeze({
  AUDIO: "audio",
  TIMING: "timing",
  NONE: "none",
});

export const REPEAT = Object.freeze({
  OFF: "off",
  ALL: "all",
  ONE: "one",
});

export function createPlayer({ audio = null, onEqualizerState = null } = {}) {
  const listeners = new Set();
  let state = {
    queue: [],
    index: -1,
    playing: false,
    positionMs: 0,
    durationMs: 0,
    repeat: REPEAT.OFF,
    shuffle: false,
    backend: BACKEND.NONE,
    error: null,
    loading: false,
  };

  let ticker = null;
  let clockStart = 0;

  function emit(patch) {
    state = { ...state, ...patch };
    for (const listener of listeners) listener(state);
  }

  function currentTrack() {
    return state.index >= 0 ? state.queue[state.index] ?? null : null;
  }

  function stopTicker() {
    if (ticker !== null) {
      clearInterval(ticker);
      ticker = null;
    }
  }

  /** The labelled clock: position advances, nothing is audible. */
  function startTimingClock() {
    stopTicker();
    clockStart = Date.now() - state.positionMs;
    ticker = setInterval(() => {
      const track = currentTrack();
      const elapsed = Date.now() - clockStart;
      if (!track) return stopTicker();
      if (elapsed >= state.durationMs) {
        stopTicker();
        return onEnded();
      }
      emit({ positionMs: elapsed });
    }, 250);
  }

  async function startAudio() {
    const track = currentTrack();
    if (!audio || !track?.streamUrl) return false;
    try {
      audio.src = track.streamUrl;
      await audio.play();
      emit({ backend: BACKEND.AUDIO, error: null });
      attachAudioGraphOnce();
      return true;
    } catch (error) {
      emit({
        backend: BACKEND.TIMING,
        error: {
          stage: "playback",
          message: error instanceof Error ? error.message : String(error),
        },
        playing: true,
      });
      startTimingClock();
      return false;
    }
  }

  let graphAttached = false;
  function attachAudioGraphOnce() {
    if (graphAttached || !audio) return;
    graphAttached = true;
    audio.addEventListener("timeupdate", () => {
      emit({ positionMs: audio.currentTime * 1000, durationMs: toMs(audio.duration) });
    });
    audio.addEventListener("loadedmetadata", () => {
      emit({ durationMs: toMs(audio.duration) });
    });
    audio.addEventListener("ended", () => onEnded());
    audio.addEventListener("error", () => {
      emit({
        error: {
          stage: "decode",
          message: audio.error?.message ?? "the browser could not decode this stream",
        },
      });
    });
  }

  function toMs(seconds) {
    return Number.isFinite(seconds) ? seconds * 1000 : 0;
  }

  /** QueueManager's advance rules: repeat-one repeats the track, the end of
   *  the queue honours repeat-all, otherwise playback stops at the last row. */
  function onEnded() {
    const track = currentTrack();
    if (state.repeat === REPEAT.ONE && track) {
      emit({ positionMs: 0 });
      if (state.backend === BACKEND.AUDIO && audio) {
        audio.currentTime = 0;
        audio.play().catch(() => {});
      } else {
        startTimingClock();
      }
      return;
    }
    if (state.index < state.queue.length - 1) return next();
    if (state.repeat === REPEAT.ALL && state.queue.length > 0) return play(0);
    stopTicker();
    emit({ playing: false, positionMs: 0 });
  }

  function play(index, { queue = state.queue, startAtMs = 0 } = {}) {
    if (!queue.length) return;
    const clamped = Math.max(0, Math.min(index, queue.length - 1));
    const track = queue[clamped];
    stopTicker();
    emit({
      queue,
      index: clamped,
      positionMs: startAtMs,
      durationMs: track.durationMs ?? 0,
      playing: true,
      error: null,
      backend: track.streamUrl ? BACKEND.AUDIO : BACKEND.TIMING,
    });

    if (track.streamUrl && audio) {
      startAudio().then((started) => {
        if (!started) startTimingClock();
      });
      return;
    }
    startTimingClock();
  }

  function pause() {
    if (!state.playing) return;
    stopTicker();
    if (state.backend === BACKEND.AUDIO && audio) audio.pause();
    emit({ playing: false });
  }

  function resume() {
    if (state.playing) return;
    emit({ playing: true });
    if (state.backend === BACKEND.AUDIO && audio) {
      audio.play().catch((error) => {
        emit({
          backend: BACKEND.TIMING,
          error: { stage: "resume", message: error.message },
        });
        startTimingClock();
      });
      return;
    }
    startTimingClock();
  }

  function toggle() {
    return state.playing ? pause() : resume();
  }

  function nextIndex(step) {
    if (state.queue.length === 0) return -1;
    if (state.shuffle && state.queue.length > 1) {
      let candidate = state.index;
      while (candidate === state.index) {
        candidate = Math.floor(Math.random() * state.queue.length);
      }
      return candidate;
    }
    const candidate = state.index + step;
    // Before the first row, previous restarts it (a transport convention older
    // than this port); after the last row there is nothing to play unless
    // repeat-all wraps the queue.
    if (candidate < 0) return state.repeat === REPEAT.ALL ? state.queue.length - 1 : 0;
    if (candidate > state.queue.length - 1) {
      return state.repeat === REPEAT.ALL ? 0 : -1;
    }
    return candidate;
  }

  function next() {
    const index = nextIndex(1);
    if (index >= 0) {
      play(index);
      return;
    }
    // End of the queue with no repeat: playback stops and the position resets,
    // which is what the app's QueueManager does.
    stopTicker();
    emit({ playing: false, positionMs: 0 });
  }

  function previous() {
    // The app restarts the track before it steps back (a transport convention
    // older than this codebase); kept so the two feel identical.
    if (state.positionMs > 3000) {
      emit({ positionMs: 0 });
      if (state.backend === BACKEND.AUDIO && audio) audio.currentTime = 0;
      if (state.backend === BACKEND.TIMING) {
        clockStart = Date.now();
        startTimingClock();
      }
      return;
    }
    const index = nextIndex(-1);
    if (index >= 0) play(index);
  }

  function seek(ms) {
    const clamped = Math.max(0, Math.min(ms, state.durationMs || 0));
    emit({ positionMs: clamped });
    if (state.backend === BACKEND.AUDIO && audio) {
      audio.currentTime = clamped / 1000;
      return;
    }
    if (state.playing) startTimingClock();
  }

  return {
    get state() {
      return state;
    },
    get currentTrack() {
      return currentTrack();
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    play,
    pause,
    resume,
    toggle,
    next,
    previous,
    seek,
    setQueue(queue, index = 0) {
      const tracks = [...queue];
      stopTicker();
      emit({
        queue: tracks,
        index: tracks.length ? index : -1,
        playing: false,
        positionMs: 0,
        durationMs: tracks[index]?.durationMs ?? 0,
      });
    },
    setRepeat(repeat) {
      emit({ repeat });
    },
    cycleRepeat() {
      const order = [REPEAT.OFF, REPEAT.ALL, REPEAT.ONE];
      emit({ repeat: order[(order.indexOf(state.repeat) + 1) % order.length] });
      return state.repeat;
    },
    setShuffle(shuffle) {
      emit({ shuffle: Boolean(shuffle) });
    },
    /** Called by the equaliser UI; the Web Audio graph is wired by main.js
     *  because only it owns the AudioContext. */
    equalizerChanged() {
      if (typeof onEqualizerState === "function") onEqualizerState();
    },
    dispose() {
      stopTicker();
      listeners.clear();
      if (audio) audio.pause();
    },
  };
}
