# Metrolist parity audit and lyrics slices

Audit: 2026-10-15. DHUN baseline `5107252`; reference
[Metrolist-test](https://github.com/99ggprooo00-code/Metrolist-test) main
`d99fd126631d45ebc32495725d858bade304b6f1`. README confirms maintenance mode.
Reviewed README, lyrics model, parsing/romanization helpers, translation helper,
provider interface and registry, and provider source files. Behavior reference only;
no reference code copied. The later romanization slice adds ICU4J with its complete upstream notices. Existing license notices
and attribution remain unchanged.

Status is intentionally not a claim that all acceptance requirements are met.
The user subsequently prioritized detailed word-by-word synchronization.

| Feature | DHUN status | Plan / implemented slice | Privacy, licensing / API risk | Tests / acceptance |
|---|---|---|---|---|
| Lyrics cache; YTM, LRCLIB, LRCLIB search, KuGou | Present | Preserve repository and SQLDelight cache | Existing external requests disclose track metadata; lyrics rights are separate from API code licensing | Existing repository/source fake-client tests |
| Persisted source priority/order, synced-first, provider picker | Present | Preserve settings and full-player control | No new endpoints | Existing settings tests; not a match picker |
| Per-song match search and persistent choice | Implemented slice; device/live-service acceptance pending | Separate player match dialog, manual LRCLIB query, bounded candidate fan-out, track-keyed persisted choice and automatic reset; not the global provider picker | Existing endpoints only; saved lyrics remain copyrighted; title/artist metadata not verified by legacy adapters is explicitly labeled | LyricsMatchTest: fake-client candidates, failures, timeouts, cancellation, track changes, persistence, malformed/oversize choices; SQLDelight recreation test |
| Line LRC, follow and line seeking | Present | Preserve manual-scroll pause / resume and smooth centering | Local only | Existing parser and LyricsFollowTest |
| Enhanced-LRC word timestamps | Partial → implemented slice | Preserve UTF-16 text ranges, times and explicit final end markers; repeated-line time shifts; cache round-trip at millisecond precision | Local parsing; no inferred timing advertised as real | EnhancedLrcTest; DB repository recreation test |
| Word karaoke and seeking | Implemented slice, device acceptance pending | Highlight each timed segment as playback reaches it; emphasize current segment; tap word to seek, accessible line-seek fallback. One shaped Text paragraph for RTL/joined scripts | Local only; does not manufacture timestamps for plain lyrics | KaraokeTextTest covers boundaries, backward seek, inactive/plain fallback, logical text hit testing |
| Overlapping vocals | Partial | Explicit enhanced-LRC end markers allow multiple active lines; agent/background metadata and TTML are not implemented | Do not infer singer identities | Overlap/end boundary tests; multi-singer device test pending |
| Per-song timing/resync | Implemented slice, device acceptance pending | Earlier/later in 100 ms increments and reset; persisted per-track via SettingsRepository; ±10 s bound; inverse seek correction | Local only, originals unchanged | LyricsTimingTest: persistence, track isolation, bounds, malformed settings, display/seek inversion |
| TTML | Missing | Secure bounded XML parser with DTD/external entities disabled | XML entity and input-size risks; never use regex as XML parser | Malformed/XML security tests needed |
| Romanization | Implemented slice; device/language-quality acceptance pending | Separate persisted player toggle and override; original + secondary line; worker-dispatched ICU4J engine and source/mode-keyed bounded cache | On-device only, no API/credentials; full Unicode/ICU notices bundled. Conservative auto leaves Han/Cyrillic ambiguous. Japanese kana only, no Kanji pronunciation guesses | Real ICU mode fixtures, mixed/punctuation/unknown text, caching, worker/cancellation/failure, controller persistence/source guards and Android API 24/35 smoke tests |
| Opt-in translation | Missing | Provider abstraction, explicit request consent, target setting, cancellation/rate limits and hash-keyed cache; secure platform credential store before any keyed integration | External disclosure/retention, lyrics copyright, Android Keystore/desktop credential facilities; no credentials in settings or URLs | Fake provider/privacy tests and platform credential acceptance needed |
| Personalized Quick Picks | Partial | Home already has remote Quick Picks and recommendations; local-history personalization needs a separate transparent ranking slice | Prefer on-device history, with clear/reset controls | Ranking/history state tests needed; do not duplicate existing shelves |
| Sleep timer | Present | Preserve PlayerViewModel runtime timer and Home/shell controls | Local only | Existing playback/state suites |
| Skip silence, normalization, tempo/pitch, audio crossfade | Missing in inspected player/settings APIs | Platform audio capability audit and actual engine consumers before adding settings | UI/artwork Crossfade is not audio crossfade; consistent engine behavior required | Audio-engine/device acceptance needed |
| Android home-screen widget | Present | Preserve transport and theme integration | Inherently Android-specific | Existing widget transport/palette/intent tests |
| Local playlists / library / history | Present | Preserve repositories and UI | Local data controls remain | Existing repository/library tests |
| Playlist import / remote library sync | Partial (browse/local library exist; import/sync not established) | Audit account/auth and conflict semantics before implementing | Account scopes, deletion conflicts, service policy | Import fixtures, sync conflict/persistence tests needed |
| User-triggered recognition | Missing | Permission-gated capture only after choosing a documented authorized provider | Microphone/audio disclosure; no always-on capture or undocumented Shazam integration | Permission denial, stop/cancel, no-background-recording tests needed |
| Last.fm | Missing | Explicit account linking, opt-in scrobbling, pause/clear; secure credential abstraction first | API registration and current terms required | Fake scrobble threshold/dedup/retry tests needed |
| Desktop Discord presence | Missing | Desktop-only optional IPC with explicit disclosure and opt-in | Exposes listening activity; no assumed Discord token | IPC availability/opt-out tests needed |
| Listen Together | Missing | No integration without approved backend, authentication, room access control and retention design | Reference mentions metroserver, but source existence is not operational/security approval | No fake backend or working-service claim |
| Dynamic themes/artwork accents/settings persistence | Present | Preserve unchanged | No new telemetry | Existing theme/settings suites |

## Provider review and limitations

Reference registry contains BetterLyrics, Paxsenix, LRCLIB, KuGou, LyricsPlus,
Zemer, YouTube subtitles and YouTube lyrics. Some adapters use rotating third-party
mirrors/aggregators (LyricsPlus), multiple backing origins (Zemer), or service-specific
endpoints. Code availability and the reference GPL license do **not** establish
permission to redistribute lyric content or use an endpoint.

This sandbox's outbound host allowlist excludes these live provider hosts and their
terms pages. Thus reachability, current API contracts, rate limits, retention and
content licensing could not be validated. No additional provider or scraper was
added; no live reliability claim is made. Existing source settings/order/fallback
remain intact. Existing LRCLIB `/api/search` now exposes multiple matches only on explicit user request; no new endpoint was added. The LRCLIB upstream GitHub README was reviewed and describes machine-friendly free APIs; this is not a content-rights or current terms/privacy guarantee. Future adapters require dated terms/privacy review and reachable
endpoint verification; routine playback must use bounded sequential fallback,
while explicit match search can request bounded candidate sets.

## Word synchronization details and remaining acceptance

- Enhanced markers use `<mm:ss.xxx>` before a segment, with an optional terminal
  marker. Only actual markers create word/segment timing. Untimed text stays visible.
- No splitting by spaces or Unicode characters: ranges refer to the original logical
  string, rendered as one Compose paragraph. This preserves shaping and BiDi layout.
- Invalid seconds, backward timestamps and word times before the line start degrade
  safely rather than becoming misleading karaoke timing. Untimed/plain LRC remains usable.
- Cache serialization preserves enhanced timing without schema changes and reads old
  line-only entries. Old cached lyrics whose markers were previously discarded cannot
  recover those markers: clear/refetch when testing a track with enhanced lyrics.
- Providers must actually supply enhanced timing. LRCLIB line-only content does not
  become word timed merely because the renderer supports it.
- Word emphasis follows player position ticks, not a fabricated per-character sweep.
  Explicit end markers permit overlapping active lines; vocalist/background tags,
  TTML and per-word gradient sweep remain outstanding. Per-track offset controls now correct both display and seeking without modifying source timing.
- Android + desktop acceptance: playback/seek forward and backward across boundaries;
  word tapping, TalkBack/keyboard line seek; RTL/Devanagari/emoji shaping; user scroll
  pause/resume; overlapping timed lines; restart/cache reload; long lyrics performance.

## Verification

Local website build and website rule tests passed (41 tests); afterwards removed
`app-web/dist` and ran Python helper suite sequentially: 384 tests passed.
`git diff --check` passed. Local focused Gradle invocation could not start: no JDK
or JAVA_HOME is installed. Hosted CI is required to compile/run Kotlin suites; results
are reported on the PR, not inferred from these unrelated tests. New deterministic
Kotlin tests do not contact lyric services. All broader missing features above remain
open acceptance requirements, so this slice must not be represented as full parity
or autonomously merged as if the original entire request were complete.

## Per-song match selection and bounded fallback slice

- A separate **Find lyrics matches for this song** dialog offers manual Search/Retry,
  provider/title/artist details, language when the response supplies it, synced status,
  word-timed status and preview. Unknown language is not guessed. Legacy source results
  identify their metadata as requested/unverified rather than pretending it is a confirmed match.
- LRCLIB search returns up to 40 records, including different recordings for manual choice.
  Automatic LRCLIB fallback retains conservative title/artist/duration filtering.
  Other existing adapters retry the current track; they do not pretend to search arbitrary text.
- Choices use bounded versioned payloads in SettingsRepository, keyed by track ID, ahead
  of the evictable cache. They retain enhanced timing through restart/repository recreation.
  Clearing the cache does not clear a song choice; **Use automatic** explicitly removes it.
- Provider registry adapters retain stable IDs and persisted source order/preference. Each
  provider request has a 5 s coroutine deadline. Normal retrieval is sequential and short-circuits
  on success (up to four enabled adapters / 20 s worst case); candidate fan-out happens only
  after explicit Search. Synced-first and unsynced fallback remain unchanged.
- Parent cancellation now propagates through LRCLIB and cache access/writes; stale matches
  and stale lyric state cannot replace the new track. Track becoming null cancels lyric loading.
  Controller generations guard search, retries and selecting while navigation changes.
- New deterministic coverage: candidates, metadata/null JSON, alternate manual matches,
  short-circuit playback, partial provider failures, timeouts, cancellation, saved timing,
  corrupt/oversize choices, reset and controller track changes. SQLDelight settings recreation
  verifies that choices survive outside fake stores.
- Repeated local verification: website build + 41 rule tests passed, then removed app-web/dist
  and ran all 384 Python tests successfully. Local Gradle remains unavailable (no JDK).
  Hosted CI results for this slice must be checked on the current PR head.
- Device acceptance still needed: picker scrolling/input/accessibility, selecting and displaying
  an enhanced result, navigation/restart, automatic reset and failure/retry states. Live service
  reachability, rate limits and terms cannot be guaranteed by fake-client tests.

## On-device romanization slice

Implements Metrolist-like optional secondary romanization without copying its code.
Both Android and desktop use ICU4J 76.1 for consistent transform data, not newer-only
Android ICU APIs. This adds a sizable runtime/data dependency; review APK/MSI size
and low-memory performance during device acceptance. Full upstream release notices
are preserved verbatim in LICENSES and generated bundled legal pages.

- Separate default-off **Romanized line** player toggle and persisted mode override.
  Original synced/unsynced lines remain visible and unmodified; timings/seeking keep
  using original text. Secondary text is never labeled translation.
- Modes: Japanese kana (Kanji unchanged), Korean, Chinese Mandarin pinyin,
  Devanagari/Gurmukhi script transliteration, Russian/Ukrainian BGN.
- Conservative auto handles kana (+ unchanged Kanji), Hangul, Devanagari and Gurmukhi.
  Han alone is ambiguous (Chinese/Japanese/etc.), and Cyrillic alone cannot identify a
  language: those require an explicit override. Unsupported/mixed ambiguous scripts,
  transform errors and oversized lines remain unchanged without duplicate secondary text.
- CPU work runs on Dispatchers.Default, checks cancellation, and is cached by source
  text + requested mode (256-entry LRU, input/output bounds). Track + exact source-line
  guards prevent late secondary text being attached to a successor or different match.
- Tests cover real transforms for all offered modes, punctuation/Latin/emoji retention,
  script ambiguity, source immutability, eviction, worker dispatch, cancellation,
  failures, saved preferences/recreation, track/source changes, and API 24/35 class use.
- Privacy text corrected to reflect the existing working lyrics switch and actual
  configured providers; local transliteration makes no new third-party request.
  Contact-honesty tests exempt only the exact vendored ICU notice block (which contains
  upstream authors' emails), while a new test requires that block verbatim exactly once.
- Local website build/41 rule tests passed; after removing app-web/dist, Python suite
  passed 385 tests. No local JDK; hosted Kotlin/Android/desktop results remain required.
- Still needs fluent-speaker review (transliteration is not pronunciation inference),
  Android/desktop glyph layout, pane scrolling, toggle/reset/restart behavior, API-floor
  device performance and package-size acceptance. TTML, translation and broader gaps
  above remain unfinished, so this is not full Metrolist parity and not merge approval.
