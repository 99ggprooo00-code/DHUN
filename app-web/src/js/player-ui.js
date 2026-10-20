/**
 * The full player: the one screen that is entirely glass.
 *
 * The app's expanded player is a sheet of translucent panels over the track's
 * own artwork, blurred far enough that it becomes colour rather than a picture,
 * with the cover, the title, the scrubber and the transport stacked in the
 * middle. This build draws the same thing, in the same order, because that is
 * what makes it the same product on a second surface.
 *
 * THE TABS. Queue and Lyrics are a real `tablist`: one panel, two tabs,
 * `aria-controls` and `aria-labelledby` wired both ways. The panel is the only
 * thing that changes when a tab is pressed, so the cover and transport stay put.
 *
 * THE LYRICS TAB IS HONEST. The catalogue this build reads has a `hasLyrics`
 * flag but no lyric text endpoint, so the tab shows what is known — whether the
 * catalogue has lyrics for the track — and says plainly that this build cannot
 * draw them. It does not print an empty box and call it lyrics, and it does not
 * scrape a third-party service the visitor did not ask for.
 */

import { html } from "./dom.js";
import { artwork, errorState, icon, progressBar } from "./components.js";
import { formatDuration } from "./format.js";

export function fullPlayer(vm) {
  if (!vm.track) return html``;
  const track = vm.track;
  return html`<section class="dhun-full" role="dialog" aria-modal="true" aria-label="Now playing" tabindex="-1">
    <div class="dhun-full__backdrop" aria-hidden="true">
      ${track.image ? html`<span class="dhun-full__wash" style="background-image:url('${track.image}')"></span>` : html``}
      <span class="dhun-full__scrim"></span>
    </div>
    <div class="dhun-full__inner">
      <div class="dhun-full__bar">
        <button type="button" class="dhun-icon-button" data-action="close-player" aria-label="Collapse the player">${icon("ChevronDown", { size: 24 })}</button>
        <span class="dhun-full__context">
          <span class="dhun-full__label">${vm.context || "Playing"}</span>
          ${vm.sourceLabel ? html`<span class="dhun-full__sublabel">${vm.sourceLabel}</span>` : html``}
        </span>
        <button type="button" class="dhun-icon-button${vm.liked ? " is-liked" : ""}" data-action="toggle-like" data-id="${track.id}"
          aria-pressed="${vm.liked ? "true" : "false"}" aria-label="${vm.liked ? "Remove from Liked songs" : "Save to Liked songs"}">
          ${icon(vm.liked ? "Favorite" : "FavoriteBorder", { size: 22 })}
        </button>
      </div>

      ${vm.error
        ? errorState({
            title: vm.error.title,
            body: vm.error.body,
            technical: vm.error.technical,
          })
        : html``}

      <div class="dhun-full__stage">
        <div class="dhun-full__art">
          ${artwork(track, { size: "hero", className: "dhun-full__cover" })}
        </div>
        <div class="dhun-full__meta">
          <h1 class="dhun-full__title">${track.title}</h1>
          <p class="dhun-full__artist">${track.artist || "Unknown artist"}${track.album?.name ? html` · ${track.album.name}` : html``}</p>
          ${track.streamQuality ? html`<p class="dhun-full__quality">${track.streamQuality} stream</p>` : html``}
        </div>
      </div>

      <div class="dhun-full__seek">
        <input class="dhun-range dhun-range--seek" type="range" min="0" max="${Math.max(1, Math.round(vm.duration))}" step="1"
          value="${Math.round(vm.position)}" data-action="seek" aria-label="Seek within the track"
          aria-valuetext="${formatDuration(vm.position)} of ${formatDuration(vm.duration)}" />
        <div class="dhun-full__times"><span>${formatDuration(vm.position)}</span><span>${formatDuration(vm.duration)}</span></div>
      </div>

      <div class="dhun-full__controls">
        <button type="button" class="dhun-icon-button${vm.shuffle ? " is-active" : ""}" data-action="toggle-shuffle"
          aria-pressed="${vm.shuffle ? "true" : "false"}" aria-label="Shuffle">${icon("Shuffle", { size: 22 })}</button>
        <button type="button" class="dhun-icon-button dhun-full__transport" data-action="previous-track" aria-label="Previous track">${icon("SkipPrevious", { size: 30 })}</button>
        <button type="button" class="dhun-full__play" data-action="toggle-play" aria-label="${vm.playing ? "Pause" : "Play"}">
          ${icon(vm.playing ? "Pause" : "Play", { size: 34 })}
        </button>
        <button type="button" class="dhun-icon-button dhun-full__transport" data-action="next-track" aria-label="Next track">${icon("SkipNext", { size: 30 })}</button>
        <button type="button" class="dhun-icon-button${vm.repeat !== "off" ? " is-active" : ""}" data-action="cycle-repeat"
          aria-label="Repeat: ${vm.repeat}" title="Repeat: ${vm.repeat}">${icon(vm.repeat === "one" ? "RepeatOne" : "Repeat", { size: 22 })}</button>
      </div>

      <label class="dhun-full__volume">
        ${icon(vm.muted ? "VolumeOff" : "VolumeUp", { size: 20 })}
        <input class="dhun-range" type="range" min="0" max="1" step="0.01" value="${vm.volume}" data-action="set-volume" aria-label="Volume" />
      </label>

      <div class="dhun-full__tabs-wrap">
        <div class="dhun-full__tabs" role="tablist" aria-label="Player panel">
          <button type="button" class="dhun-chip" role="tab" id="player-tab-queue" aria-controls="player-panel"
            aria-selected="${vm.tab === "queue" ? "true" : "false"}" data-action="player-tab" data-value="queue">
            Queue${vm.queue.length ? html` <span class="dhun-chip__count">${vm.queue.length}</span>` : html``}
          </button>
          <button type="button" class="dhun-chip" role="tab" id="player-tab-lyrics" aria-controls="player-panel"
            aria-selected="${vm.tab === "lyrics" ? "true" : "false"}" data-action="player-tab" data-value="lyrics">Lyrics</button>
        </div>
        <div class="dhun-full__panel" id="player-panel" role="tabpanel" aria-labelledby="player-tab-${vm.tab}">
          ${vm.tab === "lyrics" ? lyricsPanel(vm) : queuePanel(vm)}
        </div>
      </div>
    </div>
  </section>`;
}

function queuePanel(vm) {
  if (!vm.queue.length) return html`<p class="dhun-full__empty">The queue is empty.</p>`;
  return html`<ol class="dhun-queue" role="list">
    ${vm.queue.map((row) => html`<li class="dhun-queue__row${row.current ? " is-current" : ""}">
      <button type="button" class="dhun-queue__main" data-action="play-queue-row" data-index="${row.index}" aria-label="Play ${row.title}">
        <span class="dhun-queue__index" aria-hidden="true">${row.current && vm.playing ? icon("GraphicEq", { size: 16 }) : row.index + 1}</span>
        ${artwork(row, { size: "row" })}
        <span class="dhun-queue__text">
          <span class="dhun-queue__title">${row.title}</span>
          <span class="dhun-queue__sub">${row.artist}</span>
        </span>
        <span class="dhun-queue__duration">${formatDuration(row.duration)}</span>
      </button>
      <button type="button" class="dhun-icon-button" data-action="remove-queue-row" data-index="${row.index}" aria-label="Remove ${row.title} from the queue">
        ${icon("Close", { size: 18 })}
      </button>
    </li>`)}
  </ol>`;
}

function lyricsPanel(vm) {
  if (vm.track.hasLyrics === false) {
    return html`<div class="dhun-lyrics-state">
      <p class="dhun-full__empty">The catalogue has no lyrics for this track.</p>
      <p class="dhun-lyrics-state__note">JioSaavn's record for “${vm.track.title}” carries no lyric text.</p>
    </div>`;
  }
  return html`<div class="dhun-lyrics-state">
    <p class="dhun-full__empty">Lyrics are not available in this build.</p>
    <p class="dhun-lyrics-state__note">The catalogue marks this track as having lyrics, but its public API exposes no lyric text, and this app will not fetch words from a service you did not choose. Nothing is fabricated to fill this panel.</p>
  </div>`;
}

export function playerProgress(vm) {
  return progressBar({ value: vm.percent, className: "dhun-full__hairline" });
}
