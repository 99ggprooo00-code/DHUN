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

Nothing else is requested. The page loads no fonts, analytics, tags, embeds or
advertising from any other origin. Three player services have permitted script and frame hosts, and none is used. The page makes no requests to them, and no code loads them. YouTube's player hosts are `www.youtube.com` and `s.ytimg.com`. Spotify's Web Playback SDK is permitted but not used, and its script host is `sdk.scdn.co`. Apple's MusicKit JS is permitted but not used, and its script host is `js-cdn.music.apple.com`. Their network, image and media hosts are not allowed until a feature needs them. This is enforced by the CSP (see
[TRD.md](TRD.md) §2) and checked by `scripts/test_app_web.py` and by the
`website_claims.py` rules over the shipped dependency graph.

The browser's own requests for those origins reveal the visitor's IP address to
those origins, as any web request does. The build does not add identifiers.

## 2. What is stored on the device

One key in `localStorage`: `dhun.web.v3` (see [DATA-SCHEMA.md](DATA-SCHEMA.md)).
It holds likes, playlists, history, recent searches, a copy of track display
fields, and settings. Nothing is stored in cookies, `IndexedDB` or a server.

The visitor can:

- **See** its contents on the Library screen. The raw record is visible only
  through the browser's own storage tools.
- **Export** it: Settings → This browser → Export library.
- **Delete** it: Settings → This browser → "Erase everything in this browser"
  asks for confirmation before it removes likes, playlists, history, snapshots
  and recent searches. It keeps the settings. A single playlist can be deleted
  from its own screen. The browser's site-data controls for the origin also
  remove the stored record, settings included.

## 3. What the project does not hold

- No server that receives the visitor's library, searches or plays.
- No account, login, email or password.
- No analytics, telemetry, crash reporting or advertising identifier. The build
  has no code that sends any of these.
- No copy of catalogue responses kept by the project.

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

No API key, token or credential is required or stored. `scripts/test_repo_health.py`
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

- **YouTube developer policies** (§III.I.14, §III.I.07 and related clauses) govern
  the native YouTube path, which is not part of the browser build. Recorded in
  [`docs/research/03-youtube-youtube-music.md`](../research/03-youtube-youtube-music.md).
- Not used by the browser build, but the **Spotify developer policy** governs the planned source (§III.5 and others). Not used here, and recorded in `docs/research/04-spotify.md`.
- **Community catalogue terms.** Not reviewed. Recorded as F5 in the baseline
  audit.

The project treats these as open risks. It does not claim compliance with any of
them.
