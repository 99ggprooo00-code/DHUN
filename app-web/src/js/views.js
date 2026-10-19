/**
 * Every screen, as a pure function of its view model.
 *
 * The screens are strings, not a component tree, and the reason is the same one
 * that keeps this module dependency-free: a view that is a function of data can
 * be rendered in Node and asserted by a test, which is how the escaping rules,
 * the empty states and the honesty notices are proven without a browser.
 *
 * What changed from the mirror build, and why it matters:
 *
 *   - **Downloads are gone.** A browser cannot keep a signed stream URL working
 *     offline, and a PWA cannot silently store hundreds of megabytes of audio for
 *     a service the visitor may not be signed into. The old build showed a
 *     Downloads tab that explained it could not download, which is worse than
 *     not offering it. Library is now Playlists / Liked / History.
 *   - **Empty states name the next action.** "No playlists yet" is a dead end;
 *     "No playlists yet — make one from any track's ⋮ menu" is a route.
 *   - **Every live failure is visible with its own sentence.** A row that could
 *     not resolve keeps its place in the queue and says why.
 */

import { html, raw } from "./dom.js";
import { iconSvg } from "./icons.js";
import { parseMarkdown } from "./markdown.js";
import { artworkInfo, failureDetail, failureSummary } from "./source.js";
import { formatDuration, pluralise } from "./format.js";
import { shortcutGroups, shortcutKeys } from "./shortcuts.js";
import { WEB_VERSION, BUILD_LABEL } from "./version.js";

/** Every icon gets the `dhun-icon` class, plus whatever the call site needs.
 *  Without it the stylesheet's `.dhun-icon` rules (block layout, no shrink,
 *  `fill: currentColor`) applied to nothing — the SVG carried a size and no
 *  identity, which `tests/shell.test.mjs` catches. */
export function icon(name, options = {}) {
  const { className = "", ...rest } = options;
  return raw(iconSvg(name, { ...rest, className: className ? `dhun-icon ${className}` : "dhun-icon" }));
}

/* ------------------------------------------------------------- primitives -- */

export function sectionHeader(title, { hint = "", action = null } = {}) {
  return html`<div class="dhun-section__head">
    <h2 class="dhun-section__header">${title}${
      action
        ? html`<button type="button" class="dhun-section__action" data-action="${action.id}">${action.label}</button>`
        : html``
    }</h2>
    ${hint ? html`<p class="dhun-section__hint">${hint}</p>` : html``}
  </div>`;
}

/** Artwork: a real image when the source gave one, a deterministic gradient
 *  otherwise. Both are labelled so a screenshot never misleads. */
export function artwork(entry, { size = "thumb", className = "" } = {}) {
  const info = artworkInfo(entry);
  const classNames = `dhun-art dhun-art--${size}${className ? ` ${className}` : ""}`;
  if (info.url) {
    return html`<img class="${classNames}" src="${info.url}" alt="" width="256" height="256" loading="lazy" decoding="async" />`;
  }
  // A gradient placeholder is decorative: the row's text already names the item,
  // so it carries an empty alt and no role.
  return html`<span class="${classNames}" style="background-image:${raw(info.gradient)}" aria-hidden="true"><span class="dhun-art__mark">${icon("Music")}</span></span>`;
}

export function emptyState({ title, body = "", iconName = "Album" }) {
  return html`<div class="dhun-state">
    <span class="dhun-state__icon">${icon(iconName, { size: 32 })}</span>
    <p class="dhun-state__title">${title}</p>
    ${body ? html`<p class="dhun-state__body">${body}</p>` : html``}
  </div>`;
}

/**
 * A failure, with the reason and a way forward.
 *
 * `body` is the sentence a person reads: what happened, and what to do about it.
 * It comes from `failureHelp` in `source.js`, never from a thrown error — no
 * screen of this app prints "Failed to fetch" as its message.
 *
 * `technical` is the browser's or the server's own words, when there are any.
 * It is kept because a bug report is much easier to write when the app shows the
 * string, and it is *labelled* and set on the quietest rung of the type scale, so
 * it reads as evidence rather than as the message. A person who does not want it
 * can ignore it; a person reporting a playback failure can copy it.
 */
export function errorState({ title = "That did not work", body = "", technical = "", actions = [] } = {}) {
  return html`<div class="dhun-state dhun-state--error" role="alert">
    <span class="dhun-state__icon">${icon("Error", { size: 32 })}</span>
    <p class="dhun-state__title">${title}</p>
    ${body ? html`<p class="dhun-state__body">${body}</p>` : html``}
    ${technical
      ? html`<p class="dhun-state__detail"><span class="dhun-state__detail-label">Technical detail</span> ${technical}</p>`
      : html``}
    ${actions.length
      ? html`<div class="dhun-state__actions">${actions.map(
          (action) =>
            html`<button type="button" class="dhun-button dhun-button--tonal" data-action="${action.id}">${action.label}</button>`,
        )}</div>`
      : html``}
  </div>`;
}

export function loadingState(labels = ["Loading"]) {
  return html`<div class="dhun-state dhun-state--loading" aria-live="polite">
    ${labels.map(
      (label) =>
        html`<span class="dhun-shimmer" aria-hidden="true"></span><span class="dhun-visually-hidden">${label}</span>`,
    )}
  </div>`;
}

/**
 * Notice strip: source state, demo data, offline, gestures, errors.
 *
 * `hook` gives a strip a stable name (`data-notice="demo"`) so the browser job
 * can ask for "the notice that says the demo catalogue is answering" instead of
 * matching a sentence. A reworded sentence should stay a reworded sentence and
 * not turn a check into a search for prose that no longer exists — which is
 * exactly what happened to this module's consumers until 2026-10-16.
 */
export function notice({ tone = "info", title = "", body = "", technical = "", actions = [], hook = "" } = {}) {
  return html`<div class="dhun-notice dhun-notice--${tone}"${hook ? raw(` data-notice="${hook}"`) : raw("")} role="${tone === "error" ? "alert" : "status"}">
    <div class="dhun-notice__text">
      ${title ? html`<p class="dhun-notice__title">${title}</p>` : html``}
      ${body ? html`<p class="dhun-notice__body">${body}</p>` : html``}
      ${technical
        ? html`<p class="dhun-notice__detail"><span class="dhun-state__detail-label">Technical detail</span> ${technical}</p>`
        : html``}
    </div>
    ${actions.length
      ? html`<div class="dhun-notice__actions">${actions.map(
          (action) =>
            html`<button type="button" class="dhun-button dhun-button--ghost" data-action="${action.id}">${action.label}</button>`,
        )}</div>`
      : html``}
  </div>`;
}

/** Which source produced the rows on screen — always visible, never implied. */
export function sourceBadge(source) {
  const kind = source?.kind ?? "demo";
  const label =
    kind === "innertube" ? "YouTube Music" : kind === "endpoint" ? "Your endpoint" : "Demo data";
  return html`<span class="dhun-source" data-kind="${kind}" title="${source?.detail ?? ""}">
    <span class="dhun-source__dot" aria-hidden="true"></span>
    <span class="dhun-source__label">${label}</span>
    <span class="dhun-visually-hidden">— ${source?.detail ?? ""}</span>
  </span>`;
}

/**
 * What a browser shows when somebody else's page has framed DHUN.
 *
 * This is not a screen of the app: the app does not start (see `isFramed` in
 * platform.js and the guard at the top of `main.js`). A framed music client is
 * a click target that looks like it belongs to the page around it, and the
 * directive that normally forbids that — `frame-ancestors` — cannot be sent by
 * a static host, so it is enforced here instead. The block reuses the
 * scripting-off layout: same type scale, same measure, no shell, because there
 * is no shell to render.
 */
export function framedRefusal() {
  return html`<div class="dhun-noscript" data-notice="framed">
    <h1>DHUN will not run inside a frame</h1>
    <p>
      This page is embedded in another site, so the app has not started. A music player in a frame is a
      set of controls that appear to belong to whatever page surrounds them, and DHUN does not lend
      itself to that.
    </p>
    <p>
      <a class="dhun-button" href="./" target="_top" rel="noopener">Open DHUN directly</a>
    </p>
    <p>
      Anyone can run this app: the source is at
      <a href="https://github.com/99ggprooo00-code/DHUN">github.com/99ggprooo00-code/DHUN</a>.
    </p>
  </div>`;
}

/* ------------------------------------------------------------------ rows -- */

/**
 * One track row.
 *
 * `variant` is "list" (default), "queue" (with reorder controls) or "compact"
 * (search results, where the artwork is smaller).
 */
export function trackRow(track, {
  index = null,
  current = false,
  playing = false,
  failed = null,
  liked = false,
  variant = "list",
  subtitle = null,
} = {}) {
  const duration = track.durationMs ? formatDuration(track.durationMs) : "";
  const meta = subtitle ?? [track.artist, track.album].filter(Boolean).join(" • ");
  const action = variant === "queue" ? "play-queue-row" : "play-track";
  return html`<li class="dhun-row" data-index="${index ?? ""}" data-row-id="${track.id ?? track.videoId ?? ""}">
    <div class="dhun-row__main${variant === "queue" ? " dhun-row__main--queue" : ""}">
      ${variant === "queue"
        ? html`<span class="dhun-queue__index" aria-hidden="true">${(index ?? 0) + 1}</span>`
        : html``}
      <button type="button" class="dhun-track" data-action="${action}" data-index="${index ?? ""}" data-id="${track.id ?? track.videoId ?? ""}"
        ${current ? raw('aria-current="true"') : raw("")} data-playing="${playing ? "true" : "false"}">
        ${artwork(track, { size: variant === "compact" ? "thumb" : "row" })}
        <span class="dhun-track__meta">
          <span class="dhun-track__title">${track.title}</span>
          <span class="dhun-track__subtitle">${meta || "Unknown artist"}</span>
        </span>
        ${failed
          ? html`<span class="dhun-track__flag" title="${failureDetail(failed) ?? failureSummary(failed)}">${icon("Error", { size: 18 })}</span>`
          : html``}
      </button>
      ${duration ? html`<span class="dhun-track__time">${duration}</span>` : html``}
      ${variant === "queue"
        ? html`<span class="dhun-queue__controls">
            <button type="button" class="dhun-icon-button dhun-icon-button--up" data-action="queue-up" data-index="${index ?? ""}" aria-label="Move up">
              ${icon("ChevronDown", { size: 18 })}
            </button>
            <button type="button" class="dhun-icon-button" data-action="queue-down" data-index="${index ?? ""}" aria-label="Move down">
              ${icon("ChevronDown", { size: 18 })}
            </button>
            <button type="button" class="dhun-icon-button" data-action="queue-remove" data-index="${index ?? ""}" aria-label="Remove from queue">
              ${icon("Close", { size: 18 })}
            </button>
          </span>`
        : html`<button type="button" class="dhun-icon-button" data-action="track-menu" data-index="${index ?? ""}" data-id="${track.id ?? track.videoId ?? ""}"
            aria-label="More options for ${track.title}">
            ${icon("MoreVert", { size: 20 })}
          </button>`}
    </div>
    ${failed
      ? html`<p class="dhun-row__failure" role="status">
          ${failureSummary(failed)}
          ${failureDetail(failed) ? html`<span class="dhun-row__failure-detail">${failureDetail(failed)}</span>` : html``}
        </p>`
      : html``}
  </li>`;
}

export function trackList(tracks, options = {}) {
  if (!tracks?.length) return emptyState(options.empty ?? { title: "Nothing here yet" });
  return html`<ul class="dhun-tracklist">
    ${tracks.map((track, index) => {
      const rowIndex = options.indexOffset ? index + options.indexOffset : index;
      return trackRow(track, {
        ...options,
        index: rowIndex,
        // A row's own failure, when the list was given one. `failures` was
        // accepted and silently ignored before: a list showed no reason on the
        // row that had one, which is the state the queue's own renderer never
        // had because it passes `failed` row by row.
        failed: options.failures?.[rowIndex] ?? options.failed ?? null,
        current: options.currentId && (options.currentId === track.id || options.currentId === track.videoId),
      });
    })}
  </ul>`;
}

/** A card: album, playlist or artist, with the action that opens it. */
export function card(entry, { action = "open-entity" } = {}) {
  const title = entry.title ?? entry.name ?? "";
  const subtitle = entry.artist ?? entry.year ?? entry.trackCount ? `${entry.artist ?? entry.year ?? ""}` : "";
  const kind = entry.kind ?? (entry.name ? "artist" : "album");
  return html`<li class="dhun-card-item">
    <button type="button" class="dhun-card" data-action="${action}" data-kind="${kind}" data-id="${entry.id}" aria-label="Open ${title}">
      <span class="dhun-card__art">${artwork(entry, { size: "card" })}</span>
      <span class="dhun-grid__title">${title}</span>
      <span class="dhun-grid__subtitle">${subtitle}</span>
    </button>
  </li>`;
}

export function cardGrid(entries, options = {}) {
  if (!entries?.length) return emptyState(options.empty ?? { title: "Nothing to show" });
  return html`<ul class="dhun-grid dhun-grid--list">
    ${entries.map((entry) => card(entry, options))}
  </ul>`;
}

export function shelf(entries, options = {}) {
  if (!entries?.length) return html``;
  return html`<ul class="dhun-shelf dhun-shelf--cards">
    ${entries.map((entry) => card(entry, options))}
  </ul>`;
}

/** Quick picks: the app's `QuickPickItem` — a 48dp cover, title over artist, on
 *  the content-row surface — two to a column. */
function quickPick(track, { index, currentId }) {
  const current = currentId && (currentId === track.id || currentId === track.videoId);
  return html`<button type="button" class="dhun-quickrow" data-action="play-track" data-index="${index}" data-id="${track.id}"
    ${current ? raw('aria-current="true"') : raw("")}>
    <span class="dhun-quickrow__art">${artwork(track, { size: "quickrow" })}</span>
    <span class="dhun-quickrow__meta">
      <span class="dhun-quickrow__title">${track.title}</span>
      <span class="dhun-quickrow__sub">${track.artist ?? ""}</span>
    </span>
  </button>`;
}

/** Two tracks per column, as `QuickPicksGrid` chunks them (`chunked(2)`). */
function quickPickShelf(tracks, { indexFor, currentId }) {
  const columns = [];
  for (let i = 0; i < tracks.length; i += 2) columns.push(tracks.slice(i, i + 2));
  return html`<ul class="dhun-shelf dhun-shelf--quick">
    ${columns.map(
      (column, columnIndex) => html`<li class="dhun-quickcol-item">
        <div class="dhun-quickcol">
          ${column.map((track, rowIndex) =>
            quickPick(track, { index: indexFor(columnIndex * 2 + rowIndex), currentId }),
          )}
        </div>
      </li>`,
    )}
  </ul>`;
}

/* --------------------------------------------------------------- screens -- */

function shelvesToRows(shelves = [], { currentId, demoTracks }) {
  return shelves
    .map((shelfEntry) => {
      const tracks = shelfEntry.entries.filter((entry) => !entry.kind || entry.kind === "track");
      const cards = shelfEntry.entries.filter((entry) => entry.kind && entry.kind !== "track");
      if (tracks.length === 0 && cards.length === 0) return html``;
      const offset = demoTracks.findIndex((track) => track.id === tracks[0]?.id);
      return html`<section class="dhun-section">
        ${sectionHeader(shelfEntry.title)}
        ${tracks.length
          ? shelfEntry.title.toLowerCase().includes("quick")
            ? quickPickShelf(tracks, {
                indexFor: (position) => (offset >= 0 ? offset + position : position),
                currentId,
              })
            : trackList(tracks, { currentId, view: "home", indexOffset: offset >= 0 ? offset : 0 })
          : html``}
        ${cards.length ? shelf(cards) : html``}
      </section>`;
    })
    .reduce((out, piece) => html`${out}${piece}`, html``);
}

export function homeScreen({
  shelves = [],
  loading = false,
  error = null,
  currentId,
  demoTracks = [],
  source,
  greeting = "",
  moods = [],
  mood = "for-you",
  moodLabel = "",
  moodQuery = null,
}) {
  // The greeting and the mood rail are drawn above the state, as the app draws
  // them: a feed that failed still says hello, and still offers the moods,
  // which are their own requests against the source.
  const header = homeHeader({ greeting, moods, mood });
  /*
   * The demo strip belongs to every state, not just a full one. It used to be
   * appended after the shelves, so an *empty* screen — the state a mood lands in
   * when the source has no songs for it — silently dropped the one sentence that
   * says where the answer came from. Whether the source is the demo is a fact
   * about the request, and a request that returned nothing is still a request.
   */
  const demoStrip = source?.kind === "demo"
    ? notice({
        tone: "warn",
        title: "Demo catalogue",
        body: "These fictional rows have no audio. DHUN reached no real source — Settings explains the data-source options.",
        actions: [{ id: "open-settings-source", label: "Data source" }],
        hook: "demo",
      })
    : html``;

  if (loading) {
    return html`${header}${loadingState([moodQuery ? `Loading ${moodLabel}` : "Loading your home feed"])}${demoStrip}`;
  }
  if (error) {
    return html`${header}${errorState({
      title: moodQuery ? `${moodLabel} could not be loaded` : "The home feed could not be loaded",
      body: error.help,
      technical: error.technical,
      actions: [
        { id: "retry-home", label: "Try again" },
        { id: "open-settings-source", label: "Change data source" },
      ],
    })}${demoStrip}`;
  }
  if (!shelves.length) {
    // A mood asks the source one question, so an empty answer is that question
    // having no songs — not an empty app. "Search for something, or point DHUN
    // at your own data source" is the *feed's* empty copy, and it was telling a
    // visitor who had just pressed Chill to go and search.
    return html`${header}${emptyState({
      title: moodQuery ? `No ${moodLabel} songs` : "Nothing to show yet",
      body: moodQuery
        ? `The source answered with no songs for “${moodQuery}”. Another mood, or the search screen, is the way on.`
        : "Search for something, or point DHUN at your own data source in Settings.",
      iconName: "Search",
    })}${demoStrip}`;
  }
  return html`${header}${shelvesToRows(shelves, { currentId, demoTracks })}${demoStrip}`;
}

const SEARCH_FILTERS = Object.freeze([
  { id: "all", label: "All" },
  { id: "tracks", label: "Songs" },
  { id: "artists", label: "Artists" },
  { id: "albums", label: "Albums" },
  { id: "playlists", label: "Playlists" },
]);

/**
 * The Home header: DHUN's wordmark and the time-of-day greeting, then the mood
 * rail.
 *
 * Both are the app's, mirrored rather than invented. `HomeScreen.kt` opens with
 * `Text("DHUN", brand)` and a `headlineMedium` greeting above the mood
 * `DhunHorizontalRail`, and the greeting itself is
 * `GetHomeFeedUseCase.greetingForCurrentTime` — "Good morning" for 5..11,
 * "Good afternoon" for 12..16, "Good evening" for 17..21, "Good night"
 * otherwise, on the *local* hour.
 *
 * The moods are `HomeMood.kt`'s five entries. Four of them are a search against
 * the source (`FOCUS("Focus", "focus instrumental music")` …), so a mood is a
 * real request and a real answer — not a client-side sort of rows that happen to
 * be loaded, which would have been a filter dressed up as a mood.
 *
 * The header sits outside the loading/error switch on purpose: the app draws it
 * above the state, so a feed that failed still says hello and still offers
 * another way in.
 */
function homeHeader({ greeting, moods = [], mood = "for-you" }) {
  return html`<div class="dhun-home__head">
      <p class="dhun-home__brand">DHUN</p>
      <h1 class="dhun-home__greeting">${greeting}</h1>
    </div>
    ${moods.length
      ? html`<div class="dhun-chip-row" role="tablist" aria-label="Home mood">
          ${moods.map(
            (entry) => html`<button type="button" role="tab" class="dhun-chip" data-action="set-mood" data-mood="${entry.id}"
              aria-selected="${mood === entry.id ? "true" : "false"}">${entry.label}</button>`,
          )}
        </div>`
      : html``}`;
}

export function searchScreen({ query = "", results = null, status = "idle", filter = "all", recentSearches = [], currentId, source }) {
  const chips = html`<div class="dhun-chip-row" role="tablist" aria-label="Result type">
    ${SEARCH_FILTERS.map(
      (entry) => html`<button type="button" role="tab" class="dhun-chip" data-action="search-filter" data-filter="${entry.id}"
        aria-selected="${filter === entry.id ? "true" : "false"}">${entry.label}</button>`,
    )}
  </div>`;

  const body = (() => {
    if (status === "loading") return loadingState(["Searching"]);
    if (status === "error") {
      return errorState({
        title: "That search did not go through",
        body: results?.help ?? "The request failed.",
        technical: results?.technical ?? "",
        actions: [
          { id: "retry-search", label: "Try again" },
          { id: "open-settings-source", label: "Change data source" },
        ],
      });
    }
    if (!results) {
      return recentSearches.length
        ? html`<section class="dhun-section">
            ${sectionHeader("Recent searches", {
              action: { id: "clear-searches", label: "Clear" },
            })}
            <ul class="dhun-chip-row dhun-chip-row--wrap">
              ${recentSearches.map(
                (entry) => html`<li class="dhun-recent">
                  <button type="button" class="dhun-chip" data-action="search-again" data-query="${entry}">
                    ${icon("Search", { size: 16 })}<span>${entry}</span>
                  </button>
                  <button type="button" class="dhun-icon-button dhun-icon-button--small" data-action="forget-search" data-query="${entry}"
                    aria-label="Remove ${entry} from recent searches">${icon("Close", { size: 16 })}</button>
                </li>`,
              )}
            </ul>
          </section>`
        : emptyState({
            title: "Search YouTube Music",
            body: "Songs, albums, artists and playlists. Everything you type stays on this device.",
            iconName: "Search",
          });
    }

    const groups = [
      { id: "tracks", label: "Songs", items: results.tracks ?? [], type: "tracks" },
      { id: "artists", label: "Artists", items: results.artists ?? [], type: "entities" },
      { id: "albums", label: "Albums", items: results.albums ?? [], type: "entities" },
      { id: "playlists", label: "Playlists", items: results.playlists ?? [], type: "entities" },
    ].filter((group) => filter === "all" || filter === group.id);

    const total = groups.reduce((sum, group) => sum + group.items.length, 0);
    if (total === 0) {
      return emptyState({
        title: `No results for “${query}”`,
        body: "Check the spelling, or try fewer words.",
        iconName: "Search",
      });
    }

    const offset = 0;
    return html`${groups.map((group) =>
      group.items.length === 0
        ? html``
        : html`<section class="dhun-section">
            ${sectionHeader(`${group.label} (${group.items.length})`)}
            ${group.type === "tracks"
              ? trackList(group.items, { currentId, indexOffset: offset, view: "search" })
              : cardGrid(group.items, { empty: { title: `No ${group.label.toLowerCase()}` } })}
          </section>`,
    )}`;
  })();

  return html`<div class="dhun-searchbar">
      <form class="dhun-search" data-action="search-submit" role="search">
        <span class="dhun-search__icon">${icon("Search", { size: 20 })}</span>
        <input id="search-input" type="search" name="q" value="${query}" placeholder="Songs, artists, albums…"
          autocomplete="off" spellcheck="false" aria-label="Search" />
        ${query ? html`<button type="button" class="dhun-icon-button dhun-icon-button--small" data-action="clear-search" aria-label="Clear the search">${icon("Close", { size: 18 })}</button>` : html``}
      </form>
    </div>
    ${source?.kind === "demo" && results
      ? notice({
          tone: "warn",
          title: "Demo catalogue",
          body: "These fictional rows have no audio. DHUN reached no real source — Settings explains the data-source options.",
          actions: [{ id: "open-settings-source", label: "Data source" }],
          hook: "demo",
        })
      : html``}
    ${chips}
    ${body}`;
}

/** The user's own playlists, likes and history. No downloads tab: see the
 *  module docstring. */
export function libraryScreen({ tab = "playlists", playlists = [], liked = [], history = [], currentId, libraryKind = "indexeddb" }) {
  const tabs = [
    { id: "playlists", label: "Playlists", count: playlists.length },
    { id: "liked", label: "Liked", count: liked.length },
    { id: "history", label: "History", count: history.length },
  ];

  const header = html`<div class="dhun-chip-row" role="tablist" aria-label="Library sections">
    ${tabs.map(
      (entry) => html`<button type="button" role="tab" class="dhun-chip" data-action="library-tab" data-tab="${entry.id}"
        aria-selected="${tab === entry.id ? "true" : "false"}">${entry.label}${entry.count ? html` <span class="dhun-chip__count">${entry.count}</span>` : html``}</button>`,
    )}
  </div>`;

  const pane = (() => {
    if (tab === "liked") {
      return liked.length
        ? html`<section class="dhun-section">
            ${sectionHeader("Liked songs", {
              action: { id: "play-liked", label: "Play all" },
            })}
            ${trackList(liked, { currentId })}
          </section>`
        : emptyState({
            title: "No liked songs yet",
            body: "Press L while something plays, or use a track's ⋮ menu and choose Like.",
            iconName: "Favorite",
          });
    }
    if (tab === "history") {
      return history.length
        ? html`<section class="dhun-section">
            ${sectionHeader("Recently played", {
              hint: "Kept on this device only.",
              action: { id: "clear-history", label: "Clear" },
            })}
            ${trackList(history, { currentId, empty: { title: "Nothing played yet" } })}
          </section>`
        : emptyState({
            title: "No history yet",
            body: "Tracks you play appear here, on this device, and nowhere else.",
            iconName: "History",
          });
    }
    return html`<section class="dhun-section">
      ${sectionHeader("Your playlists", {
        action: { id: "new-playlist", label: "New playlist" },
      })}
      ${playlists.length
        ? html`<ul class="dhun-grid dhun-grid--list">
            ${playlists.map(
              (playlist) => html`<li class="dhun-card-item">
                <button type="button" class="dhun-card dhun-card--row" data-action="open-playlist" data-id="${playlist.id}">
                  <span class="dhun-card__art">${artwork({ id: playlist.id, title: playlist.name }, { size: "card" })}</span>
                  <span class="dhun-card__meta">
                    <span class="dhun-grid__title">${playlist.name}</span>
                    <span class="dhun-grid__subtitle">${pluralise(playlist.trackIds.length, "track")}</span>
                  </span>
                </button>
              </li>`,
            )}
          </ul>`
        : emptyState({
            title: "No playlists yet",
            body: "Use a track's ⋮ menu to add it to a new playlist.",
            iconName: "LibraryMusic",
          })}
    </section>`;
  })();

  return html`${header}${pane}
    ${libraryKind === "memory"
      ? notice({
          tone: "warn",
          title: "This browser is not saving your library",
          body: "IndexedDB is unavailable here (private window, or storage blocked for this site), so playlists, likes and history last only until the tab closes.",
          actions: [{ id: "open-settings-data", label: "Details" }],
        })
      : html``}`;
}

export function playlistScreen({ playlist, tracks, currentId, isOwn = false, missing = 0 }) {
  if (!playlist) {
    return errorState({
      title: "That playlist could not be loaded",
      body: "It may have been removed, or the link may be stale.",
      actions: [{ id: "go-home", label: "Go home" }],
    });
  }
  // `PlaylistScreen.kt`: cover, title, then `subtitle • meta` on one quiet line
  // and the description under it at the smallest size — the description is the
  // *third* line there, not something appended to the meta line. A remote
  // playlist gets Play / Shuffle; a local one adds Rename and Delete, which is
  // the split the app's own docstring describes ("Remote: header + ordered
  // tracks + play/shuffle. Local: all of that + rename, delete, …").
  const metaLine = [isOwn ? "Local playlist" : playlist.artist, playlist.trackCountText ?? pluralise(tracks.length, "track")]
    .filter(Boolean)
    .join(" · ");
  return html`<header class="dhun-entity">
      <span class="dhun-entity__art">${artwork(playlist, { size: "hero" })}</span>
      <div class="dhun-entity__text">
        <h1 class="dhun-entity__title">${playlist.name}</h1>
        ${metaLine ? html`<p class="dhun-entity__meta">${metaLine}</p>` : html``}
        ${playlist.description ? html`<p class="dhun-entity__submeta">${playlist.description}</p>` : html``}
        <div class="dhun-entity__actions">
          <button type="button" class="dhun-button" data-action="play-entity">${icon("Play", { size: 20 })}<span>Play</span></button>
          <button type="button" class="dhun-button dhun-button--outlined" data-action="shuffle-entity">${icon("Shuffle", { size: 20 })}<span>Shuffle</span></button>
          ${isOwn
            ? html`<button type="button" class="dhun-button dhun-button--ghost" data-action="rename-playlist">Rename</button>
                <button type="button" class="dhun-button dhun-button--ghost" data-action="delete-playlist">Delete</button>`
            : html``}
        </div>
      </div>
    </header>
    ${missing > 0
      ? notice({
          tone: "info",
          body: `${pluralise(missing, "track")} in this list could not be matched to a row DHUN can show. The playlist itself is intact.`,
        })
      : html``}
    ${tracks.length
      ? trackList(tracks, { currentId, view: "playlist" })
      : emptyState({
          title: "This playlist is empty",
          body: "Add tracks from any ⋮ menu.",
          iconName: "LibraryMusic",
        })}`;
}

export function artistScreen({ artist, tracks = [], albums = [], currentId }) {
  if (!artist) {
    return errorState({
      title: "That artist could not be loaded",
      body: "The link may be stale, or the source did not answer.",
      actions: [{ id: "go-home", label: "Go home" }],
    });
  }
  // `ArtistScreen.kt`: a 16:10 full-bleed crop with the name overlaid and the
  // source's "N monthly listeners" under it when the source sent that line.
  // The app's two buttons there are Shuffle and Radio; Radio has no counterpart
  // in this build (no `/next`-seeded station), and a button that cannot do the
  // thing it names is worse than no button, so the web pairs Shuffle with Play
  // over the same top songs instead of pretending to be a radio.
  return html`<header class="dhun-entity dhun-entity--hero">
      <span class="dhun-entity__art dhun-entity__art--hero">
        ${artwork(artist, { size: "hero" })}
        <span class="dhun-entity__veil" aria-hidden="true"></span>
        <div class="dhun-entity__text">
          <h1 class="dhun-entity__title">${artist.name}</h1>
          ${artist.listeners ? html`<p class="dhun-entity__meta">${artist.listeners}</p>` : html``}
        </div>
      </span>
      <div class="dhun-entity__actions">
        <button type="button" class="dhun-button" data-action="play-entity">${icon("Play", { size: 20 })}<span>Play</span></button>
        <button type="button" class="dhun-button dhun-button--outlined" data-action="shuffle-entity">${icon("Shuffle", { size: 20 })}<span>Shuffle</span></button>
      </div>
    </header>
    ${tracks.length
      ? html`<section class="dhun-section">${sectionHeader("Top songs")}${trackList(tracks, { currentId })}</section>`
      : html``}
    ${albums.length ? html`<section class="dhun-section">${sectionHeader("Albums")}${cardGrid(albums)}</section>` : html``}
    ${artist.description
      ? html`<section class="dhun-section">${sectionHeader("About")}
          <div class="dhun-about"><p class="dhun-about__body">${artist.description}</p></div>
        </section>`
      : html``}`;
}

export function albumScreen({ album, tracks = [], currentId }) {
  if (!album) {
    return errorState({
      title: "That album could not be loaded",
      body: "The link may be stale, or the source did not answer.",
      actions: [{ id: "go-home", label: "Go home" }],
    });
  }
  // `AlbumScreen.kt` exactly: cover, title, `artistName • year`, then a quieter
  // `trackCount • duration` line — and the two are separate lines at two
  // different type sizes, which is why the web's single merged line read as a
  // different component. The count and the duration are the *source's* own text
  // when it sent them (`AlbumDetail.trackCountText` / `durationText`); the row
  // count is the fallback for a source that sent neither.
  const metaLine = [album.trackCountText ?? pluralise(tracks.length, "track"), album.durationText]
    .filter(Boolean)
    .join(" · ");
  return html`<header class="dhun-entity">
      <span class="dhun-entity__art dhun-entity__art--album">${artwork(album, { size: "hero" })}</span>
      <div class="dhun-entity__text">
        <h1 class="dhun-entity__title">${album.title}</h1>
        <p class="dhun-entity__meta">${[album.artist, album.year].filter(Boolean).join(" · ") || "Album"}</p>
        ${metaLine ? html`<p class="dhun-entity__submeta">${metaLine}</p>` : html``}
        <div class="dhun-entity__actions">
          <button type="button" class="dhun-button" data-action="play-entity">${icon("Play", { size: 20 })}<span>Play</span></button>
          <button type="button" class="dhun-button dhun-button--outlined" data-action="shuffle-entity">${icon("Shuffle", { size: 20 })}<span>Shuffle</span></button>
        </div>
      </div>
    </header>
    ${tracks.length ? trackList(tracks, { currentId }) : emptyState({ title: "No tracks were listed for this album" })}`;
}

/* -------------------------------------------------------------- settings -- */

function settingRow({ title, subtitle = "", control = "", id = null }) {
  return html`<div class="dhun-settings-row">
    <span class="dhun-settings-row__text">
      <span class="dhun-settings-row__title">${title}</span>
      ${subtitle ? html`<span class="dhun-settings-row__subtitle">${subtitle}</span>` : html``}
    </span>
    <span class="dhun-settings-row__control"${id ? html` data-control="${id}"` : raw("")}>${control}</span>
  </div>`;
}

function switchControl({ id, label, checked, disabled = false }) {
  return html`<button type="button" class="dhun-switch" role="switch" aria-checked="${checked ? "true" : "false"}"
    data-action="${id}" aria-label="${label}"${disabled ? raw(" disabled") : raw("")}><span class="dhun-switch__thumb"></span></button>`;
}

function chipChoice({ action, name, options, value }) {
  return html`<div class="dhun-chip-row dhun-chip-row--wrap" role="radiogroup" aria-label="${name}">
    ${options.map(
      (option) => html`<button type="button" class="dhun-chip" role="radio" data-action="${action}" data-value="${option.id}"
        aria-checked="${value === option.id ? "true" : "false"}"${option.disabled ? raw(" disabled") : raw("")}>${option.label}</button>`,
    )}
  </div>`;
}

function slideRow({ label, value, action, min, max, step, unit = "", disabled = false, id = null }) {
  return html`<div class="dhun-slider-row"${id ? html` data-slider="${id}"` : raw("")}>
    <span class="dhun-slider-row__label">${label}</span>
    <input type="range" min="${min}" max="${max}" step="${step}" value="${value}" data-action="${action}"
      aria-label="${label}"${disabled ? raw(" disabled") : raw("")} />
    <span class="dhun-slider-row__value">${value}${unit}</span>
  </div>`;
}

/** One equaliser band. Ten of these, at the app's own band centres. */
function bandSlider(band, gain, disabled) {
  return html`<div class="dhun-eq__band">
    <span class="dhun-eq__gain">${gain > 0 ? "+" : ""}${gain.toFixed(1)}</span>
    <input type="range" min="-20" max="20" step="0.1" value="${gain}" data-action="set-band" data-index="${band.index}"
      aria-label="${band.label} band gain"${disabled ? raw(" disabled") : raw("")} />
    <span class="dhun-eq__freq">${band.label}</span>
  </div>`;
}

export function settingsScreen({
  settings,
  platform,
  capabilities,
  sources,
  sourceState,
  equalizer,
  library,
  pwa,
  diagnostics,
}) {
  const themeOptions = [
    { id: "dark", label: "Dark" },
    { id: "light", label: "Light" },
    { id: "system", label: "System" },
  ];
  const accentOptions = ["brand", "azure", "jade", "amber", "rose", "cyan"].map((id) => ({
    id,
    label: id[0].toUpperCase() + id.slice(1),
  }));

  return html`
    <section class="dhun-section">
      ${sectionHeader("Data source", { hint: sourceState.detail })}
      ${notice({
        tone: sourceState.tone,
        title: sourceState.title,
        body: sourceState.body,
        actions: sourceState.actions,
        hook: sourceState.kind === "demo" ? "demo" : "",
      })}
      ${settingRow({
        title: "Where metadata comes from",
        subtitle: "Automatic prefers your endpoint, then YouTube Music directly, then the demo rows.",
        control: chipChoice({
          action: "set-source",
          name: "Data source",
          options: sources,
          value: settings.sourceId,
        }),
      })}
      ${settingRow({
        title: "Your endpoint address",
        subtitle: "Optional. Any HTTPS base URL that speaks the small contract below. DHUN never proxies it.",
        control: html`<span class="dhun-input-pair">
          <input type="url" inputmode="url" class="dhun-input" data-action="endpoint-url" value="${settings.endpointUrl}"
            placeholder="https://example.org/dhun" aria-label="Endpoint address" />
          <button type="button" class="dhun-button dhun-button--tonal" data-action="test-endpoint">Test</button>
        </span>`,
      })}
      <details class="dhun-details">
        <summary>Endpoint contract</summary>
        <ul class="dhun-contract">
          ${diagnostics.contract.map(
            (entry) => html`<li><code>${entry.method} ${entry.path}</code> → ${entry.returns}${
              entry.body ? html` <span class="dhun-contract__body">body ${entry.body}</span>` : html``
            }</li>`,
          )}
        </ul>
      </details>
    </section>

    <section class="dhun-section">
      ${sectionHeader("Appearance")}
      ${settingRow({ title: "Theme", subtitle: "Dark is the app's default.", control: chipChoice({ action: "set-theme", name: "Theme", options: themeOptions, value: settings.theme }) })}
      ${settingRow({
        title: "Accent",
        subtitle: "The same six ramps the Android and desktop builds offer.",
        control: chipChoice({ action: "set-accent", name: "Accent", options: accentOptions, value: settings.accent }),
      })}
    </section>

    <section class="dhun-section">
      ${sectionHeader("Playback")}
      ${settingRow({
        title: "Volume",
        control: slideRow({ label: "Volume", value: Math.round(settings.volume * 100), action: "set-volume", min: 0, max: 100, step: 1, unit: "%" }),
      })}
      ${settingRow({ title: "Resume where you left off", subtitle: "Restores the queue and position on the next visit.", control: switchControl({ id: "toggle-resume", label: "Resume where you left off", checked: settings.resumeOnLaunch }) })}
      ${settingRow({ title: "Even volume", subtitle: "A compressor that evens out loud and quiet sources. It cannot undo a bad master.", control: switchControl({ id: "toggle-compressor", label: "Even volume", checked: settings.normalizeVolume, disabled: !capabilities.audioContext }) })}
      ${settingRow({ title: "Lyrics", subtitle: "Fetch and show synced lyrics when the source provides them.", control: switchControl({ id: "toggle-lyrics", label: "Lyrics", checked: settings.lyricsEnabled }) })}
      ${settingRow({ title: "Romanise lyrics", subtitle: "Show a Latin transliteration under non-Latin scripts where the source includes one.", control: switchControl({ id: "toggle-romanize", label: "Romanise lyrics", checked: settings.romanizeLyrics, disabled: !settings.lyricsEnabled }) })}
    </section>

    <section class="dhun-section">
      ${sectionHeader("Equaliser", {
        hint: capabilities.audioContext
          ? equalizer.status
          : "This browser has no Web Audio API, so the equaliser is hidden rather than shown as a control that does nothing.",
      })}
      ${capabilities.audioContext
        ? html`
            ${settingRow({ title: "Enable", subtitle: "Applies to the bytes being played, in real time.", control: switchControl({ id: "toggle-equalizer", label: "Enable equaliser", checked: settings.equalizerEnabled }) })}
            ${settingRow({
              title: "Preset",
              control: chipChoice({
                action: "set-eq-preset",
                name: "Equaliser preset",
                options: equalizer.presets.map((preset) => ({ id: preset.id, label: preset.name })),
                value: equalizer.activePresetId,
              }),
            })}
            ${settingRow({ title: "Preamp", control: slideRow({ label: "Preamp", value: settings.equalizerPreampDb, action: "set-preamp", min: -20, max: 20, step: 0.5, unit: " dB", disabled: !settings.equalizerEnabled }) })}
            <div class="dhun-eq" role="group" aria-label="Equaliser bands">
              ${equalizer.bands.map((band) => bandSlider(band, settings.equalizerGains[band.index] ?? 0, !settings.equalizerEnabled))}
            </div>
            <div class="dhun-eq__actions">
              <button type="button" class="dhun-button dhun-button--ghost" data-action="reset-eq">Reset to flat</button>
            </div>`
        : html``}
    </section>

    <section class="dhun-section">
      ${sectionHeader("Library & storage")}
      ${settingRow({
        title: "Where your library lives",
        subtitle: library.kind === "indexeddb" ? "IndexedDB in this browser profile, on this device." : "Memory only — anything you add is gone when the tab closes.",
        control: html`<span class="dhun-stat">${pluralise(library.counts.playlists, "playlist")} · ${pluralise(library.counts.likes, "like")} · ${pluralise(library.counts.history, "play")}</span>`,
      })}
      ${settingRow({
        title: "Export library",
        subtitle: "A versioned JSON file with your playlists, likes and history. It is the only file DHUN writes.",
        control: html`<button type="button" class="dhun-button dhun-button--tonal" data-action="export-library">Download .json</button>`,
      })}
      ${settingRow({
        title: "Import library",
        subtitle: "Merges a DHUN export into this profile. Nothing leaves the device.",
        control: html`<label class="dhun-button dhun-button--tonal dhun-button--file">
          <input type="file" accept="application/json,.json" data-action="import-library" />
          <span>Choose file</span>
        </label>`,
      })}
      ${settingRow({
        title: "Clear library",
        subtitle: "Removes playlists, likes and history from this device. It cannot be undone.",
        control: html`<span class="dhun-input-pair">
          ${settings.confirmClearLibrary
            ? html`<button type="button" class="dhun-button dhun-button--danger" data-action="clear-library-confirm">Really clear</button>
                <button type="button" class="dhun-button dhun-button--ghost" data-action="clear-library-cancel">Cancel</button>`
            : html`<button type="button" class="dhun-button dhun-button--ghost" data-action="clear-library">Clear…</button>`}
        </span>`,
      })}
      ${settingRow({
        title: "Stream cache",
        subtitle: "Resolved stream URLs are reused for a few minutes so skipping back and forth does not re-request them.",
        control: html`<span class="dhun-input-pair">
          <span class="dhun-stat">${pluralise(diagnostics.streamCacheSize, "resolved URL")}</span>
          <button type="button" class="dhun-button dhun-button--ghost" data-action="clear-stream-cache">Clear</button>
        </span>`,
      })}
    </section>

    <section class="dhun-section">
      ${sectionHeader("This browser")}
      ${settingRow({ title: "Install as an app", subtitle: pwa.installed ? "Already running as an installed app." : pwa.installable ? "Adds DHUN to your home screen or applications menu." : "Your browser has not offered an install prompt. In Chromium browsers, check the address bar's install icon.", control: pwa.installable && !pwa.installed ? html`<button type="button" class="dhun-button dhun-button--tonal" data-action="install">Install</button>` : html`<span class="dhun-stat">${pwa.installed ? "Installed" : "Not available"}</span>` })}
      ${settingRow({ title: "Offline shell", subtitle: pwa.offlineReady ? "The interface is cached, so a reload without a connection still opens the app. Audio cannot be cached: stream URLs are signed and expire." : "Not cached in this browser — see the diagnostics below.", control: pwa.updateReady ? html`<button type="button" class="dhun-button dhun-button--tonal" data-action="apply-update">Reload to update</button>` : html`<button type="button" class="dhun-button dhun-button--ghost" data-action="check-update">Check for update</button>` })}
      ${settingRow({ title: "Keyboard shortcuts", subtitle: "Space, arrows, / to search, and more.", control: html`<button type="button" class="dhun-button dhun-button--ghost" data-action="show-shortcuts">Show</button>` })}
      ${settingRow({ title: "About, privacy & legal", subtitle: "What DHUN is, what it sends, and the licence.", control: html`<button type="button" class="dhun-button dhun-button--ghost" data-action="open-about">Open</button>` })}
    </section>

    <section class="dhun-section">
      ${sectionHeader("Diagnostics", { hint: "Everything the app can see about this browser and this build." })}
      <dl class="dhun-diagnostics">
        ${diagnostics.rows.map(
          (row) => html`<div class="dhun-diagnostics__row">
            <dt>${row.label}</dt>
            <dd data-state="${row.state ?? "neutral"}">${row.value}</dd>
          </div>`,
        )}
      </dl>
      ${platform.unavailable.length
        ? html`<ul class="dhun-caveats">
            ${platform.unavailable.map((entry) => html`<li><strong>${entry.title}.</strong> ${entry.body}</li>`)}
          </ul>`
        : html``}
    </section>`;
}

/* ------------------------------------------------------------ about/legal -- */

/**
 * This build's identity, for the bundled pages.
 *
 * Deliberately not the module version in `package.json`: that is a packaging
 * version, not a release of the app. The web build is deployed from `main`, so
 * the honest answer is the app version plus the fact that this is a static web
 * build — and the About screen says exactly that.
 */
export const WEB_APP_INFO = Object.freeze({
  versionName: WEB_VERSION,
  versionCode: null,
  releaseChannel: "web (static build)",
  platform: "browser",
});

/** Render-time tokens, mirroring `LegalContent.TOKEN_*` in the app. Values the
 *  document cannot know are filled from *this* build's metadata, never typed in. */
function legalTokenTable(appInfo) {
  return {
    "{{appVersion}}": appInfo.versionName,
    "{{appVersionCode}}": appInfo.versionCode ?? "not applicable",
    "{{releaseChannel}}": appInfo.releaseChannel,
    "{{platform}}": appInfo.platform,
  };
}

/** Fills the pages' placeholders from this build's metadata. */
export function substituteLegalTokens(markdown, appInfo = WEB_APP_INFO) {
  const table = legalTokenTable(appInfo);
  return String(markdown).replace(/\{\{[a-zA-Z][a-zA-Z0-9]*\}\}/g, (token) =>
    token in table ? table[token] : token,
  );
}

/** Token literals still present — a page that keeps one renders with a warning. */
export function unresolvedLegalTokens(markdown) {
  return [...new Set(String(markdown).match(/\{\{[a-zA-Z][a-zA-Z0-9]*\}\}/g) ?? [])];
}

/** True for http(s) only. Anything else renders as text, not as a link. */
function isSafeUrl(url) {
  return /^https?:\/\//i.test(url);
}

/**
 * One span as markup.
 *
 * Every interpolated value is escaped here, and the result is marked once by the
 * caller. A span that is not a safe link is escaped text, so a future edit to the
 * corpus cannot turn a policy page into a `javascript:` launcher.
 */
function spanMarkup(span) {
  if (span.kind === "link") {
    // Escaping is the interpolation's job: returning the text plain means a
    // future edit cannot forget it.
    if (!isSafeUrl(span.url)) return span.text;
    return html`<a class="dhun-legal__link" href="${span.url}" target="_blank" rel="noopener noreferrer">${span.text}</a>`;
  }
  const classes = [span.bold ? "dhun-legal__strong" : "", span.code ? "dhun-legal__code" : "", span.strike ? "dhun-legal__strike" : ""]
    .filter(Boolean)
    .join(" ");
  return classes ? html`<span class="${classes}">${span.text}</span>` : span.text;
}

function spansMarkup(spans) {
  return html`${spans.map(spanMarkup)}`;
}

/** Cell contents are parsed too, so a link inside a table cell still works. */
function cellSpans(cell) {
  return parseMarkdown(cell).flatMap((block) => block.spans ?? []);
}

/** One Markdown block, the way the app's LegalBlock composes it. */
function blockMarkup(block) {
  switch (block.kind) {
    case "heading": {
      const level = Math.min(Math.max(block.level, 1), 6);
      // h2 is the top level here: the screen's own h1 is the page title, and a
      // second h1 would break the document outline.
      const tag = level === 1 ? "h2" : level === 2 ? "h3" : "h4";
      return html`<${raw(tag)} class="dhun-legal__heading dhun-legal__heading--${level}">${spansMarkup(block.spans)}</${raw(tag)}>`;
    }
    case "paragraph":
      return html`<p class="dhun-legal__para">${spansMarkup(block.spans)}</p>`;
    case "quote":
      return html`<blockquote class="dhun-legal__quote">${spansMarkup(block.spans)}</blockquote>`;
    case "bullet":
      return html`<li class="dhun-legal__li" data-indent="${block.indent}">${spansMarkup(block.spans)}</li>`;
    case "numbered":
      return html`<li class="dhun-legal__li" data-indent="${block.indent}" value="${block.number}">${spansMarkup(block.spans)}</li>`;
    case "code":
      return html`<pre class="dhun-legal__pre"><code>${block.text}</code></pre>`;
    case "rule":
      return html`<hr class="dhun-legal__rule" />`;
    case "table":
      // Stacked label/value records, not a grid: a four-column table on a 360px
      // phone is either illegible or sideways-scrollable, and a policy that
      // scrolls sideways is a policy nobody finishes reading.
      return html`${block.rows.map(
        (row) => html`<dl class="dhun-legal__record">${row.map((cell, column) => {
          const label = block.headers[column];
          return html`${label && label.trim() !== "" ? html`<dt class="dhun-legal__label">${label}</dt>` : html``}
            <dd class="dhun-legal__value">${spansMarkup(cellSpans(cell))}</dd>`;
        })}</dl>`,
      )}`;
    default:
      return html``;
  }
}

/** Groups consecutive list items so the markup stays a real list. */
function blocksMarkup(blocks) {
  const out = [];
  let listKind = null;
  let open = [];

  const flush = () => {
    if (!open.length) return;
    const tag = listKind === "numbered" ? "ol" : "ul";
    out.push(html`<${raw(tag)} class="dhun-legal__list">${open}</${raw(tag)}>`);
    open = [];
    listKind = null;
  };

  for (const block of blocks) {
    if (block.kind === "bullet" || block.kind === "numbered") {
      if (listKind && listKind !== block.kind) flush();
      listKind = block.kind;
      open.push(blockMarkup(block));
    } else {
      flush();
      out.push(blockMarkup(block));
    }
  }
  flush();
  return html`${out}`;
}

/**
 * About & legal.
 *
 * The documents are the project's own pages, generated from `legal/*.md` into
 * `data/legal-content.js` — the same bytes the Android and desktop builds
 * compile in. `blocks` is Markdown from that bundle, rendered here by the HTML
 * mirror of the app's `Markdown.kt`. It is app content, not visitor data, which
 * is why it renders as markup rather than as text.
 */
export function aboutLegalScreen({ documents, activeId, document, platform, build }) {
  const appInfo = { ...WEB_APP_INFO, ...(build ?? {}) , versionName: build?.version ?? WEB_APP_INFO.versionName };
  const markdown = document ? substituteLegalTokens(document.markdown, appInfo) : "";
  const unresolved = document ? unresolvedLegalTokens(markdown) : [];
  return html`
    <section class="dhun-section">
      ${sectionHeader("About DHUN in a browser", { hint: `Build ${build.version} · ${build.label}` })}
      <div class="dhun-legal__intro">
        <p>
          DHUN is a free, open-source music player. This is its web build: the same design system,
          the same navigation rules and the same queue behaviour as the Android and desktop apps, running
          entirely in your browser.
        </p>
        <p>
          <strong>What it is not.</strong> It is not an official YouTube or YouTube Music client, and it is not
          affiliated with Google. It reads YouTube Music's public endpoints anonymously — no account is used, no
          cookie is sent, and nothing is proxied through a DHUN server, because there is no DHUN server.
        </p>
        <p>
          <strong>What can break.</strong> Upstream endpoints change, and an origin can refuse cross-origin
          requests from a browser entirely. When that happens DHUN says so on screen and offers the data-source
          options in Settings, rather than showing invented rows as if they were real.
        </p>
      </div>
      <div class="dhun-input-pair">
        <button type="button" class="dhun-button dhun-button--tonal" data-action="show-shortcuts">Keyboard shortcuts</button>
        <button type="button" class="dhun-button dhun-button--ghost" data-action="export-library">Export my library</button>
      </div>
    </section>

    <section class="dhun-section">
      ${sectionHeader("Privacy, in one screen")}
      <ul class="dhun-caveats">
        <li><strong>No account.</strong> There is no sign-in, no session cookie and no DHUN user record anywhere.</li>
        <li><strong>Your library stays here.</strong> Playlists, likes and history live in this browser's storage on this device. Export is the only way they leave it.</li>
        <li><strong>Requests go to a music service, not to us.</strong> Metadata and audio are fetched from YouTube Music directly by your browser, or from an endpoint you configure. DHUN operates no server, so there is nothing for DHUN to collect.</li>
        <li><strong>No analytics, no telemetry, no advertising.</strong> Nothing in this build reports usage anywhere.</li>
        <li><strong>The service worker</strong> caches the app's own files so it opens offline. It does not cache audio.</li>
        <li><strong>Diagnostics</strong> (${platform.language ?? "an unknown language"}; online: ${String(platform.network.online)}) are shown to you on the Settings screen and are never uploaded.</li>
      </ul>
    </section>

    <section class="dhun-section">
      ${sectionHeader("Bundled documents", { hint: "Generated from legal/*.md — the same text the apps carry." })}
      <ul class="dhun-legal__index">
        ${documents.map(
          (entry) => html`<li>
            <a class="dhun-legal__entry" href="${entry.href}" ${entry.id === activeId ? raw('aria-current="true"') : raw("")}>
              <span class="dhun-legal__entry-title">${entry.title}</span>
              <span class="dhun-legal__entry-meta${entry.status === "draft" ? " dhun-legal__entry-meta--draft" : ""}">
                ${entry.status === "draft" ? `Draft · effective ${entry.effectiveDate}` : `Effective ${entry.effectiveDate}`}
              </span>
            </a>
          </li>`,
        )}
      </ul>
      ${document && document.status === "missing"
        ? notice({
            tone: "warn",
            title: document.title,
            body: `The link asked for a document this build does not carry (${document.id}). The list above is what is bundled.`,
          })
        : html``}
      ${document && document.status !== "missing"
        ? html`<article class="dhun-legal__document">
            ${unresolved.length
              ? html`<p class="dhun-legal__warn" role="alert">
                  This page still contains an unfilled placeholder (${unresolved.join(", ")}), so part of it
                  may read incorrectly.
                </p>`
              : html``}
            ${document.status === "draft"
              ? html`<aside class="dhun-legal__banner" role="note">
                  <strong>Draft — not yet in force</strong>
                  <p>
                    This page has not been reviewed by a lawyer and no contact channel has been published.
                    It is effective ${document.effectiveDate}.
                  </p>
                </aside>`
              : html``}
            <div class="dhun-legal__body" data-doc-id="${document.id}" data-sha256="${document.sha256}">
              ${blocksMarkup(parseMarkdown(markdown))}
            </div>
          </article>`
        : html`<p class="dhun-state__body">Choose a document above.</p>`}
    </section>`;
}

/* ---------------------------------------------------------------- sheets -- */

/** The ⋮ menu for a track. Every action here works on a real data source. */
export function trackMenuSheet({ track, inQueue = false, liked = false, playlists = [] }) {
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-label="Track actions">
    <div class="dhun-menu-header">
      ${artwork(track, { size: "thumb" })}
      <span class="dhun-menu-header__text">
        <span class="dhun-menu-header__title">${track.title}</span>
        <span class="dhun-menu-header__sub">${track.artist ?? ""}</span>
      </span>
      <button type="button" class="dhun-icon-button" data-action="close-sheet" aria-label="Close">${icon("Close", { size: 20 })}</button>
    </div>
    <div class="dhun-menu" role="menu">
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-play-now">${icon("Play", { size: 20 })}<span>Play now</span></button>
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-play-next">${icon("QueueMusic", { size: 20 })}<span>Play next</span></button>
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-queue-last">${icon("Add", { size: 20 })}<span>Add to queue</span></button>
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-like">${icon("Favorite", { size: 20 })}<span>${liked ? "Remove from liked" : "Like"}</span></button>
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-add-playlist">${icon("Add", { size: 20 })}<span>Add to playlist…</span></button>
      ${inQueue ? html`<button type="button" class="dhun-menu__row" role="menuitem" data-action="queue-remove" data-index="${track.queueIndex ?? ""}">${icon("Close", { size: 20 })}<span>Remove from queue</span></button>` : html``}
      ${track.albumId
        ? html`<button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-open-album" data-id="${track.albumId}">${icon("Album", { size: 20 })}<span>Go to album</span></button>`
        : html``}
      ${track.artistId
        ? html`<button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-open-artist" data-id="${track.artistId}">${icon("Person", { size: 20 })}<span>Go to artist</span></button>`
        : html``}
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="menu-copy-link">${icon("Link", { size: 20 })}<span>Copy link to this track</span></button>
    </div>
  </div>`;
}

export function playlistPickerSheet({ track, playlists }) {
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-label="Add to playlist">
    <div class="dhun-menu-header">
      <span class="dhun-menu-header__text">
        <span class="dhun-menu-header__title">Add to playlist</span>
        <span class="dhun-menu-header__sub">${track?.title ?? ""}</span>
      </span>
      <button type="button" class="dhun-icon-button" data-action="close-sheet" aria-label="Close">${icon("Close", { size: 20 })}</button>
    </div>
    ${playlists.length
      ? html`<div class="dhun-menu" role="menu">
          ${playlists.map(
            (playlist) => html`<button type="button" class="dhun-menu__row" role="menuitem" data-action="add-to-playlist" data-id="${playlist.id}">
              ${icon("Add", { size: 20 })}<span>${playlist.name}</span>
            </button>`,
          )}
        </div>`
      : emptyState({ title: "No playlists yet", body: "Create one below.", iconName: "LibraryMusic" })}
    <form class="dhun-newplaylist" data-action="new-playlist">
      <input class="dhun-input" type="text" name="name" placeholder="New playlist name" aria-label="New playlist name" maxlength="120" />
      <button type="submit" class="dhun-button dhun-button--tonal">Create</button>
    </form>
  </div>`;
}

/**
 * The one place a playlist is named.
 *
 * Both ways in land here: "New playlist" in the library header and "Rename" on a
 * playlist. The library button used to call the *form's* action from a button
 * with no form under it, so it read no input and returned — a button that did
 * nothing. Rename called `window.prompt`, which is a browser dialog, not this
 * app's, and cannot be styled, translated or reached by a screen reader the way
 * the rest of the shell is.
 */
export function playlistNameSheet({ mode = "create", playlist = null }) {
  const renaming = mode === "rename";
  const title = renaming ? "Rename playlist" : "New playlist";
  const action = renaming ? "rename-playlist" : "new-playlist";
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-label="${title}">
    <div class="dhun-menu-header">
      <span class="dhun-menu-header__text">
        <span class="dhun-menu-header__title">${title}</span>
        <span class="dhun-menu-header__sub">${renaming
          ? "The new name is saved on this device."
          : "It is kept in this browser, and nowhere else."}</span>
      </span>
      <button type="button" class="dhun-icon-button" data-action="close-sheet" aria-label="Close">${icon("Close", { size: 20 })}</button>
    </div>
    <form class="dhun-newplaylist" data-action="${action}"${renaming ? html` data-id="${playlist?.id ?? ""}"` : html``}>
      <input class="dhun-input" type="text" name="name" value="${renaming ? playlist?.name ?? "" : ""}" placeholder="Playlist name" aria-label="Playlist name" maxlength="120" autocomplete="off" enterkeyhint="done" />
      <button type="submit" class="dhun-button dhun-button--tonal">${renaming ? "Save" : "Create"}</button>
    </form>
  </div>`;
}

export function confirmSheet({ title, body, confirmLabel, action, cancelLabel = "Cancel" }) {
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-label="${title}">
    <div class="dhun-menu-header">
      <span class="dhun-menu-header__text">
        <span class="dhun-menu-header__title">${title}</span>
        ${body ? html`<span class="dhun-menu-header__sub">${body}</span>` : html``}
      </span>
    </div>
    <div class="dhun-menu">
      <button type="button" class="dhun-menu__row dhun-menu__row--danger" role="menuitem" data-action="${action}">${icon("Delete", { size: 20 })}<span>${confirmLabel}</span></button>
      <button type="button" class="dhun-menu__row" role="menuitem" data-action="close-sheet">${icon("Close", { size: 20 })}<span>${cancelLabel}</span></button>
    </div>
  </div>`;
}

/** The keyboard map, generated from `shortcuts.js` so it cannot drift. */
export function shortcutsSheet({ platform }) {
  return html`<div class="dhun-sheet dhun-sheet--help" role="dialog" aria-modal="true" aria-label="Keyboard shortcuts">
    <div class="dhun-menu-header">
      <span class="dhun-menu-header__text">
        <span class="dhun-menu-header__title">Keyboard shortcuts</span>
        <span class="dhun-menu-header__sub">They never fire while you are typing in a field.</span>
      </span>
      <button type="button" class="dhun-icon-button" data-action="close-sheet" aria-label="Close">${icon("Close", { size: 20 })}</button>
    </div>
    <div class="dhun-help">
      ${shortcutGroups().map(
        (group) => html`<section class="dhun-help__group">
          <h3 class="dhun-help__title">${group.name}</h3>
          <dl class="dhun-help__list">
            ${group.items.map(
              (item) => html`<div class="dhun-help__row">
                <dt><kbd class="dhun-kbd">${shortcutKeys(item, platform)}</kbd></dt>
                <dd>${item.label}</dd>
              </div>`,
            )}
          </dl>
        </section>`,
      )}
    </div>
  </div>`;
}

/** A transient confirmation ("Added to queue"). Announced, never blocking. */
export function toast(message) {
  return html`<p class="dhun-toast" role="status">${message}</p>`;
}

export { WEB_VERSION, BUILD_LABEL };
