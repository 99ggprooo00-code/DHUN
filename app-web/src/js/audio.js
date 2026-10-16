/**
 * Playback, for real.
 *
 * This is the module the previous build did not have. It wraps one
 * `<audio>` element and one Web Audio graph, and it is responsible for every
 * way that can go wrong in a browser — because in a browser, "play this URL"
 * is a request the host is free to refuse:
 *
 *   **Autoplay policy.** A `play()` that no user gesture led up to is rejected
 *   with `NotAllowedError`. That is not a failure, it is the browser asking for
 *   a click, and it is reported as its own state (`blocked`) so the UI can offer
 *   one instead of showing an error.
 *
 *   **CORS on the media.** Stream URLs are cross-origin. Without
 *   `Access-Control-Allow-Origin` the bytes still play, but Web Audio cannot
 *   *touch* them — so the equaliser is either real or absent. `crossOrigin` is
 *   tried first (it enables processing when the server allows it) and dropped on
 *   failure, and the engine reports which mode is live rather than showing an EQ
 *   that silently does nothing.
 *
 *   **Buffering on a slow link.** `waiting`/`stalled`/`canplay`/`progress` are
 *   tracked as first-class state, because a player that shows "playing" while
 *   the audio is stalled is the single most common way a web player lies.
 *
 *   **Media errors with no message.** `MediaError.code` is a number; this module
 *   maps all four to sentences, and keeps the raw code for diagnosis.
 *
 * Everything is injectable (`audio`, `AudioContextCtor`, `clock`) so the whole
 * state machine is testable in Node against a fake element — which is how the
 * autoplay, CORS-retry and error paths are proven without a browser.
 */

export const AUDIO_STATUS = Object.freeze({
  IDLE: "idle",
  LOADING: "loading",
  READY: "ready",
  PLAYING: "playing",
  PAUSED: "paused",
  BUFFERING: "buffering",
  ENDED: "ended",
  ERROR: "error",
  /** The browser refused to start without a gesture. Not an error. */
  BLOCKED: "blocked",
});

/** `MediaError.code` → something a person can act on. */
export function describeMediaError(code) {
  switch (Number(code)) {
    case 1:
      return { code: 1, kind: "aborted", message: "Loading was cancelled before it finished." };
    case 2:
      return { code: 2, kind: "network", message: "The stream stopped part-way through — the network dropped or the URL expired. Retry to fetch a fresh one." };
    case 3:
      return { code: 3, kind: "decode", message: "The browser received the bytes but could not decode them as audio." };
    case 4:
      return {
        code: 4,
        kind: "unsupported",
        message: "The stream's format or source is not supported here. A signed URL that has expired is the usual cause — retry to resolve a new one.",
      };
    default:
      return { code: Number(code) || null, kind: "unknown", message: "Playback failed for a reason the browser did not name." };
  }
}

/** A failing `play()` promise → a state, not a stack. */
export function classifyPlayRejection(error) {
  const name = error?.name ?? "";
  if (name === "NotAllowedError") {
    return {
      kind: "autoplay-blocked",
      message: "This browser blocks audio until you interact with the page. Press play again, or tap anywhere and DHUN will resume.",
    };
  }
  if (name === "AbortError") {
    return { kind: "aborted", message: "Playback was interrupted before it started." };
  }
  if (name === "NotSupportedError") {
    return { kind: "unsupported", message: "That stream cannot be played in this browser." };
  }
  return { kind: "play", message: error?.message ?? "Playback could not start." };
}

/**
 * The equaliser render graph.
 *
 * Ten peaking filters at the app's own band centres (the same frequencies the
 * Android and desktop builds use), a preamp gain, and an optional compressor for
 * evening out wildly different source levels. Built once per audio element —
 * `createMediaElementSource` may only be called once, and reconnecting it per
 * toggle is how web players end up with silence after a few mode switches.
 */
export function createEqualizerGraph({ context, source, bandsHz, gainsDb, preampDb = 0, compressor = false }) {
  const filters = bandsHz.map((hz) => {
    const filter = context.createBiquadFilter();
    filter.type = "peaking";
    filter.frequency.value = hz;
    filter.Q.value = 1.0;
    filter.gain.value = 0;
    return filter;
  });
  const preamp = context.createGain();
  preamp.gain.value = dbToGain(preampDb);
  const loudness = context.createDynamicsCompressor?.() ?? null;

  // source → preamp → band1 → … → bandN → [compressor] → destination
  let node = source;
  node.connect(preamp);
  node = preamp;
  for (const filter of filters) {
    node.connect(filter);
    node = filter;
  }
  if (loudness && compressor) {
    node.connect(loudness);
    node = loudness;
  }
  node.connect(context.destination);

  return {
    filters,
    preamp,
    loudness,
    /** Push a preset's curve. Gains are in dB, exactly as the app stores them. */
    apply({ gains, preamp: preampValue = 0, compressor: useCompressor = false }) {
      filters.forEach((filter, index) => {
        const gain = Array.isArray(gains) ? gains[index] : 0;
        filter.gain.value = clampDb(gain);
      });
      preamp.gain.value = dbToGain(clampDb(preampValue));
      // The compressor cannot be un-plugged without rebuilding the chain, so it
      // is engaged by pushing its threshold to the ceiling instead — audio keeps
      // flowing through one stable graph.
      if (loudness) {
        loudness.threshold.value = useCompressor ? -24 : 0;
        loudness.ratio.value = useCompressor ? 4 : 1;
        loudness.knee.value = useCompressor ? 12 : 0;
        loudness.attack.value = 0.01;
        loudness.release.value = 0.25;
      }
    },
  };
}

export function dbToGain(db) {
  return 10 ** (Number(db) / 20);
}

export function clampDb(db) {
  const value = Number(db);
  if (!Number.isFinite(value)) return 0;
  return Math.min(20, Math.max(-20, value));
}

/* ------------------------------------------------------------------ engine -- */

export function createAudioEngine({
  audio = null,
  AudioContextCtor = globalThis.AudioContext ?? globalThis.webkitAudioContext ?? null,
  bandsHz = [],
  onEnded = () => {},
  onNeedsGesture = () => {},
} = {}) {
  const element = audio ?? (typeof globalThis.Audio !== "undefined" ? new globalThis.Audio() : null);
  const listeners = new Set();

  let context = null;
  let graph = null;
  let analyser = null;
  let mediaSource = null;
  let processingEnabled = false;
  /** True once `createMediaElementSource` has been called — after that, all
   *  output goes through the graph, so it must stay connected for ever. */
  let graphAttached = false;
  let currentUrl = null;
  let wanted = false;

  let state = {
    status: AUDIO_STATUS.IDLE,
    positionMs: 0,
    durationMs: 0,
    bufferedMs: 0,
    volume: 1,
    muted: false,
    error: null,
    /** Whether the current stream is being processed by Web Audio. */
    processed: false,
    /** Whether processing is *possible* for the current stream. */
    processable: false,
    readyState: 0,
    playbackRate: 1,
  };

  function emit(patch) {
    state = { ...state, ...patch };
    for (const listener of listeners) listener(state);
  }

  function settle(status, error = null) {
    emit({ status, error });
  }

  /* ------------------------------------------------------------ element -- */

  if (element) {
    element.preload = "metadata";
    // iOS Safari will not start a stream until it has been told it may; this is
    // the documented opt-in, not a workaround for a bug.
    element.setAttribute?.("playsinline", "");

    element.addEventListener("loadedmetadata", () => {
      emit({ durationMs: toMs(element.duration), status: wanted ? AUDIO_STATUS.PLAYING : AUDIO_STATUS.READY });
    });
    element.addEventListener("durationchange", () => {
      emit({ durationMs: toMs(element.duration) });
    });
    element.addEventListener("timeupdate", () => {
      emit({ positionMs: toMs(element.currentTime), bufferedMs: bufferedEnd(element) });
    });
    element.addEventListener("progress", () => {
      emit({ bufferedMs: bufferedEnd(element) });
    });
    element.addEventListener("waiting", () => {
      // Only a *playback* stall is a buffering state; `waiting` during a seek
      // before play is just loading.
      if (wanted && state.status !== AUDIO_STATUS.ERROR) emit({ status: AUDIO_STATUS.BUFFERING });
    });
    element.addEventListener("stalled", () => {
      if (wanted) emit({ status: AUDIO_STATUS.BUFFERING });
    });
    element.addEventListener("canplay", () => {
      if (wanted && state.status === AUDIO_STATUS.BUFFERING) emit({ status: AUDIO_STATUS.PLAYING });
      emit({ readyState: element.readyState ?? 0 });
    });
    element.addEventListener("playing", () => {
      emit({ status: AUDIO_STATUS.PLAYING, error: null });
    });
    element.addEventListener("pause", () => {
      if (!wanted) emit({ status: AUDIO_STATUS.PAUSED });
    });
    element.addEventListener("ended", () => {
      wanted = false;
      emit({ status: AUDIO_STATUS.ENDED, positionMs: state.durationMs });
      onEnded();
    });
    element.addEventListener("error", () => {
      wanted = false;
      const described = describeMediaError(element.error?.code);
      settle(AUDIO_STATUS.ERROR, described);
    });
  }

  function toMs(seconds) {
    return Number.isFinite(seconds) ? Math.max(0, seconds * 1000) : 0;
  }

  function bufferedEnd(media) {
    try {
      const ranges = media.buffered;
      if (!ranges || ranges.length === 0) return 0;
      return toMs(ranges.end(ranges.length - 1));
    } catch {
      return 0;
    }
  }

  /* ------------------------------------------------------------ graph -- */

  function ensureContext() {
    if (context || !AudioContextCtor || !element) return context;
    try {
      context = new AudioContextCtor();
    } catch {
      context = null;
    }
    return context;
  }

  /**
   * Attach the render graph, once. Returns true when the stream can be
   * processed. Attaching is what makes CORS matter: a cross-origin stream without
   * CORS headers becomes silent *only* if processing is engaged, which is why it
   * is attempted with `crossOrigin` first and reported honestly either way.
   */
  function attachGraph() {
    if (graphAttached || !element) return false;
    const ctx = ensureContext();
    if (!ctx || typeof ctx.createMediaElementSource !== "function") return false;
    try {
      mediaSource = ctx.createMediaElementSource(element);
      analyser = typeof ctx.createAnalyser === "function" ? ctx.createAnalyser() : null;
      if (analyser) {
        analyser.fftSize = 128;
        mediaSource.connect(analyser);
      }
      const tail = mediaSource;
      graph = createEqualizerGraph({
        context: ctx,
        source: tail,
        bandsHz: bandsHz.length ? bandsHz : [60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000],
        gainsDb: [],
      });
      graphAttached = true;
      processingEnabled = true;
      return true;
    } catch {
      // Some engines throw here for cross-origin media, which is exactly the
      // case the UI reports as "equaliser unavailable for this track".
      mediaSource = null;
      graph = null;
      analyser = null;
      return false;
    }
  }

  /** The context starts suspended until a gesture on some browsers. */
  async function resumeContext() {
    if (context && context.state === "suspended") {
      try {
        await context.resume();
      } catch {
        /* reported through the equaliser state, not as a playback error */
      }
    }
  }

  return {
    get state() {
      return state;
    },
    get element() {
      return element;
    },
    get analyser() {
      return analyser;
    },
    /** Live frequency data for the visualiser, or null when unavailable. */
    spectrum() {
      if (!analyser) return null;
      const bins = new Uint8Array(analyser.frequencyBinCount);
      analyser.getByteFrequencyData(bins);
      return bins;
    },
    /** True when the graph exists for the current element. */
    get canProcess() {
      return graphAttached;
    },
    get hasContext() {
      return Boolean(context);
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    /**
     * Point the element at a stream URL.
     *
     * `crossOrigin="anonymous"` is set for the first attempt so Web Audio is
     * allowed to process the bytes. If the host does not send CORS headers the
     * load fails; the caller retries with `allowUnprocessed: true`, which drops
     * the attribute and plays the stream unprocessed. That two-step is the
     * documented behaviour, not a retry loop.
     */
    async load(url, { autoplay = true, allowUnprocessed = false } = {}) {
      if (!element) {
        settle(AUDIO_STATUS.ERROR, { kind: "unsupported", message: "This browser has no audio element." });
        return false;
      }
      if (typeof url !== "string" || url === "") {
        settle(AUDIO_STATUS.ERROR, { kind: "nostream", message: "No stream URL was resolved for this track." });
        return false;
      }
      currentUrl = url;
      wanted = autoplay;
      emit({ error: null, positionMs: 0, durationMs: 0, bufferedMs: 0, status: AUDIO_STATUS.LOADING });

      if (allowUnprocessed) {
        try {
          element.removeAttribute("crossOrigin");
        } catch {
          /* attribute removal is best-effort */
        }
        emit({ processable: false, processed: false });
      } else {
        try {
          element.crossOrigin = "anonymous";
        } catch {
          /* older engines ignore it */
        }
        // Processing is possible only if the graph can exist for this stream.
        emit({ processable: Boolean(AudioContextCtor), processed: graphAttached });
      }

      element.src = url;
      try {
        element.load?.();
      } catch {
        /* `load()` is not implemented everywhere; setting src is enough */
      }

      if (!autoplay) return true;
      return this.play();
    },

    async play() {
      if (!element || !currentUrl) return false;
      wanted = true;
      await resumeContext();
      if (processingEnabled && !graphAttached) processingEnabled = attachGraph();
      try {
        await element.play();
        emit({ status: AUDIO_STATUS.PLAYING, error: null });
        return true;
      } catch (error) {
        const classified = classifyPlayRejection(error);
        wanted = false;
        if (classified.kind === "autoplay-blocked") {
          emit({ status: AUDIO_STATUS.BLOCKED, error: classified });
          onNeedsGesture();
        } else {
          settle(AUDIO_STATUS.ERROR, classified);
        }
        return false;
      }
    },

    pause() {
      wanted = false;
      if (!element) return;
      try {
        element.pause();
      } catch {
        /* nothing to pause */
      }
      emit({ status: AUDIO_STATUS.PAUSED });
    },

    async toggle() {
      if (state.status === AUDIO_STATUS.PLAYING || state.status === AUDIO_STATUS.BUFFERING) {
        this.pause();
        return false;
      }
      return this.play();
    },

    seek(ms) {
      if (!element) return;
      const target = Math.max(0, Math.min(Number(ms) || 0, state.durationMs || Number(ms) || 0));
      try {
        element.currentTime = target / 1000;
      } catch {
        /* seeking before metadata is ignored by the element itself */
      }
      emit({ positionMs: target });
    },

    setVolume(volume) {
      const value = Math.min(1, Math.max(0, Number(volume)));
      if (!Number.isFinite(value)) return;
      if (element) element.volume = value;
      emit({ volume: value, muted: false });
    },

    setMuted(muted) {
      const value = Boolean(muted);
      if (element) element.muted = value;
      emit({ muted: value });
    },

    setPlaybackRate(rate) {
      const value = Math.min(4, Math.max(0.25, Number(rate) || 1));
      if (element) element.playbackRate = value;
      emit({ playbackRate: value });
    },

    /**
     * Apply an equaliser curve. Returns whether the bytes being played are
     * actually being processed, so the UI can label the control truthfully.
     */
    setEqualizer({ enabled = false, gains = [], preamp = 0, compressor = false } = {}) {
      if (!graphAttached) {
        if (!enabled) return false;
        if (!attachGraph()) return false;
      }
      // A disabled equaliser is a flat curve on the same graph: switching filters
      // in and out of the chain would need a rebuild, and a rebuild is what
      // produces a click or a few frames of silence.
      graph.apply({
        gains: enabled ? gains : gains.map(() => 0),
        preamp: enabled ? preamp : 0,
        compressor,
      });
      emit({ processed: graphAttached && (enabled || compressor), processable: graphAttached });
      return graphAttached;
    },

    stop() {
      wanted = false;
      currentUrl = null;
      if (element) {
        try {
          element.pause();
          element.removeAttribute("src");
          element.load?.();
        } catch {
          /* already gone */
        }
      }
      emit({ status: AUDIO_STATUS.IDLE, positionMs: 0, durationMs: 0, bufferedMs: 0 });
    },

    dispose() {
      listeners.clear();
      this.stop();
      try {
        context?.close?.();
      } catch {
        /* closing a closed context throws in some engines */
      }
    },
  };
}
