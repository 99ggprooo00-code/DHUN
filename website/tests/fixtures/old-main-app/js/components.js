/**
 * The pieces every screen is built from.
 *
 * This file is the app's component library: artwork, cards, rows, chips,
 * sheets, and the three states a screen can be in. It holds no data fetching and
 * no routing — a component receives values and returns markup, which is what
 * makes the screens readable and the tests cheap.
 *
 * THE LOOK. DHUN's own vocabulary, not a generic dark theme: translucent glass
 * over the page, artwork blurred behind a hero, one accent ramp per theme, and
 * the type scale mirrored from `DhunAppearance.kt`. Each component's comment
 * says what it is standing in for in the application, so the web build can be
 * checked against the app's screens rather than against taste.
 */

import { html, raw } from "./dom.js";
import { iconSvg } from "./icons.js";
import { formatCount, formatDuration, metaLine, pluralise, trackSubtitle } from "./format.js";

export function icon(name, options) {
  // `iconSvg` returns a string; it is markup this module generates, so it is
  // marked rather than escaped. Without this the whole icon set rendered as the
  // literal text of its own `<svg>` tag.
  return html`${raw(iconSvg(name, options))}`;
}

/* --------------------------------------------------------------- artwork -- */

/**
 * A cover, with a drawn fallback.
 *
 * The fallback is not a grey box: covers are the identity of a music app, and a
 * missing one still has to say which record it is. So the tile takes the accent
 * ramp, a soft gradient, and the record's initial. `loading="lazy"` keeps a
 * 40-row list from opening 40 requests at once, and `decoding="async"` keeps
 * the main thread free while they arrive.
 */
export function artwork(item, { size = "card", className = "", label = "" } = {}) {
  const source = item?.image ?? item?.imageSmall ?? "";
  const title = item?.title ?? item?.name ?? "";
  const initial = (title.trim()[0] ?? "♪").toUpperCase();
  const classes = `dhun-art dhun-art--${size}${className ? ` ${className}` : ""}`;
  if (!source) {
    return html`<span class="${classes} dhun-art--blank" aria-hidden="true"><span class="dhun-art__initial">${initial}</span></span>`;
  }
  return html`<img class="${classes}" src="${source}" alt="${label || `${title} cover`}" loading="lazy" decoding="async" />`;
}

/* ----------------------------------------------------------------- cards -- */

/**
 * A card for an album, artist or playlist.
 *
 * It is one `<a>`: the whole tile is the target, the way the app's cards behave,
 * and a keyboard user gets one stop per card rather than three. The hover play
 * badge is decorative — pressing the card opens the collection, and the
 * collection's own Play button starts it, so there is no invisible second action
 * competing for the same press.
 */
export function card(item, { href, kind = "album" } = {}) {
  const title = item?.title ?? item?.name ?? "Untitled";
  const sub = kind === "artist" ? "Artist" : kind === "playlist" ? "Playlist" : item?.artist || item?.year || "Album";
  return html`<a class="dhun-card" href="${href}" data-kind="${kind}">
    <span class="dhun-card__art">
      ${artwork(item, { size: "card", className: "dhun-card__image" })}
      <span class="dhun-card__play" aria-hidden="true">${icon("Play", { size: 20 })}</span>
    </span>
    <span class="dhun-card__text">
      <span class="dhun-card__title">${title}</span>
      <span class="dhun-card__sub">${sub}</span>
    </span>
  </a>`;
}

/** A horizontally scrolling shelf. The heading is the only thing that varies. */
export function rail({ title, items, hrefFor, kind, more = "", emptyText = "" }) {
  if (!items.length) return emptyText ? html`<section class="dhun-rail"><h2 class="dhun-rail__title">${title}</h2><p class="dhun-rail__empty">${emptyText}</p></section>` : html``;
  return html`<section class="dhun-rail" aria-labelledby="rail-${slug(title)}">
    <div class="dhun-rail__head">
      <h2 class="dhun-rail__title" id="rail-${slug(title)}">${title}</h2>
      ${more ? html`<a class="dhun-rail__more" href="${more}">Show all</a>` : html``}
    </div>
    <div class="dhun-rail__track" role="list">
      ${items.map((item) => html`<div class="dhun-rail__item" role="listitem">${card(item, { href: hrefFor(item), kind })}</div>`)}
    </div>
  </section>`;
}

function slug(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

/* ------------------------------------------------------------------ rows -- */

/**
 * A track row.
 *
 * The row is a `<button>` (the app's rows are one press target) with two
 * separate icon buttons beside it: like and "more". Those are siblings, not
 * children, because a button inside a button is invalid markup and a keyboard
 * trap — the previous build's rows had exactly that problem.
 *
 * `data-*` carries the contract: `data-action="play-row"`, `data-id`,
 * `data-index`. The delegated handler reads those and never guesses from text.
 */
export function songRow(song, { index = 0, current = false, playing = false, liked = false, context = "list" } = {}) {
  return html`<li class="dhun-row${current ? " dhun-row--current" : ""}" data-id="${song.id}">
    <button type="button" class="dhun-row__main" data-action="play-row" data-id="${song.id}" data-index="${index}" data-context="${context}"
      aria-label="${playing ? "Pause" : "Play"} ${song.title} by ${song.artist || "unknown artist"}">
      <span class="dhun-row__art">
        ${artwork(song, { size: "row" })}
        <span class="dhun-row__veil" aria-hidden="true">
          <span class="dhun-row__icon">${icon(playing ? "Pause" : "Play", { size: 18 })}</span>
        </span>
        ${current && playing ? html`<span class="dhun-row__bars" aria-hidden="true"><i></i><i></i><i></i></span>` : html``}
      </span>
      <span class="dhun-row__text">
        <span class="dhun-row__title">${song.title}</span>
        <span class="dhun-row__sub">${trackSubtitle(song) || metaLine([song.language, song.year])}</span>
      </span>
      <span class="dhun-row__duration">${formatDuration(song.duration)}</span>
    </button>
    <span class="dhun-row__actions">
      <button type="button" class="dhun-icon-button dhun-row__like${liked ? " is-liked" : ""}" data-action="toggle-like" data-id="${song.id}"
        aria-pressed="${liked ? "true" : "false"}" aria-label="${liked ? "Remove from Liked songs" : "Save to Liked songs"}">
        ${icon(liked ? "Favorite" : "FavoriteBorder", { size: 20 })}
      </button>
      <button type="button" class="dhun-icon-button" data-action="row-menu" data-id="${song.id}" data-index="${index}" aria-label="More options for ${song.title}">
        ${icon("MoreVert", { size: 20 })}
      </button>
    </span>
  </li>`;
}

/** An ordered list of rows with one heading and, optionally, a meta line. */
export function songList(songs, { currentId = "", playing = false, likes = [], context = "list", empty = "Nothing here yet." } = {}) {
  if (!songs.length) return html`<p class="dhun-list__empty">${empty}</p>`;
  return html`<ol class="dhun-list" role="list">
    ${songs.map((song, index) =>
      songRow(song, {
        index,
        current: song.id === currentId,
        playing: song.id === currentId && playing,
        liked: likes.includes(song.id),
        context,
      }),
    )}
  </ol>`;
}

/* ----------------------------------------------------------------- chips -- */

/**
 * A filter chip. `aria-pressed` rather than a radio group: the chips filter one
 * list in place, they do not choose a page.
 */
export function chip(label, { active = false, action, value = "", count = null } = {}) {
  return html`<button type="button" class="dhun-chip${active ? " is-active" : ""}" data-action="${action}" data-value="${value}"
    aria-pressed="${active ? "true" : "false"}">${label}${count === null ? html`` : html` <span class="dhun-chip__count">${formatCount(count)}</span>`}</button>`;
}

/* ---------------------------------------------------------------- states -- */

/**
 * A loading state that says what is loading.
 *
 * `aria-busy` on the region is the app's own signal; the visible label is there
 * because a screen reader user should not have to infer "still working" from
 * silence, and a sighted user should not see a layout that looks broken.
 */
export function loadingState(labels = ["Loading"]) {
  return html`<div class="dhun-state dhun-state--loading" aria-busy="true">
    <span class="dhun-spinner" aria-hidden="true"></span>
    <p class="dhun-state__title">${labels[0] ?? "Loading"}</p>
    ${labels.length > 1 ? html`<p class="dhun-state__body">${labels.slice(1).join(" ")}</p>` : html``}
    <div class="dhun-skeleton-list" aria-hidden="true">
      ${Array.from({ length: 4 }, () => html`<div class="dhun-skeleton-row"><span class="dhun-skeleton dhun-skeleton--art"></span><span class="dhun-skeleton dhun-skeleton--text"></span></div>`)}
    </div>
  </div>`;
}

export function emptyState({ title, body = "", iconName = "Music", action = null }) {
  return html`<div class="dhun-state">
    <span class="dhun-state__icon" aria-hidden="true">${icon(iconName, { size: 28 })}</span>
    <p class="dhun-state__title">${title}</p>
    ${body ? html`<p class="dhun-state__body">${body}</p>` : html``}
    ${action ? html`<button type="button" class="dhun-button dhun-button--tonal" data-action="${action.id}">${action.label}</button>` : html``}
  </div>`;
}

/**
 * An error, with its evidence.
 *
 * `technical` is the line a person can copy: the URL, the HTTP status, the
 * element's own error text. The interface never invents a cause, and never
 * hides one behind "Something went wrong".
 */
/**
 * The action row of a state.
 *
 * `actions` is normally a list of `{id, label}` buttons. A state may also pass
 * ready-made markup, which is how the framing refusal offers a real link
 * (`target="_top"`) instead of a button that has to fake one. Accepting both
 * here is what keeps `errorState` from crashing on the second shape.
 */
function renderActions(actions) {
  if (!actions) return html``;
  if (!Array.isArray(actions)) return html`<div class="dhun-state__actions">${actions}</div>`;
  if (!actions.length) return html``;
  return html`<div class="dhun-state__actions">${actions.map((action) => html`<button type="button" class="dhun-button dhun-button--tonal" data-action="${action.id}">${action.label}</button>`)}</div>`;
}

export function errorState({ title, body = "", technical = "", actions = [], heading = false }) {
  return html`<div class="dhun-state dhun-state--error" role="alert">
    <span class="dhun-state__icon" aria-hidden="true">${icon("Error", { size: 28 })}</span>
    ${heading ? html`<h1 class="dhun-state__title">${title}</h1>` : html`<p class="dhun-state__title">${title}</p>`}
    ${body ? html`<p class="dhun-state__body">${body}</p>` : html``}
    ${technical ? html`<p class="dhun-state__detail"><span class="dhun-state__detail-label">Technical detail</span> ${technical}</p>` : html``}
    ${renderActions(actions)}
  </div>`;
}

/* ---------------------------------------------------------------- sheets -- */

/**
 * A bottom sheet, which is the app's dialog.
 *
 * `role="dialog" aria-modal="true"` is a promise the shell keeps: focus moves
 * into the sheet when it opens, Tab and Shift+Tab wrap inside it, Escape closes
 * it, and closing returns focus to the control that opened it. The sheet itself
 * only declares the contract.
 */
export function sheet({ title, subtitle = "", body, footer = "" }) {
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title">
    <div class="dhun-sheet__grab" aria-hidden="true"></div>
    <!--
      A div, not a <header>: a <header> that is not inside sectioning content is
      a banner landmark, so the sheet's title bar announced a second banner
      beside the app bar (axe: landmark-no-duplicate-banner, landmark-unique).
      Nothing about the sheet is a page banner; the app bar is the only one.
    -->
    <div class="dhun-sheet__head">
      <span class="dhun-sheet__titles">
        <span class="dhun-sheet__title" id="sheet-title">${title}</span>
        ${subtitle ? html`<span class="dhun-sheet__sub">${subtitle}</span>` : html``}
      </span>
      <button type="button" class="dhun-icon-button" data-action="close-sheet" aria-label="Close">${icon("Close", { size: 20 })}</button>
    </div>
    <div class="dhun-sheet__body">${body}</div>
    ${footer ? html`<footer class="dhun-sheet__footer">${footer}</footer>` : html``}
  </div>`;
}

export function sheetMenuRow({ action, label, iconName = "", id = "", value = "", tone = "" }) {
  return html`<button type="button" class="dhun-menu-row${tone ? ` dhun-menu-row--${tone}` : ""}" data-action="${action}"
    ${id ? html`data-id="${id}"` : html``}${value ? html` data-value="${value}"` : html``}>
    ${iconName ? icon(iconName, { size: 20 }) : html``}<span>${label}</span>
  </button>`;
}

/**
 * The layer a sheet sits on.
 *
 * A sheet is not just the panel: it is the panel over a scrim that catches the
 * press which closes it. Wrapping is a decision the renderer must make
 * *explicitly* — a rendered fragment is a `String` subclass and therefore
 * always truthy, so `content ? wrap(content) : nothing` would wrap an empty
 * fragment. That bug shipped: with no sheet open the app still drew a
 * full-viewport scrim over itself, and nothing on any screen could be pressed.
 * `content` is `null` when there is nothing to show, and only then does this
 * return nothing.
 */
export function sheetLayer(content = null) {
  // "Empty" has to mean *rendered* empty as well: an empty fragment is truthy,
  // and that is precisely how the app shipped a full-viewport scrim with no
  // sheet under it.
  if (!content || String(content).trim() === "") return html``;
  return html`<div class="dhun-scrim" data-action="scrim-close">${content}</div>`;
}

export function toast({ message, tone = "info" }) {
  return html`<div class="dhun-toast dhun-toast--${tone}" role="status">${message}</div>`;
}

/* --------------------------------------------------------------- banners -- */

/**
 * The source badge. Which catalogue produced what is on screen is a fact the
 * interface states, not one it leaves the visitor to infer: sample rows and live
 * rows look identical otherwise.
 */
export function sourceBadge({ label, tone = "live", detail = "" }) {
  // `data-kind` is the machine-readable half of the badge: the colour says
  // "live or sample" to a person, and this attribute says it to anything that
  // checks the app's honesty without guessing from a label's wording.
  return html`<p class="dhun-source-badge dhun-source-badge--${tone}" data-kind="${tone}"${detail ? html` title="${detail}"` : html``}>
    <span class="dhun-source-badge__dot" aria-hidden="true"></span>${label}
  </p>`;
}

export function notice({ tone = "info", kind = "", title, body = "", action = null }) {
  return html`<div class="dhun-notice dhun-notice--${tone}"${kind ? html` data-notice="${kind}"` : html``}>
    <span class="dhun-notice__icon" aria-hidden="true">${icon(tone === "warn" ? "Offline" : "Link", { size: 18 })}</span>
    <span class="dhun-notice__text"><strong>${title}</strong>${body ? html` ${body}` : html``}</span>
    ${action
      ? html`<button type="button" class="dhun-button dhun-button--outlined dhun-notice__action" data-action="${action.id}">${action.label}</button>`
      : html``}
  </div>`;
}

/**
 * A thin progress line.
 *
 * Drawn as a `progress` element so the value is in the accessibility tree
 * ("37 percent"), rather than a div whose width is the only signal. The seek
 * controls in the full player use a range input, which is a different job.
 */
export function progressBar({ value, className = "", label = "Playback progress" }) {
  return html`<progress class="dhun-progress ${className}" max="100" value="${Math.round(value)}" aria-label="${label}">${Math.round(value)}%</progress>`;
}

/** A screen's heading block: overline, title, meta, and whatever actions belong. */
export function pageHeader({ overline = "", title, meta = "", actions = "" }) {
  return html`<header class="dhun-page-head">
    ${overline ? html`<p class="dhun-page-head__overline">${overline}</p>` : html``}
    <h1 class="dhun-page-head__title">${title}</h1>
    ${meta ? html`<p class="dhun-page-head__meta">${meta}</p>` : html``}
    ${actions ? html`<div class="dhun-page-head__actions">${actions}</div>` : html``}
  </header>`;
}

/** "5 songs · 18 min", plus the language and year when they are known. */
export function collectionMeta(collection) {
  return metaLine([
    collection?.songCount ? pluralise(collection.songCount, "song") : "",
    collection?.duration ? `${Math.max(1, Math.round(collection.duration / 60))} min` : "",
    collection?.language,
    collection?.year,
  ]);
}
