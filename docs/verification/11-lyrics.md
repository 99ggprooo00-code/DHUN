# Phase 11 verification — Lyrics (LRCLIB + YTM, synced)

Status: 🟨 **CODE COMPLETE** — units tested in CI; on-hardware acceptance OPEN (needs physical run on Android + desktop, like Phases 08/09/10 checklists).

## What was built (code-level, auditable)

| Spec item (PROMPT_SEQUENCE.md Phase 11) | Implementation |
|---|---|
| `shared/lyrics` — `LyricsSource` interface, `LrcLibSource` (title+artist+duration, synced LRC), `YouTubeLyricsSource` (InnerTube browse), `LyricsRepository` = cache → YTM → LRCLIB → NotAvailable, `LrcParser` ([mm:ss.xx] + enhanced tolerated) | `shared/src/commonMain/kotlin/dev/dhun/lyrics/LrcParser.kt` — `object LrcParser { timestampRegex + wordTimingRegex, parse(lrcText, allowUnsyncedFallback) → sorted List<LyricsLine>, isSynced() }` — handles `[mm:ss.xx]` / `[mm:ss.xxx]` / `[mm:ss]`, multiple timestamps per line (`[00:10.00][00:12.00]Repeat` → 3 entries), enhanced `<mm:ss.xx>` stripped, metadata `[ti:/ar:/al:/by:/offset:/length:]` skipped, blank lines kept as `""` (UI → “♪”), sorted by `startTimeMs`; `LyricsSource.kt` — `interface LyricsSource { suspend fun fetch(track: Track): DhunResult<Lyrics> }`; `LrcLibSource.kt` — Ktor CIO `GET https://lrclib.net/api/get?artist_name=&track_name=&album_name=&duration=` (title+artist required, album/duration optional), `syncedLyrics` → `LrcParser.parse(..., allowUnsyncedFallback=false)` → `Synced`, else `plainLyrics` → `Unsynced`, 404/empty → `NotAvailable`, 429→RateLimited/5xx→Network; `YouTubeLyricsSource.kt` — `provider.getLyrics(track.id)` wrapper; `LyricsRepository.kt` — `cache.get(track.id)` → instant `Success(cached)` else `ytm.fetch` → cache + return if `Synced/Unsynced` else `lrcLib.fetch` → cache + return else `Success(NotAvailable)` (failures from either source fall through — lyrics are non-fatal; `NotAvailable` not cached); `LyricsCacheRepository.kt` + `LyricsCache.sq` + `migrations/1.sqm` (below) |
| Lyrics persisted cache (Phase 05 DB) | `shared/src/commonMain/sqldelight/dev/dhun/database/LyricsCache.sq` — `CREATE TABLE LyricsCache(trackId TEXT PRIMARY KEY, isSynced BOOLEAN, content TEXT, cachedAt INTEGER)` + `insert OR REPLACE / selectByTrackId / deleteByTrackId / clear / selectAll`; `shared/src/commonMain/sqldelight/migrations/1.sqm` — `CREATE TABLE IF NOT EXISTS LyricsCache ...` (schema v1→v2 via `DhunDatabase.Schema`); `shared/src/commonMain/kotlin/dev/dhun/data/LyricsCacheRepository.kt` — `interface LyricsCacheRepository { get/put/clear/observe }`, `SqlDelightLyricsCacheRepository(db, clock, io=Default)` — `get` re-parses synced LRC via `LrcParser` (single source of truth), `put` re-serializes `Synced` as `[mm:ss.cs]text` lines (manual pad, no `java.util.Formatter`) or stores `Unsynced` text, `NotAvailable` not stored; `shared/src/commonMain/kotlin/dev/dhun/data/DatabaseFactory.kt` — `DataLayer.lyricsCache = SqlDelightLyricsCacheRepository(db, clock)` |
| Lyrics tab in FullPlayer: active line large/bright/centered, others dim, smooth auto-scroll, tap-to-seek, unsynced scrollable, empty | `shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerTabs.kt` — `LyricsTabContent(viewModel, accent)` — `when (lyricsState)` → `Loading` (7 shimmers) / `Unavailable` (`EmptyView "No lyrics"`) / `Error` (`ErrorView` + retry `viewModel.refreshLyrics`) / `Unsynced` (`verticalScroll` + `Text` `bodyMedium` `textSecondary`) / `Synced` — `activeIndex = indexOfLast { start <= positionMs }`, `LazyColumn` `rememberLazyListState` + `LaunchedEffect(activeIndex) { animateScrollToItem((activeIndex-1).coerceAtLeast(0)) }`, `itemsIndexed` with `animateColorAsState` (`textPrimary` active vs `textTertiary` dim), `style = titleMedium` active vs `bodyMedium` dim, `textAlign = Center`, `Modifier.clickable { seekTo(startTimeMs) }` (±0s — within ±1s requirement), blank `→ "♪"` placeholder; `shared/src/commonMain/kotlin/dev/dhun/presentation/player/PlayerViewModel.kt` — `lyricsRepository: LyricsRepository? = null` (optional — fallback to `provider.getLyrics` if not wired), `loadLyrics(track)` → `lyricsRepository.getLyrics(track)` else `provider.getLyrics(track.id)` → `LyricsUiState.Synced/Unsynced/Unavailable/Error` |
| Wiring Android + Desktop (Koin) | `app-android/src/main/kotlin/dev/dhun/android/di/AppModule.kt` — `single { LrcLibSource() }`, `single { YouTubeLyricsSource(get()) }`, `single { LyricsRepository(cache = get<DataLayer>().lyricsCache, ytm = get(), lrcLib = get()) }`; `app-android/src/main/kotlin/dev/dhun/android/MainActivity.kt` — `val lyricsRepository: LyricsRepository = koin.get()` + `PlayerViewModel(..., lyricsRepository = lyricsRepository)`; `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt` — identical 3 singles + `PlayerViewModel(..., lyricsRepository = get())` |

## Player wiring (before → after)

Before Phase 11: `PlayerViewModel(player, provider, scope, persistence)` → `loadLyrics` called `provider.getLyrics(id)` (YTM unsynced only, no cache, no LRCLIB).
After: `PlayerViewModel(..., lyricsRepository)` → `lyricsRepository.getLyrics(track)` (cache → YTM → LRCLIB) with `Track` metadata for LRCLIB match; `provider.getLyrics` retained as fallback when `lyricsRepository == null` (tests/fakes).

## LRCLIB verification (code-level)

- API: `GET https://lrclib.net/api/get` with `artist_name=`, `track_name=`, optional `album_name=`, `duration=` (seconds, `Int`), keyless, CORS-friendly — matches spike.
- Matching: title+artist required (quick bail `Success(NotAvailable)` if blank), album/duration forwarded, LRCLIB does fuzzy match server-side.
- Response JSON: `syncedLyrics` (LRC string) preferred → `LrcParser` validated; `plainLyrics` → `Unsynced`; both blank/404 → `NotAvailable`; HTTP 429→`RateLimited`, 5xx→`Network`, exceptions → `Network` (repository maps both to `NotAvailable` so the UI shows empty rather than an error — lyrics are non-fatal, retry via `refreshLyrics`).

## Cache behavior (second open = instant)

- `LyricsCache` row keyed by `trackId` (YouTube videoId) — `isSynced` + `content` (raw LRC or plain text) + `cachedAt` (epoch ms).
- `Synced` re-serialized as LRC `[mm:ss.cs]text` on `put`, re-parsed on `get` via `LrcParser` (parser is single source of truth — no drift).
- `NotAvailable` is deliberately not cached (negative caching would hide future LRCLIB/YTM availability after a miss).
- `LyricsRepository.getLyrics` checks `cache.get` first (SQLDelight `executeAsOneOrNull` on `Dispatchers.Default`, ~single-digit ms) — no network on hit. `observe(trackId)` also exposed for reactive UI (unused in v1 — `loadLyrics` is pull-based).
- Verification hook: `LyricsRepository.cached(trackId)` and `clearCache()` for tests/diagnostics (“second open instant”).

## Parser coverage (unit tests — jvmTest, no network)

`shared/src/jvmTest/kotlin/dev/dhun/lyrics/LrcParserTest.kt` — 10 tests, green in CI:

- `parseMmSsXx` — `[00:12.34]Hello` → 12_340 ms (2-digit centis ×10), `[01:05.67]` → 65_670 ms
- `parseMmSsXxx` — `[00:00.500]` → 500 ms, `[00:12.345]` → 12_345 ms (3-digit millis)
- `parseMmSsNoFraction` — `[01:02]` → 62_000 ms, `[02:03]` → 123_000 ms
- `parseMultiTimestamp` — `[00:10.00][00:12.00][00:14.00]Repeat` → 3 lines sorted 10_000/12_000/14_000 same text
- `parseEnhancedStripped` — `Hello <00:05.20>world` word-timings stripped, `<` removed, both words retained, line 5_000 ms
- `parseMetadataSkipped` — `[ti:/ar:/al:/by:]` ignored, only `[00:10.00]Real line` retained
- `parseUnsyncedFallback` — 3 plain lines → `startTimeMs=null` with `allowUnsyncedFallback=true`, 0 with `false`
- `parseSorted` — out-of-order timestamps sorted
- `isSyncedHeuristic` — `[00:10.00]Hello` true, plain/metadata false
- `parseBlankLines` — `[00:10.00]` → `""` (UI placeholder “♪”) + next line

## On-hardware checklist (OPEN)

- [ ] **Install**: `./gradlew :app-android:assembleDebug` or `:app-desktop:run` — play any track → FullPlayer → Lyrics tab
- [ ] **Synced — 5 diverse tracks** (must span languages/scripts — e.g. English, Hindi, Nepali, Japanese/Korean + one long track): pick 5 tracks known to have LRCLIB synced LRC (verify at `https://lrclib.net/api/get?artist_name=&track_name=&duration=` or via search `https://lrclib.net/search`). For each: start playback → open Lyrics (should show synced list within ~1 s; first open hits network, second is instant) → verify active line is larger/brighter/centered, others dim, list auto-scrolls as the song plays (no manual scroll needed), seek via progress bar → active line jumps correctly
- [ ] **Tap-to-seek ±1s**: in Synced state, tap any non-active line → playback jumps to that line's timestamp (±1 s audible/positionMs), active highlight updates within ~300 ms, `animateScrollToItem` scrolls smoothly. Tap blank “♪” lines also seeks. Unsynced text taps do nothing (no crash)
- [ ] **LRCLIB fallback verified**: find a track where YTM has no lyrics (FullPlayer previously showed “No lyrics” from YTM alone) but LRCLIB does (e.g. indie/Nepali track not on YTM lyrics DB). Play → Lyrics should now show Synced (from LRCLIB) or Unsynced, proving `cache→YTM→LRCLIB` fallback. Kill data `cache.clear()` → replay same track → still resolves via LRCLIB (not cache)
- [ ] **Unsynced**: track with only YTM/plain LRCLIB lyrics (e.g. fresh release) → Lyrics shows scrollable plain `Text` (no timestamps, no highlight), vertical scroll works with touch/mouse, `Error` shows retry button and `refreshLyrics` re-fetches
- [ ] **Empty**: track with no lyrics on either source → `EmptyView("No lyrics", "Lyrics aren't available for this track yet.")` — no spinner, no crash. Works after airplane-mode (both sources `NotAvailable` → same empty state, not `Error`)
- [ ] **Second open = instant (cache hit)**: play track A → Lyrics loads (network, ~800 ms) → collapse FullPlayer → reopen → Lyrics shows instantly (<100 ms, no shimmer). Verify via `adb shell` / desktop log: second `getLyrics` should be `cache hit` (no LRCLIB/YTM HTTP). After `clearCache` or fresh install, first open is network again
- [ ] **Parser edge cases live**: track whose LRC has multi-timestamps, `[mm:ss]` without fraction, and enhanced `<mm:ss.xx>` (e.g. LRCLIB “enhanced” tracks) — all render as single lines, not duplicated word entries, metadata headers hidden
- [ ] **Desktop mouse**: sync scroll via wheel/trackpad, tap seeks with click, unsynced scrolls with wheel; no hover crash
- [ ] **Rotation / process death**: rotate Android while Lyrics Synced → no state loss, active line persists, scroll position retained; kill app → relaunch → same track's Lyrics still cached (DB file `dhun.db` retained)

## Known gaps (carry to KNOWN_LIMITATIONS)

- Enhanced LRC word timings (`<mm:ss.xx>`) are stripped to line granularity in v1 — word-level karaoke highlighting (per-word scroll/color) is deferred (would require `LyricsLine.words: List<WordTiming>` and a karaoke composable).
- LRCLIB match is title+artist(+album+duration) fuzzy server-side; very similar titles (covers/remixes) may return the wrong LRC — the UI shows whichever LRCLIB returns (no client-side duration re-validation beyond the `duration` param). A future pass could surface `Search` results and let the user pick.
- LRCLIB is a third-party volunteer service (rate-limited at 429 — mapped to `RateLimited`, surfaced as `NotAvailable` in v1 rather than a retry banner). YTM remains the primary for unsynced fallback on rate-limit.
- Cache stores raw LRC/plain text at centisecond precision (`[mm:ss.cs]`) — sub-centisecond timings (`[mm:ss.xxx]` where last digit ≠0) round to 10 ms on re-serialize (parser still handles 3-digit input, but cached read-back is 2-digit). Audibly negligible (<10 ms) and keeps the cache text human-readable.
- `NotAvailable` is not cached (intentional — avoids hiding future availability), so repeated opens of a genuinely lyric-less track still hit YTM+LRCLIB each time (2 HTTPs, ~600 ms, still shows empty quickly). A negative-TTL cache (e.g. 24 h) could be added if needed.

## Screenshots (to capture on hardware)

- Lyrics Synced: active line bright/large, neighbors dim, centered, with “♪” blank lines
- Tap-to-seek: before/after positionMs + active highlight jump
- LRCLIB fallback: same track, YTM-only empty → after Phase 11 shows Synced (prove via `lrclib.net/api/get` URL log)
- Unsynced scrollable text (long plain lyrics)
- Empty state (“No lyrics”)
- Second open instant: video of collapse→reopen (no shimmer)
