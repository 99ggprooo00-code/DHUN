/**
 * Screens.
 *
 * Each function renders one screen the way its Compose counterpart composes
 * it — same order, same headings, same copy. The copy strings are the app's
 * own (HomeScreen's "Quick picks" / "Recommended songs" / "Listen again",
 * SearchScreen's "Search YouTube Music for songs, albums, and artists",
 * LibraryScreen's "No downloads yet", SettingsScreen's section headers).
 *
 * Every screen is a pure function of its inputs: no reading of global state,
 * no DOM writes. `main.js` owns state and swaps the HTML.
 */

import { html, raw } from "./dom.js";
import { iconSvg } from "./icons.js";
import { escapeHtml, formatDuration, trackSubtitle, pluralise, cacheOptionLabel, CACHE_OPTIONS_MB } from "./format.js";
import { artworkStyle } from "./catalog.js";
import { BANDS_HZ, frequencyLabel, formatDb, PRESETS, CUSTOM_ID, MIN_GAIN_DB, MAX_GAIN_DB } from "./equalizer.js";

export function icon(name, { size = 24, className = "" } = {}) {
  return raw(iconSvg(name, { size, className }));
}

/** SectionHeader — titleLarge over a row of content. */
export function sectionHeader(title, hint = "") {
  return html`<h2 class="dhun-section__header">${title}</h2>
    ${hint ? html`<p class="dhun-section__hint">${hint}</p>` : ""}`;
}

/** The persistent honesty notice. Required by ADR-008 boundary 4 and asserted
 *  by scripts/test_app_web.py — it is not decorative. */
export function previewNotice() {
  return html`<div class="dhun-notice" role="note" data-testid="preview-notice">
    <p>
      <strong>Engineering preview.</strong> This is the DHUN interface running in a browser.
      Audio playback is <strong>not proven</strong> from this origin — the transport advances a labelled
      clock so the interface can be exercised, and produces no sound. Do not describe this as a web player.
    </p>
  </div>`;
}

export function dataSourceNotice(source) {
  if (source === "live") return "";
  return html`<div class="dhun-notice" role="note" data-testid="sample-data-notice">
    <p>
      <strong>Sample data.</strong> Titles and artwork below are a fictional catalogue bundled with this
      build — the live catalogue is not reachable from this origin. Layout, navigation and the player are real.
    </p>
  </div>`;
}

/** ErrorView — the app's retry-capable error state, same three parts. */
export function errorState({ title = "Something went wrong", body = "", actionLabel = "Try again" } = {}) {
  return html`<div class="dhun-state" role="alert">
    ${icon("Error", { size: 32, className: "dhun-state__icon" })}
    <h2 class="dhun-state__title">${title}</h2>
    ${body ? html`<p class="dhun-state__body">${body}</p>` : ""}
    <button class="dhun-button dhun-button--tonal" type="button" data-action="retry">${actionLabel}</button>
  </div>`;
}

export function emptyState({ title, body = "", iconName = "Album" } = {}) {
  return html`<div class="dhun-state">
    ${icon(iconName, { size: 32, className: "dhun-state__icon" })}
    <h2 class="dhun-state__title">${title}</h2>
    ${body ? html`<p class="dhun-state__body">${body}</p>` : ""}
  </div>`;
}

/** LoadingShimmer — three skeleton rows at the app's skeleton widths. */
export function loadingState(rows = 3) {
  return html`<div class="dhun-tracklist" aria-busy="true" aria-live="polite">
    ${Array.from({ length: rows }, () => html`
      <div class="dhun-track" aria-hidden="true">
        <div class="dhun-track__art dhun-shimmer"></div>
        <div class="dhun-track__meta">
          <div class="dhun-shimmer" style="height:var(--dhun-space-md-plus);width:60%;border-radius:var(--dhun-shape-small)"></div>
          <div class="dhun-shimmer" style="height:var(--dhun-space-sm-plus);width:40%;margin-top:var(--dhun-space-sm);border-radius:var(--dhun-shape-small)"></div>
        </div>
      </div>`)}
  </div>`;
}

/** TrackRow — 56dp thumb, trackTitle + bodySmall subtitle, overflow menu. */
export function trackRow(track, { index = null, playing = false, overflow = true } = {}) {
  return html`<li>
    <button
      class="dhun-track"
      type="button"
      data-action="play-track"
      data-track-id="${track.id}"
      data-playing="${playing ? "true" : "false"}"
    >
      <span class="dhun-track__art" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></span>
      <span class="dhun-track__meta">
        <span class="dhun-track__title">${track.title}</span>
        <span class="dhun-track__subtitle">${trackSubtitle(track)}</span>
      </span>
      <span class="dhun-track__time">${formatDuration(track.durationMs)}</span>
    </button>
    ${overflow
      ? html`<button
          class="dhun-icon-button"
          type="button"
          data-action="track-overflow"
          data-track-id="${track.id}"
          aria-label="More actions for ${track.title}"
        >
          ${icon("MoreVert")}
        </button>`
      : ""}
  </li>`;
}

export function trackList(tracks, { currentTrackId = null } = {}) {
  return html`<ol class="dhun-tracklist">
    ${raw(
      tracks
        .map((track, index) =>
          trackRow(track, { index, playing: track.id === currentTrackId }),
        )
        .join(""),
    )}
  </ol>`;
}

/* ------------------------------------------------------------------- home -- */

export function homeScreen({ feed, source, currentTrackId }) {
  if (!feed) return loadingState(4);
  const [quickPicks, ...rest] = feed.sections;
  return html`
    ${raw(dataSourceNotice(source))}
    ${raw(
      quickPicks
        ? html`<section class="dhun-section">
            ${raw(sectionHeader(quickPicks.title))}
            <div class="dhun-shelf">
              ${raw(
                quickPicks.tracks
                  .map(
                    (track) => html`<button class="dhun-quickpick" type="button" data-action="play-track" data-track-id="${track.id}">
                      <span class="dhun-quickpick__art" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></span>
                      <span class="dhun-track__meta">
                        <span class="dhun-track__title">${track.title}</span>
                        <span class="dhun-track__subtitle">${track.artist}</span>
                      </span>
                    </button>`,
                  )
                  .join(""),
              )}
            </div>
          </section>`
        : "",
    )}
    ${raw(
      rest
        .map(
          (section) => html`<section class="dhun-section">
            ${raw(sectionHeader(section.title))}
            ${raw(trackList(section.tracks, { currentTrackId }))}
          </section>`,
        )
        .join(""),
    )}
  `;
}

/* ----------------------------------------------------------------- search -- */

export function searchScreen({ query, results, source, recentSearches, status }) {
  if (status === "loading") return loadingState(5);
  if (status === "error") {
    return errorState({ title: "Search failed", body: "The catalogue could not be reached." });
  }
  if (query.trim() === "") {
    return html`
      ${raw(dataSourceNotice(source))}
      ${recentSearches.length > 0
        ? html`<section class="dhun-section">
            ${raw(sectionHeader("Recent searches"))}
            <div class="dhun-tracklist">
              ${raw(
                recentSearches
                  .map(
                    (term) => html`<li style="display:flex;align-items:center;gap:var(--dhun-space-sm)">
                      <button class="dhun-menu__row" type="button" data-action="recent-search" data-term="${term}">
                        ${icon("History")}
                        <span>${term}</span>
                      </button>
                      <button
                        class="dhun-icon-button"
                        type="button"
                        data-action="forget-search"
                        data-term="${term}"
                        aria-label="Delete recent search ${term}"
                      >
                        ${icon("Close")}
                      </button>
                    </li>`,
                  )
                  .join(""),
              )}
            </div>
          </section>`
        : emptyState({
            title: "Search",
            body: "Search for songs, albums, artists and playlists.",
            iconName: "Search",
          })}
    `;
  }

  const { tracks = [], albums = [], artists = [], playlists = [] } = results ?? {};
  const total = tracks.length + albums.length + artists.length + playlists.length;
  if (total === 0) {
    return emptyState({ title: "No results found", body: `Nothing matched “${query}”.`, iconName: "Search" });
  }

  return html`
    ${raw(dataSourceNotice(source))}
    ${tracks.length
      ? html`<section class="dhun-section">
          ${raw(sectionHeader("Songs"))}
          ${raw(trackList(tracks))}
        </section>`
      : ""}
    ${artists.length
      ? html`<section class="dhun-section">
          ${raw(sectionHeader("Artists"))}
          <ul class="dhun-grid">
            ${raw(
              artists
                .map(
                  (artist) => html`<li>
                    <button class="dhun-grid__item" type="button" data-action="open-artist" data-id="${artist.id}">
                      <span class="dhun-artwork-card" style="${raw(artworkStyle(artist.id))}" aria-hidden="true"></span>
                      <p class="dhun-grid__title">${artist.name}</p>
                      <p class="dhun-grid__subtitle">Artist</p>
                    </button>
                  </li>`,
                )
                .join(""),
            )}
          </ul>
        </section>`
      : ""}
    ${albums.length
      ? html`<section class="dhun-section">
          ${raw(sectionHeader("Albums"))}
          <ul class="dhun-grid">
            ${raw(
              albums
                .map(
                  (album) => html`<li>
                    <button class="dhun-grid__item" type="button" data-action="open-album" data-id="${album.id}">
                      <span class="dhun-artwork-card" style="${raw(artworkStyle(album.id))}" aria-hidden="true"></span>
                      <p class="dhun-grid__title">${album.title}</p>
                      <p class="dhun-grid__subtitle">${album.artist}</p>
                    </button>
                  </li>`,
                )
                .join(""),
            )}
          </ul>
        </section>`
      : ""}
    ${playlists.length
      ? html`<section class="dhun-section">
          ${raw(sectionHeader("Playlists"))}
          <ul class="dhun-grid">
            ${raw(
              playlists
                .map(
                  (playlist) => html`<li>
                    <button class="dhun-grid__item" type="button" data-action="open-playlist" data-id="${playlist.id}">
                      <span class="dhun-artwork-card" style="${raw(artworkStyle(playlist.id))}" aria-hidden="true"></span>
                      <p class="dhun-grid__title">${playlist.name}</p>
                      <p class="dhun-grid__subtitle">${pluralise(playlist.trackIds.length, "track")}</p>
                    </button>
                  </li>`,
                )
                .join(""),
            )}
          </ul>
        </section>`
      : ""}
  `;
}

/* ---------------------------------------------------------------- library -- */

export const LIBRARY_TABS = Object.freeze(["PLAYLISTS", "DOWNLOADS", "HISTORY"]);

export function libraryScreen({ tab, playlists, history, favourites, downloadsAvailable }) {
  return html`
    <div class="dhun-chip-row" role="tablist" aria-label="Library">
      ${raw(
        LIBRARY_TABS.map(
          (name) => html`<button
            class="dhun-chip"
            type="button"
            role="tab"
            data-action="library-tab"
            data-tab="${name}"
            aria-selected="${tab === name ? "true" : "false"}"
          >
            ${name === "PLAYLISTS" ? "Playlists" : name === "DOWNLOADS" ? "Downloads" : "History"}
          </button>`,
        ).join(""),
      )}
    </div>
    ${tab === "PLAYLISTS" ? raw(playlistsPane({ playlists, favourites })) : ""}
    ${tab === "DOWNLOADS" ? raw(downloadsPane({ downloadsAvailable })) : ""}
    ${tab === "HISTORY" ? raw(historyPane({ history })) : ""}
  `;
}

function playlistsPane({ playlists, favourites }) {
  return html`
    <section class="dhun-section">
      ${raw(sectionHeader("Your Playlists"))}
      ${playlists.length === 0
        ? emptyState({
            title: "No playlists yet — create one below.",
            iconName: "QueueMusic",
          })
        : html`<ul class="dhun-tracklist">
            ${raw(
              playlists
                .map((playlist) => {
                  const count = playlist.isLikedFolder
                    ? favourites.length
                    : playlist.trackIds.length;
                  return html`<li>
                    <button class="dhun-track" type="button" data-action="open-playlist" data-id="${playlist.id}">
                      <span class="dhun-track__art" style="${raw(artworkStyle(playlist.id))}" aria-hidden="true"></span>
                      <span class="dhun-track__meta">
                        <span class="dhun-track__title">${playlist.name}</span>
                        <span class="dhun-track__subtitle">${pluralise(count, "song")}</span>
                      </span>
                    </button>
                    <button class="dhun-icon-button" type="button" data-action="play-playlist" data-id="${playlist.id}" aria-label="Play ${playlist.name}">
                      ${icon("Play")}
                    </button>
                  </li>`;
                })
                .join(""),
            )}
          </ul>`}
      <form class="dhun-search" data-action="new-playlist" style="margin-top:var(--dhun-space-lg)">
        <input type="text" name="name" placeholder="My playlist" aria-label="New playlist name" maxlength="60" />
        <button class="dhun-button dhun-button--tonal" type="submit">New playlist</button>
      </form>
    </section>
  `;
}

function downloadsPane({ downloadsAvailable }) {
  return html`
    ${downloadsAvailable
      ? ""
      : html`<div class="dhun-notice" role="note">
          <p>
            <strong>Downloads are not available in the browser build.</strong> Offline downloads
            (ADR-006) write media files to the device through the Android and desktop clients. A browser
            has no equivalent private store, so the two sections below are shown empty rather than filled
            with something that cannot be played offline.
          </p>
        </div>`}
    <section class="dhun-section">
      ${raw(sectionHeader("Active downloads"))}
      ${raw(emptyState({ title: "Nothing downloading", body: "Downloads started in the app do not appear here.", iconName: "Pending" }))}
    </section>
    <section class="dhun-section">
      ${raw(sectionHeader("Downloaded"))}
      ${raw(emptyState({ title: "No downloads yet", body: "Tracks you download in the Android or desktop client are stored on that device.", iconName: "Offline" }))}
    </section>
  `;
}

function historyPane({ history }) {
  if (history.length === 0) {
    return html`<section class="dhun-section">
      ${raw(sectionHeader("History"))}
      ${raw(emptyState({ title: "No history yet", body: "Tracks you play in this browser will be listed here.", iconName: "History" }))}
    </section>`;
  }
  return html`<section class="dhun-section">
    ${raw(sectionHeader("History"))}
    <ul class="dhun-tracklist">
      ${raw(
        history
          .map(
            (track) => html`<li>
              <button class="dhun-track" type="button" data-action="play-track" data-track-id="${track.id}">
                <span class="dhun-track__art" style="${raw(artworkStyle(track.id))}" aria-hidden="true"></span>
                <span class="dhun-track__meta">
                  <span class="dhun-track__title">${track.title}</span>
                  <span class="dhun-track__subtitle">${trackSubtitle(track)}</span>
                </span>
              </button>
              <button
                class="dhun-icon-button"
                type="button"
                data-action="remove-history"
                data-track-id="${track.id}"
                aria-label="Remove ${track.title} from history"
              >
                ${icon("Close")}
              </button>
            </li>`,
          )
          .join(""),
      )}
    </ul>
  </section>`;
}

/* ----------------------------------------------------------------- browse -- */

export function artistScreen({ artist, tracks, albums }) {
  return html`
    <div class="dhun-state" style="padding-top:var(--dhun-space-xl)">
      <span class="dhun-artwork-hero" style="${raw(artworkStyle(artist.id))}" aria-hidden="true"></span>
      <h2 class="dhun-state__title">${artist.name}</h2>
      <p class="dhun-state__body">${pluralise(tracks.length, "song")} · ${pluralise(albums.length, "album")}</p>
      <button class="dhun-button dhun-button--tonal" type="button" data-action="play-collection">
        ${icon("Play")} Play
      </button>
    </div>
    ${albums.length
      ? html`<section class="dhun-section">
          ${raw(sectionHeader("Albums"))}
          <ul class="dhun-grid">
            ${raw(
              albums
                .map(
                  (album) => html`<li>
                    <button class="dhun-grid__item" type="button" data-action="open-album" data-id="${album.id}">
                      <span class="dhun-artwork-card" style="${raw(artworkStyle(album.id))}" aria-hidden="true"></span>
                      <p class="dhun-grid__title">${album.title}</p>
                      <p class="dhun-grid__subtitle">${album.year}</p>
                    </button>
                  </li>`,
                )
                .join(""),
            )}
          </ul>
        </section>`
      : ""}
    <section class="dhun-section">${raw(sectionHeader("Songs"))} ${raw(trackList(tracks))}</section>
  `;
}

export function albumScreen({ album, tracks }) {
  return html`
    <div class="dhun-state" style="padding-top:var(--dhun-space-xl)">
      <span class="dhun-artwork-hero" style="${raw(artworkStyle(album.id))}" aria-hidden="true"></span>
      <h2 class="dhun-state__title">${album.title}</h2>
      <p class="dhun-state__body">${album.artist} · ${album.year} · ${pluralise(tracks.length, "track")}</p>
      <button class="dhun-button dhun-button--tonal" type="button" data-action="play-collection">
        ${icon("Play")} Play
      </button>
    </div>
    <section class="dhun-section">${raw(sectionHeader("Tracks"))} ${raw(trackList(tracks))}</section>
  `;
}

export function playlistScreen({ playlist, tracks }) {
  return html`
    <div class="dhun-state" style="padding-top:var(--dhun-space-xl)">
      <span class="dhun-artwork-hero" style="${raw(artworkStyle(playlist.id))}" aria-hidden="true"></span>
      <h2 class="dhun-state__title">${playlist.name}</h2>
      <p class="dhun-state__body">${pluralise(tracks.length, "track")}</p>
      <button class="dhun-button dhun-button--tonal" type="button" data-action="play-collection">
        ${icon("Play")} ${playlist.isLikedFolder ? "Play Liked Songs" : "Play"}
      </button>
    </div>
    <section class="dhun-section">
      ${raw(sectionHeader("Tracks"))}
      ${tracks.length === 0
        ? raw(emptyState({ title: "No songs in this playlist yet", iconName: "QueueMusic" }))
        : raw(trackList(tracks))}
    </section>
  `;
}

/* --------------------------------------------------------------- settings -- */

export function settingsScreen({ theme, accent, cacheSizeMb, resumeOnLaunch, equalizer }) {
  return html`
    <section class="dhun-section" style="margin-top:var(--dhun-space-md)">
      ${raw(sectionHeader("Appearance"))}
      <div class="dhun-chip-row" role="radiogroup" aria-label="Theme">
        ${raw(
          [
            ["dark", "Dark"],
            ["light", "Light"],
          ]
            .map(
              ([id, label]) => html`<button
                class="dhun-chip"
                type="button"
                role="radio"
                data-action="set-theme"
                data-theme="${id}"
                aria-checked="${theme === id ? "true" : "false"}"
              >
                ${label}
              </button>`,
            )
            .join(""),
        )}
      </div>
      <div class="dhun-chip-row" role="radiogroup" aria-label="Accent" style="margin-top:var(--dhun-space-sm)">
        ${raw(
          ["brand", "azure", "jade", "amber", "rose", "cyan"]
            .map(
              (id) => html`<button
                class="dhun-chip"
                type="button"
                role="radio"
                data-action="set-accent"
                data-accent="${id}"
                aria-checked="${accent === id ? "true" : "false"}"
              >
                ${id[0].toUpperCase()}${id.slice(1)}
              </button>`,
            )
            .join(""),
        )}
      </div>
    </section>

    <section class="dhun-section">
      ${raw(sectionHeader("Playback & storage"))}
      <div style="padding:0 var(--dhun-space-screen-padding)">
        <p class="dhun-track__subtitle" style="margin:0 0 var(--dhun-space-sm)">Audio cache size</p>
        <div class="dhun-chip-row" style="padding:0" role="radiogroup" aria-label="Audio cache size">
          ${raw(
            CACHE_OPTIONS_MB.map(
              (mb) => html`<button
                class="dhun-chip"
                type="button"
                role="radio"
                data-action="set-cache"
                data-mb="${mb}"
                aria-checked="${cacheSizeMb === mb ? "true" : "false"}"
              >
                ${cacheOptionLabel(mb)}
              </button>`,
            ).join(""),
          )}
        </div>
        <p class="dhun-track__subtitle" style="margin:var(--dhun-space-sm) 0 0">
          Space reserved for streamed audio. Applies on next launch.
        </p>
      </div>
      <div class="dhun-settings-row">
        <span class="dhun-settings-row__text">
          <span class="dhun-settings-row__title">Resume on launch</span>
          <span class="dhun-settings-row__subtitle">Restore the last queue when the app starts</span>
        </span>
        <button
          class="dhun-switch"
          type="button"
          role="switch"
          data-action="toggle-resume"
          aria-checked="${resumeOnLaunch ? "true" : "false"}"
          aria-label="Resume on launch"
        ></button>
      </div>
    </section>

    <section class="dhun-section">
      ${raw(sectionHeader("Equalizer"))}
      <div class="dhun-settings-row">
        <span class="dhun-settings-row__text">
          <span class="dhun-settings-row__title">Enabled</span>
        </span>
        <button
          class="dhun-switch"
          type="button"
          role="switch"
          data-action="toggle-equalizer"
          aria-checked="${equalizer.enabled ? "true" : "false"}"
          aria-label="Enabled"
        ></button>
      </div>
      <div style="padding:0 var(--dhun-space-screen-padding)">
        <p class="dhun-track__subtitle" style="margin:0 0 var(--dhun-space-sm)">Preset</p>
        <div class="dhun-chip-row" style="padding:0" role="radiogroup" aria-label="Preset">
          ${raw(
            PRESETS.map(
              (preset) => html`<button
                class="dhun-chip"
                type="button"
                role="radio"
                data-action="set-preset"
                data-preset="${preset.id}"
                aria-checked="${equalizer.presetId === preset.id ? "true" : "false"}"
              >
                ${preset.displayName}
              </button>`,
            ).join(""),
          )}
          ${raw(
            equalizer.presetId === CUSTOM_ID
              ? html`<span class="dhun-chip" aria-checked="true" role="radio" aria-disabled="true">Custom</span>`
              : "",
          )}
        </div>
      </div>
      <div style="padding:var(--dhun-space-md) var(--dhun-space-screen-padding) 0">
        ${raw(
          sliderRow({
            label: "Preamp",
            value: equalizer.preampDb,
            action: "set-preamp",
          }),
        )}
        ${raw(
          BANDS_HZ.map((hz, index) =>
            sliderRow({
              label: frequencyLabel(hz),
              value: equalizer.gainsDb[index] ?? 0,
              action: "set-band",
              index,
              enabled: equalizer.enabled,
            }),
          ).join(""),
        )}
      </div>
    </section>
  `;
}

function sliderRow({ label, value, action, index = null, enabled = true }) {
  return html`<div class="dhun-slider-row">
    <span class="dhun-slider-row__label">${label}</span>
    <input
      type="range"
      min="${MIN_GAIN_DB}"
      max="${MAX_GAIN_DB}"
      step="0.1"
      value="${value}"
      ${raw(enabled ? "" : "disabled")}
      aria-label="${label}"
      data-action="${action}"
      ${raw(index === null ? "" : `data-index="${index}"`)}
    />
    <span class="dhun-slider-row__value">${formatDb(value)}</span>
  </div>`;
}

/* ----------------------------------------------------------------- dialogs -- */

export function trackOverflowSheet(track, { inFavourites }) {
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-label="More actions for ${track.title}">
    <h2 class="dhun-sheet__title">${track.title}</h2>
    <ul class="dhun-menu">
      <li>
        <button class="dhun-menu__row" type="button" data-action="queue-next">
          ${icon("QueueMusic")} Play next
        </button>
      </li>
      <li>
        <button class="dhun-menu__row" type="button" data-action="toggle-favourite">
          ${icon(inFavourites ? "Favorite" : "FavoriteBorder")}
          ${inFavourites ? "Remove from liked songs" : "Add to liked songs"}
        </button>
      </li>
      <li>
        <button class="dhun-menu__row" type="button" data-action="add-to-playlist">
          ${icon("QueueMusic")} Add to playlist
        </button>
      </li>
      <li>
        <button class="dhun-menu__row" type="button" data-action="go-to-artist">
          ${icon("Person")} Go to artist
        </button>
      </li>
      <li>
        <button class="dhun-menu__row" type="button" data-action="go-to-album">
          ${icon("Album")} Go to album
        </button>
      </li>
      <li>
        <button class="dhun-menu__row" type="button" data-action="close-sheet">${icon("Close")} Close</button>
      </li>
    </ul>
  </div>`;
}

export function addToPlaylistSheet(track, playlists) {
  return html`<div class="dhun-sheet" role="dialog" aria-modal="true" aria-label="Add ${track.title} to a playlist">
    <h2 class="dhun-sheet__title">Add to playlist</h2>
    <ul class="dhun-menu">
      ${raw(
        playlists
          .map(
            (playlist) => html`<li>
              <button
                class="dhun-menu__row"
                type="button"
                data-action="confirm-add-to-playlist"
                data-playlist-id="${playlist.id}"
              >
                ${icon("QueueMusic")} ${playlist.name}
              </button>
            </li>`,
          )
          .join(""),
      )}
      <li>
        <button class="dhun-menu__row" type="button" data-action="close-sheet">${icon("Close")} Close</button>
      </li>
    </ul>
  </div>`;
}

export { escapeHtml };
