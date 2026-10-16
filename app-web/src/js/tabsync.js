/**
 * One sound at a time, across tabs.
 *
 * People open a music app in the tab they already have. That means several tabs
 * of the same app, all restoring the same queue, all ready to play — and the
 * browser will happily play all of them at once. No native app has this problem,
 * and no honest web app can ignore it.
 *
 * The protocol is a small leader election over `BroadcastChannel`:
 *
 *   - a tab that starts playback claims the `playing` channel by announcing
 *     `{ type: "claim", id }`;
 *   - any other tab that is playing hears the claim, stops its own audio and
 *     announces `{ type: "yield", id }`, which is what the visitor sees as
 *     "paused here — playing in another tab";
 *   - a tab that takes over sends `{ type: "takeover" }` and the previous
 *     leader yields.
 *
 * It degrades to a no-op where `BroadcastChannel` does not exist (older Safari),
 * which is stated in the Settings diagnostics rather than silently assumed to
 * work. Nothing here touches the network, and the messages carry no data beyond
 * a random tab id and the track title that is already in the user's library.
 */

export function createTabSync({ channel = null, onOtherTabPlaying = () => {}, onTakeover = () => {} } = {}) {
  const supported = Boolean(channel);
  const id = Math.random().toString(36).slice(2, 10);
  let leader = null;

  function post(message) {
    if (!supported) return;
    try {
      channel.postMessage({ ...message, from: id, at: Date.now() });
    } catch {
      /* a closed channel throws; the election then simply does not happen */
    }
  }

  if (supported) {
    channel.addEventListener("message", (event) => {
      const message = event?.data;
      if (!message || message.from === id) return;
      switch (message.type) {
        case "claim":
          // Someone else started playing: give them the floor.
          if (leader !== message.from) onOtherTabPlaying(message);
          break;
        case "yield":
          if (leader === message.from) leader = null;
          break;
        case "takeover":
          onTakeover(message);
          break;
        default:
          break;
      }
    });
  }

  return {
    get supported() {
      return supported;
    },
    get tabId() {
      return id;
    },
    /** This tab is now the one playing. */
    claim(track) {
      leader = id;
      post({ type: "claim", title: track?.title ?? null });
    },
    /** This tab stopped; whoever hears it may take over. */
    release() {
      if (leader === id) {
        leader = null;
        post({ type: "yield" });
      }
    },
    /** The visitor pressed play in *this* tab while another was playing. */
    takeOver() {
      leader = id;
      post({ type: "takeover" });
    },
  };
}

/** Create the channel, or null when the browser has none. */
export function openTabChannel(name = "dhun.playback") {
  try {
    if (typeof globalThis.BroadcastChannel !== "function") return null;
    return new globalThis.BroadcastChannel(name);
  } catch {
    return null;
  }
}
