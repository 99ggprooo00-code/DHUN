# 02 — Baseline and repository audit (2026-10-20)

**Scope.** What the repository was, and what was verified, before the multi-source
research and the proposed provider architecture. This is a snapshot. Its source of
truth is the repository itself at the commit below; the commands are reproducible.

**Starting state.** Branch `arena/488bcaa0-dhun`, branched from `main` at
`58b919f` (merge of PR #193). Working tree clean before this session. Git history
in the checkout is a single commit, so history-based archaeology was not possible
here; the repo's own `.ai/` logs were used for history instead.

**Companion documents.** Source research: [03 — YouTube / YouTube Music](03-youtube-youtube-music.md),
[04 — Spotify](04-spotify.md). Capability matrix, cost, privacy, cross-platform:
[05 — Source capability matrix](05-source-capability-matrix.md). Proposed decision:
[ADR-010](../decisions/ADR-010-multi-source-provider-direction.md).

---

## 1. Baseline checks actually run

Environment: Node v22.22.3, Python 3.11.2, `gh` authenticated (it answered
`gh pr list` and `gh run list`). Android and Windows toolchains are not part of
this environment (the root `package.json` says so explicitly).

| Check | Command | Result (observed) |
|---|---|---|
| Web app unit tests | `cd app-web && npm test` | 155 tests, 155 pass, 0 fail |
| Web app build | `cd app-web && node tools/build.mjs` | 29 files, 358,301 B; 20 reachable modules, 29 shell entries, all cached; icons up to date |
| Repo-level Python tests | `python3 -m unittest discover -s scripts -p 'test_*.py'` | Ran 459 tests, OK |
| Web-player doc gate | `npm run check:docs` | OK: 5 web-player documents match the code |
| Open pull requests | `gh pr list --state open` | One: #54 (PO-token / InnerTubeX research comparison, opened 2026-09-10, not merged) |
| Recent CI on `main` | `gh run list --limit 8` | CI, Build APK, test-release, website — all `success` for merge #193 |

**Not run in this session, and why:**

- `website/` build and its Playwright browser suite. Needs `npm ci` in `website/`
  and a Chromium download. Not executed here; CI's `website` workflow ran green on
  `main` for #193. Treat its result as CI evidence, not as a local run.
- Real-browser checks of `/app/` (search, play, reload, offline). Not run; no
  browser session and no live catalogue measurement were made.
- Android (`gradlew`) and Windows/desktop builds and tests. Not executable in this
  sandbox, per the root manifest's own comment.
- Live provider calls (JioSaavn instances, YouTube, Spotify). None made from this
  session except the documentation fetches listed in the research documents.

---

## 2. What the uploaded master prompt assumes, versus what the repository says

The uploaded file (`DHUN MASTER AGENT PROMPT.md`, 1,320 lines) was written against
an earlier state of the project. Its "initial working context" was explicitly
marked as something to verify. Verification found these differences:

| # | Prompt assumption | Repository evidence | Consequence |
|---|---|---|---|
| 1 | Web app is the JioSaavn-backed player, and YouTube is the intended primary source. | Web app reads JioSaavn through three community deployments (`app-web/src/js/api.js`, `API_SOURCES`). Native apps use YouTube Music through DHUN's own InnerTube client plus a stream resolver chain (`shared/.../innertube/`, `shared/.../extraction/`, ADR-001). The web build abandoned InnerTube because the endpoint refused browser player requests (comment at top of `api.js`). | "YouTube primary" is a native-app fact. On the web it is not implemented, and the prompt's Phase B does not describe the real web source. |
| 2 | Create a new `.ai/web/` documentation set. | `docs/web-player/` already holds five web-specific documents (README, ARCHITECTURE, DEVELOPMENT-GUIDE, IMPLEMENTATION-STATUS, KNOWN-LIMITATIONS). They are checked by `scripts/web_player_docs.py` on every `npm run check`. `.ai/` holds agent-operating files (roadmap, debug log, prompts). | A second web doc set in `.ai/` would duplicate and drift. Decision: extend `docs/web-player/` and keep `.ai/` for agent state. See §4. |
| 3 | "Earlier architecture docs described a static Eleventy website under `website/`." | `website/` is an Eleventy 3.1.6 static site with Playwright and axe tests, deployed with `/DHUN/` base paths by `.github/workflows/website.yml`. ADR-009 (proposed) and ADR-008 (amended 2026-10-16) govern it. | Still accurate; the site is the front door for `/app/`. |
| 4 | No DHUN backend, proxy, accounts, telemetry, third-party CDN/fonts. | Still true for the web app. CSP in `app-web/src/index.html` lists only the three catalogue hosts and `*.saavncdn.com`; `scripts/test_app_web.py` enforces it. | Constraint holds. Any YouTube IFrame or Spotify SDK path would widen the CSP; that is a decision (ADR-010 D2, D3). |
| 5 | Phase 2 requires current YouTube and Spotify research. | No repository document cites the YouTube API Services Developer Policies or the Spotify Developer Policy (grep of all `*.md` for "developer polic", "III.I", "api-services-terms", "developer-policies": no matches). Spotify appears in the code only as a stated non-feature: the website says "No import from Spotify, YouTube Music or any other service's library export" (`website/src/_data/site.js`, line 749), and `scripts/website_claims.py` (lines 94 and 118) forbids claims of Spotify import or support. No Spotify code or adapter exists. | The policy position of the native YouTube path has never been written down. See §3, finding F1. |
| 6 | Use YouTube Data API quota figures and Spotify API surface as they were. | Spotify changed its Development Mode rules in February 2026 (Premium owner, five users, reduced endpoints). YouTube's `search.list` now has its own 100-per-day bucket. Both are recorded in the research documents with primary-source links. | Any earlier planning figure for either service is stale. |

---

## 3. Evidence-based inventory

Status vocabulary: **Working** (exercised by a test or a CI gate in this repo),
**Partial** (code exists; a required part is missing or unverified),
**Missing** (no code), **Unverified** (cannot be checked from this environment).

### 3.1 Web application (`app-web/`, deployed at `/DHUN/app/`)

| Area | Status | Evidence |
|---|---|---|
| Build, module graph, shell list | Working | `node tools/build.mjs` output above; `tools/build.mjs` fails on unreachable modules |
| Catalogue search and normalisation | Working in unit tests; live behaviour Unverified | `app-web/tests/api.test.mjs`; `docs/web-player/IMPLEMENTATION-STATUS.md` "Not verified" |
| Provider failover across three instances | Working in unit tests; live Unverified | six failover tests in `api.test.mjs`; no recorded live response from the deployed page |
| Playback via one `<audio>` element | Partial | Code path exists (`player.js`, `index.html` line 85). Nobody has heard audio from the deployed origin (IMPLEMENTATION-STATUS). |
| Library, likes, playlists, history, settings (`localStorage`, key `dhun.web.v3`) | Working in unit tests; browser run in CI | `library.test.mjs`; `website/tests/browser.mjs` (`local playlist`) |
| Offline shell (service worker) | Working in CI browser test | `website/tests/browser.mjs` (`offline`); `app-web/src/sw.js` |
| Lyrics, equaliser, downloads, offline audio | Absent by design | `docs/web-player/KNOWN-LIMITATIONS.md` |
| Sample catalogue fallback | Working; labelled as sample, no audio | `app-web/src/js/demo.js`, `docs/web-player/README.md` |
| Export / import of library | Partial — code exists (`transfer.js`); no browser end-to-end check in this session | `app-web/tests/transfer.test.mjs` |
| Accessibility (axe, focus, contrast) | Working in CI browser test | `website/tests/browser.mjs` |
| Screen-reader pass | Missing | IMPLEMENTATION-STATUS |

### 3.2 Public website (`website/`, deployed at `/DHUN/`)

| Area | Status | Evidence |
|---|---|---|
| Static build, minify, CSS pruning, html-validate | Working in CI | `website/package.json`; `.github/workflows/website.yml` (green on #193) |
| Claims engine (forbidden / required caveats) | Working | `scripts/website_claims.py`, `scripts/test_website_claims.py` |
| Served-bytes smoke test | Working in CI | `scripts/website_smoke.py` |
| Source attribution and licence pages | Partial | `THIRD_PARTY.md` exists; the web app's catalogue attribution is in the in-app About and Terms pages; no legal sign-off recorded |

### 3.3 Native application (`shared/`, `app-android/`, `app-desktop/`)

| Area | Status | Evidence |
|---|---|---|
| `MusicProvider` interface and one implementation, `YouTubeMusicProvider` | Working; single implementation | `shared/src/commonMain/kotlin/dev/dhun/provider/MusicProvider.kt`. Most interface methods are required; two (`radioQueuePage`, `radioQueueContinuation`) have default implementations. There are no capability declarations. |
| Stream resolution (`StreamResolver`, `OwnClientStreamResolver`, `OfflineFirstStreamResolver`) | Working per ADR-001 and the 2026-09-01 probe; policy status unresolved | `docs/research/01-extraction-spike.md`; see finding F1 |
| Lyrics source (`YouTubeLyricsSource`, LRCLIB adapter) | Partial; content rights not established | `docs/development/provider-integration-review.md` |
| Offline downloads (ADR-006) | Implemented per ADR; device verification owned by the maintainer | `docs/decisions/ADR-006-*`, `docs/verification/` |
| Kotlin test files | 28 test-source files counted statically | `find shared app-android app-desktop -path '*test*' -name '*.kt'`; test execution not run here |

### 3.4 Repository hygiene

- `.gitignore` and `.env.example` are present. Tracked files are scanned for secret
  patterns by `scripts/test_repo_health.py::test_no_secret_patterns_outside_the_public_allowlist`,
  which passed in the 459-test run above.
- `CHANGELOG.md` (175 KB) and `.ai/ROADMAP.md` (197 KB) are very large. Trimming them
  is maintenance work, not a blocker, and was not done here.
- `.ai/` contains 14 files; `docs/` contains 9 subfolders. Duplication was checked
  for the web workstream only (§2, row 2).

---

## 4. Decision taken in this session: where the web documentation lives

The prompt asks for `.ai/web/`. The repository's own convention is:

- `docs/web-player/` — product-facing web documentation, CI-checked, already complete
  for the implemented build.
- `.ai/` — agent operating state (roadmap, debug log, autonomy rules).

**Decision:** keep web documentation in `docs/web-player/` and add only the missing
provider and policy material there or in `docs/research/`. This follows the
repository's stated rule ("Reuse existing conventions and avoid duplicate or
contradictory master prompts"). It does not create a parallel `.ai/web/` tree.

---

## 5. Findings that need a decision, not a code change

- **F1 — YouTube developer policy vs. the native extraction path (highest priority).**
  The YouTube API Services Developer Policies, §III.I.14, prohibit "use [of] any
  technology other than YouTube API Services to access or retrieve API Data,
  including to access any portion of any YouTube audiovisual content". §III.I.07
  prohibits separating or isolating audio components, and §III.I.09 prohibits
  playback from a background player. ADR-001 selects yt-dlp and DHUN's own InnerTube
  client for audio. Whether the policy binds an unofficial InnerTube client is a
  legal question the repository has not answered. See
  [03 — YouTube / YouTube Music](03-youtube-youtube-music.md) §4 and ADR-010 D1.
- **F2 — Spotify policy §III.5 vs. the multi-source goal.** Spotify's Developer Policy
  says "Do not create any product or service which is integrated with streams or
  content from another service." Read literally, a single Dhun product that combines
  Spotify with other streaming sources is prohibited. See
  [04 — Spotify](04-spotify.md) §4 and ADR-010 D3.
- **F3 — Retention limit for YouTube API data.** §III.E.4 limits storage of most
  YouTube API Data to 30 calendar days unless the data is authorised user data.
  Dhun's liked tracks, history and library store YouTube-derived metadata locally
  without expiry. Applicability depends on F1 (the policy is written for API data,
  and §III.I.14 extends it to non-API access). See 03 §4.
- **F4 — Spotify Developer Mode costs money.** Development Mode requires the app
  owner to hold an active Premium subscription. That conflicts with the $0.00
  operating budget in the prompt unless the owner already pays for Premium. See 04 §3.
- **F5 — Web catalogue terms.** The JioSaavn-via-community-wrapper approach is already
  disclosed in-product and in `docs/web-player/KNOWN-LIMITATIONS.md`. Its legal
  status is unresolved and is recorded there as an open question for the maintainer.

---

## 6. What this audit did not change

No application code, test, CI workflow, CSP, or deployed content was modified in this
session. The changes are documentation only: this file, documents 03–05, ADR-010 (proposed),
and index and roadmap entries. Every claim above is linked to a file, a command, or a
primary source.
