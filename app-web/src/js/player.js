/**
 * The queue and the transport.
 *
 * This is the layer between "a list of tracks the visitor chose" and "one
 * `<audio>` element that may or may not be allowed to play them". It owns the
 * queue, the repeat/shuffle rules, stream resolution, the resolution cache and
 * the retry policy, and it exposes exactly one state object for the UI to
 * render.
 *
 * Three decisions worth naming:
 *
 *   **Stream URLs are resolved lazily and cached briefly.** Resolving a stream
 *   is a network round-trip; doing it for all 24 queue rows when the visitor
 *   presses play on one of them would be absurd, and doing it once and keeping
 *   it for ever fails when the signed URL expires. So: resolve on demand, cache
 *   for `STREAM_TTL_MS`, and re-resolve once when a cached URL fails to play.
 *
 *   **A failure never eats the queue.** A track that cannot be resolved marks
 *   itself failed in the queue and is skipped to the next one only when the
 *   visitor asked for continuous playback; the queue is the user's, and a
 *   player that clears it because a network call failed is a player nobody
 *   trusts with a long queue.
 *
 *   **Queue edits are pure.** `enqueue`, `removeAt`, `moveTo` return new
 *   arrays rather than mutating, so the player's state is testable and the UI's
 *   undo-last is a matter of holding the previous array.
 */

import { AUDIO_STATUS } from "./audio.js";

/** Repeat modes, in the app's own words (QueueManager.RepeatMode). */
export const REPEAT = Object.freeze({
  OFF: "off",
  ALL: "all",
  ONE: "one",
});

/** How long a resolved stream URL is reused before it is re-resolved. */
export const STREAM_TTL_MS = 20 * 60 * 1000;

/** The queue is persisted; a thousand rows would blow the storage budget the
 *  settings store shares, and nobody scrolls past a thousand. */
export const MAX_QUEUE = 500;

/* --------------------------------------------------------- pure queue ops -- */

export function enqueueTrack(queue, track, { next = false } = {}) {
  const list = [...queue];
  if (next) list.unshift(track);
  else list.push(track);
  return list.slice(0, MAX_QUEUE);
}

export function removeAt(queue, index) {
  const list = [...queue];
  if (index < 0 || index >= list.length) return list;
  list.splice(index, 1);
  return list;
}

export function moveTo(queue, from, to) {
  const list = [...queue];
  if (from < 0 || from >= list.length) return list;
  const target = Math.max(0, Math.min(to, list.length - 1));
  const [item] = list.splice(from, 1);
  list.splice(target, 0, item);
  return list;
}

/** The shuffle order: every other row, randomised, current track kept in front. */
export function shuffledOrder(length, currentIndex, random = Math.random) {
  const rest = [];
  for (let index = 0; index < length; index += 1) {
    if (index !== currentIndex) rest.push(index);
  }
  for (let index = rest.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(random() * (index + 1));
    [rest[index], rest[swap]] = [rest[swap], rest[index]];
  }
  return currentIndex >= 0 ? [currentIndex, ...rest] : rest;
}

/**
 * The next queue index under the current repeat/shuffle mode.
 *
 * Mirrors the app's QueueManager rules, with one web-specific addition: a
 * shuffle plays every row once before repeating, because a browser tab is often
 * left running for hours and "shuffle" that replays the same three songs in a
 * sixty-track queue reads as broken.
 */
export function nextIndex({ length, index, repeat, shuffle, order }) {
  if (length === 0) return -1;
  if (repeat === REPEAT.ONE) return index;
  if (shuffle && Array.isArray(order) && order.length === length) {
    const position = order.indexOf(index);
    if (position >= 0 && position < order.length - 1) return order[position + 1];
    return repeat === REPEAT.ALL ? order[0] : -1;
  }
  if (index < length - 1) return index + 1;
  return repeat === REPEAT.ALL ? 0 : -1;
}

export function previousIndex({ length, index, positionMs, repeat, restartAfterMs = 3000 }) {
  if (length === 0) return -1;
  // The convention every player shares: previous restarts the track first.
  if (positionMs > restartAfterMs) return index;
  if (index > 0) return index - 1;
  return repeat === REPEAT.ALL ? length - 1 : 0;
}

/* ------------------------------------------------------------------ player -- */

export function createPlayer({
  engine,
  source,
  settings,
  onEvent = () => {},
  now = () => Date.now(),
} = {}) {
  const listeners = new Set();
  const streamCache = new Map();

  let state = {
    queue: [],
    index: -1,
    playing: false,
    repeat: settings?.get?.("repeat") ?? REPEAT.OFF,
    shuffle: settings?.get?.("shuffle") ?? false,
    order: [],
    /** Per-row resolution failures, keyed by queue index → failure object. */
    failures: {},
    /** The stream the engine is currently holding, if any. */
    stream: null,
    resolving: false,
    /** The last thing that went wrong, for the notice strip. */
    error: null,
    /** Set when playback is waiting for a gesture (autoplay policy). */
    needsGesture: false,
    volume: settings?.get?.("volume") ?? 1,
    muted: false,
    positionMs: 0,
    durationMs: 0,
    bufferedMs: 0,
    audioStatus: AUDIO_STATUS.IDLE,
  };

  function emit(patch) {
    state = { ...state, ...patch };
    for (const listener of listeners) listener(state);
  }

  engine?.subscribe?.((audioState) => {
    emit({
      audioStatus: audioState.status,
      positionMs: audioState.positionMs,
      durationMs: audioState.durationMs || state.durationMs,
      bufferedMs: audioState.bufferedMs,
      volume: audioState.volume,
      muted: audioState.muted,
      playing: audioState.status === AUDIO_STATUS.PLAYING || audioState.status === AUDIO_STATUS.BUFFERING,
      error: audioState.status === AUDIO_STATUS.ERROR ? audioState.error : state.error,
      needsGesture: audioState.status === AUDIO_STATUS.BLOCKED,
    });
  });

  function current() {
    return state.index >= 0 ? state.queue[state.index] ?? null : null;
  }

  function setQueue(queue, index = -1) {
    emit({
      queue: queue.slice(0, MAX_QUEUE),
      index: queue.length ? Math.max(0, Math.min(index, queue.length - 1)) : -1,
      failures: {},
      order: state.shuffle ? shuffledOrder(queue.length, index) : [],
    });
  }

  /** A cached stream URL is only as good as its signature; the TTL bounds it. */
  function cachedStream(videoId) {
    const entry = streamCache.get(videoId);
    if (!entry) return null;
    if (now() - entry.at > STREAM_TTL_MS) {
      streamCache.delete(videoId);
      return null;
    }
    return entry;
  }

  /**
   * Resolve and start one queue row.
   *
   * The retry rule is deliberately narrow: a *cached* URL that the browser
   * refuses is re-resolved exactly once (signatures expire, and that is the
   * whole reason a stream can stop working between two presses of play). A
   * fresh URL that fails is reported, not retried in a loop — an infinite retry
   * against a refusing origin is how a tab ends up hammering a server.
   */
  async function start(index, { autoplay = true, fromCache = true } = {}) {
    const track = state.queue[index];
    if (!track) return false;
    emit({ index, error: null, needsGesture: false, positionMs: 0, durationMs: track.durationMs ?? 0 });

    if (track.demo) {
      const demoFailure = {
        kind: "demo",
        message: `"${track.title}" is a demo row with no audio. Configure a data source in Settings to play real tracks.`,
      };
      emit({
        error: demoFailure,
        failures: { ...state.failures, [index]: demoFailure },
        playing: false,
      });
      onEvent({ type: "track-failed", track, failure: demoFailure });
      return false;
    }

    const videoId = track.videoId ?? track.id;
    if (!videoId) {
      const failure = { kind: "nostream", message: "This row has no playable id." };
      emit({ error: failure, failures: { ...state.failures, [index]: failure } });
      onEvent({ type: "track-failed", track, failure });
      return false;
    }

    let resolved = fromCache ? cachedStream(videoId) : null;
    if (!resolved) {
      emit({ resolving: true });
      const result = await source.resolveStream(videoId);
      emit({ resolving: false });
      if (!result?.ok) {
        const failure = { kind: result?.kind ?? "resolve", message: result?.message ?? "The stream could not be resolved." };
        emit({ error: failure, failures: { ...state.failures, [index]: failure }, playing: false });
        onEvent({ type: "track-failed", track, failure });
        return false;
      }
      resolved = { at: now(), stream: result.stream, meta: result.track ?? null };
      streamCache.set(videoId, resolved);
    }

    // Merge anything the stream resolution learned that the row did not have —
    // most usefully the real duration and artwork, which list rows often lack.
    const merged = { ...track, ...(resolved.meta?.durationMs ? { durationMs: resolved.meta.durationMs } : {}) };
    if (merged.durationMs !== track.durationMs) {
      const queue = [...state.queue];
      queue[index] = merged;
      emit({ queue });
    }

    emit({ stream: resolved.stream, playing: autoplay });
    onEvent({ type: "track-start", track: merged, index });

    const started = await engine.load(resolved.stream.url, { autoplay });
    if (!started && state.audioStatus === AUDIO_STATUS.ERROR) {
      // The cached URL is the prime suspect; drop it and try once more with a
      // fresh one — but only for the URL-freshness failure kinds.
      const kind = state.error?.kind;
      if (fromCache && (kind === "unsupported" || kind === "network" || kind === "decode")) {
        streamCache.delete(videoId);
        return start(index, { autoplay, fromCache: false });
      }
    }
    return started;
  }

  /** A queue row the visitor asked for by name, not by index. */
  async function playTrack(track, { queue = null, index = 0 } = {}) {
    if (queue) setQueue(queue, index);
    else setQueue([track], 0);
    return start(index);
  }

  async function playTracks(tracks, index = 0) {
    if (!tracks?.length) return false;
    setQueue(tracks, index);
    return start(index);
  }

  function advance(step) {
    const next = nextIndex({
      length: state.queue.length,
      index: state.index,
      repeat: state.repeat,
      shuffle: state.shuffle,
      order: state.order,
    });
    if (step === -1) {
      const back = previousIndex({
        length: state.queue.length,
        index: state.index,
        positionMs: state.positionMs,
        repeat: state.repeat,
      });
      if (back === state.index) {
        engine.seek(0);
        return true;
      }
      return start(back);
    }
    if (next < 0) {
      engine.pause();
      emit({ playing: false, positionMs: 0 });
      return false;
    }
    return start(next);
  }

  return {
    get state() {
      return state;
    },
    get current() {
      return current();
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    /* ------------------------------------------------------------ transport */
    play: () => engine.play(),
    pause: () => engine.pause(),
    async toggle() {
      if (state.playing) {
        engine.pause();
        return false;
      }
      if (state.index < 0 && state.queue.length > 0) return start(0);
      return engine.play();
    },
    next: () => advance(1),
    previous: () => advance(-1),
    seekTo: (ms) => engine.seek(ms),
    seekBy: (deltaMs) => engine.seek(Math.max(0, Math.min(state.positionMs + deltaMs, state.durationMs || state.positionMs + deltaMs))),
    setVolume: (value) => {
      engine.setVolume(value);
      settings?.set?.({ volume: value });
    },
    setMuted: (value) => engine.setMuted(value),

    /* ---------------------------------------------------------------- queue */
    playTrack,
    playTracks,
    setQueue,
    /** Play a queue row that is already there — used by every track row click. */
    playAt(index) {
      if (index < 0 || index >= state.queue.length) return false;
      if (index === state.index && state.playing) return true;
      return start(index);
    },
    enqueue(track, options) {
      const queue = enqueueTrack(state.queue, track, options);
      emit({ queue, order: state.shuffle ? shuffledOrder(queue.length, state.index) : state.order });
      return queue.length;
    },
    enqueueMany(tracks, options) {
      let queue = state.queue;
      for (const track of tracks) queue = enqueueTrack(queue, track, options);
      emit({ queue, order: state.shuffle ? shuffledOrder(queue.length, state.index) : state.order });
      return queue.length;
    },
    removeAt(index) {
      const queue = removeAt(state.queue, index);
      const wasPlaying = index === state.index;
      let nextCurrent = state.index;
      if (index < state.index) nextCurrent = state.index - 1;
      if (index === state.index) nextCurrent = Math.min(index, queue.length - 1);
      emit({ queue, index: queue.length ? nextCurrent : -1 });
      if (queue.length === 0) {
        engine.stop();
        emit({ playing: false, stream: null });
      } else if (wasPlaying) {
        start(nextCurrent);
      }
      return queue.length;
    },
    move(from, to) {
      const queue = moveTo(state.queue, from, to);
      let index = state.index;
      if (from === state.index) index = to;
      else if (from < state.index && to >= state.index) index = state.index - 1;
      else if (from > state.index && to <= state.index) index = state.index + 1;
      emit({ queue, index });
      return queue.length;
    },
    clearQueue() {
      engine.stop();
      emit({ queue: [], index: -1, playing: false, stream: null, failures: {}, order: [], error: null });
    },

    /* -------------------------------------------------------------- modes */
    setRepeat(repeat) {
      emit({ repeat, order: state.shuffle ? shuffledOrder(state.queue.length, state.index) : state.order });
      settings?.set?.({ repeat });
    },
    cycleRepeat() {
      const order = [REPEAT.OFF, REPEAT.ALL, REPEAT.ONE];
      const repeat = order[(order.indexOf(state.repeat) + 1) % order.length];
      this.setRepeat(repeat);
      return repeat;
    },
    setShuffle(shuffle) {
      const enabled = Boolean(shuffle);
      emit({
        shuffle: enabled,
        order: enabled ? shuffledOrder(state.queue.length, state.index) : [],
      });
      settings?.set?.({ shuffle: enabled });
    },
    toggleShuffle() {
      this.setShuffle(!state.shuffle);
      return state.shuffle;
    },

    /* ------------------------------------------------------------ streams */
    /** Force a re-resolve of the current track — the "Retry" button's work. */
    async retry() {
      const track = current();
      if (!track) return false;
      streamCache.delete(track.videoId ?? track.id);
      return start(state.index, { fromCache: false });
    },
    /** Warm the next row while the current one plays: the difference between a
     *  gap and no gap when the listener is driving manually. */
    async preResolve(index) {
      const track = state.queue[index];
      const videoId = track?.videoId ?? track?.id;
      if (!videoId || track?.demo) return null;
      if (cachedStream(videoId)) return null;
      const result = await source.resolveStream(videoId);
      if (result?.ok) streamCache.set(videoId, { at: now(), stream: result.stream, meta: result.track ?? null });
      return result;
    },
    /** Restore a persisted session: queue, position, and whether it was playing. */
    async restore({ queue, index, positionMs = 0, autoplay = false }) {
      const rows = Array.isArray(queue) ? queue.filter((row) => row && (row.videoId || row.id)).slice(0, MAX_QUEUE) : [];
      if (rows.length === 0) return false;
      setQueue(rows, index >= 0 && index < rows.length ? index : 0);
      if (positionMs > 0) emit({ positionMs });
      const track = current();
      const videoId = track?.videoId ?? track?.id;
      const cached = videoId ? cachedStream(videoId) : null;
      if (!cached) return false;
      const started = await engine.load(cached.stream.url, { autoplay });
      if (started && positionMs > 0) engine.seek(positionMs);
      return started;
    },
    /** The stream cache size, for the Settings diagnostics row. */
    get cacheSize() {
      return streamCache.size;
    },
    clearStreamCache() {
      streamCache.clear();
    },
    dispose() {
      listeners.clear();
      engine.dispose();
    },
  };
}
