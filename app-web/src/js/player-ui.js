/**
 * MiniPlayer and FullPlayer.
 *
 * Mirrors shared/src/commonMain/kotlin/dev/dhun/ui/player/: MiniPlayer.kt
 * (docked 72dp bar, 56dp thumb, play/pause + next, a progress hairline),
 * FullPlayer.kt (immersive blurred backdrop, artwork, seek bar, transport,
 * PlayerTabs for Up next / Lyrics) and TransportControls.kt
 * (shuffle · previous · play/pause 52dp · next · repeat).
 */

import { html, raw } from "./dom.js";
import { icon } from "./views.js";
import { formatDuration } from "./format.js";
import { artworkStyle } from "./catalog.js";
import { REPEAT, BACKEND } from "./player.js";

export function miniPlayer(player) {
  const { state } = player;
  const track = player.currentTrack;
  if (!track) return "";
  const progress = state.durationMs > 0 ? (state.positionMs / state.durationMs) * 100 : 0;
  return html`<div class="dhun-mini" data-testid="mini-player" ${raw(track ? "" : "hidden")}>
    <span class="dhun-mini__art" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></span>
    <button class="dhun-mini__meta" type="button" data-action="expand-player" aria-label="Open the full player">
      <span class="dhun-mini__title">${track.title}</span>
      <span class="dhun-mini__subtitle">${track.artist}</span>
    </button>
    <button
      class="dhun-icon-button"
      type="button"
      data-action="toggle-play"
      aria-label="${state.playing ? "Pause" : "Play"} ${track.title}"
    >
      ${icon(state.playing ? "Pause" : "Play")}
    </button>
    <button class="dhun-icon-button" type="button" data-action="next" aria-label="Next track">${icon("SkipNext")}</button>
    <span class="dhun-mini__progress" aria-hidden="true"><i style="width:${progress.toFixed(2)}%"></i></span>
  </div>`;
}

export function fullPlayer(player, { lyrics = [], activeLyricIndex = -1, tab = "queue" } = {}) {
  const { state } = player;
  const track = player.currentTrack;
  if (!track) return "";
  const progress = state.durationMs > 0 ? (state.positionMs / state.durationMs) * 100 : 0;

  return html`<div class="dhun-full" data-testid="full-player" role="dialog" aria-modal="true" aria-label="Now playing">
    <div class="dhun-full__backdrop" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></div>
    <div class="dhun-full__scrim" aria-hidden="true"></div>
    <div class="dhun-full__inner">
      <div class="dhun-full__bar">
        <button class="dhun-icon-button" type="button" data-action="collapse-player" aria-label="Collapse player">
          ${icon("ChevronDown")}
        </button>
        <p class="dhun-full__label">Now playing</p>
        <button class="dhun-icon-button" type="button" data-action="open-queue" aria-label="Queue actions for ${track.title}">
          ${icon("QueueMusic")}
        </button>
      </div>

      <span class="dhun-full__art" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></span>
      <div class="dhun-full__meta">
        <h2 class="dhun-full__title">${track.title}</h2>
        <p class="dhun-full__artist">${track.artist} · ${track.album}</p>
      </div>

      <div class="dhun-full__controls">
        <div class="dhun-seek">
          <input
            type="range"
            min="0"
            max="${Math.max(1, Math.round(state.durationMs))}"
            step="250"
            value="${Math.round(state.positionMs)}"
            aria-label="Playback position"
            data-action="seek"
          />
          <div class="dhun-seek__times">
            <span>${formatDuration(state.positionMs)}</span>
            <span>${formatDuration(state.durationMs)}</span>
          </div>
        </div>

        <div class="dhun-transport">
          <button
            class="dhun-icon-button dhun-transport__secondary"
            type="button"
            data-action="toggle-shuffle"
            aria-label="Shuffle"
            aria-pressed="${state.shuffle ? "true" : "false"}"
          >
            ${icon("Shuffle", { className: state.shuffle ? "dhun-icon" : "dhun-icon" })}
          </button>
          <button class="dhun-icon-button dhun-transport__secondary" type="button" data-action="previous" aria-label="Previous track">
            ${icon("SkipPrevious")}
          </button>
          <button
            class="dhun-transport__play"
            type="button"
            data-action="toggle-play"
            aria-label="${state.playing ? "Pause" : "Play"} ${track.title}"
          >
            ${icon(state.playing ? "Pause" : "Play")}
          </button>
          <button class="dhun-icon-button dhun-transport__secondary" type="button" data-action="next" aria-label="Next track">
            ${icon("SkipNext")}
          </button>
          <button
            class="dhun-icon-button dhun-transport__secondary"
            type="button"
            data-action="cycle-repeat"
            aria-label="Repeat mode: ${state.repeat}"
          >
            ${icon(state.repeat === REPEAT.ONE ? "RepeatOne" : "Repeat")}
          </button>
        </div>

        ${raw(backendNotice(state))}

        <div class="dhun-tabs" role="tablist" aria-label="Player panels">
          <button class="dhun-tab" type="button" role="tab" data-action="player-tab" data-tab="queue" aria-selected="${tab === "queue" ? "true" : "false"}">
            Up next
          </button>
          <button class="dhun-tab" type="button" role="tab" data-action="player-tab" data-tab="lyrics" aria-selected="${tab === "lyrics" ? "true" : "false"}">
            Lyrics
          </button>
        </div>

        ${tab === "queue" ? raw(queuePanel(player)) : raw(lyricsPanel(lyrics, activeLyricIndex))}
      </div>
    </div>
  </div>`;
}

/** Which backend is running is part of the UI, not a log line: the visitor is
 *  entitled to know they are looking at a clock, not audio. */
function backendNotice(state) {
  if (state.backend === BACKEND.AUDIO) return "";
  const detail = state.error?.message ? ` ${escapeMessage(state.error.message)}` : "";
  return html`<div class="dhun-notice dhun-notice--error" role="note" data-testid="backend-notice">
    <p>
      <strong>No audio stream.</strong> This build has no playable source for this track, so the transport
      advances a labelled clock — it makes no sound.${raw(detail ? ` (Last error: ${detail})` : "")}
    </p>
  </div>`;
}

function escapeMessage(value) {
  return String(value).replace(/[<>&]/g, "");
}

function queuePanel(player) {
  const { state } = player;
  if (state.queue.length === 0) return html`<p class="dhun-state__body">The queue is empty.</p>`;
  return html`<ol class="dhun-tracklist" style="padding:var(--dhun-space-md) 0 0">
    ${raw(
      state.queue
        .map(
          (track, index) => html`<li>
            <button
              class="dhun-track"
              type="button"
              data-action="play-queue-index"
              data-index="${index}"
              data-playing="${index === state.index ? "true" : "false"}"
            >
              <span class="dhun-track__art" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></span>
              <span class="dhun-track__meta">
                <span class="dhun-track__title">${track.title}</span>
                <span class="dhun-track__subtitle">${track.artist}</span>
              </span>
              <span class="dhun-track__time">${formatDuration(track.durationMs)}</span>
            </button>
          </li>`,
        )
        .join(""),
    )}
  </ol>`;
}

function lyricsPanel(lyrics, activeIndex) {
  if (lyrics.length === 0) {
    return html`<p class="dhun-state__body" style="padding:var(--dhun-space-lg) 0">
      No lyrics for this track. Synced lyrics come from LRCLIB and YouTube Music lyrics in the app.
    </p>`;
  }
  return html`<div class="dhun-lyrics" data-testid="lyrics">
    ${raw(
      lyrics
        .map(
          (line, index) => html`<p class="dhun-lyrics__line" data-active="${index === activeIndex ? "true" : "false"}">
            ${line.text === "" ? "♪" : line.text}
          </p>`,
        )
        .join(""),
    )}
  </div>`;
}
