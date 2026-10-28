# Security and privacy — DHUN Web (`/app/`)

What the browser build protects, what it sends to whom, and what it does not
claim. This is an engineering description. It is **not** a legal review and not
a compliance statement. No lawyer has reviewed it, and the project cannot fund
one at present (owner decision D1 in
[ADR-010](../decisions/ADR-010-multi-source-provider-direction.md)). The in-app
privacy and terms texts are in `app-web/src/js/data/web-legal.js`.

## 1. What leaves the visitor's browser

| Destination | What is sent | Why |
|---|---|---|
| `saavn.dev`, `saavn.sumit.co`, `saavnx.vercel.app` | Search text, song/album/artist/playlist ids, page numbers. Standard request headers, sent by the browser. No cookies (`credentials: omit`). | To read the catalogue. |
| `*.saavncdn.com` | The audio and artwork request for a URL the catalogue returned. | To play and show a track. |

No other third-party runtime is requested by app code. GitHub Pages still serves
the interface and updates; voluntary issues go to GitHub. The page loads no fonts, scripts, analytics, tags,
embeds or advertising from any other origin. This is enforced by the CSP (see
[TRD.md](TRD.md) §2) and checked by `scripts/test_app_web.py` and by the
`website_claims.py` rules over the shipped dependency graph.

The browser's own requests for those origins reveal the visitor's IP address to
those origins, as any web request does. The build adds no tracking identifier,
not a promise that providers cannot identify requests. Failover may send the
same query/IDs to several independent operators; API uses `cache: no-store`.
Media/images and referrer/cache/headers are browser-managed; no packet audit here.

## 2. What is stored on the device

One key in `localStorage`: `dhun.web.v3` (see [DATA-SCHEMA.md](DATA-SCHEMA.md)).
It holds likes, playlists, history, recent searches, referenced track-display
snapshots (including artwork URLs, blanked stream URLs), and settings;
unreferenced snapshots are pruned. SW caches shell only, browser media caching is separate. Nothing is stored in cookies, `IndexedDB` or a server.

The visitor can:

- **See** its contents on the Library screen. The raw record is visible only
  through the browser's own storage tools.
- **Export** it: Settings → This browser → Export library.
- **Delete** it: Settings → This browser → "Erase library"
  asks for confirmation before it removes likes, playlists, history, snapshots
  and recent searches. It keeps the settings. A single playlist can be deleted
  from its own screen. The browser's site-data controls for the origin also
  remove the stored record, settings included.

**Storage failure is not successful deletion.** Destructive library changes and
replacement import now require a confirmed localStorage write before replacing
in-tab data. Failed confirmations stay open with an announced error and retry;
ordinary edits may remain visit-only with an explicit warning. Saved copies may
still exist after refusal. The origin-wide browser controls can affect other
projects on the same host; no DHUN action deletes wrapper/CDN/GitHub logs.
History is recorded per selection rather than progress tick, so erase during
ongoing playback is not immediately undone. Later selections can record history.

## 3. What the project does not hold

- No **DHUN-operated** server that receives the library, searches or plays; wrapper/CDN/hosting requests are still third-party processing.
- No account, login, email or password.
- No analytics, telemetry, crash reporting or advertising identifier. The build
  has no code that sends any of these.
- No server-side catalogue mirror; local metadata snapshots still exist.

Whether the three catalogue services keep logs of requests is **not known** to
the project. It is the services' own policy and is not verified.

## 4. Framing and embedding

- The page sets `<meta name="robots" content="noindex, nofollow">`, so search
  engines are asked not to index it.
- At boot, if the page is inside a frame, the framed-refusal screen is drawn and
  nothing else starts. A page that cannot prove it is top-level does not start.
- `frame-ancestors` is not set in the meta CSP, because a meta tag cannot set it.
  The boot-time check is the protection in this build.

## 5. Content Security Policy

The policy is in [TRD.md](TRD.md) §2. Its main effects:

- Scripts only from the same origin. No inline script, no `eval`.
- Styles from the same origin plus `'unsafe-inline'`, which is allowed for styles
  only.
- Images, audio and network requests only to the listed origins.
- No form submission (`form-action 'none'`) and no `<base>` override.

The policy is delivered by a `<meta>` tag. Headers that the host would add are
not under this repository's control on GitHub Pages.

## 6. Input handling

- Templates are built with the `html` helper in `dom.js`, which escapes every
  interpolated value unless the value is already marked as markup. Catalogue text
  is also converted with a plain-text function before it is used.
- Imported files are read only if they are at most 8 MB, are JSON, and carry the
  `app` marker. The normaliser then discards fields it does not know and clamps
  numbers (see [DATA-SCHEMA.md](DATA-SCHEMA.md) §5).
- Hash routes are parsed by the router; an unknown name opens Home.

## 7. Secrets

No user API key/token/account credential is required or stored by this build.
Returned signed media URLs are access-bearing/transient, not user OAuth tokens.
Spotify is planned/deferred with no auth or traffic; future secure-token and
privacy design is not implemented. `scripts/test_repo_health.py`
checks the repository for committed secrets.

## 8. Known weaknesses

- The catalogue is a third-party community service. Its availability, correctness
  and privacy practices are outside this project's control.
- A shared computer can read the library, because it is stored unencrypted in the
  browser. That is how browser storage works and is not hidden.
- `'unsafe-inline'` for styles weakens the CSP somewhat. It is kept because the
  app writes an inline `background-image` on a hero's backdrop (the HTML comment
  gives this reason); there is no user-controlled CSS path.

## 9. Policy questions open

- **YouTube developer policies** (§III.I.14, §III.I.07 and related clauses) raise
  unresolved applicability/permission questions for the native path, not browser JioSaavn. Recorded in
  [`docs/research/03-youtube-youtube-music.md`](../research/03-youtube-youtube-music.md).
- Not used by the browser build, but the **Spotify developer policy** governs the planned source (§III.5 and others). Not used here, and recorded in `docs/research/04-spotify.md`.
- **Community catalogue permissions.** Targeted [JioSaavn Terms](https://www.jiosaavn.com/corporate/terms/)
  clauses re-read 2026-10-28 restrict unofficial/automated access. Required
  permissions, wrapper-specific terms/retention and content rights remain
  unresolved; [JioSaavn Privacy](https://www.jiosaavn.com/corporate/privacy/)
  does not prove each independent wrapper's practices. This is not legal review.

The project treats these as open risks. It does not claim compliance with any of
them.
