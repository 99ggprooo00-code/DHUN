# 38 — Native application recovery and root-cause ledger

> **Historical record — superseded on 2026-10-31.** The owner directed a native
> rollback to the pre-PR #196 snapshot while preserving the current website.
> See [`39-pre-pr196-native-rollback.md`](39-pre-pr196-native-rollback.md).
> The R9–R12 fixes and status below describe the superseded recovery line, not
> the current app implementation or an accepted phone recovery.

**Date:** 2026-10-31

**PR:** #217 (open; not merged)

**Recovery commits:** `1158b33`, `10400ca`, `dbc3e20`, `9be284c`, `30f4a1a`, `1abbc47`, `30fb887`

**Overall:** 🔴 **new compatibility fixes await exact-head gates and Android acceptance**

This record supersedes any implication that the `c8d5dc1` package fixed the
phone's loading problem. Green compilation, packaging or JVM tests are not phone,
Windows, artwork or audible-playback acceptance.

## 1. Evidence and regression origin

- The session started from `main` at `942d2df`. The defects listed below were
  present in that baseline unless a different origin is stated.
- Authentic phone screenshots in the session workspace show a Focus `/search`
  HTTP 400, Search failure, and For You continuation failure on the supplied
  `c8d5dc1` build. They are evidence of behaviour on that package, not proof of
  the upstream trigger.
- PRs #215/#216 and the background/colour portion of #217 were inspected. Their
  diffs did not establish a change to the shared artwork extraction/loader or the
  Search continuation implementation. They are therefore **not** blamed for these
  defects. PR #216 backgrounds and PR #217 blur/dynamic-colour corrections are
  retained.
- Earlier Home live probes passed for sampled US and Nepal responses. Those probes
  did not reproduce the phone and cannot establish that Android, Nepal, a visitor
  ID or YouTube alone caused its rejection.
- New sanitized CI probes (`41178495022`, `41178809006`) compared anonymous Search
  requests without cookies or credentials. US requests for All, old Songs and
  current Songs params all returned HTTP 200; the exact Focus query with `gl=NP`
  also returned HTTP 200 for all three. Basic and persistent `/next` requests in
  US and NP returned 19 `playlistPanelVideoRenderer` entries and one radio
  continuation. This disproves “old params always fail” and “the panel renderer
  was renamed”; it does **not** reproduce or negate the affected phone's failures.
  The second probe's JVM summary did not run because its relative output path was
  wrong, so no parser/client conclusion is taken from that run.
- Corrected probe `41179034524` then passed its JVM step. Captured old/current
  Search responses parsed 20 songs in US and 30 in NP; basic/persistent/NP `/next`
  responses parsed 19 tracks and a continuation. DHUN's actual `InnerTubeClient`
  with `country=NP` returned 30 Focus songs and 19 Related tracks plus a token.
  This is real-service evidence for the corrected code on CI, not affected-phone
  acceptance or proof that the phone's original trigger has been identified.
- Exact first-introducing commits are not asserted without evidence. “Baseline
  defect” below means present at `942d2df`, not “introduced by #215/#216/#217.”

## 2. Defect ledger

### R1 — Anonymous request context and bounded HTTP 400 recovery

**Severity:** Critical. **Status:** 🟡 code-corrected; phone trigger unproven.

- **Behaviour / expectation:** the rejected phone could not load Search/moods or
  continue Home. Anonymous requests should retain the served WEB_REMIX client
  context and fail visibly only after a bounded recovery attempt.
- **Confirmed implementation cause:** request bodies could replace rather than
  merge the served context, visitor alignment was incomplete, and generic metadata
  HTTP 400 handling had no single fresh-session retry. This is a compatibility
  defect; it is **not** proof that it was the sole cause of the phone's 400.
- **Origin:** baseline request/session architecture; the rejected `c8d5dc1` Home
  HTML fallback did not repair Search or continuation.
- **Fix:** `1158b33` in `InnerTubeClient.kt` merges served context, aligns visitor
  data, and permits one bounded fresh-session retry for eligible metadata 400s.
  Parse errors and repeated rejections remain failures; there is no infinite retry.
- **Tests:** `InnerTubeRequestTest.kt` covers served-context merging, visitor
  alignment, Search retry and repeated-400 termination. `HomeHtmlParserTest.kt`
  retains real-response extraction coverage. Android verification is still needed.

### R2 — Home categories shared continuation state

**Severity:** High. **Status:** 🟡 implemented and JVM-covered; phone-open.

- **Behaviour / expectation:** For You, Focus, Chill, Workout and Party must keep
  their own real results and bounded continuation. A category must never silently
  display another category or For You after a failure.
- **Cause:** category requests and continuation shared mutable cursor/job state;
  late pages could belong to an older selection, repeated cursors could be
  requested again, and category scroll positions were not independently retained.
- **Origin:** baseline `HomeViewModel` pagination design, not the recent colour PRs.
- **Fix:** `dbc3e20` gives each category its own feed/cursor/generation/job state,
  rejects stale pages, deduplicates tracks, stops repeated cursors, exposes real
  Retry, and retains per-category scroll state in `HomeScreen.kt`.
- **Tests:** `HomePaginationTest.kt` covers category return, stale responses,
  duplicate prevention, empty pages, repeated cursors, failures and explicit retry.
  Mood searches remain real Search-backed category feeds, not invented content or
  a claim to expose separate provider mood pages.

### R3 — Search continuation could lose or corrupt the visible result set

**Severity:** High. **Status:** 🟡 implemented and JVM-covered; phone-open.

- **Behaviour / expectation:** initial results stay visible if a later page fails;
  retry requests that page once; old responses cannot overwrite a newer query.
- **Cause:** continuation lacked generation-scoped acceptance and a durable,
  retryable footer error; repeated tokens and duplicate rows were not bounded at
  the ViewModel boundary.
- **Origin:** baseline `SearchViewModel` continuation design.
- **Fix:** `dbc3e20` adds request generations, retained results, distinct footer
  errors, explicit Retry, deduplication and repeated-token termination. Errors keep
  sanitized endpoint diagnostics; no query, visitor ID or credential is logged.
- **Tests:** `SearchViewModelTest.kt` covers retry, deduplication, stale pages,
  repeated tokens and sanitized details. Two malformed new fixtures initially
  broke CI compilation at `dbc3e20`; the required `query` arguments were corrected
  without weakening the tests.

### R4 — Artwork succeeded in Full Player but was fragile elsewhere

**Severity:** High. **Status:** 🟡 shared code-corrected; device artwork-open.

- **Behaviour / expectation:** rows, cards, History, playlists, queue and Mini
  Player should use real source artwork with the same recovery policy as Full
  Player; no fake or random image is acceptable.
- **Cause:** extraction preferred only one thumbnail shape, ordinary artwork loads
  had a narrower candidate set than Full Player, and resizing could discard the
  original source URL. The local playlist list separately ignored its model cover
  (R5); that is not claimed to explain every artwork failure.
- **Origin:** baseline shared parser/loader path. Inspection did not establish
  #215/#216 as the cause.
- **Fix:** `10400ca` unifies thumbnail extraction in `Parsers.kt` /
  `BrowseParsers.kt`, retains original sources in `ArtworkUrls.kt`, and uses bounded
  real candidates plus genuine video-cover fallback in `ArtworkImage.kt`.
- **Tests:** artwork extraction/candidate regressions cover alternate response
  shapes, URL recovery and valid fallback IDs. Coil/network decoding and visible
  results still require device verification.

### R5 — Local playlist covers were ignored or depended on the first track

**Severity:** Medium. **Status:** 🟡 implemented and repository-covered; UI-open.

- **Behaviour / expectation:** a saved playlist shows a real stored/derived cover,
  including older rows with sparse artwork, and follows reorder/removal without a
  destructive migration.
- **Cause:** `PlaylistRow` ignored `playlist.thumbnailUrl`; local detail used only
  the first track's artwork rather than the ordered first nonblank cover.
- **Origin:** baseline Library/detail implementation.
- **Fix:** `9be284c` routes Library through shared `ArtworkImage` and adds an
  ordered SQLDelight cover query with stored-cover fallback. No schema migration or
  preference/data reset was introduced.
- **Tests:** `RepositoriesTest.kt` covers sparse artwork, reorder and deleting the
  current cover track. `LibraryDownloadsViewModelTest.kt` retains saved local
  artwork priority for downloads.

### R6 — Remote playlists exposed only their first page

**Severity:** High. **Status:** 🟡 implemented and JVM-covered; real service/device-open.

- **Behaviour / expectation:** long remote playlists continue with real tracks,
  preserve existing rows on failure, retry explicitly and terminate safely.
- **Cause:** playlist parsing/model/provider exposed tracks but no continuation
  cursor or `/browse` continuation request; the screen had no paging state.
- **Origin:** baseline remote playlist implementation.
- **Fix:** `9be284c` adds scoped shelf parsing, `/browse` continuation, sparse-row
  cover inheritance, generation-scoped paging, deduplication, retryable footer UI,
  and repeated-cursor/empty-page termination.
- **Tests:** `PlaylistPaginationParserTest.kt` and
  `BrowseViewModelTest.remotePlaylistContinuationRetriesAndDeduplicates` cover the
  wrappers, cursor ownership, retry, duplicate prevention and termination.

### R7 — Search Videos could never populate the list its UI reads

**Severity:** High. **Status:** 🟡 implemented and JVM-covered; phone-open.

- **Behaviour / expectation:** Videos shows video results and later pages remain
  videos; Songs/All retain their existing behaviour.
- **Cause:** `parseSearchResults` allocated `videos` but put every video-ID row in
  `songs`; continuation also lost the selected filter at the provider boundary.
- **Origin:** baseline parser/provider contract.
- **Fix:** `30f4a1a` passes `SearchFilter` into initial and continuation parsing and
  stores the filter with the Search request generation.
- **Tests:** `ArtworkExtractionTest.videoFilterPopulatesTheListReadByTheVideosTab`
  and `SearchViewModelTest.videoFilterIsPreservedForContinuation` (including two
  appended video pages and no leakage into Songs).

### R8 — Removing one recent search could escape as an unhandled coroutine failure

**Severity:** Medium. **Status:** 🟡 source-corrected.

- **Behaviour / expectation:** a persistence failure is understandable and
  retryable, not an escaped exception or false success.
- **Cause:** single-row deletion used an unguarded `scope.launch` unlike the other
  native mutations.
- **Fix:** `30f4a1a` uses the existing `MutationTracker` and shell error reporting.
- **Tests:** the tracker has failure/cancellation/retry coverage. No Android
  database-fault injection was performed for this specific row.

### R9 — Filtered Search still reported HTTP 400 on the accepted-test phone

**Severity:** Critical. **Status:** 🟡 bounded compatibility implemented; phone-open.

- **Behaviour / expectation:** For You can load while Focus/Chill/Workout/Party and
  Search report `HTTP 400 from /search; INVALID_ARGUMENT; badRequest`. Every real
  Search type must retain its own results, details, history and continuation.
- **Cause and limits:** `1158b33`'s fresh-session retry did not recover the phone.
  DHUN's filtered params and current upstream params differ, but sanitized live CI
  accepted both in US and NP, so stale params are a compatibility candidate, not a
  proven device root cause. No region, visitor or server-only conclusion is made.
- **Files:** `shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt`;
  regressions in `shared/src/jvmTest/kotlin/dev/dhun/innertube/InnerTubeRequestTest.kt`.
- **Fix:** `30fb887` keeps the existing params first. Only a filtered `/search`
  HTTP 400 tries the current upstream params for the **same** filter, retaining one
  bounded fresh-session retry. It never drops the filter, substitutes unfiltered
  content, fabricates rows or hides a final failure.
- **Tests:** `InnerTubeRequestTest` pins old→current same-type recovery, real
  filtered fixture parsing, distinct values for every filter, no unfiltered
  fallback, and termination after old/current/fresh-current attempts.

### R10 — A newly downloaded song lacked blurred artwork outside Full Player

**Severity:** High. **Status:** 🟡 shared artwork path corrected; device-open.

- **Behaviour / expectation:** Full Player artwork already works. Outside it, an
  older downloaded song with valid local art can work while a newly downloaded
  song has no blurred cover. Both must use real artwork without regressing local
  files or the established ambient fallback.
- **Confirmed implementation causes:** `FileDownloadManager.fetchArtwork` returned
  immediately for a missing remote URL and otherwise tried only that one URL, so a
  new row could remain `localArtworkPath=null`. Separately, `NowPlayingBackdrop`
  made one direct `AsyncImage` request while Full Player used shared bounded
  candidates and a genuine video-cover fallback.
- **Files:** `download/FileDownloadManager.kt`, `design/components/ArtworkImage.kt`,
  `design/components/NowPlayingBackdrop.kt`, `design/ArtworkUrls.kt` and
  `ui/shell/DhunAppShell.kt` under `shared/src/commonMain/kotlin/dev/dhun/`;
  download/policy regressions under `shared/src/jvmTest/kotlin/dev/dhun/`.
- **Fix:** `1abbc47` uses `ArtworkUrls.loadingCandidates` in both the download
  writer and shell backdrop. Downloads persist the first real candidate, including
  `i.ytimg.com` video art; the backdrop reuses `ArtworkImage` with terminal load
  callbacks. Final failure still reveals the ambient surface, not a giant glyph.
- **Tests:** `FileDownloadManagerTest` covers failed remote candidates and a null
  remote URL, both persist the genuine video cover. `NowPlayingBackdropPolicyTest`
  covers a sparse track with only a valid video fallback. Existing local-art
  priority tests remain unchanged. Coil decoding and visible blur need Android.

### R11 — Background Brightness did not affect customized solid surfaces

**Severity:** High. **Status:** 🟡 shared policy corrected; visual phone-open.

- **Behaviour / expectation:** changing Background Brightness must visibly adjust
  the background even when a custom surface colour is used without artwork, while
  preserving the selected colour, independent blur/dynamic switches and contrast.
- **Confirmed cause:** brightness reached `NowPlayingBackdropPolicy.veilStops`, but
  only the artwork veil consumed it. `AmbientBackdrop`'s colour-only/custom palette
  path had no brightness input.
- **Files:** `shared/src/commonMain/kotlin/dev/dhun/design/components/NowPlayingBackdrop.kt`
  (`AmbientBackdrop` / `AmbientBackdropPolicy`) and
  `shared/src/jvmTest/kotlin/dev/dhun/design/GlassSystemTest.kt`.
- **Fix:** `1abbc47` adds a shared bounded relative overlay to `AmbientBackdrop`.
  The neutral value 55 is transparent (preserving PR #216's established palette);
  lower values darken and higher values lighten without rewriting the chosen base
  or bloom hues. The existing artwork veil remains independent.
- **Tests:** `GlassSystemTest` verifies a transparent neutral setting, clamped
  bounded overlays, and decreasing/increasing channels over a customized colour.
  Exact visual contrast and slider response still require device inspection.

### R12 — Related was empty for every song while the queue worked

**Severity:** Critical. **Status:** 🟡 current watch contract implemented; phone-open.

- **Behaviour / expectation:** Related must show real applicable recommendations;
  tapping them and seamless radio must retain queue, continuation and explicit
  filtering behaviour.
- **Cause and limits:** the ViewModel/provider path returns Empty only after a
  successful page has no tracks other than the current one; failures use Error.
  DHUN still sent the legacy implicit `/next` panel request, omitting current
  persistent-panel/audio/tuner fields. Live responses still use
  `playlistPanelVideoRenderer`, so a renderer rename is not a supported cause.
  The precise reason the affected phone receives/parses an empty page is unproven.
- **Files:** request change in `shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt`;
  unchanged contracts in `shared/src/commonMain/kotlin/dev/dhun/innertube/Parsers.kt`
  and `shared/src/commonMain/kotlin/dev/dhun/presentation/player/PlayerViewModel.kt`;
  request/fixture regression in
  `shared/src/jvmTest/kotlin/dev/dhun/innertube/InnerTubeRequestTest.kt`.
- **Fix:** `30fb887` adds the current persistent audio-panel fields to the shared
  radio request. It deliberately leaves the working parser, continuation endpoint,
  ViewModel queue operations and explicit-content filters unchanged.
- **Tests:** `InnerTubeRequestTest` asserts every new request field and parses the
  real radio fixture with multiple tracks and a continuation. Existing queue/radio
  refill and explicit-filter tests remain in the shared suite. Android acceptance
  must confirm actual rows and seamless playback rather than infer them from CI.

## 3. Playback, queue, persistence and visual audit

No new playback-engine root cause was established in this pass, so the playback
stack was not rewritten. Existing queue occurrence handling, move/remove,
repeat/shuffle, radio refill, now-playing restore, history, downloads, settings
rollback, lyrics and navigation tests were retained and run by shared CI. This is
not new audible-playback evidence.

PR #216/#217 appearance behaviour, one palette pipeline, four component colours,
seven quick colours/HSL More colours, independent Background Blur/Dynamic Colours,
and existing preferences/database data remain in scope. Light-mode stacking,
Settings readability and general/Full Player responsiveness still require visual
phone acceptance; source changes are not recorded as visual success.

## 4. Verification record

### Local source gates

- `PYTHONPATH=/tmp/dhun-testdeps python -m pytest scripts -q --ignore=scripts/test_version.py --deselect=scripts/test_test_version.py::test_version`
  → **518 passed, 159 subtests passed**. The one deselection is an imported helper
  function named `test_version`, not an application test.
- `python scripts/validate_fixtures.py` → **40 JSON fixture files valid (syntax
  only; Kotlin tests not executed)**.
- `git diff --check` → clean.
- No local JDK/Android SDK, emulator, phone/ADB, Windows session or audible output
  is available in the sandbox.

### CI history during recovery

| Head | Result | Meaning |
| --- | --- | --- |
| `1158b33` | ✅ CI `41172115882` / `41172111843`, Build `41172115904`, test-release `41172115862` | Request-context changes passed their automated gates; not phone proof |
| `10400ca` | ✅ Build `41172880172`, CI `41172880204` / `41172874435`; test-release `41172880191` cancelled | Artwork code passed available gates; Windows/device artwork not accepted |
| `dbc3e20` | 🔴 CI `41173236235` / `41173232254`, test-release `41173236366`; Build `41173236267` passed | Two new Search test fixtures omitted `query`; packaging did not run the failing tests |
| `9be284c` | 🔴 CI `41173612519` / `41173616208`: 855 shared tests, 1 failed; test-release `41173616216` failed its Windows JVM step; Build `41173616245` passed | Playlist fixture expected a different artist string than its own helper supplied; production and other regressions ran |
| `30f4a1a` | ✅ CI `41173851125` / `41173854396`, Build `41173854404`, test-release `41173854389` | Shared/Android/Desktop tests, Android builds/lint, Windows JVM and MSI/install-over checks passed; publication skipped; no device claim |
| `2a6038d` | ✅ CI `41174466874` / `41174578447`, Build `41174578491`, test-release `41174578467`; 🔴 phone acceptance | Automation was green, but Search 400, new-download backdrop, custom-colour brightness and empty Related remained; artifact `13031609048` is not accepted |

The fixture and compilation failures above are recorded rather than hidden. A
later green run applies only to its exact head.

### Android

🔴 The `c8d5dc1` Build artifact `13028524117` failed phone acceptance for loading
and continuation. The later `2a6038d` artifact `13031609048` was also rejected:
Search still returned HTTP 400, a new download lacked blurred artwork outside Full
Player, customized-colour brightness did not respond, and Related was empty. Neither
artifact may be presented as the fixed build. No package containing R9–R12 has been
installed or viewed by the agent. A new APK should be supplied only from a
successful Build APK run on the final reviewed head.

Simplest phone acceptance after installing that artifact's ARM64 APK:

1. Open each of For You, Focus, Chill, Workout and Party; scroll far enough to load
   another page, switch away/back, and confirm distinct real content and position.
2. Search Songs and Videos, open album/artist/playlist results, load more, and try
   aeroplane mode/Retry without losing existing results.
3. Check artwork on Home, Search, History, local and remote playlists, queue, Mini
   Player and Full Player. Download a **new** song, play it outside Full Player,
   and compare its blurred backdrop with an older download and Full Player.
4. Open Related for several songs: confirm real rows, tap one, use Play radio, and
   check that the queue continues without repeating the current song.
5. Play from History and a playlist; exercise next/previous, seek, queue move/remove,
   repeat/shuffle and a downloaded track with networking off.
6. Background/foreground and reopen the app; check queue, settings and downloads.
7. With a customized Main pages/Full Player colour, move Background Brightness to
   0, 55 and 100 with Background blur both off and on. Check visible response,
   selected-colour retention and readable text; then check lyrics, themes,
   independent blur/dynamic colours, Settings and narrow/wide layouts.

### Windows and real service

⚠️ No interactive Windows, VLC/libVLC audible playback, tray/SMTC or installer
session was available. A green test-release can establish JVM tests/package checks
only. Sanitized CI did reach the real anonymous metadata service, but it did not
reproduce the phone's Search rejection and is not Android evidence. No real-service
run from the affected phone has established that R1–R12 are fixed.

## 5. Remaining status

- ✅ Confirmed implementation corrections are organized in reviewable commits on
  PR #217; local repository/fixture gates pass and playback/queue/persistence tests
  were not weakened.
- 🔴 Both previously supplied phone packages are rejected for this recovery. Search
  and Related remain critical until the affected phone accepts a new package.
- 🟡 R9–R12 compatibility/source fixes must pass CI, Build APK and test-release on
  their exact final head before another artifact is offered; green automation will
  still not be phone acceptance.
- ⚠️ Android, interactive Windows, visible artwork/brightness/Related and audible
  playback acceptance remain explicit user/environment gates. Live CI evidence is
  useful but did not reproduce the phone's failures.

PR #217 remains open and unmerged. No release, tag, publication or protected asset
was changed.
