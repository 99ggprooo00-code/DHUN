/**
 * The shell: app bar, navigation, and the docked mini player.
 *
 * The app's shell is a glass surface over the page — the bar at the top, the
 * navigation at the bottom of a phone (a rail beside the content on a wide
 * window), and the mini player docked above the navigation. The web build keeps
 * that structure because it is the product's structure, not a phone convention:
 * a visitor who has used the Android app should recognise where everything is.
 *
 * The mini player renders in `renderChrome`, never in `renderView`, so pressing
 * play does not rebuild the screen under the visitor's finger.
 */

import { html } from "./dom.js";
import { artwork, icon, progressBar } from "./components.js";
import { NAV_TABS } from "./router.js";

/** The top bar. `back` is the app's own back control, not the browser's. */
export function appBar({ title, overline = "", back = false, actions = "", hidden = false }) {
  return html`<header class="dhun-bar${hidden ? " dhun-bar--hidden" : ""}">
    <div class="dhun-bar__left">
      ${back
        ? html`<button type="button" class="dhun-icon-button dhun-bar__back" data-action="back" aria-label="Go back">${icon("ArrowBack", { size: 22 })}</button>`
        : html`<span class="dhun-bar__mark" aria-hidden="true">${icon("GraphicEq", { size: 20 })}</span>`}
      <span class="dhun-bar__titles">
        ${overline ? html`<span class="dhun-bar__overline">${overline}</span>` : html``}
        <span class="dhun-bar__title" id="dhun-bar-title">${title}</span>
      </span>
    </div>
    <div class="dhun-bar__actions">${actions}</div>
  </header>`;
}

/** The phone's bottom navigation. Three destinations, exactly the app's three. */
export function bottomNav({ active }) {
  return html`<nav class="dhun-tabs-nav" aria-label="Primary">
    ${NAV_TABS.map((tab) =>
      html`<button type="button" class="dhun-nav-item${active === tab.id ? " is-active" : ""}" data-action="nav" data-tab="${tab.id}"
        aria-current="${active === tab.id ? "page" : "false"}">
        <span class="dhun-nav-item__icon">${icon(tab.icon, { size: 24 })}</span>
        <span class="dhun-nav-item__label">${tab.label}</span>
      </button>`,
    )}
  </nav>`;
}

/** The wide window's rail. Same destinations, same state, different geometry. */
export function navRail({ active }) {
  return html`<nav class="dhun-rail-nav" aria-label="Primary">
    <span class="dhun-rail-nav__brand" aria-hidden="true">${icon("GraphicEq", { size: 22 })}<span>DHUN</span></span>
    ${NAV_TABS.map((tab) =>
      html`<button type="button" class="dhun-rail-nav__item${active === tab.id ? " is-active" : ""}" data-action="nav" data-tab="${tab.id}"
        aria-current="${active === tab.id ? "page" : "false"}">
        ${icon(tab.icon, { size: 22 })}<span class="dhun-rail-nav__label">${tab.label}</span>
      </button>`,
    )}
    <a class="dhun-rail-nav__item" href="#/settings">${icon("Settings", { size: 22 })}<span class="dhun-rail-nav__label">Settings</span></a>
  </nav>`;
}

/**
 * The mini player.
 *
 * It is one row: cover, title/artist, and three controls. Pressing the row opens
 * the full player (a `button`, so it is reachable and announced); play/pause and
 * next are siblings. The 3-pixel progress hairline is the same information the
 * full player's scrubber carries, at the size the dock has room for.
 */
export function miniPlayer(vm) {
  if (!vm.track) return html``;
  return html`<section class="dhun-mini" aria-label="Now playing">
    ${progressBar({ value: vm.percent, className: "dhun-mini__progress" })}
    <button type="button" class="dhun-mini__main" data-action="open-player" aria-label="Open the full player for ${vm.track.title}">
      <span class="dhun-mini__art">
        ${artwork(vm.track, { size: "mini" })}
        ${vm.status === "playing" ? html`<span class="dhun-mini__bars" aria-hidden="true"><i></i><i></i><i></i></span>` : html``}
      </span>
      <span class="dhun-mini__text">
        <span class="dhun-mini__title">${vm.track.title}</span>
        <span class="dhun-mini__sub">${vm.track.artist}</span>
      </span>
    </button>
    <span class="dhun-mini__controls">
      <button type="button" class="dhun-icon-button" data-action="toggle-play" aria-label="${vm.status === "playing" ? "Pause" : "Play"}">
        ${icon(vm.status === "playing" ? "Pause" : "Play", { size: 22 })}
      </button>
      <button type="button" class="dhun-icon-button" data-action="next-track" aria-label="Next track">${icon("SkipNext", { size: 22 })}</button>
    </span>
  </section>`;
}

/** The inline status line an error needs when the dock is showing. */
export function miniError({ title, technical = "" }) {
  return html`<div class="dhun-mini-error" role="alert">
    <span class="dhun-mini-error__title">${title}</span>
    ${technical ? html`<span class="dhun-mini-error__detail">${technical}</span>` : html``}
  </div>`;
}
