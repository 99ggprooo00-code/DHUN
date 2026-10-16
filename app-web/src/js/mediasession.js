/**
 * Lock-screen, notification and headset controls.
 *
 * On a phone, a web player whose music cannot be paused from the lock screen is
 * not a music player — the screen goes off, and the only control left is the
 * browser chrome of a background tab. The Media Session API is what fixes that,
 * and it is also what puts the track on a car display and routes play/pause from
 * a headset.
 *
 * Nothing here is inferred: every call is guarded, because the API is absent on
 * older browsers and `setPositionState` throws on some values (a NaN duration,
 * a position past the end) — an exception in a metadata update must never stop
 * playback, which is the failure mode this module exists to prevent.
 */

export function createMediaSession({ navigator = globalThis.navigator, document = globalThis.document } = {}) {
  const session = navigator?.mediaSession;
  const supported = Boolean(session);

  /** Artwork entries must be absolute URLs and a sane size; a bad one makes
   *  some platforms drop the whole metadata object. */
  function artworkFor(track) {
    const sources = [];
    if (track?.artwork) sources.push({ src: track.artwork, sizes: "256x256", type: "image/jpeg" });
    const icon = document?.querySelector?.('link[rel="icon"]')?.href;
    if (icon) sources.push({ src: icon, sizes: "any", type: "image/svg+xml" });
    return sources;
  }

  return {
    get supported() {
      return supported;
    },

    /** Publish what is playing. Called on every track start. */
    update(track) {
      if (!supported || !track) return;
      try {
        session.metadata = new globalThis.MediaMetadata({
          title: track.title ?? "Unknown track",
          artist: track.artist ?? "",
          album: track.album ?? "",
          artwork: artworkFor(track),
        });
      } catch {
        /* metadata is a nicety; playback outranks it */
      }
    },

    /** Publish the seek bar. Invalid values are dropped, not clamped: a state
     *  the OS does not accept is worse than no state at all. */
    position({ positionMs, durationMs, rate = 1 }) {
      if (!supported || typeof session.setPositionState !== "function") return;
      const duration = Number(durationMs) / 1000;
      const position = Math.min(Number(positionMs) / 1000, duration);
      if (!Number.isFinite(duration) || duration <= 0) return;
      if (!Number.isFinite(position) || position < 0) return;
      try {
        session.setPositionState({ duration, position, playbackRate: rate });
      } catch {
        /* some engines reject edge values; the seek bar is then OS-estimated */
      }
    },

    setPlaybackState(stateName) {
      if (!supported) return;
      try {
        session.playbackState = stateName;
      } catch {
        /* read-only on some browsers */
      }
    },

    /**
     * Wire OS actions to the player.
     *
     * Every action is registered, including the ones this app cannot complete
     * (seeking a live stream, for instance) — an unregistered action makes some
     * platforms hide the whole control cluster, which is worse than a no-op.
     */
    bind(handlers = {}) {
      if (!supported || typeof session.setActionHandler !== "function") return;
      const actions = {
        play: handlers.play,
        pause: handlers.pause,
        stop: handlers.stop,
        nexttrack: handlers.next,
        previoustrack: handlers.previous,
        seekbackward: handlers.seekBackward,
        seekforward: handlers.seekForward,
        seekto: handlers.seekTo,
      };
      for (const [action, handler] of Object.entries(actions)) {
        try {
          session.setActionHandler(action, handler ? (details) => handler(details) : null);
        } catch {
          /* an unsupported action name throws on some engines */
        }
      }
    },

    clear() {
      if (!supported) return;
      try {
        session.metadata = null;
        session.playbackState = "none";
      } catch {
        /* nothing to clear */
      }
    },
  };
}
