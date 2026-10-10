# Audit — in-app About & Legal pages

**Audit date:** 2026-10-10
**Branch:** `arena/6d488b89-dhun`
**Base commit:** `5c7bc438`
**Scope:** what had to be true before any in-app legal page could be built, and
what was found.

Every row below is labelled. **verified from source** means read from a file in
this repository at the cited location. **verified by runtime test** means a test
in this branch executes the claim. **official third-party documentation** means
taken from a vendor document. **awaiting maintainer confirmation** and
**awaiting legal review** mean no one with authority has signed it off.

Every file path and line number below was re-checked against the working tree
on 2026-10-10. An earlier draft of this document cited paths that do not exist
(`shared/.../network/InnerTubeClient.kt`, `shared/.../platform/`,
`shared/.../network/ApiPaths.kt`), wrong line numbers for `iconSize`,
`GlassCard` and `encodeRoute`, and a `predictiveBack` manifest flag that is not
present anywhere in the repository. Those were wrong and are corrected here.

This document records the audit. It does not replace
`docs/legal/AUDIT-2026-10-06-claims-and-risks.md`, which audits the release
process, or `docs/legal/AUDIT-2026-10-06-privacy-claims.md`, which audits the
privacy policy's factual claims.

---

## 1. What existed before this work

| Item | State before | Evidence | Status |
| --- | --- | --- | --- |
| In-app legal UI | **None.** No privacy, terms, licences, notices, about, support or security screen on any platform | no matching route or screen in `shared/src/commonMain/kotlin/dev/dhun/ui/shell/` | verified from source |
| External links in the UI | **None.** No `LinkAnnotation`, no `LocalUriHandler`, no `UriHandler.openUri` call anywhere | repo-wide search | verified from source |
| Canonical legal text | `legal/` did not exist | — | verified from source |
| Draft policy prose | `docs/legal/PRIVACY-DRAFT.md` (8,329 B), `docs/legal/TERMS-DRAFT.md` (3,958 B) | both read in full | verified from source |
| Third-party notices | `THIRD_PARTY.md` (3,180 B) at repo root | read in full | verified from source |
| Security policy | `SECURITY.md` (1,411 B); private reporting **not enabled** | read in full | verified from source |
| Licence texts on disk | `LICENSE` = GPL-3.0 (35,149 B). `LICENSES/` holds **Apache-2.0.txt only** | `ls LICENSES` | verified from source |
| Version metadata | Android `versionName "1.00.001"` / `versionCode 6`; desktop `dhunInstallerVersion "1.0.6"`; web `package.json` `"0.0.0"` | `app-android/build.gradle.kts:19-20`, `app-desktop/build.gradle.kts:12`, `app-web/package.json` | verified from source |
| Website legal pages | None. `website/src/pages/` has index, support, roadmap, changelog, ui | `ls website/src/pages` | verified from source |

---

## 2. Navigation, on all three targets

Three different navigation systems, all of which had to be wired by hand.

| Target | Mechanism | Evidence |
| --- | --- | --- |
| Android + Desktop | one shared `AppNavState` in `shared/`; `DetailRoute` is a `sealed interface`; two panes on wide screens, single-pane otherwise | `shared/.../ui/shell/AppNavState.kt:106`, `DhunAppShell.kt` |
| Android back button | the Activity installs its own `BackHandler`; if `nav.onBack()` declines, the task goes to the background rather than finishing | `app-android/src/main/kotlin/dev/dhun/android/MainActivity.kt:198` — `BackHandler { if (!nav.onBack()) moveTaskToBack(true) }` |
| Android state restoration | `NavStatePersistence.encodeRoute` is an **exhaustive `when` over `DetailRoute`** — adding a route without updating it does not compile | `app-android/.../ui/NavStatePersistence.kt:64-72`; new branches are `AboutLegalPage -> "about-legal"` and `LegalDocumentPage -> "legal:${id}"` |
| Desktop | same `shared` shell; no separate nav state | `app-desktop/src/main/kotlin/dev/dhun/desktop/Main.kt` |
| Web SPA | a plain array in `NavState.detailStack`, `push` / `popDetail` / `selectTab` | `app-web/src/js/nav.js` |
| Shared back semantics | `AppNavState.popDetail()`; the platform shell installs the handler and the doc comment says so | `shared/.../ui/shell/AppNavState.kt:149`, comment at `:54` |
| Web routing | hash routes read from `window.location.hash`; `applyHashRoute` maps hash → stack | `app-web/src/js/main.js` |

**Why hash routes on the web.** A hash route needs no server support: the
browser resolves it against the one static document that is always served, so
`#/legal/privacy` works on a direct load, a refresh and a pasted link under any
mount path, including `/DHUN/`.

A path route would depend on the host instead. `app-web/tools/serve.mjs:70-80`
returns `index.html` with HTTP 200 for any unknown path, so a path route *looks*
fine in local development. GitHub Pages has no such fallback: the deployed
artifact is a static tree whose `website/dist/404.html` is the Pages 404 page, so
`/DHUN/legal/privacy` would be served that page.

**Not verified by live test.** This sandbox has no route to `github.io`, so the
Pages behaviour above is inferred from the artifact layout and the presence of
`404.html`, not from an HTTP request against the deployed site. The local 200
*was* measured:

```
/legal/privacy        200      <- dev-server SPA fallback, tools/serve.mjs
/#/legal/privacy      200      <- static document; the browser resolves the hash
```

Those two lines do not compare like with like. The first is a property of
`serve.mjs`.

---

## 3. Design system available to the screens

| Token / component | Value | Evidence |
| --- | --- | --- |
| `DhunSpacing.legalContentMaxWidth` | `680.dp` — added by this work | `shared/.../design/DhunSpacing.kt:51` |
| `DhunSpacing.iconSize` | `24.dp` | `shared/.../design/DhunSpacing.kt:122` |
| `GlassCard` | content lambda is `@Composable BoxScope.() -> Unit`, **not** `ColumnScope` — so a legal body needs its own inner `Column`. The *bottom-sheet* composable in the same file does take `ColumnScope`, at line 140; the two are easy to confuse | `shared/.../design/components/GlassCard.kt:63` (glass), `:140` (sheet) |
| Frosted surfaces | `Brush.linearGradient` with `onSurface.copy(alpha = …)`; no `Modifier.blur` | `shared/.../design/Gradients.kt` |
| `Modifier.verticalScroll` | requires `androidx.compose.foundation.rememberScrollState` + `verticalScroll`, both absent from `AboutLegalScreen.kt` before this work | verified from source |
| Web spacing | `app-web/src/css/tokens.css`; `scripts/test_app_web.py` forbids raw hex colours and px values outside it | verified from source |
| Reduced motion | Compose: `LocalInspectionMode` gate in `DhunAppShell.kt:71-87`. Web: no transition or animation is declared in the legal block at all | verified from source |

Two web constraints shaped the implementation and are enforced by
`scripts/test_app_web.py`:

- `app-web/src/index.html` carries a strict CSP — `default-src 'none'`, no
  `connect-src` beyond self, YouTube and lrclib. A legal page **cannot fetch its
  Markdown at runtime**; the text has to be in the bundle. That is why the
  generator emits a JS module alongside the Kotlin one.
- The build is a byte-for-byte copy (`app-web/tools/build.mjs`), so what is in
  `src/` is what ships.

---

## 4. Network behaviour, re-verified

Re-checked because a privacy policy is only honest if it matches the traffic.

**Outbound, real:**

| Destination | Purpose | Evidence |
| --- | --- | --- |
| `music.youtube.com`, `www.youtube.com` | InnerTube API for search, browse, playback | `shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt` |
| `lrclib.net` | synced lyrics | `shared/src/commonMain/kotlin/dev/dhun/lyrics/LyricsRepository.kt` |
| artwork CDNs | album/artist artwork at the URLs InnerTube returns | `shared/.../repository/impl/LibraryRepositoryImpl.kt` |
| `picsum.photos` | sample artwork in the **design catalog screen only** — not on any user-facing surface | `shared/.../design/catalog/ComponentCatalogScreen.kt:241` |

**Not outbound**, despite looking like it:

| String | What it actually is | Evidence |
| --- | --- | --- |
| `videolan.org` | error-message text telling the user to install VLC | `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/player/DesktopDhunPlayer.kt:176,262,486` |
| `reddit.com` | a `thirdPartyEmbedUrl` header value sent to YouTube; the comment at line 437 explains it is what yt-dlp sends | `shared/.../innertube/InnerTubeClient.kt:612`, comment at `:437` |

**Android manifest** (`app-android/src/main/AndroidManifest.xml`):

- permissions: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE_DATA_SYNC`
- `allowBackup="false"`, `hasFragileUserData="false"`,
  `usesCleartextTraffic="false"`
- no deep-link intent filters
- `DhunPlaybackService` `exported="true"`; `DhunDownloadService`
  `exported="false"`

**File writes:** offline downloads and their `.part` temp files under the app's
external files dir; the SQLDelight database; DataStore preferences; the audio
player's own cache.

---

## 5. Privacy controls that actually exist

Each was traced to the code that performs the deletion.

| Control | UI | What it does | Evidence |
| --- | --- | --- | --- |
| Clear downloads | Library screen | under a mutation lock: cancels every worker and joins them, then deletes audio, artwork and both deterministic `.part` paths per row, then `repository.clearAll()` | `LibraryViewModel.clearDownloads():392` → `FileDownloadManager.clearAll():112-134` |
| Clear playback history | Library screen | `historyQueries.deleteAll()`; surfaces "Could not clear playback history" on failure | `LibraryViewModel.clearHistory():471`; message at `LibraryScreen.kt:1524` |
| Clear recent searches | Search screen | `recentSearchQueries.deleteAll()` | `SearchScreen.kt:141` → `SearchViewModel.clearAllRecentSearches():132` → `SqlDelightRepositories.kt:314` |

All three are destructive and none is undoable. A test in
`scripts/test_legal_content.py` forbids the Support page from naming a control
that has no implementation, so this table cannot silently drift.

**Gaps found, and deliberately not papered over:**

- `LyricsRepository.clearCache()` exists at `shared/.../lyrics/LyricsRepository.kt:87` with **no UI at all**. Not listed as a user control.
- There is no control for the audio player's own cache.
- **Nothing about any of this is in Settings.** All three controls live on
  Library and Search. The Support page says where each one is.
- There is **no account system**, therefore no account-deletion control. None
  was added: inventing one to make the policy look complete would be worse than
  its absence.

**One live discrepancy:** `SettingsKeys.LYRICS_ENABLED` is written but has **no
reader** — only `SettingsKeys.kt:29-30,67`, a comment at
`SettingsViewModel.kt:32`, and `RepositoriesTest.kt:175-177`. Meanwhile
`PlayerViewModel.loadLyrics` (line 562) fires from line 187 on every
`currentTrack` change, ungated. So lyrics are fetched whether or not the toggle
is on. Disclosed in `legal/privacy.md` §4.1 rather than quietly fixed.

---

## 6. Content-accuracy labels

The seven pages use a three-valued status vocabulary, deliberately not
`draft`/`final`:

Read from the `status:` field of each canonical page, not from memory — an
earlier draft of this table had the assignment almost exactly backwards.

| Value | Meaning | Pages |
| --- | --- | --- |
| `draft` | not signed off; the in-app page **says so on screen** | `privacy`, `terms`, `support` |
| `current` | the operative description of this project today | `about` |
| `reference` | not a policy statement at all — a licence or notice text | `open-source-licenses`, `third-party-notices`, `security-reporting` |

`scripts/test_legal_content.py::test_the_status_vocabulary_is_pinned` asserts
this exact mapping, so the table above cannot drift from the front matter.

Status is **per page**, not global, and that is load-bearing: `support` is
`draft` because there is no support commitment to make, while `about` is
`current` because describing what the app is needs no sign-off. A single
project-wide flag would have had to call all seven the same thing — either
over-claiming on the policies or needlessly hedging the about page.

Two specific things the text refuses to claim, on instruction:

- no affiliation, endorsement or authorisation by YouTube or Google
- no statement that DHUN complies with all laws or with platform policies

And one thing it refuses to imply: clearing local history deletes records held by
YouTube or Google. It does not, and the text says so.

---

## 7. What is **not** true and is labelled as such in-app

| Claim in the app | Label shown | Reason |
| --- | --- | --- |
| Private security reporting channel | "awaiting maintainer confirmation" | GitHub private vulnerability reporting is not enabled on this repository |
| Privacy contact address | "contact not yet published" placeholder | no contact exists; inventing an email or a jurisdiction would be a fabricated fact |
| Privacy Policy | "Draft — not legal advice" | not reviewed by a lawyer or the maintainer |
| Terms of Use | "Draft — not legal advice" | ditto; also relies on a YouTube ToS page dated 15 December 2023 that was **not re-fetched** for this work |
| Web app version | "not applicable" | `app-web/package.json` says `0.0.0`, which is a module version, not a release version |

---

## 8. Open decisions, unchanged by this work

- Android `1.00.001` vs desktop `1.00.006` vs web `0.0.0` — three different
  version numbers for one product. Each in-app About page shows its own
  platform's number. **Awaiting maintainer decision.**
- Website legal pages are **out of scope for this branch.** Adding a public
  route touches `scripts/website_quality.py` (hardcoded `ROUTES` at line 114,
  `PAGES` at line 154) and `website/budget-baseline.json`, which is keyed per
  route. That is a governed change to the honesty contract and would publish an
  unreviewed DRAFT. Documented follow-up, not done silently.
- `website/dist` auto-deploys on merge to `main`. Nothing in this branch changes
  it, but it means a merge publishes.

---

## 9. What could not be verified in this sandbox

Stated plainly rather than omitted.

| Not verifiable here | Why |
| --- | --- |
| Kotlin compilation of `shared/`, `app-android/`, `app-desktop/` | no JVM installed (`command -v java` returns nothing; `/usr/lib/jvm` absent) |
| Gradle build, Android `assembleDebug`, desktop `jvmTest` | same |
| Windows MSI packaging | no Windows, no PowerShell |
| `LocalUriHandler` actually opening a browser | needs a real device or window |
| GitHub Pages 404 behaviour for a path route | no route to `github.io` from this sandbox |
| The two Markdown parsers against each other | one needs a JVM, the other needs Node, and CI runs them in separate steps |

The last one is worth being explicit about: the Kotlin and JS parsers were
written as mirrors and both are tested, but **they were never executed against
each other**. Both suites pin the same invariants over the same bundled bytes
instead, and the bundled bytes are digest-checked on both sides. That is strong
evidence of agreement. It is not the same thing as having run them side by side.

CI (`.github/workflows/ci.yml`, ubuntu-latest, Temurin 17) covers the Kotlin
side: python unittest → pwsh syntax check → `:shared:jvmTest` →
`:app-android:testDebugUnitTest` → `assembleDebug` → `bundleDebug` →
`:app-android:lintDebug` → `:shared:lintDebug` →
`:tools:playback-probe:compileKotlin` → `:tools:playback-probe:test` →
`:app-desktop:compileKotlinJvm` → `:app-desktop:jvmTest`.

---

## 10. Version metadata (nothing may be hardcoded)

| Platform | Source | Value at `5c7bc43` |
|---|---|---|
| Android | `versionCode` / `versionName` in `app-android/build.gradle.kts` L19-20, read at runtime via `packageManager.getPackageInfo(...)` (`MainActivity.appVersionName()` L486-490) | `6` / `1.00.001` |
| Desktop | `dhunInstallerVersion` gradle property, default `1.0.6` (`app-desktop/build.gradle.kts` L12), passed as `-Ddhun.installer.version=` (L58), read by `System.getProperty("dhun.installer.version", "development")` (`Main.kt` L191) | `1.0.6` |
| Web | `app-web/package.json` `"version"` | `0.0.0` — **not an app release version** |

`grep -rn 'buildConfigField\|BuildConfig'` over `app-android`, `app-desktop`,
`shared` returns **nothing** — there is no BuildConfig channel today.
`shared` has **no Compose resources plugin** (`shared/build.gradle.kts` L1-7), and
the only `getResourceAsStream` uses are in `jvmTest`. So there is no existing
bundled-asset mechanism common to Android + Desktop.

The Android and desktop version numbers **do not match each other**
(`1.00.001` vs `1.0.6`). That is a maintainer decision, not something this work
may paper over: the About page shows each platform's own value with its own label.

---

## 11. Build and serve under `/DHUN/`

- `website/` is Eleventy 3.1.6 → `website/dist/`, **committed** (drift check in
  `.github/workflows/website.yml` L141). `DHUN_SITE_PATH_PREFIX=/DHUN` is set only
  for the Pages artifact build (L189-193).
- `app-web/` is deployed at `/DHUN/app/`: `node tools/build.mjs` copies `src/` →
  `dist/` byte-for-byte (no bundler), then the workflow copies it into
  `pages-deploy/app/` (website.yml L198-204). Its files reference each other
  relatively, so they work under any mount path.
- **Route resolution on refresh:** covered in §2 above, including what was and
  was not measured. Short version: hash routing needs no server support, which is
  why these pages use it.
- **CSP on the web preview** (`app-web/src/index.html` L12-15):
  `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline';
  img-src 'self' data:; media-src 'self' https:; connect-src 'self'
  https://music.youtube.com https://lrclib.net; base-uri 'none'; form-action 'none'`.
  Same-origin module scripts are allowed; a runtime fetch of remote legal text is
  not (and would break offline).
- `scripts/test_app_web.py` additionally forbids raw hex outside `tokens.css`
  (L77), raw `px` outside `tokens.css` (L84), and raw `px` in inline styles (L109).

---

## 12. Third-party requirements reviewed

| Document | Status |
|---|---|
| YouTube Terms of Service | **Awaiting legal review.** The page fetched 2026-10-10 was dated 15 Dec 2023 (`docs/legal/TERMS-DRAFT.md` §3 quotes restrictions 01, 03, 09). Not re-fetched in this session — the sandbox has no access to youtube.com. Do not treat the 2023 text as current. |
| Google Privacy Policy | Not reviewed. No access in this sandbox. |
| lrclib.net | The app calls `https://lrclib.net/api/get` with title/artist/album/duration (`lyrics/LrcLibSource.kt`). Its own privacy terms were not fetched. |
| GPL-3.0 (`LICENSE`) | Present, 35,149 bytes. Requires that recipients get the licence text — that is what the Open-Source Licenses page ships. |
| Apache-2.0 (`LICENSES/MaterialDesignIcons-Apache-2.0.txt`) | Present, 11,357 bytes. §4 requires the NOTICE/attribution be retained. |

---

## 13. Test baselines measured on `5c7bc43` before any edit

| Command | Result |
|---|---|
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | **Ran 343 tests … OK** |
| `cd app-web && npm test` | **pass 91, fail 0** |
| `website/` | `node_modules` absent; `npm ci` added 145 packages |

Not runnable in this sandbox (no JDK, no Android SDK, no display —
`command -v java` → nothing, `/usr/lib/jvm` absent): Gradle, Android build,
Windows MSI, PowerShell, Compose UI tests.
