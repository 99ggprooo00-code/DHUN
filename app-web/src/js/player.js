/**
 * The player: one `<audio>` element, a queue, and an honest status line.
 *
 * THE STATE MACHINE IS SMALL ON PURPOSE. There are exactly seven statuses
 * (`idle`, `loading`, `playing`, `paused`, `ended`, `error`, `blocked`) and one
 * rule that matters: the interface draws the status it is given, so it
 * reports "playing" only for the current playback intent after the element's
 * playing event or play-promise acknowledgement. That is transport evidence,
 * not proof a person heard sound (muting/devices remain outside this module). A rejected
 * `play()` (autoplay policy, a dead URL, a codec the browser refuses) lands in
 * `blocked` or `error` with the reason attached — it does not silently bounce
 * back to `paused` and leave the button looking stuck.
 *
 * ROWS FROM THE LIBRARY HAVE NO STREAM. Audio URLs expire, so likes and history
 * store the row and not the URL. Playing one asks the injected `resolve` for a
 * fresh stream first, and a failure there is reported as the source's failure —
 * the same taxonomy the rest of the app uses.
 */

import { FAILURE_KIND, SourceError, describeFailure } from "./api.js";

export const STATUS = Object.freeze({
  idle: "idle",
  loading: "loading",
  playing: "playing",
  paused: "paused",
  ended: "ended",
  error: "error",
  blocked: "blocked",
});

export const REPEAT = Object.freeze(["off", "all", "one"]);

/** A fresh player state. Pure: the tests build one without a browser. */
export function emptyPlayerState() {
  return {
    queue: [],
    index: -1,
    status: STATUS.idle,
    context: "",
    position: 0,
    duration: 0,
    volume: 0.8,
    muted: false,
    repeat: "off",
    shuffle: false,
    order: [],
    error: null,
  };
}

/** The track on screen, or null. */
export function currentTrack(state) {
  if (state.index < 0) return null;
  return state.queue[state.index] ?? null;
}

/**
 * A shuffle order that starts with the track that is playing.
 *
 * Shuffling must not cut the current song off, so the current index is placed
 * first and everything else is a Fisher–Yates pass over the remainder.
 */
export function shuffledOrder(length, startIndex, random = Math.random) {
  const rest = [];
  for (let index = 0; index < length; index += 1) if (index !== startIndex) rest.push(index);
  for (let index = rest.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(random() * (index + 1));
    [rest[index], rest[swap]] = [rest[swap], rest[index]];
  }
  return startIndex >= 0 && startIndex < length ? [startIndex, ...rest] : rest;
}

/** Where `next` goes, honouring repeat and shuffle. `-1` means "stop". */
export function nextIndex(state) {
  const length = state.queue.length;
  if (!length) return -1;
  if (state.repeat === "one" && state.index >= 0) return state.index;
  if (state.shuffle && state.order.length) {
    const at = state.order.indexOf(state.index);
    if (at >= 0 && at + 1 < state.order.length) return state.order[at + 1];
    if (state.repeat === "all") return state.order[0];
    return -1;
  }
  if (state.index + 1 < length) return state.index + 1;
  return state.repeat === "all" ? 0 : -1;
}

/** Where `previous` goes. A long press on most players means "restart". */
export function previousIndex(state, { restartAfterSeconds = 3 } = {}) {
  const length = state.queue.length;
  if (!length) return -1;
  if (state.position > restartAfterSeconds) return state.index;
  if (state.shuffle && state.order.length) {
    const at = state.order.indexOf(state.index);
    if (at > 0) return state.order[at - 1];
    return state.repeat === "all" ? state.order[state.order.length - 1] : state.index;
  }
  if (state.index - 1 >= 0) return state.index - 1;
  return state.repeat === "all" ? length - 1 : 0;
}

/**
 * What a failed `play()` means, in the browser's own terms.
 *
 * The three cases are not interchangeable: `NotAllowedError` means the browser
 * wanted a gesture and nothing is wrong with the track; `NotSupportedError`
 * means the file itself is unusable; everything else is the network. The player
 * shows the difference so a visitor knows whether to press the button again.
 */
export function playRejection(error) {
  const name = error?.name ?? "";
  if (name === "NotAllowedError") {
    return { status: STATUS.blocked, title: "The browser blocked playback", body: "Press play once more — browsers require a gesture before audio starts." };
  }
  if (name === "NotSupportedError") {
    return { status: STATUS.error, title: "This browser cannot play that file", body: "The audio URL answered, but the format was refused by the browser." };
  }
  if (name === "AbortError") {
    // A load interrupted by the next track is not an error the visitor needs.
    return null;
  }
  return { status: STATUS.error, title: "Playback stopped", body: String(error?.message ?? "The audio element refused to play the track.") };
}

/** "The track has no audio URL" is a real state, not an empty string src. */
function missingStreamError(track) {
  if (track?.demo) {
    // A sample row is invented. It must say so at the moment someone presses
    // play on it, not only in the banner above the list.
    return {
      status: STATUS.error,
      title: "This is a sample row",
      body: `“${track?.title ?? "This track"}” is an invented example drawn when the JioSaavn API could not be reached. It carries no audio. The live catalogue returns as soon as the API answers.`,
    };
  }
  return {
    status: STATUS.error,
    title: "This row has no audio URL",
    body: `“${track?.title ?? "The track"}” came from the saved library, and this build re-reads audio from the catalogue instead of storing it. Open the track from its album or search again.`,
  };
}

/**
 * Create the player.
 *
 * `resolve` is injected so the player never imports the network itself: the
 * audio path and the catalogue path stay separable, which is what lets the
 * player be tested against a fake element.
 */
export function createPlayer({
  audio,
  resolve = async (song) => song,
  describe = describeFailure,
  persist = () => {},
  now = () => Date.now(),
} = {}) {
  let state = emptyPlayerState();
  let listeners = new Set();
  let loadToken = 0;
  let playbackId = 0; // local selection identity; never persisted or sent
  let playRequested = false;

  function emit() {
    for (const listener of listeners) listener(state);
  }

  function set(patch) {
    state = { ...state, ...patch };
    emit();
  }

  function resetError() {
    if (state.error) set({ error: null });
  }

  function fail(error) {
    const described = describe(error);
    playRequested = false;
    set({ status: STATUS.error, error: { ...described, technical: described.technical, at: now() } });
  }

  function releaseMedia() {
    audio.pause?.();
    audio.removeAttribute?.("src");
    audio.load?.();
  }

  function canMarkPlaying() {
    return playRequested && currentTrack(state) && audio.src && !audio.paused;
  }

  async function requestPlayback(token) {
    try {
      await audio.play();
      if (token !== loadToken || !canMarkPlaying()) return;
      set({ status: STATUS.playing, error: null });
    } catch (error) {
      if (token !== loadToken) return;
      const rejection = playRejection(error);
      playRequested = false;
      if (!rejection) {
        set({ status: STATUS.paused });
        return;
      }
      set({ status: rejection.status, error: { ...rejection, technical: String(error?.name ?? ""), at: now() } });
    }
  }

  /**
   * Load and start one queue position.
   *
   * `token` guards against a slow track landing after the visitor has already
   * pressed next twice: the stale response is dropped instead of replacing the
   * song they are now listening to.
   */
  async function startAt(index, { autoplay = true } = {}) {
    const token = (loadToken += 1);
    playRequested = false;
    // No old stream may continue while new metadata/resolution is on screen.
    releaseMedia();
    if (!Number.isInteger(index) || index < 0 || index >= state.queue.length) {
      set({ status: STATUS.idle, index: -1, position: 0, duration: 0 });
      return;
    }
    const requested = state.queue[index];
    playbackId += 1;
    playRequested = autoplay;
    set({
      index,
      status: STATUS.loading,
      position: 0,
      duration: requested?.duration ?? 0,
      error: null,
    });

    let track = requested;
    if (!track?.stream) {
      try {
        const hydrated = await resolve(track);
        if (token !== loadToken) return;
        if (hydrated?.stream) {
          track = hydrated;
          const queue = state.queue.slice();
          // Removing an earlier queue row can move this still-current request.
          queue[state.index] = hydrated;
          set({ queue });
        }
      } catch (error) {
        if (token !== loadToken) return;
        fail(error);
        return;
      }
    }
    if (token !== loadToken) return;
    if (!track?.stream) {
      const described = missingStreamError(track);
      playRequested = false;
      set({ status: described.status, error: { ...described, at: now() } });
      return;
    }

    try {
      audio.src = track.stream;
      audio.load?.();
    } catch (error) {
      if (token !== loadToken) return;
      fail(new SourceError(FAILURE_KIND.network, String(error?.message ?? "could not load the audio URL"), { url: track.stream }));
      return;
    }
    persist({ track, index: state.index });

    if (!autoplay) {
      set({ status: STATUS.paused });
      return;
    }
    await requestPlayback(token);
  }

  /* ------------------------------------------------------------- element -- */

  const onTimeUpdate = () => {
    if (!audio || !currentTrack(state) || !audio.src) return;
    const duration = Number.isFinite(audio.duration) ? audio.duration : state.duration;
    set({ position: audio.currentTime || 0, duration: duration || 0 });
  };
  const onPlaying = () => {
    if (canMarkPlaying() && state.status !== STATUS.playing) set({ status: STATUS.playing });
  };
  const onWaiting = () => {
    if (canMarkPlaying()) set({ status: STATUS.loading });
  };
  const onPause = () => {
    // A pause caused by the track ending is not a user pause; `ended` owns that.
    if (state.status === STATUS.playing) {
      if (playRequested) loadToken += 1;
      playRequested = false;
      set({ status: STATUS.paused });
    }
  };
  const onEnded = () => {
    if (!currentTrack(state) || !audio.src) return;
    const next = nextIndex(state);
    if (state.repeat === "one") {
      startAt(state.index);
      return;
    }
    if (next >= 0) startAt(next);
    else {
      loadToken += 1;
      playRequested = false;
      audio.pause?.();
      set({ status: STATUS.ended, position: 0 });
    }
  };
  const onError = () => {
    if (!currentTrack(state) || !audio?.src) return;
    const code = audio?.error?.code ?? 0;
    const message = audio?.error?.message ?? "";
    fail(new SourceError(FAILURE_KIND.network, message || `the audio element reported error ${code}`, { url: audio?.src ?? "" }));
  };
  const onVolumeChange = () => set({ volume: audio.volume ?? 0, muted: Boolean(audio.muted) });
  const onLoadedMetadata = () => {
    if (currentTrack(state) && audio.src && Number.isFinite(audio.duration)) set({ duration: audio.duration });
  };

  audio?.addEventListener?.("timeupdate", onTimeUpdate);
  audio?.addEventListener?.("playing", onPlaying);
  audio?.addEventListener?.("waiting", onWaiting);
  audio?.addEventListener?.("pause", onPause);
  audio?.addEventListener?.("ended", onEnded);
  audio?.addEventListener?.("error", onError);
  audio?.addEventListener?.("volumechange", onVolumeChange);
  audio?.addEventListener?.("loadedmetadata", onLoadedMetadata);

  const api = {
    get state() {
      return state;
    },
    get track() {
      return currentTrack(state);
    },
    subscribe(listener) {
      listeners.add(listener);
      listener(state);
      return () => listeners.delete(listener);
    },

    /** Replace the queue and start one position. */
    play(songs, index = 0, context = "") {
      const queue = (songs ?? []).filter(Boolean);
      if (!queue.length) return;
      const order = state.shuffle ? shuffledOrder(queue.length, index) : queue.map((_, position) => position);
      set({ queue, order, context, index: -1, status: STATUS.loading, position: 0, duration: 0, error: null });
      startAt(index);
    },

    /** Add to the end of the queue without interrupting what is playing. */
    enqueue(song) {
      if (!song?.id) return false;
      if (state.queue.some((entry) => entry.id === song.id)) return false;
      const queue = [...state.queue, song];
      const order = state.shuffle ? [...state.order, queue.length - 1] : queue.map((_, position) => position);
      set({ queue, order });
      return true;
    },

    /** Play next: insert after the current position, not at the end. */
    playNext(song) {
      if (!song?.id) return false;
      if (state.queue.some((entry) => entry.id === song.id)) return false;
      const at = Math.max(state.index, 0) + 1;
      const queue = [...state.queue];
      queue.splice(at, 0, song);
      set({ queue, order: state.shuffle ? shuffledOrder(queue.length, state.index) : queue.map((_, position) => position) });
      return true;
    },

    removeAt(index) {
      if (index < 0 || index >= state.queue.length) return;
      const queue = state.queue.filter((_, position) => position !== index);
      if (!queue.length) {
        api.stop();
        return;
      }
      const order = state.order.filter(position => position !== index).map(position => position > index ? position - 1 : position);
      if (index < state.index) set({ queue, order, index: state.index - 1 });
      else if (index === state.index) {
        set({ queue, order });
        startAt(Math.min(index, queue.length - 1));
      } else set({ queue, order });
    },

    toggle() {
      if (!state.queue.length) return;
      if (state.status === STATUS.playing || state.status === STATUS.loading) {
        loadToken += 1;
        playRequested = false;
        audio.pause();
        set({ status: STATUS.paused });
        return;
      }
      if (state.status === STATUS.ended) {
        startAt(state.index);
        return;
      }
      if (state.status === STATUS.paused || state.status === STATUS.blocked) {
        if (!audio.src && state.index >= 0) {
          startAt(state.index);
          return;
        }
        const token = ++loadToken;
        playRequested = true;
        set({ status: STATUS.loading, error: null });
        requestPlayback(token);
        return;
      }
      if (state.status === STATUS.idle && state.queue.length) startAt(Math.max(state.index, 0));
    },

    next({ manual = true } = {}) {
      const index = nextIndex(state);
      if (index >= 0) startAt(index);
      else if (manual) {
        loadToken += 1;
        playRequested = false;
        audio.pause?.();
        set({ status: STATUS.ended, position: 0 });
      }
    },

    previous() {
      const index = previousIndex(state);
      if (index >= 0) startAt(index);
    },

    seek(seconds) {
      const duration = Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : state.duration;
      const target = Math.max(0, Math.min(seconds, duration || seconds));
      try {
        audio.currentTime = target;
      } catch {
        /* some browsers refuse a seek before metadata */
      }
      set({ position: target });
    },

    seekBy(delta) {
      api.seek((audio.currentTime || state.position || 0) + delta);
    },

    setVolume(value) {
      const volume = Math.max(0, Math.min(1, Number(value) || 0));
      audio.volume = volume;
      if (volume > 0 && audio.muted) audio.muted = false;
      set({ volume, muted: Boolean(audio.muted) });
      persist({ volume });
    },

    toggleMute() {
      audio.muted = !audio.muted;
      set({ muted: Boolean(audio.muted) });
    },

    cycleRepeat() {
      const repeat = REPEAT[(REPEAT.indexOf(state.repeat) + 1) % REPEAT.length];
      set({ repeat });
      persist({ repeat });
      return repeat;
    },

    toggleShuffle() {
      const shuffle = !state.shuffle;
      set({
        shuffle,
        order: shuffle ? shuffledOrder(state.queue.length, state.index) : state.queue.map((_, position) => position),
      });
      persist({ shuffle });
      return shuffle;
    },

    stop() {
      loadToken += 1;
      playRequested = false;
      releaseMedia();
      set({ queue: [], index: -1, status: STATUS.idle, position: 0, duration: 0, context: "", error: null });
    },

    clearError() {
      resetError();
      if (state.status === STATUS.error || state.status === STATUS.blocked) set({ status: STATUS.paused });
    },

    /** The queue as rows, with the playing one marked. */
    queueView() {
      return state.queue.map((track, index) => ({
        ...track,
        index,
        current: index === state.index,
        playing: index === state.index && state.status === STATUS.playing,
      }));
    },

    get requested() {
      return playRequested;
    },

    get playbackId() {
      return playbackId;
    },

    destroy() {
      listeners = new Set();
      api.stop();
      audio?.removeEventListener?.("timeupdate", onTimeUpdate);
      audio?.removeEventListener?.("playing", onPlaying);
      audio?.removeEventListener?.("waiting", onWaiting);
      audio?.removeEventListener?.("pause", onPause);
      audio?.removeEventListener?.("ended", onEnded);
      audio?.removeEventListener?.("error", onError);
      audio?.removeEventListener?.("volumechange", onVolumeChange);
      audio?.removeEventListener?.("loadedmetadata", onLoadedMetadata);
      listeners = new Set();
    },
  };

  return api;
}

/* --------------------------------------------------------- media session -- */

/**
 * Hardware media keys and the OS "now playing" card.
 *
 * Features are asked for, never assumed: `mediaSession` is absent in Safari
 * 14 and `setPositionState` throws on a duration of 0, and both must be
 * harmless rather than a console full of errors.
 */
export function attachMediaSession({ track, status, position, duration, handlers, mediaSession = globalThis.navigator?.mediaSession } = {}) {
  if (!mediaSession) return;
  if (!track) {
    try {
      mediaSession.metadata = null;
      mediaSession.playbackState = "none";
      try { mediaSession.setPositionState?.(); } catch { /* optional position API */ }
      for (const name of ["play", "pause", "previoustrack", "nexttrack", "seekbackward", "seekforward", "seekto"]) {
        try { mediaSession.setActionHandler?.(name, null); } catch { /* optional action */ }
      }
    } catch { /* optional platform capability */ }
    return;
  }
  try {
    if (typeof MediaMetadata === "function") {
      mediaSession.metadata = new MediaMetadata({
        title: track.title ?? "",
        artist: track.artist ?? "",
        album: track.album?.name ?? "",
        artwork: track.image ? [{ src: track.image, sizes: "500x500", type: "image/jpeg" }] : [],
      });
    }
    mediaSession.playbackState = status === STATUS.playing ? "playing" : status === STATUS.paused ? "paused" : "none";
    if (typeof mediaSession.setPositionState === "function" && Number.isFinite(duration) && duration > 0) {
      mediaSession.setPositionState({
        duration,
        playbackRate: 1,
        position: Math.min(Math.max(position ?? 0, 0), duration),
      });
    }
    const action = (name, handler) => {
      if (typeof handler !== "function" || typeof mediaSession.setActionHandler !== "function") return;
      try {
        mediaSession.setActionHandler(name, handler);
      } catch {
        /* the browser knows the action and refuses the handler */
      }
    };
    action("play", handlers?.play);
    action("pause", handlers?.pause);
    action("previoustrack", handlers?.previous);
    action("nexttrack", handlers?.next);
    action("seekbackward", handlers?.seekBackward);
    action("seekforward", handlers?.seekForward);
    action("seekto", handlers?.seekTo);
  } catch {
    /* a media session is an enhancement; never a boot failure */
  }
}
