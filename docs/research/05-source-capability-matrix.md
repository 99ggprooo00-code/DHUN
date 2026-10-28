# 05 — Source capability, platform and evidence matrix

**Current ledger: 2026-10-28.** Supersedes this document's 2026-10-20 matrix,
which mixed upstream API possibilities with DHUN implementation using
“Supported”, omitted native audio/lyrics/artwork storage and left Spotify
Terms/SDK details unexamined. Those were not runtime acceptance records.
The original matrix is preserved in §6 below; other original dated research
remains under the historical sections of [03](03-youtube-youtube-music.md), [04](04-spotify.md) and [02](02-baseline-audit-2026-10-20.md).

**Authoritative product direction and vocabulary:** [PRD §1](../PRD.md#1-product-definition-and-source-of-truth).
DHUN is free, open-source, cross-platform and multi-source; YouTube / YouTube
Music first, Spotify Planned, implementation Deferred. Other sources require
value/feasibility/permission/maintenance/privacy/platform/licence evidence.
This ledger describes actual differences; it does not grant provider permission.

Evidence: **[R]** source inspected; **[T]** tests run with command/date in
[06](06-multi-source-claims-audit.md); **[H]** older dated hardware/runtime record,
not repeated here; **[P]** current primary provider text read, not DHUN code;
**[U]** unverified. Product direction, Implemented, Partial, Planned, Deferred,
Blocked, Research required and Unavailable have the meanings in PRD §1.

## 1. Capability matrix

### Current DHUN integrations — code and verification, not availability promises

| Capability | Native Android — YouTube Music | Native desktop JVM — YouTube Music | Browser — JioSaavn wrappers |
| --- | --- | --- | --- |
| Integration status | Implemented paths [R]; historical residential playback [H]; current hardware acceptance still open | Implemented paths [R]; Windows primary desktop target [H]; Linux/macOS hardware unverified [U] | Partial: source/unit evidence [R,T]; live catalogue and audible deployed-origin playback unverified [U] |
| Search/catalogue/browse | InnerTube [R] | InnerTube [R] | Three unofficial wrapper instances for **one catalogue family**, not three independent sources [R] |
| Playback | Own-client resolver → Media3 [R]; no user OAuth | Own-client resolver → VLCJ/system libVLC; optional user-installed yt-dlp fallback; NewPipe watch only [R] | Wrapper stream URL → HTML audio [R]; CORS/media/autoplay/service constraints; samples silent |
| Queue / repeat / shuffle | Native queue [R] | Native queue [R] | In-memory browser queue [R,T]; not a universal cross-provider queue |
| Accounts / provider writes | No provider sign-in, OAuth or provider playlist/library writes [R] | Same [R] | No provider sign-in, OAuth or provider writes [R] |
| Local likes / playlists / history | SQLDelight [R] | SQLDelight [R] | `localStorage` `dhun.web.v3`, metadata snapshots, export/import/erase [R,T] |
| Offline audio | Implemented resumable downloads + offline-first routing [R]; permission/policy unresolved | Same plus whole-track audio cache/prebuffer [R]; permission/policy unresolved | Unavailable: no audio downloads or SW audio caching [R]; normal browser cache is not a DHUN offline feature |
| Lyrics | YouTube Music/LRCLIB/KuGou configurable source order, SQLDelight cache, saved matches, local TTML/transliteration [R] | Same [R] | Unavailable: no lyric provider/request [R] |
| Equaliser | System AudioEffect binding [R]; device-dependent | libVLC ten-band [R] | Unavailable: no EQ [R] |
| Privacy controls | Settings history/recording, lyrics and platform audio-cache controls; download removal [R]; outcome feedback gaps remain | Same, with platform callback [R] | Export/import/erase library; erase **keeps settings and shell cache** [R,T] |
| YouTube in browser / JioSaavn in native | No native JioSaavn adapter | No native JioSaavn adapter | No YouTube API, extraction or IFrame integration; D2 unchanged [R] |
| Spotify (any capability) | Planned; execution Deferred; no adapter/auth/SDK/runtime [R] | Same [R] | Same project-level intent; no platform route approved [R] |

Source trace: [`MusicProvider.kt`](../../shared/src/commonMain/kotlin/dev/dhun/provider/MusicProvider.kt),
native [`Entities.kt`](../../shared/src/commonMain/kotlin/dev/dhun/core/Entities.kt),
Android [`AppModule.kt`](../../app-android/src/main/kotlin/dev/dhun/android/di/AppModule.kt),
desktop [`Main.kt`](../../app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt),
[`SettingsScreen.kt`](../../shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt),
browser [`api.js`](../../app-web/src/js/api.js), [`library.js`](../../app-web/src/js/library.js),
[`player.js`](../../app-web/src/js/player.js), [`sw.js`](../../app-web/src/sw.js).
Current commands/results are in [06](06-multi-source-claims-audit.md). Local Java
is unavailable, but the authorised PR #197 follow-up (§12) verifies source
`626d8fe` with new shared/Android/desktop/probe CI, APK/AAB compile, API-24 lint,
Windows MSI/install-over and browser/Lighthouse checks. All applicable checks
passed; no physical sound or deployed-origin live audio is inferred. Browser
UI checks use controlled network/HTTP failures, not live-provider acceptance.
A source path or unit-test result is **not** new on-device audible playback proof.

### Upstream possibilities — not implemented integrations

| Surface | Provider-documented capability [P] | DHUN status / gate |
| --- | --- | --- |
| YouTube Data API | Metadata/search/playlist resources; quota, key/project and OAuth where applicable | Research required; no official Data API path. Not a raw audio URL API. [03](03-youtube-youtube-music.md) |
| YouTube visible IFrame | Queue/load/play/pause/seek; visible player, Referer/size/branding/autoplay requirements | Proposed Watch mode only, not approved implementation; D2/current CSP remain. No hidden/background/offline-audio parity. |
| Spotify Web API | Search/metadata/user library/playlist operations, limited by access mode/scopes; player commands control a Spotify device | Planned / Deferred. Metadata ≠ remote control ≠ audio. Premium owner, five-user development limit, policy/permissions and access/scale gates. [04](04-spotify.md) |
| Spotify Web Playback SDK | Eligible Premium browser Connect playback; DRM/browser/autoplay and commercial-approval limits | Planned / Deferred; not HTML-audio URLs; third-party runtime/privacy/CSP decision required |
| Spotify Android App Remote | Controls the installed Spotify app, which owns playback/cache/network | Planned / Deferred; not Media3/direct PCM; Beta SDK and platform/account/licensing verification required |
| Spotify desktop / offline | No direct JVM/libVLC audio route established; Terms restrict copying/ripping with narrow conditional exceptions | Research required; no download authorisation or desktop route implied |

## 2. Cost, hosting, authentication and privacy

| Build / candidate | Current recipients and local data [R] | Cost / authorisation / unknowns |
| --- | --- | --- |
| Native YouTube Music | YouTube/Google: query, IDs, client version/country/language/headers, returned in-memory visitor identifiers; media/art requests. Library, queue restore, history/searches, lyrics/matches, settings in SQLDelight; persistent audio/artwork plus segment/whole-track/prebuffer caches | No DHUN backend or mandatory provider subscription in code. Unofficial extraction/download/background/metadata policy applicability and content permissions unresolved; not “free therefore permitted.” |
| Native lyrics | LRCLIB: artist/title/album/duration/manual queries and version User-Agent. KuGou: lyric queries/candidate IDs/access keys/downloads when selected. On-device ICU transliteration / local TTML sends no extra service request | API accessibility/software licences are not lyric rights. Retention and permissions unestablished; no paid translation/recognition service adopted. |
| Native diagnostics / OS integration | Local Android Logcat/playback details and desktop startup log; OS/JVM/path/stack/IDs/URLs may be present. Notifications, widgets and system media surfaces can show title/artwork | No automatic uploader found. Optional public GitHub issue submissions are user-chosen third-party processing; redact first. Settings clearing is not universal erase-all. |
| Browser JioSaavn | `saavn.dev`, `saavn.sumit.co`, `saavnx.vercel.app` receive queries/IDs; failover can contact several. `*.saavncdn.com` receives audio/art requests. API uses `credentials: omit`, `cache: no-store`. Local snapshots blank stream URLs; SW caches shell only; browser media policy separate | Three separate operators and hosting/CDN logs beyond DHUN control. JioSaavn Terms restrict unofficial/automated access; wrapper deployment permissions/retention unresolved. No support contract/uptime promise. |
| Hosting / voluntary reports | GitHub Pages receives page/app/update requests; GitHub processes issues/releases | No DHUN-operated server. Zero app telemetry does not mean host/provider has no logs. Hosting entitlement/availability not guaranteed forever. |
| Spotify — future only | No current requests or tokens. A proposed OAuth/SDK integration would disclose scopes/account/library/playback/network data and need secure token lifecycle, disconnect/delete and clear notices | Premium owner is a recurring prerequisite; eligible listener Premium and scale/commercial approval separate. Current D4 disallows new recurring costs. No account/dashboard, price, agreement, quota approval or SDK/GPL compatibility verified. |

Native deletion controls remove only their stated local stores; automatic lyric
cache clear keeps explicit song choices. Packaged desktop data is next to the
launcher (`userdata`); unpackaged OS data and temp logs may survive uninstall.
Browser erase keeps settings; origin site-data controls remove remaining local
storage/cache/registration, not third-party logs. No blanket “nothing leaves”,
“everything is deleted” or encryption guarantee is justified.

Primary evidence accessed 2026-10-28: [YouTube Terms](https://www.youtube.com/static?template=terms),
[API policies](https://developers.google.com/youtube/terms/developer-policies),
[Google Privacy](https://policies.google.com/privacy),
[JioSaavn Terms](https://www.jiosaavn.com/corporate/terms/) and
[Privacy](https://www.jiosaavn.com/corporate/privacy/),
[Spotify Policy](https://developer.spotify.com/policy) and
[Terms](https://developer.spotify.com/terms). Wrapper project
[identifies itself as unofficial](https://github.com/sumitkolhe/jiosaavn-api);
its code licence is not a content agreement. No retention schedule for each
wrapper was established; reviewing targeted clauses is not legal review.

## 3. Cross-platform compatibility

**Architecture finding: the provider seams are real, but not multi-provider-ready.**

- Native `MusicProvider` imports InnerTube `SearchFilter`, uses `videoId`/browse
  IDs; `Track`, stream resolution, lyrics/download/queue/recommendation callers
  and SQLDelight keys assume that identity. `YouTubeMusicProvider` is the only
  implementation. No capability declaration/registry/auth interface exists.
- Browser `api.js` has one normalised JioSaavn catalogue family and wrapper
  failover; entity/snapshot keys and playback URLs are not a provider-scoped
  federated model. Source failover ≠ catalogue aggregation.
- Keep native KMP/Compose and separate JS browser. Share a vocabulary/contracts
  conceptually, not a framework rewrite or forced identical providers. Media3,
  VLCJ, HTML audio, a visible IFrame, Spotify Connect and App Remote have different
  state/control/audio ownership. OAuth/security/storage differs per platform.
- Separate follow-up before any second adapter: provider-scoped stable IDs and
  user-data migration plan; capability/status/region/account restrictions;
  source-aware search/browse/queue/library/lyrics/download routing; explicit
  player/auth/lifecycle/error adapters; least-privilege scopes/token storage;
  privacy/retention/revocation; offline eligibility and permissions; fixtures and
  hardware/deployed-origin tests. D5 remains **design only**, not completed code.
- Existing Settings clear operations have incomplete pending/error semantics.
  Correct disclosures now; fix handlers in a bounded code task with failure/retry
  tests, not a broad player rewrite or silent data migration in this audit.

## 4. Risk register (provider-specific)

Judged impact/likelihood, not measured legal outcomes. Engineering mitigation
is not permission. D1–D6 and the 2026-10-28 amendment remain traceable in ADR-010.

| ID | Risk / scope | Impact / likelihood | Evidence / current boundary | Next dependency |
| --- | --- | --- | --- | --- |
| R1 | Native unofficial extraction/audio/background policy applicability | High / unresolved legal outcome | YouTube Terms and policy §§III.I.7/9/14 [P]; existing path retained without expansion | Permission/legal review; no claim of compliance |
| R2 | YouTube resolver/client changes and network gating | High / recurring | Own-client resolver + daily drill + historical residential checks [R,H] | Dated current probe/hardware results; distinguish runner gating from parser failure |
| R3 | Official YouTube API quotas unsuitable for a broad public client by default | High if adopted / usage-dependent | 100 search calls/day, 10,000 other units [P]; not implemented | Approved API/credential/quota design, no backend assumed |
| R4 | Credential or scope leakage in future official providers | High / design-dependent | No user OAuth today [R]; client IDs are not secrets, bearer/refresh tokens are | Platform threat model, secure storage, no secrets in source/logs |
| R5 | YouTube IFrame adds visible player and third-party runtime/network | Medium / certain if adopted | Current CSP/D2 prohibit silent adoption [R]; official controls exist [P] | Explicit owner decision, privacy/CSP/Referer and real-origin tests |
| R6 | Spotify §III.5 multi-service integration and §§III.7/11/13 restrictions | High / applicability unresolved | Primary Policy [P]; separation is not automatic approval | Provider clarification/permitted scope/legal review before adapter |
| R7 | Spotify Premium/account/access/scale conflicts with $0/student constraints | High / known gate | Premium owner, 5 users; extended access organisations/250k MAU criteria [P] | Owner account/funding authority later; no recurring cost now |
| R8 | Spotify endpoint, field, SDK and quota changes | Medium / recurring | February migration + July **25 client IDs**, shared developer quota [P] | Fresh recheck, endpoint contract and error tests before implementation |
| R9 | Community wrappers disappear/rate-limit/change/CORS fails | High / plausible | Failover, shape checks, errors, silent labelled samples [R,T] | Live catalogue and audible deployed-origin validation; never make tests fake success |
| R10 | JioSaavn unofficial access and wrapper/content permissions | High / unresolved legal outcome | Terms “Your Use of JioSaavn” restrict unofficial/automated interfaces [P]; no agreement found | Operator/provider permission and legal review; disclosure not cure |
| R11 | Native metadata/lyrics/artwork retention and local diagnostics privacy | High / applicability unknown | Indefinite local records vs API §III.E.4 [R,P]; paths/IDs/URLs in diagnostics | Lifecycle/rights review; diagnostic redaction; deletion feedback fix |
| R12 | Download/cache/offline rights and release licence-notice completeness | High / unresolved | Native audio storage [R]; provider terms [P]; notices not resolved/transitive SBOM | Permissions, resolved artifact/licence/NOTICE review before publication |

## 5. Unresolved items

1. Existing native and browser provider permissions/policy/legal questions; no
   lawyer approval, operator contract or adopted legal policy exists in this audit.
2. Current hardware and deployed-origin audible catalogue playback; native
   compilation cannot be inferred from static Python/JS tests.
3. Secure account/capability/identity/player architecture and migration design;
   no second native source or Spotify runtime is built.
4. Provider/operator retention/deletion, logs/redaction, distribution-notice
   completeness and legal controller/private contact remain open.
5. Spotify feasibility and owner account/recurring-cost/access decisions remain
   gates, not permission to purchase Premium or promise public support. See
   [04 current recheck](04-spotify.md) for primary links and exact unknowns.


## 6. Historical matrix — 2026-10-20 (superseded)

> Preserved verbatim below as dated research, **not current product scope,
> implemented capability, permission or a new test result**. “Supported” in the
> original table mixed upstream possibilities with code; §§1–5 above supersede
> it. Current vocabulary lives in PRD §1.

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
