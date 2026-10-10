# Audit — in-app Privacy / Legal / About / Support pages

Session branch `arena/6d488b89-dhun`, base `origin/main` = `5c7bc43` (PR #155 merge).
Every line below was read from the tree at that commit in this session unless it is
labelled otherwise. Nothing here is a legal conclusion.

## 1. Navigation

| Platform | Mechanism | File |
|---|---|---|
| Android | `ComponentActivity` + `setContent`; `remember { NavStatePersistence.restore(lastSavedState) }` then `BackHandler { if (!nav.onBack()) moveTaskToBack(true) }`. **No Jetpack NavHost.** | `app-android/src/main/kotlin/dev/dhun/android/MainActivity.kt` L194-196 |
| Desktop | Compose Desktop `Window` + `remember { AppNavState() }`; `Key.Escape` → `nav.onBack()`. No NavHost. | `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt` L521, L539 |
| Web | vanilla SPA, hash routing: `window.addEventListener("hashchange", …)`, `applyHashRoute()` matches `#/artist|album|playlist/<id>` and `#/settings` | `app-web/src/js/main.js` L853-862 |

**Both app platforms share one navigator**: `shared/src/commonMain/kotlin/dev/dhun/ui/shell/AppNavState.kt`
— `detailStack: List<DetailRoute>`, `push()` / `popDetail()` / `closeTop()` / `popTab()` /
`onBack() = closeTop() || popTab()`. `DetailRoute` is a sealed interface with
`ArtistPage`, `AlbumPage`, `PlaylistPage`, and the id-less singleton `SettingsPage`
(L12-27). Routes are dispatched by two exhaustive `when` blocks —
`ShellMasterPane` (single-pane) and `ShellDetailPane` (two-pane) in
`shared/src/commonMain/kotlin/dev/dhun/ui/shell/DhunAppShell.kt`.

`app-web/src/js/nav.js` is a deliberate line-for-line mirror of `AppNavState.kt`
(its header comment says so) and is pinned by `app-web/tests/nav.test.mjs`.

**Consequence:** a legal page is a new `DetailRoute` case, added once in shared and
rendered by both platforms. No new top-level nav destination is needed.

## 2. Reusable UI

`shared/src/commonMain/kotlin/dev/dhun/ui/settings/SettingsScreen.kt` (381 lines) —
sections `Appearance`, `Playback & storage`, `Equalizer`. Header row is
`DhunIconButton` + `DhunIcon.ArrowBack` + `headlineMedium` title. Body is
`Column(...).verticalScroll(rememberScrollState())` with
`SectionHeader(title = …)` between groups. There is **no About or Legal section**.

Design tokens/components available in `commonMain`: `DhunColors` (52 tokens incl.
`accent`, `textPrimary/Secondary/Tertiary`, `glassEdge`, `border`), `DhunSpacing`
(no raw `dp` outside `design/`), `DhunTypography` (19 styles), `DhunShapes`,
`components/{SectionHeader, DhunButton, DhunIconButton, GlassCard, Chip, Cards}`.
`GlassCard`'s doc comment bans blurring the content layer — "Content stays **sharp**".
`supportsRealtimeBlur` is an `expect val` with android/jvm actuals — the pattern to
follow for platform-specific behaviour.

## 3. Version metadata (nothing may be hardcoded)

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

## 4. Build / serve under `/DHUN/`

- `website/` is Eleventy 3.1.6 → `website/dist/`, **committed** (drift check in
  `.github/workflows/website.yml` L141). `DHUN_SITE_PATH_PREFIX=/DHUN` is set only
  for the Pages artifact build (L189-193).
- `app-web/` is deployed at `/DHUN/app/`: `node tools/build.mjs` copies `src/` →
  `dist/` byte-for-byte (no bundler), then the workflow copies it into
  `pages-deploy/app/` (website.yml L198-204). Its files reference each other
  relatively, so they work under any mount path.
- **Route resolution on refresh:** GitHub Pages serves static files only. A path
  route `/DHUN/legal/privacy` would 404. `app-web`'s existing `#/…` hash routing
  resolves on direct load, refresh and share with no server support — that is the
  mechanism used here.
- **CSP on the web preview** (`app-web/src/index.html` L12-15):
  `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline';
  img-src 'self' data:; media-src 'self' https:; connect-src 'self'
  https://music.youtube.com https://lrclib.net; base-uri 'none'; form-action 'none'`.
  Same-origin module scripts are allowed; a runtime fetch of remote legal text is
  not (and would break offline).
- `scripts/test_app_web.py` additionally forbids raw hex outside `tokens.css`
  (L77), raw `px` outside `tokens.css` (L84), and raw `px` in inline styles (L109).

## 5. Privacy facts — re-verified this session

| Claim | Result | Evidence |
|---|---|---|
| `LYRICS_ENABLED` gates lyric requests | **False — no reader exists.** | `grep` over all `.kt`: only `SettingsKeys.kt` L29-30 (definition), `SettingsViewModel.kt` L32 (comment), `RepositoriesTest.kt` L175-177 (round-trip). `PlayerViewModel.kt` L187 calls `loadLyrics(track)` unconditionally on every track change; `loadLyrics` (L562-585) has no gate. |
| Permissions | Confirmed: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE_DATA_SYNC` | `AndroidManifest.xml` L4-18 |
| `allowBackup=false` | Confirmed; also `hasFragileUserData="false"` (L23) and `usesCleartextTraffic="false"` (L24) | `AndroidManifest.xml` L22-24 |
| No deep-link intent filters | Confirmed. The four `<intent-filter>`s are MAIN/LAUNCHER (×2), `androidx.media3.session.MediaSessionService`, `android.appwidget.action.APPWIDGET_UPDATE`. No `<data android:scheme>`. | `AndroidManifest.xml` L58-121 |
| No cookie jar | Confirmed. Every `cookie` hit in non-test source is a comment saying there are none (`OwnClientStreamResolver.kt` L29, `InnerTubeClient.kt` L602, `YtDlpStreamResolver.kt` L19). No `HttpCookies` plugin. | as cited |
| Network destinations | Real outbound hosts in non-test source: `music.youtube.com` + `www.youtube.com` (`innertube/InnerTubeClient.kt`), `lrclib.net` (`lyrics/LrcLibSource.kt`), `picsum.photos` (`design/catalog/ComponentCatalogScreen.kt` L241/249/325 — dev catalogue, not in `AppTab.userTabs`). All other `https://` hits are in `jvmTest` fixtures. | as cited |

**Corrections to assumptions in the brief:**

- `www.videolan.org` in `DesktopDhunPlayer.kt` (L176, L262, L486) is **text inside
  an error message**, not a request. DHUN never contacts videolan.org.
- `www.reddit.com` in `InnerTubeClient.kt` L612 is a **header value**
  (`thirdParty.embedUrl`) sent *to YouTube*, mirroring yt-dlp. DHUN never contacts
  reddit.com.
- **New finding, not in the brief:** `DhunPlaybackService` is declared
  `android:exported="true"` (`AndroidManifest.xml` L79). Media3 session services do
  not require export; this widens the local attack surface. Reported, not changed.
- `app-android/keystores/dhun-test.p12` is a committed public test key
  (`SECURITY.md`). APK signature proves nothing about provenance.

### Privacy controls that actually exist

| Control | Where | Behaviour (read, not assumed) |
|---|---|---|
| Clear downloads | `LibraryScreen.kt` L187 → `LibraryViewModel.clearDownloads()` L395-415 → `DownloadManager.clearAll()` | Stops/joins workers, deletes audio + artwork + `.part` files, then `repository.clearAll()` (`FileDownloadManager.kt` L112-133). Failure sets `errorMessage` "Some files may remain" and **keeps the confirmation open** — it does not claim success. |
| Clear history | `LibraryScreen.kt` L201 → `LibraryViewModel.clearHistory()` L471-474 → `HistoryRepository.clear()` = `historyQueries.deleteAll()` | Suspends until persistence succeeds; UI surfaces "Could not clear playback history" on throw (`LibraryScreen.kt` L1524). |
| Clear recent searches | `SearchScreen.kt` L141 → `SearchViewModel.clearAllRecentSearches()` L132 → `recentSearchQueries.deleteAll()` | Confirmed. |
| Clear lyrics cache | `LyricsRepository.clearCache()` L87 | **Exists in code, no UI entry point found.** Not exposed, so it is not described as a user control. |
| Clear audio cache | — | **Not found.** Only a size budget (`SettingsViewModel.setCacheSizeMb`). |
| Account deletion | — | **Not applicable.** No account system anywhere in the tree. |

All three UI-facing controls sit in **Library/Search, not Settings**. Settings has
no data-clearing control at all.

## 6. Third-party requirements reviewed

| Document | Status |
|---|---|
| YouTube Terms of Service | **Awaiting legal review.** The page fetched 2026-10-10 was dated 15 Dec 2023 (`docs/legal/TERMS-DRAFT.md` §3 quotes restrictions 01, 03, 09). Not re-fetched in this session — the sandbox has no access to youtube.com. Do not treat the 2023 text as current. |
| Google Privacy Policy | Not reviewed. No access in this sandbox. |
| lrclib.net | The app calls `https://lrclib.net/api/get` with title/artist/album/duration (`lyrics/LrcLibSource.kt`). Its own privacy terms were not fetched. |
| GPL-3.0 (`LICENSE`) | Present, 35,149 bytes. Requires that recipients get the licence text — that is what the Open-Source Licenses page ships. |
| Apache-2.0 (`LICENSES/MaterialDesignIcons-Apache-2.0.txt`) | Present, 11,357 bytes. §4 requires the NOTICE/attribution be retained. |

## 7. Test baselines measured on `5c7bc43` before any edit

| Command | Result |
|---|---|
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | **Ran 343 tests … OK** |
| `cd app-web && npm test` | **pass 91, fail 0** |
| `website/` | `node_modules` absent; `npm ci` added 145 packages |

Not runnable in this sandbox (no JDK, no Android SDK, no display —
`command -v java` → nothing, `/usr/lib/jvm` absent): Gradle, Android build,
Windows MSI, PowerShell, Compose UI tests.
