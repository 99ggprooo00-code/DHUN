# 44 — Post-#219 visual QA: reported defects, root causes, fixes

Baseline: `7840e9d372e7114eee9a7ca64bdc85250557df36` (merge of PR #219).
Fix PR: **#221**, branch `arena/957ea001-dhun`.

## Environment and visual evidence

| Environment | Result | Notes |
|---|---|---|
| Physical Android device | **NOT TESTED** | No device was available to me. The initial defect observations came from the user's report, not my device run. |
| Android emulator | **NOT TESTED** | No SDK or `/dev/kvm` in this environment. |
| Interactive Windows app | **NOT TESTED** | CI builds an MSI, but I did not launch or drive the installed app. |
| CI Compose/Skia frames | **TESTED — renderer only** | Frames are real Compose `ImageComposeScene` renders, not fabricated or hand-edited captures. The signed Actions artifact host is blocked, but the workflow publishes a Git-blob manifest; I fetched the PNG blobs through `api.github.com` and verified their SHA-256 hashes. |
| JVM/Robolectric tests | **CI only** | No local JDK/Gradle, so no tests were run locally. |
| Code inspection | **TESTED** | Used to trace each defect to its root cause. |

On current code head `717186d`, Visual evidence run `42352928738` rendered **64 images, 6 tests, 0 failures**; manifest blob `e7b75b64d6881a24289ff9767d4cc75e23e1ece0`. I fetched the images through the manifest, verified each selected PNG's SHA-256, and inspected them directly:

- `app-home-no-blur-bright-light.png` — colored artwork remains visible as a soft backdrop under the Home content; headings and controls remain legible. At alpha 0.44 it is subdued, but not a flat smear.
- `app-home-no-blur-black-artwork-light.png` / `app-home-no-blur-white-artwork-light.png` — black artwork becomes veiled warm gray; white artwork blends into the light base. At `(200,190)`, current-frame pixels were no-art `(246,244,241)`, bright-art `(245,243,240)`, black `(198,195,192)`, and white `(243,241,238)`.
- `app-player-related-light.png` — Related rows have a distinguishable fill and hairline on the sheet without a second blur/card layer.
- `app-home-pagination-recovered-dark.png` — after two transient failures the recovered shelf appears while the list remains at the end.

These are CI-renderer observations, **not Android-device acceptance**. The `09b418c` run caught a test-fixture mismatch: the isolated scene chose a light Material palette while `DhunColors.background` still read DHUN's process-default dark tokens. The test now sets the actual DHUN light appearance and checks for a warm-white base. The deterministic production-layer pixel test passed on Linux and Windows on `717186d`; no production alpha or veil change was made in response to the fixture failure.

## QA matrix

Statuses are restricted to PASS / FAIL / PARTIAL / NOT TESTED / FIXED. Environments are named explicitly; CI Compose/Skia is not a device.

| Area | Environment tested | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| Home / For You endless scrolling | Code inspection; `HomeScrollRetryTest`; Compose `HomeScreen` integration in CI | Near-end scrolling keeps paginating; transient continuation failures self-heal without an endless request loop | The scroll and footer paths originally shared an error guard. After the first fix, the near-end `snapshotFlow` still emitted `true` only once; fast state transitions could be conflated. A completion revision now re-arms it. CI integration rendered two transient failures followed by a successful continuation while still at the end. | FIXED | `HomeViewModel.paginationRevision`; `HomeScreen` trigger; `HomeScrollRetryTest`; frame `app-home-pagination-recovered-dark.png` on run `42352928738` |
| Home: stale served session | Code inspection + JVM test (`StaleSessionRetryTest`) | Rotated WEB_REMIX build recovers without app restart | A stale scraped `clientVersion` caused HTTP 400 for continuations while page 1 still rendered. One forced re-discovery + replay now recovers; still-rejected requests remain visible. | FIXED | `InnerTubeClient.postJson`; `StaleSessionRetryTest` |
| Home: retry budget | JVM test + Compose integration | Recover from transient outages but stop when service is dead | Two automatic retries after the initial page request (three page requests total) are bounded. Only `Network`, `RateLimited` and `Unknown` failures are ambient-retry eligible; `Parse`, `AuthRequired` and `Unavailable` stay explicit. This is a conservative default, not a field-measured optimum. | FIXED | `MAX_AUTO_RETRIES = 2`; `HomeScrollRetryTest.theAutomaticRetryBudgetIsBounded`; `nonTransientContinuationErrorsWaitForAnExplicitRetry` |
| Home: stale/repeated category scroll position | Code inspection | Switching mood starts at the top | Each mood has its own keyed `LazyListState`. | FIXED | `HomeScreen` `key(selectedMood)` |
| Search keyboard after navigation | Code inspection + JVM test (`SearchKeyboardPolicyTest`) | IME closes when user leaves Search | Search is deliberately retained; previously nothing released its text-input focus when tab/detail/player navigation hid it. Focus is now dismissed on those transitions. | FIXED | `SearchKeyboardPolicy`; `DismissSearchKeyboard` in `DhunAppShell` |
| Multi-artist byline | Code inspection + JVM test (`ArtistCreditsTest`) | Each credited artist opens their own page | `artistName` was joined text paired with only the first `artistId`. The model/UI now keeps a destination per linked credit and refuses unsafe partial splits. | FIXED | `Track.artists`; `ArtistCredits.resolve`; player artist row |
| Inline lyric translations | Code inspection + JVM test (`LyricsTranslationTest`) | Translation is on its own secondary line | Inline bilingual LRC and Kugou's separate `contentts` document now populate `LyricsLine.translation`; player renders a separate type rung. | FIXED | `LrcParser.splitInlineTranslation`; `KuGouLyricsSource`; codec v3; `SyncedLyrics` |
| Translation survives cache | JVM test (`RepositoriesTest`, `TtmlRolesTest`) | Save/load preserves vocal roles and background flags | The first codec-v3 implementation gated `agent`/`background` on v2 and dropped roles. Decode now accepts both v2 and v3. | FIXED | `SyncedLyricsCodec` regression tests |
| Related Songs surface | Code inspection + CI-rendered Compose frame, visually inspected | Rows read as part of the sheet | Previously bare rows on the sheet veil; now match the queue-row translucent fill and hairline edge. The light Related frame reads as a distinct surface in CI. | FIXED | `RelatedRow`; `app-player-related-light.png` |
| Light-mode backdrop, blur off | Contrast math; deterministic production-layer pixel test on Linux and Windows; CI Compose/Skia frames visually inspected | Artwork visible while direct text stays readable | Current head passes the black/white pixel bounds. The frame shows soft, perceptible colored artwork; black art is veiled gray and white art blends into the warm base. Device-specific rendering remains unverified. | FIXED | `LIGHT_NON_BLUR_LAYER_ALPHA = 0.44`; `DhunThemeContrastTest`; `NowPlayingBackdropRenderTest`; run `42352928738` frames and manifest above |
| Light mode, rest of app | — | — | — | NOT TESTED | Out of scope; targeted backdrop fix only. |
| “About the playing song” view | — | — | — | NOT TESTED | Carry-over item; not part of these six fixes. |
| Home scroll-to-top (§2.10) | — | — | — | NOT TESTED | Carry-over item; not part of these six fixes. |
| Search playlist results (§3.5) | — | — | — | NOT TESTED | Carry-over item; not part of these six fixes. |
| Whole-playlist saving | — | — | — | NOT TESTED | Explicitly out of scope. |
| Per-surface colour customisation | — | — | — | NOT TESTED | Explicitly out of scope. |
| Artwork fallback | — | — | — | NOT TESTED | Explicitly out of scope. |
| Lyrics manual scrolling | — | — | — | NOT TESTED | Explicitly out of scope. |
| Queue | Existing JVM suite; untouched | Queue works | Existing suite passed; queue was not changed. | PASS | Existing queue tests |
| All pixel-dependent behavior on physical Android | Physical device | Verify final pixels and layout on device | No device was available. Compose frames do not substitute for this. | NOT TESTED | No fabricated device evidence. |

## Root causes and fixes

1. **Home pagination had three layers.** The initial transient error latched `pageError`, and the scroll path refused further calls. A stale cached `clientVersion` could independently make every continuation fail with HTTP 400 until restart. The first `snapshotFlow` rewrite still had a gap: its near-end Boolean remained `true` and was conflated after the first event, and Compose could skip a fast loading `true → false` transition. The current implementation increments `paginationRevision` on each completed page request and keys the trigger on it, so the near-end event is re-armed even for fast failures. Ambient retries are restricted to transient errors and bounded at two retries after the initial request.
2. **Search keyboard** — `TextInputFocusRegistry` recorded focus but navigation never released it; Search is retained so its query survives.
3. **Artists** — one joined display string was paired with one target id.
4. **Translations** — lyrics had no translation field, cache representation, or separate rendering concept.
5. **Related rows** — no row surface existed.
6. **Light backdrop** — a dark-tuned blur-off alpha was applied over a warm-white base. The light value is bounded by the real contrast gate and the same production artwork/veil layer now has a deterministic color-input pixel test.

A first version of the pixel test used Coil to load local file URIs in the app-shell scene. Linux rendered the fixture, but the Windows MSI JVM test did not; it returned the base color for the black-cover sample and caused the downstream MSI install-evidence job to lack its expected artifact. That was a **test-fixture portability failure**, not evidence of a product MSI defect. The replacement renders the production `NowPlayingBackdropLayer` with deterministic Compose colors, avoiding platform-dependent image loading. The first replacement run on `09b418c` then exposed a second test-only error: setting `DhunTheme(mode = LIGHT)` changes Material3's local scheme but not `DhunAppearance.tokens`, which `DhunColors.background` reads. The supposed light sample therefore used the process-default dark base `(22,22,22)` on both Linux and Windows; the veil pixel `(73,71,70)` exposed the mismatch. The fixture now also calls `DhunAppearance.setThemePreference("light", systemDark = false)` and asserts that the base is warm-white before evaluating the artwork pixels. This test-only correction passed on both Linux and Windows on `717186d`; no production alpha or veil change was made.

## Files changed

Production: `core/Entities.kt`, `design/DhunTypography.kt`, `design/components/NowPlayingBackdrop.kt`, `innertube/ArtistCredits.kt` (new), `BrowseParsers.kt`, `InnerTubeClient.kt`, `Parsers.kt`, `lyrics/KuGouLyricsSource.kt`, `LrcParser.kt`, `SyncedLyricsCodec.kt`, `presentation/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`, `ui/player/FullPlayer.kt`, `PlayerTabs.kt`, `SyncedLyrics.kt`, `ui/search/SearchKeyboardPolicy.kt` (new), `ui/shell/DhunAppShell.kt`.

Tests: `ArtistCreditsTest`, `StaleSessionRetryTest`, `LyricsTranslationTest`, `HomeScrollRetryTest`, `SearchKeyboardPolicyTest`, `NowPlayingBackdropRenderTest` (new), `NowPlayingBackdropPolicyTest`, `DhunThemeContrastTest`, `LyricsPresentationTest`, plus `VisualAppEvidenceTest` and `VisualEvidenceTest`.

No database migration was added: `Track.artists` is in-memory and re-derived from provider data; the legacy `artistName` / `artistId` storage is unchanged.

## Verification history and remaining unknowns

- Earlier green head `b71bc4d`: shared JVM 843 / 0 failures, Android Robolectric 169 / 0, desktop JVM 51 / 0, playback probe 4 / 0; visual evidence 62 images / 5 tests / 0 failures.
- Head `5ef5fd3`: the Compose integration test caught that fast failure only caused one request; fixed by the completion revision in `7dc878a`.
- Head `7dc878a`: Visual evidence passed at 64 images / 6 tests / 0 failures. Its Windows run exposed the Coil file-URI test portability issue above; the deterministic in-memory pixel test followed.
- Head `09b418c`: CI run `42352635315` failed on the new `NowPlayingBackdropRenderTest` fixture on Linux (shared JVM: 845 tests, 1 failure); Windows MSI run `42352635322` failed on the same assertion with the same dark-base evidence. APK build passed; MSI installed-evidence then failed downstream because MSI did not complete. The fixture-only failure is diagnosed above and was corrected on `717186d`.
- Code/test head `717186d`: both CI runs `42352928737` and `42352923723` passed. Run `42352928737` reported shared JVM 845 / 0 failures, Android Robolectric 169 / 0, playback probe 4 / 0, desktop JVM 52 / 0; zero errors or skips in those suites. Visual evidence run `42352928738` passed with 64 images / 6 tests / 0 failures (manifest blob `e7b75b64d6881a24289ff9767d4cc75e23e1ece0`); Build APK run `42352928742` passed; DHUN beta run `42352928810` passed `release_plan`, APK, MSI, and disposable Windows install-over/userdata evidence. AAB and publish jobs were skipped by their workflow conditions. The deterministic pixel test passed on Linux and Windows.
- This final QA-report sync changes documentation only; all product/test code is unchanged from `717186d`. The documentation commit's PR checks must also pass before merge.
- No local tests were run (no JDK). No Android device, emulator, or interactive Windows app was used.
- Still unverified on a real screen: OEM blur/compositing, actual field retry outcomes, large-font artist wrapping, and inline translation quality across real bilingual catalogues. Two scroll retries remains a bounded default; field telemetry is needed to optimize it.
