# 05 — Source capability matrix, cost, privacy, cross-platform, and risk register

**Date:** 2026-10-20. **Companion documents:** [02 — baseline audit](02-baseline-audit-2026-10-20.md),
[03 — YouTube / YouTube Music](03-youtube-youtube-music.md), [04 — Spotify](04-spotify.md),
decision in [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md).

**Status vocabulary (used in every cell):**

- **Supported** — the capability exists in official documentation *and* in this repository's code, with a test or measurement noted.
- **Partial** — exists with a stated limit, or only one part of the capability is available.
- **Unavailable** — the source does not offer it.
- **Not permitted** — the source's policy prohibits it, read literally. Legal review may narrow this.
- **Not verified** — not established from primary evidence in this session. Never read as a pass.
- **Not built** — documented as possible, but no code exists in this repository.

No cell in this matrix is marked as working in the live product unless it says so, and
no cell is a check mark.

---

## 1. Capability matrix

Columns: **YT-native** = current native apps (DHUN's InnerTube client and resolver chain, ADR-001).
**YT-DataAPI** = official YouTube Data API v3 from a client. **YT-IFrame** = official YouTube
IFrame Player API. **JioSaavn-web** = current web app (`app-web/`, three community instances).
**Spotify-API** = Spotify Web API. **Spotify-SDK** = Spotify Web Playback SDK.

| Capability | YT-native | YT-DataAPI | YT-IFrame | JioSaavn-web | Spotify-API | Spotify-SDK |
|---|---|---|---|---|---|---|
| Keyword search | Supported in code; live result not verified in this session (probe passed 2026-09-01 per `docs/research/01-extraction-spike.md`) | Partial — `search.list` is 100 calls/day per project by default | Unavailable | Supported in unit tests; live not verified from deployed origin | Supported; `limit` capped at 10 (Feb 2026) | Unavailable |
| Track / album / artist metadata | Supported in code (InnerTube parsers; fixtures in `tests/fixtures/`) | Not verified for music objects; the Data API returns video, channel and playlist resources | Unavailable (metadata only via player events) | Supported in unit tests (`api.test.mjs`); live not verified | Partial — popularity, available_markets, linked_from removed (Feb 2026) | Partial — current track metadata only |
| Artwork | Supported in code (InnerTube thumbnails) | Not verified | Not applicable | Supported; generic artist placeholder art is filtered out (`api.js`) | Supported with attribution and link-back (developer policy §II.4–II.5) | Partial — current track only |
| Direct in-app audio playback (`<audio>` or native player with a resolved audio URL) | **Not permitted, read literally** — policy §III.I.14 and §III.I.07. Still shipped; unresolved (ADR-010 D1). | Unavailable | Unavailable — the embed is a displayed video, not an audio element | Supported in code; a catalogue URL goes into `<audio>`. Not heard from the deployed origin. | Unavailable | Partial — Premium users only (policy §IV.1); browser SDK only; not verified inside Dhun |
| Embedded player inside the page (visible) | Not applicable | Not applicable | Supported (official) — requires a third-party script and a visible player | Not applicable | Not applicable | Not applicable (SDK creates a Connect device) |
| Background / screen-off playback | Not verified | Not applicable | **Not permitted** when the player is not displayed (policy §III.I.09) | Not verified; platform decides (`docs/web-player/KNOWN-LIMITATIONS.md`) | Not applicable | Not verified |
| Queue (in-app) | Supported in code (`PlayerViewModel`, radio queue) | Not applicable | Partial — the official introduction describes queueing functions [P]; individual function names were not read | Supported in code (`player.js`) | Partial — queue endpoints control Connect device only | Not verified |
| Seek, shuffle, repeat | Supported in code | Not applicable | Partial — official playback-control functions exist [P]; not tested | Not verified in this session | Partial — endpoints exist; Premium per endpoint not verified | Not verified |
| Account connection required | No (tokenless) | Only for user data (OAuth) | No | No | Yes — OAuth, and Development Mode owner needs Premium | Yes — Premium account |
| User library / likes / history from provider | Not built (likes and history are local) | Not built (requires OAuth) | Not applicable | Not applicable (local only) | Supported with OAuth (saved tracks, saved albums, own playlists); not built | Not applicable |
| Playlist modification on provider | Not built | Supported with OAuth (`playlists.insert`, 50 units) [P]; not built | Not applicable | Not applicable | Supported with OAuth (`PUT /playlists/{id}`, create playlist); not built | Not applicable |
| Lyrics | Partial — InnerTube browse; content rights unresolved (`provider-integration-review.md`) | Not verified | Not applicable | Unavailable (by design, `KNOWN-LIMITATIONS.md`) | Not verified | Not applicable |
| Offline download / storage of audio | Shipped per ADR-006 (Android/desktop); **policy status Not permitted, read literally** (§III.I.07, §III.E.4) | Limited — stored API Data must be refreshed or deleted within 30 days (§III.E.4) [P]; no audio is provided by the API | Not applicable — the embedded player does not provide audio to store | Unavailable (by design) | Not verified — caching and offline terms are in the Developer Terms, which were not read | Not verified — SDK terms not read in full |
| Cross-source matching | Not built | Not applicable | Not applicable | Not applicable | Not applicable | Not applicable |
| Works without a DHUN backend | Yes (resolution runs on device or in the user's own yt-dlp) | Yes, from browser with a key (**key exposure would breach §III.D.1**) | Yes (third-party script) | Yes | Yes (PKCE, client ID only) | Yes (Premium) |

**Reading the matrix.** Most "Supported" cells in the YT-native and JioSaavn-web columns mean
*code exists and has tests*, not *verified against the live service from the deployed
origin*. The repository says this itself (`docs/web-player/IMPLEMENTATION-STATUS.md`).

---

## 2. Cost, hosting, authentication and privacy

| Source | Cost to the project | Backend needed? | Authentication | Data sent to the provider | Data retained by the provider | Constraints Dhun must meet |
|---|---|---|---|---|---|---|
| YT-native | $0 in this repo's current architecture. Risk of enforcement or takedown cost is not priced. | No (on device); yt-dlp runs on desktop | None (tokenless InnerTube) | Search text, track IDs, client identity string, IP address (standard HTTP) | Not verified | Policy §III.I.14, §III.I.07, §III.E.4 — ADR-010 D1 |
| YT-DataAPI | No fee found in the quota page read [P]; quota-limited (10,000 units/day, 100 searches/day by default). Extensions via review. | No, if called from client. A shared key in a public client would be exposed, and §III.D.1 forbids embedding it in open source. | API key (must not ship) or OAuth for user data | Query, IP, referrer, key | Not verified | Store API Data max 30 days (§III.E.4) |
| YT-IFrame | No fee identified in the pages read; not verified in full [U] | No | None for playback | The player loads from `www.youtube.com`; Referer identifies the app (required-minimum-functionality) | Governed by YouTube's own privacy terms — not read | Third-party script; CSP widening; visible player; no background playback |
| JioSaavn-web | $0. Three community deployments of an MIT project, run by others. No agreement with this project. [R] | No DHUN backend (ADR-008 boundary 3) | None | Search text, IP, user agent, and the request to the catalogue host (via `connect-src`) [R: CSP] | Not verified; the wrapper's own terms were not read | Disclosed in-app and in `docs/web-player/KNOWN-LIMITATIONS.md`; legal status unresolved |
| Spotify-API | **Premium subscription for the app owner (recurring cost; price not verified)**. No API fee. | No (PKCE on the client) | OAuth 2.0 Authorization Code with PKCE [P, navigation listing of tutorials]; no secret in browser | User's OAuth token to Spotify, IP, user agent | Not verified | Disconnect and delete flow (§I.2); Dev Mode 5-user limit; quota per developer account |
| Spotify-SDK | As Spotify-API, plus the policy requirement to approve commercial use | No | As Spotify-API | Playback events to Spotify | Not verified | Premium for the listener (§IV.1); CSP changes; commercial approval |

**Information categories (for the Privacy Policy and §III.E of the YouTube policy):**

- **Stored on the device:** library, likes, history, playlists and settings in `localStorage` (web) or local storage (native). No provider content beyond metadata and artwork URLs.
- **Sent to a provider:** the search text and the identifiers needed for the request. The provider sees the visitor's network address like any HTTP server.
- **Stored by a third-party service:** not determined for any source in this session. Each provider's privacy policy must be read before a claim is made.
- **Optional user submission:** none exists in the product today. The diagnostics and issue-report paths are not part of this research.
- **Cannot be controlled or deleted by DHUN:** anything a provider keeps from a request. The Privacy Policy must say so.

---

## 3. Cross-platform compatibility

| Concern | Web (`app-web/`) | Android (`app-android/`) | Windows (`app-desktop/`) |
|---|---|---|---|
| Playback engine | One `<audio>` element | Media3 (ExoPlayer) per repository docs | libVLC per repository docs |
| Can a web-hosted player be shared? | Yes | No — native | No — native |
| OAuth redirect (Spotify) | Authorization Code + PKCE, redirect to the static origin; allowed redirect rules not verified | Custom scheme or app link; not researched | Loopback or custom scheme; not researched |
| Token storage | Must not use `localStorage` for refresh tokens without a risk note; not decided | Android Keystore (the repository's credential rules in `provider-integration-review.md` apply) | Windows user-bound protection (same rule) |
| Background playback | Browser-controlled; no guarantee (`KNOWN-LIMITATIONS.md`) | Media3 per repository docs; background behaviour not reviewed here | Application lifetime; not reviewed here |
| YouTube IFrame | Possible, with CSP changes; needs visible player | Possible in WebView with a set Referer; not built | Possible in WebView2 with a set Referer; not built |
| Storage limit | Per origin; browser-dependent | Device | Device |
| Offline shell | Service worker caches app files only (`app-web/src/sw.js`) | Not applicable | Not applicable |

**Platform-specific conclusion.** The provider *model* can be shared (capabilities,
identifiers, error types). Playback, authentication and storage cannot be shared.
Each platform's implementation must be documented separately, as the prompt requires.

---

## 4. Risk register (provider-specific)

Severity uses **H / M / L** for impact and **H / M / L** for likelihood, both judged from the
evidence cited. These are judgements, not measurements.

| ID | Risk | Source | Impact | Likelihood | Evidence | Current mitigation | Decision |
|---|---|---|---|---|---|---|---|
| R1 | YouTube policy §III.I.14 prohibits the native audio path; the project could be required to remove it or be suspended from the API | YT-native | H | Not assessed | [P] policy text; no repo analysis | None recorded | ADR-010 D1 (legal review) |
| R2 | Stream extraction breaks when YouTube changes its clients (already happened: 2026-08/09 wave, ADR-001) | YT-native | H | H | `docs/research/01-extraction-spike.md`; ADR-001 | Rot drill (`tools/playback-probe`), pinned fallbacks | Existing process |
| R3 | Data API quota exhausted by a public web client (100 searches/day default) | YT-DataAPI | H | H, if used by many users | [P] quota calculator | None — no web YouTube path exists | Keep out of web until ADR-010 D2 |
| R4 | A shared API key is exposed in a public GitHub Pages bundle | YT-DataAPI | M | M | [P] §III.D.1 | No key exists; grep clean on 2026-10-20 | Forbidden by policy; any future design uses no key in the client |
| R5 | IFrame player widens CSP and adds a third-party script, contrary to the project's privacy rule | YT-IFrame | M | Certain if adopted | [R] ADR-008 boundary 1; CSP in `index.html` | Not adopted | ADR-010 D2 |
| R6 | Spotify §III.5 prohibits a multi-source product | Spotify | H | Certain under literal reading | [P] developer policy | None | ADR-010 D3 |
| R7 | Spotify Development Mode requires a paid Premium owner account; violates the $0 budget | Spotify | M | Certain if adopted | [P] migration guide, quota-modes [S] | None | ADR-010 D4 |
| R8 | Spotify endpoint and field changes (Feb 2026, and later) break an adapter | Spotify | M | H | [P] changelog | Not applicable until built | Re-check before any build |
| R9 | JioSaavn community deployments disappear, rate-limit, or change shape | JioSaavn-web | M | H | `docs/web-player/KNOWN-LIMITATIONS.md`; three-instance failover in `api.js` | Failover, honest failure text, sample catalogue (no audio) | Existing; monitor |
| R10 | JioSaavn terms do not permit use of the catalogue | JioSaavn-web | H | Unknown | `KNOWN-LIMITATIONS.md` "not verified" | In-app disclosure, no claim of authorisation | Maintainer and counsel (open question) |
| R11 | Stored YouTube metadata kept beyond 30 days | YT-native | L–M | Certain, if §III.E.4 applies | [P] §III.E.4 | None | ADR-010 D1 (scope of the same review) |
| R12 | Offline download of audio (ADR-006) is not permitted under the policy as read | YT-native | H | Certain, if policy applies | [P] §III.I.07, §III.E.4 | Shipped; not reviewed against policy | ADR-010 D1 |

---

## 5. Unresolved items

- Whether any official YouTube Music catalogue API exists (not verified).
- Each provider's privacy policy and retention behaviour (not read for this matrix).
- Spotify native SDKs, redirect-URI rules, and per-endpoint Premium requirements (not verified).
- The IFrame player's full function and event list, and the exact script origin it loads from (only the introduction was read).
- Live behaviour of every "Supported in code" cell from the deployed origin (not measured in this session).
