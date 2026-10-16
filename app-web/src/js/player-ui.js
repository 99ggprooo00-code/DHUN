/**
 * The player's two surfaces: the docked mini bar and the expanded sheet.
 *
 * Both are pure functions of the player's state, which is what makes the states
 * a web player must not hide — buffering, waiting-for-a-gesture, a failed
 * stream, a demo row with no audio — visible instead of implied by a spinner
 * that never resolves.
 *
 * Design decisions worth stating:
 *
 *   - **The mini player never lies about progress.** Its hairline is driven by
 *     real `buffered` and `position` values, so a stalled stream shows a bar
 *     that has stopped rather than one that keeps creeping.
 *   - **The error is inside the player**, next to the transport that caused it,
 *     with the two actions that can fix it (Retry, change source). An error
 *     toast in the corner of a screen the user has scrolled past is not help.
 *   - **The equaliser badge tells the truth.** When a stream is cross-origin
 *     without CORS headers the bytes cannot be processed, so the badge says the
 *     equaliser is inactive for this track rather than pretending.
 */

import { html, raw } from "./dom.js";
import { icon, artwork, trackRow, emptyState, notice } from "./views.js";
import { formatDuration } from "./format.js";
import { AUDIO_STATUS } from "./audio.js";

/** Status → the sentence the player prints under the transport. */
export function statusLine(state) {
  if (state.error) {
    if (state.error.kind === "demo") return "Demo row — no audio to play";
    if (state.error.kind === "autoplay-blocked") return "Press play to allow sound";
    return "This track could not be played";
  }
  switch (state.audioStatus) {
    case AUDIO_STATUS.LOADING:
      return "Resolving the stream…";
    case AUDIO_STATUS.BUFFERING:
      return "Buffering…";
    case AUDIO_STATUS.BLOCKED:
      return "Waiting for a click before it may play";
    case AUDIO_STATUS.ENDED:
      return "Finished";
    case AUDIO_STATUS.ERROR:
      return "Playback error";
    default:
      return "";
  }
}

export function miniPlayer(state, { context = "" } = {}) {
  const track = state.track;
  if (!track) return html``;
  const duration = state.durationMs || track.durationMs || 0;
  const progress = duration > 0 ? Math.min(100, (state.positionMs / duration) * 100) : 0;
  const buffered = duration > 0 ? Math.min(100, (state.bufferedMs / duration) * 100) : 0;
  const busy = state.audioStatus === AUDIO_STATUS.LOADING || state.resolving;

  return html`<div class="dhun-mini" data-playing="${state.playing ? "true" : "false"}" role="region" aria-label="Now playing">
    <button type="button" class="dhun-mini__open" data-action="expand-player" aria-label="Open the full player">
      ${artwork(track, { size: "mini" })}
      <span class="dhun-mini__meta">
        <span class="dhun-mini__title">${track.title}</span>
        <span class="dhun-mini__subtitle">${statusLine(state) || track.artist || "Unknown artist"}</span>
      </span>
    </button>
    <div class="dhun-mini__controls">
      <button type="button" class="dhun-icon-button" data-action="toggle-play" aria-label="${state.playing ? "Pause" : "Play"}">
        ${busy ? html`<span class="dhun-spinner" aria-hidden="true"></span>` : icon(state.playing ? "Pause" : "Play", { size: 24 })}
      </button>
      <button type="button" class="dhun-icon-button" data-action="next-track" aria-label="Next track">${icon("SkipNext", { size: 22 })}</button>
    </div>
    <div class="dhun-mini__progress" role="progressbar" aria-label="Track progress"
      aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.round(progress)}">
      <span class="dhun-mini__buffered" style="width:${buffered.toFixed(1)}%"></span>
      <i style="width:${progress.toFixed(1)}%"></i>
    </div>
    ${context ? html`<span class="dhun-visually-hidden">Playing from ${context}</span>` : html``}
  </div>`;
}

function seekBar(state) {
  const duration = state.durationMs || 0;
  const max = duration > 0 ? Math.round(duration / 1000) : 0;
  const value = Math.min(max, Math.round(state.positionMs / 1000));
  return html`<div class="dhun-seek">
    <input type="range" min="0" max="${max || 1}" step="1" value="${value}" data-action="seek"
      aria-label="Seek" aria-valuetext="${formatDuration(state.positionMs)} of ${formatDuration(duration)}"
      ${max ? raw("") : raw("disabled")} />
    <div class="dhun-seek__times">
      <span>${formatDuration(state.positionMs)}</span>
      <span>${duration ? `-${formatDuration(duration - state.positionMs)}` : state.audioStatus === AUDIO_STATUS.LOADING ? "…" : ""}</span>
    </div>
  </div>`;
}

function transport(state) {
  return html`<div class="dhun-transport">
    <button type="button" class="dhun-icon-button ${state.shuffle ? "dhun-icon-button--active" : ""}" data-action="toggle-shuffle"
      aria-pressed="${state.shuffle ? "true" : "false"}" aria-label="Shuffle">${icon("Shuffle", { size: 22 })}</button>
    <button type="button" class="dhun-icon-button" data-action="previous-track" aria-label="Previous track">${icon("SkipPrevious", { size: 28 })}</button>
    <button type="button" class="dhun-transport__play" data-action="toggle-play" aria-label="${state.playing ? "Pause" : "Play"}">
      ${state.resolving || state.audioStatus === AUDIO_STATUS.LOADING
        ? html`<span class="dhun-spinner dhun-spinner--lg" aria-hidden="true"></span>`
        : icon(state.playing ? "Pause" : "Play", { size: 32 })}
    </button>
    <button type="button" class="dhun-icon-button" data-action="next-track" aria-label="Next track">${icon("SkipNext", { size: 28 })}</button>
    <button type="button" class="dhun-icon-button ${state.repeat !== "off" ? "dhun-icon-button--active" : ""}" data-action="cycle-repeat"
      aria-label="Repeat: ${state.repeat}" aria-pressed="${state.repeat !== "off" ? "true" : "false"}">
      ${icon(state.repeat === "one" ? "RepeatOne" : "Repeat", { size: 22 })}
    </button>
  </div>`;
}

function volumeRow(state) {
  const value = Math.round((state.muted ? 0 : state.volume) * 100);
  return html`<div class="dhun-volume">
    <button type="button" class="dhun-icon-button" data-action="toggle-mute" aria-label="${state.muted ? "Unmute" : "Mute"}">
      ${icon(state.muted || value === 0 ? "VolumeOff" : "VolumeUp", { size: 22 })}
    </button>
    <input type="range" min="0" max="100" step="1" value="${value}" data-action="set-volume" aria-label="Volume" />
    <span class="dhun-volume__value">${value}%</span>
  </div>`;
}

/**
 * The visualiser. It reads the analyser node when the audio graph exists and
 * draws nothing otherwise — an idle animation over silent audio would be
 * decoration pretending to be evidence.
 */
function visualiser(state) {
  if (!state.canProcess || !state.playing) return html``;
  return html`<canvas class="dhun-visualiser" width="320" height="72" aria-hidden="true"></canvas>`;
}

function queuePanel(state) {
  if (!state.queue.length) {
    return emptyState({ title: "The queue is empty", body: "Play something and its neighbours appear here.", iconName: "QueueMusic" });
  }
  return html`<ul class="dhun-tracklist dhun-tracklist--queue">
    ${state.queue.map((track, index) =>
      trackRow(track, {
        index,
        variant: "queue",
        current: index === state.index,
        playing: state.playing && index === state.index,
        failed: state.failures?.[index] ?? null,
      }),
    )}
  </ul>`;
}

function lyricsPanel(state) {
  const lyrics = state.lyrics ?? { status: "idle", lines: [] };
  if (lyrics.status === "loading") return html`<p class="dhun-lyrics__status">Looking for lyrics…</p>`;
  if (lyrics.status === "error") {
    return html`<p class="dhun-lyrics__status">Lyrics could not be fetched: ${lyrics.message}</p>`;
  }
  if (!lyrics.lines.length) {
    return emptyState({
      title: "No lyrics for this track",
      body: "The source did not provide any. DHUN does not invent them.",
      iconName: "Lyrics",
    });
  }
  return html`<div class="dhun-lyrics" role="log" aria-label="Lyrics" aria-live="off">
    ${lyrics.lines.map(
      (line, index) => html`<p class="dhun-lyrics__line" data-active="${lyrics.activeIndex === index ? "true" : "false"}">
        <span>${line.text}</span>
        ${state.romanize && line.roman ? html`<span class="dhun-lyrics__roman">${line.roman}</span>` : html``}
      </p>`,
    )}
  </div>`;
}

/** The error/gesture/status strip inside the expanded player. */
function playerNotice(state) {
  if (state.error) {
    const actions = [];
    if (state.error.kind !== "demo") actions.push({ id: "retry-track", label: "Retry" });
    actions.push({ id: "open-settings-source", label: "Data source" });
    if (state.error.kind === "demo") actions.push({ id: "open-settings-source", label: "Configure a source" });
    return notice({
      tone: state.error.kind === "demo" ? "info" : "error",
      title: statusLine(state),
      body: state.error.message,
      actions,
    });
  }
  if (state.needsGesture) {
    return notice({
      tone: "warn",
      title: "Your browser is waiting for a gesture",
      body: "Autoplay policies stop sound until you interact with the page. Press play, or tap anywhere and DHUN resumes.",
      actions: [{ id: "toggle-play", label: "Play" }],
    });
  }
  if (state.audioStatus === AUDIO_STATUS.BUFFERING) {
    return notice({ tone: "info", body: "Buffering — the stream is catching up with you." });
  }
  return html``;
}

export function fullPlayer(state) {
  const track = state.track;
  const context = state.context ?? "";
  return html`<section class="dhun-full" role="dialog" aria-modal="true" aria-label="Now playing">
    <div class="dhun-full__backdrop" aria-hidden="true">
      ${track ? artwork(track, { size: "backdrop" }) : html``}
      <span class="dhun-full__scrim"></span>
    </div>
    <div class="dhun-full__inner">
      <div class="dhun-full__bar">
        <button type="button" class="dhun-icon-button dhun-icon-button--up" data-action="collapse-player" aria-label="Collapse the player">
          ${icon("ChevronDown", { size: 24 })}
        </button>
        <span class="dhun-full__context">
          <span class="dhun-full__label">${context || "Playing"}</span>
          ${state.sourceLabel ? html`<span class="dhun-full__sublabel">${state.sourceLabel}</span>` : html``}
        </span>
        <button type="button" class="dhun-icon-button" data-action="track-menu" aria-label="Track options"${track ? html` data-id="${track.id}"` : raw("")}>
          ${icon("MoreVert", { size: 24 })}
        </button>
      </div>

      <div class="dhun-full__stage">
        <div class="dhun-full__art">
          ${track ? artwork(track, { size: "hero" }) : emptyState({ title: "Nothing is playing", body: "Pick a track from Home, Search or your library." })}
          ${visualiser(state)}
        </div>
        ${track
          ? html`<div class="dhun-full__meta">
              <h1 class="dhun-full__title">${track.title}</h1>
              <p class="dhun-full__artist">${track.artist || "Unknown artist"}${track.album ? html` · ${track.album}` : html``}</p>
            </div>`
          : html``}
      </div>

      ${playerNotice(state)}
      ${seekBar(state)}
      ${transport(state)}
      ${volumeRow(state)}

      <div class="dhun-full__actions">
        <button type="button" class="dhun-icon-button ${state.liked ? "dhun-icon-button--active" : ""}" data-action="toggle-like"
          aria-pressed="${state.liked ? "true" : "false"}" aria-label="Like this track">${icon(state.liked ? "Favorite" : "FavoriteBorder", { size: 22 })}</button>
        <button type="button" class="dhun-icon-button ${state.eqActive ? "dhun-icon-button--active" : ""}" data-action="open-equalizer"
          aria-label="Equaliser" title="${state.eqLabel}">${icon("GraphicEq", { size: 22 })}</button>
        <button type="button" class="dhun-icon-button" data-action="track-menu" aria-label="Add to playlist">${icon("Add", { size: 22 })}</button>
        <span class="dhun-full__tabs">
          <button type="button" class="dhun-chip" data-action="player-tab" data-tab="queue" aria-selected="${state.tab === "queue" ? "true" : "false"}" role="tab">
            Queue${state.queue.length ? html` <span class="dhun-chip__count">${state.queue.length}</span>` : html``}
          </button>
          <button type="button" class="dhun-chip" data-action="player-tab" data-tab="lyrics" aria-selected="${state.tab === "lyrics" ? "true" : "false"}" role="tab">
            Lyrics
          </button>
        </span>
      </div>

      <div class="dhun-full__panel" role="tabpanel">
        ${state.tab === "lyrics" ? lyricsPanel(state) : queuePanel(state)}
      </div>
    </div>
  </section>`;
}
