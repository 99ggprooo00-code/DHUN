/**
 * The screens.
 *
 * Every function here takes a view model and returns markup. The models are
 * built in `main.js` from the route, the source and the library, so a screen
 * never reads storage, never fetches, and never knows whether the rows in front
 * of it came from the network or from a saved snapshot.
 *
 * THE ORDER OF HONESTY, repeated on every screen:
 *   1. loading — says what is loading;
 *   2. error — the source's own words plus the technical line;
 *   3. empty — says what would be here and how to get it;
 *   4. content.
 *
 * The four states are exclusive. A screen that draws its error while loading is
 * a screen that tells the visitor something false for a second, and this app has
 * been that before.
 */

import { html } from "./dom.js";
import { BACKUP_MAX_BYTES } from "./backup-policy.js";
import { icon } from "./components.js";
import { formatCount, formatDuration, greetingFor, metaLine, pluralise } from "./format.js";
import {
  artwork,
  card,
  chip,
  collectionMeta,
  emptyState,
  errorState,
  loadingState,
  notice,
  pageHeader,
  rail,
  songList,
  sourceBadge,
} from "./components.js";

/* ------------------------------------------------------------------ home -- */

/**
 * Home: a greeting, one hero, then shelves.
 *
 * The hero is the first row of the first rail, drawn large. That is deliberately
 * not a "featured" flag the app invents — it is the top result of the trending
 * query, which is a real answer from the catalogue and changes when the
 * catalogue does.
 */
export function homeScreen(vm) {
  const body = (() => {
    if (vm.status === "loading") return loadingState(["Loading your music", "The catalogue is being read."]);
    // A failure the visitor can do nothing about is a dead end. The catalogue
    // failures this build meets (offline, timeout, a 5xx) are worth one more
    // attempt, so the state offers it instead of only describing it.
    if (vm.status === "error") return errorState({ ...vm.error, actions: [{ id: "retry", label: "Try again" }] });
    if (!vm.rails.length) return emptyState({ title: "Nothing to show yet", body: "The catalogue answered, but every shelf came back empty." });
    const hero = vm.hero;
    return html`
      ${hero ? heroBlock(hero, vm) : html``}
      ${vm.rails.map((entry) =>
        rail({
          title: entry.title,
          items: entry.items,
          kind: entry.kind,
          hrefFor: vm.hrefFor,
        }),
      )}`;
  })();

  return html`<div class="dhun-screen">
    <header class="dhun-home-head">
      <div>
        <p class="dhun-home-head__kicker">${greetingFor(vm.hour)}</p>
        <h1 class="dhun-home-head__title">${vm.headline}</h1>
      </div>
      ${sourceBadge(vm.source)}
    </header>
    ${vm.status === "sample"
      ? notice({
          kind: "sample",
          tone: "warn",
          title: "Sample catalogue.",
          body:
            vm.sampleNote ??
            "No catalogue instance answered, so these are example rows — nothing in this list has audio. The live catalogue returns as soon as one answers.",
          action: { id: "retry", label: "Try the catalogue again" },
        })
      : html``}
    ${body}
  </div>`;
}

/** The big card at the top of Home: artwork, title, and the two real actions. */
function heroBlock(item, vm) {
  const title = item.title ?? item.name ?? "";
  const meta = item.kind === "song" ? metaLine([item.artist, item.year]) : collectionMeta(item) || item.artist;
  return html`<section class="dhun-hero dhun-hero--feature" aria-labelledby="hero-title">
    ${item.image ? html`<span class="dhun-hero__backdrop" aria-hidden="true" style="background-image:url('${item.image}')"></span>` : html``}
    <span class="dhun-hero__veil" aria-hidden="true"></span>
    <div class="dhun-hero__inner">
      ${artwork(item, { size: "hero", className: "dhun-hero__art" })}
      <div class="dhun-hero__text">
        <p class="dhun-hero__kicker">${item.kind === "song" ? "Trending now" : item.kind === "playlist" ? "Playlist" : "New release"}</p>
        <h2 class="dhun-hero__title" id="hero-title">${title}</h2>
        <p class="dhun-hero__meta">${meta}</p>
        <div class="dhun-hero__actions">
          <button type="button" class="dhun-button dhun-button--filled dhun-button--play" data-action="play-hero">
            ${icon("Play", { size: 20 })}<span>Play</span>
          </button>
          ${item.kind === "song"
            ? html`<button type="button" class="dhun-button dhun-button--glass" data-action="play-hero-shuffled">${icon("Shuffle", { size: 18 })}<span>Shuffle</span></button>`
            : html`<a class="dhun-button dhun-button--glass" href="${vm.hrefFor(item)}">${icon("Album", { size: 18 })}<span>Open</span></a>`}
        </div>
      </div>
    </div>
  </section>`;
}

/* ---------------------------------------------------------------- search -- */

export const SEARCH_FILTERS = Object.freeze([
  { id: "songs", label: "Songs" },
  { id: "albums", label: "Albums" },
  { id: "artists", label: "Artists" },
  { id: "playlists", label: "Playlists" },
]);

/**
 * Search.
 *
 * The field is a real `<form>` with `role="search"`, so Enter submits, Escape
 * clears, and the browser's own "clear" affordance is a button with a name. The
 * caret stays in the field across a submit (the shell restores it) because a
 * search that loses the caret makes the visitor click before typing a second
 * word.
 */
export function searchScreen(vm) {
  return html`<div class="dhun-screen">
    <h1 class="dhun-visually-hidden">Search</h1>
    <form class="dhun-search" role="search" data-action="search-submit">
      <span class="dhun-search__icon" aria-hidden="true">${icon("Search", { size: 22 })}</span>
      <input id="search-input" class="dhun-search__input" type="search" name="q" value="${vm.query}" placeholder="Songs, albums, artists, playlists"
        aria-label="Search the catalogue" autocomplete="off" enterkeyhint="search" spellcheck="false" />
      ${vm.query ? html`<button type="button" class="dhun-icon-button dhun-search__clear" data-action="clear-search" aria-label="Clear the search">${icon("Close", { size: 18 })}</button>` : html``}
      <button type="submit" class="dhun-search__go">Search</button>
    </form>

    <div class="dhun-chips" role="group" aria-label="Filter results">
      ${SEARCH_FILTERS.map((filter) => chip(filter.label, { active: vm.filter === filter.id, action: "set-search-filter", value: filter.id }))}
    </div>

    <div id="search-results">${searchResults(vm)}</div>
  </div>`;
}

/** Only the result region changes during the same Search/IME intent. */
export function searchResults(vm) {
  if (vm.status === "loading") return loadingState([`Searching for “${vm.query}”`]);
  // A failure the visitor can do nothing about is a dead end. The catalogue
  // failures this build meets (offline, timeout, a 5xx) are worth one more
  // attempt, so the state offers it instead of only describing it.
  if (vm.status === "error") return errorState({ ...vm.error, actions: [{ id: "retry", label: "Try again" }] });
  if (vm.status === "idle") return searchIdle(vm);
  if (!vm.results.length) {
    return emptyState({
      title: `No ${vm.filter} for “${vm.query}”`,
      body: "The catalogue answered with nothing for that query. Try a different spelling, or another filter.",
      iconName: "Search",
    });
  }
  if (vm.filter === "songs") {
    return songList(vm.results, { currentId: vm.currentId, playing: vm.playing, likes: vm.likes, context: "search" });
  }
  return html`<div class="dhun-grid dhun-grid--${vm.filter}">
    ${vm.results.map((item) => card(item, { href: vm.hrefFor(item), kind: vm.filter === "albums" ? "album" : vm.filter === "artists" ? "artist" : "playlist" }))}
  </div>`;
}


function searchIdle(vm) {
  if (!vm.recent.length) {
    return emptyState({
      title: "Search the catalogue",
      body: "Songs, albums, artists and playlists — typed here, answered by JioSaavn, never sent anywhere else.",
      iconName: "Search",
    });
  }
  return html`<section class="dhun-recent" aria-labelledby="recent-title">
    <div class="dhun-rail__head">
      <h2 class="dhun-rail__title" id="recent-title">Recent searches</h2>
      <button type="button" class="dhun-rail__more" data-action="clear-recent">Clear</button>
    </div>
    <div class="dhun-chips">
      ${vm.recent.map((entry) => chip(entry, { action: "use-recent", value: entry }))}
    </div>
  </section>`;
}

/* --------------------------------------------------------------- entities -- */

/**
 * The hero every collection shares: a wide artwork field, blurred behind, with
 * the title sitting on the veil the way the app draws an artist screen.
 *
 * `actions` is a markup slot so an album gets Play/Shuffle and a local playlist
 * gets Rename/Delete without this function knowing what a playlist is.
 */
export function entityHero({ kicker, title, meta = "", description = "", image = "", actions = "", round = false, extra = "" }) {
  return html`<section class="dhun-hero dhun-hero--entity${round ? " dhun-hero--round" : ""}">
    ${image ? html`<span class="dhun-hero__backdrop" aria-hidden="true" style="background-image:url('${image}')"></span>` : html``}
    <span class="dhun-hero__veil" aria-hidden="true"></span>
    <div class="dhun-hero__inner">
      ${artwork({ image, title }, { size: "entity", className: `dhun-hero__art${round ? " dhun-hero__art--round" : ""}` })}
      <div class="dhun-hero__text">
        ${kicker ? html`<p class="dhun-hero__kicker">${kicker}</p>` : html``}
        <h1 class="dhun-hero__title">${title}</h1>
        ${meta ? html`<p class="dhun-hero__meta">${meta}</p>` : html``}
        ${description ? html`<p class="dhun-hero__description">${description}</p>` : html``}
        ${actions ? html`<div class="dhun-hero__actions">${actions}</div>` : html``}
      </div>
    </div>
    ${extra}
  </section>`;
}

function playAllActions({ playAction = "play-collection", shuffleAction = "shuffle-collection", extra = "" }) {
  return html`<button type="button" class="dhun-button dhun-button--filled dhun-button--play" data-action="${playAction}">${icon("Play", { size: 20 })}<span>Play</span></button>
    <button type="button" class="dhun-button dhun-button--glass" data-action="${shuffleAction}">${icon("Shuffle", { size: 18 })}<span>Shuffle</span></button>
    ${extra}`;
}

export function albumScreen(vm) {
  if (vm.status === "loading") return html`<div class="dhun-screen">${loadingState(["Loading the album"])}</div>`;
  if (vm.status === "error") {
    return html`<div class="dhun-screen">${errorState({
      ...vm.error,
      heading: true,
      actions: [
        { id: "retry", label: "Try again" },
        { id: "go-home", label: "Go home" },
      ],
    })}</div>`;
  }
  if (!vm.album) {
    // The screen is only ever built with data, but a render that arrives a frame
    // early must not throw inside the renderer: a missing record is a state.
    return html`<div class="dhun-screen">${errorState({
      title: "That album could not be loaded",
      body: "The catalogue answered, but no album came back with it.",
      actions: [{ id: "go-home", label: "Go home" }],
    })}</div>`;
  }
  const album = vm.album;
  return html`<div class="dhun-screen">
    ${entityHero({
      kicker: "Album",
      title: album.title,
      meta: metaLine([album.artist, album.year, pluralise(album.songCount, "song")]),
      image: album.image,
      actions: playAllActions({ extra: vm.saved
        ? html`<button type="button" class="dhun-icon-button dhun-button--glass" data-action="toggle-collection-save" aria-pressed="true" aria-label="Remove from your library">${icon("Favorite", { size: 20 })}</button>`
        : html`<button type="button" class="dhun-icon-button dhun-button--glass" data-action="toggle-collection-save" aria-pressed="false" aria-label="Save to your library">${icon("FavoriteBorder", { size: 20 })}</button>` }),
    })}
    ${songList(album.songs, { currentId: vm.currentId, playing: vm.playing, likes: vm.likes, context: `album:${album.id}` })}
  </div>`;
}

export function playlistScreen(vm) {
  if (vm.status === "loading") return html`<div class="dhun-screen">${loadingState(["Loading the playlist"])}</div>`;
  if (vm.status === "error") {
    return html`<div class="dhun-screen">${errorState({
      ...vm.error,
      heading: true,
      actions: [
        { id: "retry", label: "Try again" },
        { id: "go-home", label: "Go home" },
      ],
    })}</div>`;
  }
  if (!vm.playlist) {
    return html`<div class="dhun-screen">${errorState({
      title: "That playlist could not be loaded",
      body: "The catalogue answered, but no playlist came back with it.",
      actions: [{ id: "go-home", label: "Go home" }],
    })}</div>`;
  }
  const playlist = vm.playlist;
  return html`<div class="dhun-screen">
    ${entityHero({
      kicker: "Playlist",
      title: playlist.title,
      meta: collectionMeta(playlist),
      description: playlist.description,
      image: playlist.image,
      actions: playAllActions({}),
    })}
    ${songList(playlist.songs, { currentId: vm.currentId, playing: vm.playing, likes: vm.likes, context: `playlist:${playlist.id}` })}
  </div>`;
}

export function artistScreen(vm) {
  if (vm.status === "loading") return html`<div class="dhun-screen">${loadingState(["Loading the artist"])}</div>`;
  if (vm.status === "error") {
    return html`<div class="dhun-screen">${errorState({
      ...vm.error,
      heading: true,
      actions: [
        { id: "retry", label: "Try again" },
        { id: "go-home", label: "Go home" },
      ],
    })}</div>`;
  }
  if (!vm.artist) {
    return html`<div class="dhun-screen">${errorState({
      title: "That artist could not be loaded",
      body: "The catalogue answered, but no artist came back with it.",
      actions: [{ id: "go-home", label: "Go home" }],
    })}</div>`;
  }
  const { artist } = vm;
  return html`<div class="dhun-screen">
    ${entityHero({
      kicker: "Artist",
      title: artist.title,
      meta: metaLine([
        artist.verified ? "Verified artist" : "",
        artist.followerCount ? `${formatCount(artist.followerCount)} followers` : "",
        artist.dominantLanguage,
      ]),
      description: artist.description,
      image: artist.image,
      round: true,
      actions: playAllActions({}),
    })}
    ${vm.songs.length
      ? html`<section class="dhun-section"><h2 class="dhun-section__title">Popular</h2>
          ${songList(vm.songs, { currentId: vm.currentId, playing: vm.playing, likes: vm.likes, context: `artist:${artist.id}` })}
        </section>`
      : html``}
    ${vm.albums.length
      ? rail({ title: "Albums", items: vm.albums, kind: "album", hrefFor: vm.hrefFor })
      : html``}
  </div>`;
}

/**
 * A playlist you made. Same shape as a catalogue playlist, different actions:
 * a local list can be renamed, deleted, and have its rows removed, and it knows
 * its own name without asking the network.
 */
export function collectionScreen(vm) {
  if (!vm.playlist) {
    return html`<div class="dhun-screen">${errorState({
      title: "That playlist is not in this browser",
      body: "It was deleted, or it was made in a different browser. Playlists live in this device's storage and are not synced anywhere.",
      heading: true,
      actions: [{ id: "go-library", label: "Open your library" }],
    })}</div>`;
  }
  // The tools are drawn for every playlist, empty or not: a playlist created a
  // moment ago is the one most likely to be renamed or deleted, and it used to
  // be the one with no Rename and no Delete anywhere on the screen.
  const extraActions = html`<button type="button" class="dhun-icon-button dhun-button--glass" data-action="rename-playlist" aria-label="Rename this playlist">${icon("MoreVert", { size: 20 })}</button>`;
  return html`<div class="dhun-screen">
    ${entityHero({
      kicker: "Your playlist",
      title: vm.playlist.name,
      meta: pluralise(vm.playlist.trackIds.length, "song"),
      image: vm.tracks[0]?.image ?? "",
      actions: playAllActions({
        playAction: "play-collection",
        shuffleAction: "shuffle-collection",
        extra: extraActions,
      }),
    })}
    <div class="dhun-collection-tools">
      <button type="button" class="dhun-text-button" data-action="rename-playlist">Rename</button>
      <button type="button" class="dhun-text-button dhun-text-button--danger" data-action="delete-playlist">Delete playlist</button>
    </div>
    ${vm.tracks.length
      ? songList(vm.tracks, { currentId: vm.currentId, playing: vm.playing, likes: vm.likes, context: `collection:${vm.playlist.id}` })
      : emptyState({
          title: "This playlist is empty",
          body: "Open any track's menu and choose “Add to playlist” — the rows stay in this browser.",
          iconName: "LibraryMusic",
        })}
  </div>`;
}

/* --------------------------------------------------------------- library -- */

export function libraryScreen(vm) {
  const tabs = [
    { id: "playlists", label: "Playlists", count: vm.playlists.length },
    { id: "likes", label: "Liked songs", count: vm.likes.length },
    { id: "history", label: "Recently played", count: vm.history.length },
  ];
  const body = (() => {
    if (vm.tab === "playlists") {
      // The empty state already offers "New playlist"; drawing the header button
      // over it too put two identical controls on an otherwise empty screen.
      return html`${vm.playlists.length
          ? html`<div class="dhun-library-tools">
              <button type="button" class="dhun-button dhun-button--filled" data-action="new-playlist">${icon("Add", { size: 18 })}<span>New playlist</span></button>
            </div>`
          : html``}
        ${vm.playlists.length
          ? html`<div class="dhun-grid dhun-grid--playlists">
              ${vm.playlists.map((playlist) =>
                card(
                  { ...playlist, image: vm.playlistCovers[playlist.id] ?? "", title: playlist.name, kind: "playlist" },
                  { href: `#/collection/${encodeURIComponent(playlist.id)}`, kind: "playlist" },
                ),
              )}
            </div>`
          : emptyState({
              title: "No playlists yet",
              body: "Make one here, then add tracks from any song's menu. It stays in this browser.",
              iconName: "LibraryMusic",
              action: { id: "new-playlist", label: "New playlist" },
            })}`;
    }
    if (vm.tab === "likes") {
      return vm.likes.length
        ? songList(vm.likes, { currentId: vm.currentId, playing: vm.playing, likes: vm.likeIds, context: "likes" })
        : emptyState({
            title: "No liked songs yet",
            body: "The heart on any row saves it here — no account, no sync, just this browser.",
            iconName: "FavoriteBorder",
          });
    }
    return vm.history.length
      ? songList(vm.history, { currentId: vm.currentId, playing: vm.playing, likes: vm.likeIds, context: "history" })
      : emptyState({ title: "Nothing played yet", body: "Tracks you play show up here, newest first.", iconName: "History" });
  })();

  return html`<div class="dhun-screen">
    ${pageHeader({ overline: "Your library", title: "Made of what you saved here", meta: vm.storage.persistent ? "Kept in this browser only" : "This tab cannot confirm saving changes" })}
    <div class="dhun-tabs" role="tablist" aria-label="Library sections">
      ${tabs.map((tab) => html`<button type="button" role="tab" id="lib-tab-${tab.id}" class="dhun-tab${vm.tab === tab.id ? " is-active" : ""}"
        aria-selected="${vm.tab === tab.id ? "true" : "false"}" aria-controls="library-panel" data-action="set-library-tab" data-value="${tab.id}">
        ${tab.label}${tab.count ? html` <span class="dhun-tab__count">${tab.count}</span>` : html``}
      </button>`)}
    </div>
    <div class="dhun-panel" id="library-panel" role="tabpanel" aria-labelledby="lib-tab-${vm.tab}">${body}</div>
    ${!vm.storage.persistent
      ? notice({ tone: "warn", title: "This tab cannot confirm saving.", body: vm.storage.note ?? "Changes may last only for this visit. Keep existing browser data and your backups." })
      : html``}
  </div>`;
}

/**
 * The instances behind the catalogue, and what each answered last.
 *
 * The list is the app's own record, not a claim: `answered` is only printed for
 * the host that actually returned the rows on screen. A visitor debugging "why
 * is this the sample catalogue" can see which host refused and how.
 */
function chainRows(chain) {
  if (!chain || !Array.isArray(chain.origins) || !chain.origins.length) return html``;
  const seen = new Map((chain.attempts ?? []).map((entry) => [entry.id, entry]));
  const activeId = chain.origin?.id ?? "";
  return html`<ul class="dhun-source-list">
    ${chain.origins.map((origin) => {
      const attempt = seen.get(origin.id);
      const outcome = !attempt
        ? activeId === origin.id
          ? "in use"
          : "not asked yet"
        : attempt.kind === "ok"
          ? "answered"
          : attempt.kind === "http"
            ? `HTTP ${attempt.status}`
            : attempt.kind === "timeout"
              ? "no answer in time"
              : "could not be reached";
      return html`<li class="dhun-source-list__row">
        <span class="dhun-source-list__host">${origin.label}</span>
        <span class="dhun-source-list__state">${outcome}</span>
      </li>`;
    })}
  </ul>`;
}

/* --------------------------------------------------------------- settings -- */

const ACCENT_LABELS = { brand: "Violet", azure: "Azure", jade: "Jade", amber: "Amber", rose: "Rose", cyan: "Cyan" };

/**
 * Settings.
 *
 * Everything here is a choice this build can actually honour: theme, accent,
 * volume, the catalogue's disclosure, and what this browser is storing. The
 * application's native equaliser is deliberately absent — its bands are a
 * platform audio binding, and the browser's Web Audio path would route the
 * catalogue's cross-origin audio through a graph that can fail silently. A
 * control that might mute the product is worse than no control, so the rebuild
 * does not draw one. `LIMITATIONS.md` says the same thing.
 */
export function settingsScreen(vm) {
  const { settings } = vm;
  return html`<div class="dhun-screen dhun-screen--settings">
    ${pageHeader({ overline: "Settings", title: "Make it yours", meta: vm.storage?.incomplete ? "Saved data could not be read safely. The library shown may be incomplete." : vm.storage?.persistent ? "Saved in this browser. There is no account to sync." : "Storage could not confirm saving. Export a backup; some changes may last only for this visit." })}

    <section class="dhun-settings">
      <h2 class="dhun-settings__title">Appearance</h2>
      <div class="dhun-setting">
        <span class="dhun-setting__label" id="theme-label">Theme</span>
        <div class="dhun-chips" role="group" aria-labelledby="theme-label">
          ${["dark", "light", "system"].map((theme) => chip(theme[0].toUpperCase() + theme.slice(1), { active: settings.theme === theme, action: "set-theme", value: theme }))}
        </div>
      </div>
      <div class="dhun-setting">
        <span class="dhun-setting__label" id="accent-label">Accent</span>
        <div class="dhun-chips" role="group" aria-labelledby="accent-label">
          ${Object.entries(ACCENT_LABELS).map(([id, label]) => chip(label, { active: settings.accent === id, action: "set-accent", value: id }))}
        </div>
      </div>
    </section>

    <section class="dhun-settings">
      <h2 class="dhun-settings__title">Playback</h2>
      <div class="dhun-setting">
        <label class="dhun-setting__label" for="volume-range">Volume</label>
        <input id="volume-range" class="dhun-range" type="range" min="0" max="1" step="0.05" value="${settings.volume}" data-action="set-volume" />
      </div>
      <div class="dhun-setting">
        <span class="dhun-setting__label" id="repeat-label">Repeat</span>
        <div class="dhun-chips" role="group" aria-labelledby="repeat-label">
          ${["off", "all", "one"].map((mode) => chip(mode[0].toUpperCase() + mode.slice(1), { active: settings.repeat === mode, action: "set-repeat", value: mode }))}
        </div>
      </div>
    </section>

    <section class="dhun-settings">
      <h2 class="dhun-settings__title">Catalogue</h2>
      <div class="dhun-setting dhun-setting--stack">
        <span class="dhun-setting__label">Source</span>
        <p class="dhun-setting__note">${vm.source.note}</p>
        <p class="dhun-setting__note dhun-setting__note--mono">${vm.source.endpoint}</p>
        ${chainRows(vm.source.chain)}
        <p class="dhun-setting__note">${vm.source.disclosure}</p>
      </div>
    </section>

    <section class="dhun-settings">
      <h2 class="dhun-settings__title">This browser</h2>
      <div class="dhun-setting dhun-setting--stack">
        <p class="dhun-setting__note">${vm.storage.note}</p>
        <p class="dhun-setting__note">Imports accept up to ${BACKUP_MAX_BYTES / (1024 * 1024)} MiB of UTF-8 JSON, subject to complexity limits. Even a valid backup may exceed browser storage quota or available memory. Keep your backup until you have verified the restore.</p>
        <div class="dhun-setting__buttons">
          <button type="button" class="dhun-button dhun-button--outlined" data-action="export-library">Export library</button>
          <label class="dhun-button dhun-button--outlined dhun-file">Import library<input type="file" accept="application/json,.json" data-action="import-library" /></label>
        </div>
        <button type="button" class="dhun-text-button dhun-text-button--danger" data-action="clear-library">Erase library</button>
      </div>
    </section>

    <section class="dhun-settings">
      <h2 class="dhun-settings__title">About</h2>
      <a class="dhun-setting-row" href="#/about/privacy">${icon("Link", { size: 20 })}<span>Privacy, terms and licences</span></a>
      <button type="button" class="dhun-setting-row" data-action="show-shortcuts">${icon("Pending", { size: 20 })}<span>Keyboard shortcuts</span></button>
      <p class="dhun-settings__version">${vm.version}</p>
    </section>
  </div>`;
}

/* ------------------------------------------------------------------ about -- */

/**
 * What a visitor sees when the page is loaded inside someone else's frame.
 *
 * `frame-ancestors` cannot be delivered by a meta tag and GitHub Pages sends no
 * headers, so the rule is enforced in the app: `boot()` refuses to start and
 * this is the whole screen. It is deliberately not an interactive app — an
 * embedded copy would run a second player inside an unknown page.
 */
export function framedRefusal() {
  return html`<div class="dhun-screen dhun-screen--framed">
    ${pageHeader({ overline: "DHUN web", title: "This build only runs in its own tab." })}
    ${errorState({
      title: "It is embedded in another page.",
      body: "DHUN refuses to open inside a frame, because an embedded player would be controlled by whatever page surrounds it. Open the app directly to continue.",
      actions: html`<a class="dhun-button dhun-button--filled" href="./" target="_top" rel="noopener">Open DHUN</a>`,
    })}
  </div>`;
}

export function aboutScreen(vm) {
  return html`<div class="dhun-screen dhun-screen--about">
    ${pageHeader({ overline: "About & legal", title: vm.title, meta: "Bundled with this build; no request leaves the page." })}
    <nav class="dhun-about-nav" aria-label="Legal documents">
      ${vm.documents.map((document) =>
        html`<a class="dhun-chip${document.id === vm.activeId ? " is-active" : ""}" href="#/about/${document.id}" aria-current="${document.id === vm.activeId ? "page" : "false"}">${document.title}</a>`,
      )}
    </nav>
    <article class="dhun-prose">${vm.body}</article>
  </div>`;
}

