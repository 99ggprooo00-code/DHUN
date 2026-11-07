# 44 — Post-#219 visual QA: confirmed defects, root causes, fixes

Baseline: `7840e9d372e7114eee9a7ca64bdc85250557df36` (merge of PR #219).
Fix PR: **#221**, branch `arena/957ea001-dhun`.

## What could be tested, and from where

| Environment | Available | Notes |
|---|---|---|
| Physical Android device | **No** | No device attached to this run; the observations below are the reported ones. |
| Android emulator | **No** | No SDK, no `/dev/kvm` in the CI/agent environment. |
| Windows desktop app (interactive) | **No** | Built and packaged by CI, not launched or driven. |
| CI-rendered Compose frames | **Yes, indirectly** | `visual-evidence` renders 62 frames / 5 tests, 0 failures. The PNGs live on the Actions artifact host (`productionresultssa*.blob.core.windows.net`), which this environment cannot reach, so **no frame was visually inspected** — only its pass/fail result is reported. |
| JVM unit tests | **Yes** | `:shared:jvmTest`, `app-android` Robolectric, desktop JVM. |
| Code inspection | **Yes** | Used for every root cause below. |

Final CI on `93a9c8f`+ (PR #221 head): `shared_jvm 843 tests / 0 failures`,
`android_robolectric 169 / 0`, `desktop_jvm 51 / 0`, `playback_probe 4 / 0`.

## The QA matrix

Statuses are restricted to PASS / FAIL / PARTIAL / NOT TESTED / FIXED.
"Environment" distinguishes device, emulator, CI-rendered frame, JVM test and code inspection.

| Area | Environment tested | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| Home / For You endless scrolling | Code inspection + JVM test (`HomeScrollRetryTest`, 7 cases) | Scrolling past the end keeps loading; a transient failure self-heals | One transient continuation failure latched `pageError` and every later scroll event was refused until the footer button was used; For You is continuation-only so this reads as "endless scrolling is broken" | FIXED | `HomeViewModel.onScrollNearEnd`, `MAX_AUTO_RETRIES = 2`, reset on growth; `HomeScreen` `snapshotFlow` trigger; `HomeScrollRetryTest` |
| Home: stale served session | Code inspection + JVM test (`StaleSessionRetryTest`, 4 cases) | A rotated WEB_REMIX build recovers without an app restart | Stale scraped `clientVersion` ⇒ bare HTTP 400 ⇒ continuations fail permanently while page 1 still renders | FIXED | one forced re-discovery + replay in `InnerTubeClient.postJson`, still-rejected requests still fail |
| Home: per-category scroll position | Code inspection | Switching mood starts at the top | Filter chips inside one `LazyColumn`, so a mood switch inherited the previous category's offset | FIXED | per-mood `LazyListState` under `key(selectedMood)` |
| Search keyboard after navigation | Code inspection + JVM test (`SearchKeyboardPolicyTest`) | IME closes when the user leaves Search | Search is never disposed (its query must survive) and the Full Player is an overlay, so focus — and the IME — stayed on a screen no longer visible | FIXED | `SearchKeyboardPolicy` + `DismissSearchKeyboard` in `DhunAppShell` on tab change / detail push / player expand |
| Multi-artist byline | Code inspection + JVM test (`ArtistCreditsTest`) | Each credited artist opens its own page | `artistName` is one joined string, `artistId` only the first channel ⇒ a collaboration always opened one artist | FIXED | `Track.artists` + `ArtistCredits.resolve`; one clickable credit per linked channel; refuses to split when fewer than two channels are linked |
| Inline translations | Code inspection + JVM test (`LyricsTranslationTest`) | Translation sits on its own line under the lyric | Secondary text was rendered on the lyric's own baseline | FIXED | `LrcParser.splitInlineTranslation`, `LyricsLine.translation`, kugou `contentts` overlay, codec v3, 3-rung type ladder |
| Translation survives cache | JVM test (`RepositoriesTest`, `TtmlRolesTest`) | A saved-then-loaded lyric is identical | **Regression introduced by the first version of this fix**: v3 decode gated `agent`/`background` on `version == "2"` and silently dropped every vocal role | FIXED | decode now accepts roles for v2 **and** v3 |
| Related Songs surface | Code inspection + CI-rendered frames | Rows read as part of the sheet | Bare rows on the sheet veil, unlike the queue rows beside them | FIXED | queue-row translucent fill + `glassEdge` hairline on `RelatedRow` |
| Light-mode backdrop (blur off) | Code inspection + contrast math + JVM test | Artwork visible and text readable | The dark-tuned 40% layer over the warm-white base reads as a pale smear | FIXED | `LIGHT_NON_BLUR_LAYER_ALPHA = 0.44`, derived from the tertiary-text gate (holds to 0.46, fails from 0.50); pinned by `DhunThemeContrastTest` |
| Light mode, rest of the app | — | — | — | NOT TESTED | out of scope by instruction (targeted backdrop fix only) |
| Whole-playlist saving | — | — | — | NOT TESTED | out of scope by instruction |
| Per-surface colour customisation | — | — | — | NOT TESTED | out of scope by instruction |
| Artwork fallback | — | — | — | NOT TESTED | out of scope by instruction |
| Lyrics manual scrolling | — | — | — | NOT TESTED | out of scope by instruction |
| Queue | JVM test (pre-existing, green) | Works | Works | PASS | existing suite, untouched |
| Every row above, on a real screen | Physical device | — | — | NOT TESTED | no device or emulator available; CI frames rendered but the artifact host is unreachable from the agent |

## Root causes

1. **Home** — the scroll trigger and the footer retry shared one guard, so a
   single transient failure was latched indefinitely; on For You (continuation-only)
   that is indistinguishable from broken pagination. Separately, a rotated
   `clientVersion` produced HTTP 400 for every continuation until restart.
2. **Search keyboard** — `TextInputFocusRegistry` records focus but nothing ever
   released it, and Search is deliberately never disposed.
3. **Artists** — a joined display string with a single id target.
4. **Translations** — no translation concept existed in the model, codec or UI.
5. **Related rows** — they simply never had a surface.
6. **Light mode** — a dark-tuned constant applied to a light base.

## Files changed

`core/Entities.kt`, `design/DhunTypography.kt`,
`design/components/NowPlayingBackdrop.kt`, `innertube/ArtistCredits.kt` (new),
`innertube/BrowseParsers.kt`, `innertube/InnerTubeClient.kt`, `innertube/Parsers.kt`,
`lyrics/KuGouLyricsSource.kt`, `lyrics/LrcParser.kt`, `lyrics/SyncedLyricsCodec.kt`,
`presentation/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`,
`ui/player/FullPlayer.kt`, `ui/player/PlayerTabs.kt`, `ui/player/SyncedLyrics.kt`,
`ui/search/SearchKeyboardPolicy.kt` (new), `ui/shell/DhunAppShell.kt`,
plus tests: `ArtistCreditsTest`, `LyricsTranslationTest`, `HomeScrollRetryTest`,
`SearchKeyboardPolicyTest`, `StaleSessionRetryTest`, `NowPlayingBackdropPolicyTest`,
`LyricsPresentationTest`, `DhunThemeContrastTest`.

No database migration was added: `Track.artists` is an in-memory field re-derived
from the provider response, and the legacy `artistName`/`artistId` columns are
unchanged.

## Tests actually run

CI only (no local JDK). The five failures CI reported during development were all
real and are recorded here because they shaped the fix:

1. `ArtistCredits.linkedArtistIds` returned `List<String>` from nullable ids — compile error.
2. `assertEquals` could not infer `T` over a nullable token list — test compile error.
3. Codec v3 dropped vocal roles (regression, above).
4. Light layer at 0.58 broke the tertiary-text gate (`secondary 4.11:1`).
5. Two test premises were wrong: a JSON-only "homepage" fixture the scraper cannot
   read, and an inverted light/dark veil assertion.

## Remaining unverified

- Every visual judgement on a real screen: whether the Related fill reads right
  over bright artwork, whether 0.44 looks correct as well as measuring correct,
  whether the artist credit row wraps acceptably at large font sizes, and whether
  inline translation splitting produces good text on real bilingual catalogues.
- Whether the Home self-heal budget of 2 is the right number in the field.
