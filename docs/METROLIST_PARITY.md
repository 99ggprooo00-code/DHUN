# Metrolist parity audit and word-sync slice

Audit: 2026-10-15. DHUN baseline `5107252`; reference
[Metrolist-test](https://github.com/99ggprooo00-code/Metrolist-test) main
`d99fd126631d45ebc32495725d858bade304b6f1`. README confirms maintenance mode.
Reviewed README, lyrics model, parsing/romanization helpers, translation helper,
provider interface and registry, and provider source files. Behavior reference only;
no reference code copied or new dependencies introduced. Existing license notices
and attribution remain unchanged.

Status is intentionally not a claim that all acceptance requirements are met.
The user subsequently prioritized detailed word-by-word synchronization.

| Feature | DHUN status | Plan / implemented slice | Privacy, licensing / API risk | Tests / acceptance |
|---|---|---|---|---|
| Lyrics cache; YTM, LRCLIB, LRCLIB search, KuGou | Present | Preserve repository and SQLDelight cache | Existing external requests disclose track metadata; lyrics rights are separate from API code licensing | Existing repository/source fake-client tests |
| Persisted source priority/order, synced-first, provider picker | Present | Preserve settings and full-player control | No new endpoints | Existing settings tests; not a match picker |
| Per-song match search and persistent choice | Missing | Future vertical slice: candidate source adapter, track-keyed choice storage, cancellable VM state and search dialog | Only approved documented search endpoints; manual choice must survive cache clearing | Candidate details, persistence, stale track/cancellation tests still needed |
| Line LRC, follow and line seeking | Present | Preserve manual-scroll pause / resume and smooth centering | Local only | Existing parser and LyricsFollowTest |
| Enhanced-LRC word timestamps | Partial → implemented slice | Preserve UTF-16 text ranges, times and explicit final end markers; repeated-line time shifts; cache round-trip at millisecond precision | Local parsing; no inferred timing advertised as real | EnhancedLrcTest; DB repository recreation test |
| Word karaoke and seeking | Implemented slice, device acceptance pending | Highlight each timed segment as playback reaches it; emphasize current segment; tap word to seek, accessible line-seek fallback. One shaped Text paragraph for RTL/joined scripts | Local only; does not manufacture timestamps for plain lyrics | KaraokeTextTest covers boundaries, backward seek, inactive/plain fallback, logical text hit testing |
| Overlapping vocals | Partial | Explicit enhanced-LRC end markers allow multiple active lines; agent/background metadata and TTML are not implemented | Do not infer singer identities | Overlap/end boundary tests; multi-singer device test pending |
| TTML, offset/resync | Missing | Secure bounded XML parser with DTD/external entities disabled; persisted runtime offset and safe seek mapping | XML entity and input-size risks; never use regex as XML parser | Malformed/XML security and offset tests needed |
| Romanization | Missing | Separate persisted toggle/override; platform transliteration service with off-thread bounded cache | Verify script-specific quality and library/data licenses; no translation label | Mixed scripts, punctuation, unknown language and immutable-original tests needed |
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
remain intact. Future adapters require dated terms/privacy review and reachable
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
  TTML, per-word gradient sweep and resync controls remain outstanding.
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
