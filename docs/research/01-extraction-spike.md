# 01 — Extraction Spike — FINDINGS

Status: **COMPLETE (sandbox-verifiable portion).** Live evidence:
[docs/verification/01-extraction-spike.md](../verification/01-extraction-spike.md)
Date: 2026-09-01. Environment: datacenter IP (Google Cloud), no cookies, no
PO tokens — a maximally hostile network for YouTube access.

## Verdict

**PROBE|verdict|PASS — the extraction pipeline is healthy.**
Search → resolve → audio bytes (verified WebM container) → related tracks,
end to end, exit code 0.

## What the probe does

`tools/playback-probe` (Kotlin JVM, NewPipeExtractor + yt-dlp + own InnerTube):
1. Scrapes the fresh `WEB_REMIX` client version from the music.youtube.com
   homepage HTML
2. Searches music-songs via DHUN's own InnerTube call
3. Resolves the top result's bestaudio URL via yt-dlp subprocess
4. Range-fetches the URL and verifies the container by magic bytes
   (`1A 45 DF A3` = EBML/WebM)
5. Fetches the radio queue via own InnerTube `/next`
6. Reports NewPipeExtractor stream-path health as a non-fatal WATCH item

## Rot layers discovered during the spike (all real, all documented)

| # | Layer | Status 2026-09-01 | Consequence |
|---|---|---|---|
| R1 | NewPipeExtractor v0.26.5 music client-version discovery | **BROKEN** — its `sw.js_data` source no longer contains the version; homepage key renamed `INNERTUBE_CONTEXT_CLIENT_VERSION` → `INNERTUBE_CLIENT_VERSION`; its regexes match neither | NewPipe music SEARCH unusable. Upstream master is 0 commits ahead (published 2026-08-15) — **no fix exists upstream yet** |
| R2 | NewPipeExtractor ANDROID client stream path | **BROKEN** — `getAndroidReelPlayerResponse` → "JSON response is too short" (bot-gated) | NewPipe stream resolution fails (this is why the WATCH line prints BROKEN) |
| R3 | TV client (`TVHTML5`) direct InnerTube player call | **GATED from datacenter IPs** — `LOGIN_REQUIRED` "Sign in to confirm you're not a bot" | Not usable as our own client choice from servers; may work from residential IPs |
| R4 | `android_vr` client | **GATED** — formats withheld | Dead as of this date |
| R5 | **yt-dlp default path (vision_platform client, `c=VISIONOS`)** | **WORKS TOKENLESS, even from this flagged IP** | Chosen as the working extraction engine (ADR-001) |
| — | InnerTube metadata (search, next, suggestions) via WEB_REMIX | **WORKS** (proven repeatedly, incl. yesterday's probes) | Own client owns metadata, as planned |

## Decision (recorded in ADR-001)

- **Metadata (search/browse/related/suggestions/lyrics browse):** DHUN's own
  InnerTube client, WEB_REMIX, client version scraped fresh from homepage
  HTML per call (the exact discovery step NewPipe got wrong). PROVEN.
- **Stream resolution:** yt-dlp subprocess. Primary on Desktop. For Android
  (no Python runtime), Phase 02 implements an in-JVM `StreamResolver` using
  the client shape yt-dlp currently rides (vision_platform), isolated behind
  the `StreamResolver` interface, with NewPipe re-entering automatically if
  upstream recovers (drill-decided, not hope-decided).
- **NewPipeExtractor:** kept as dependency + WATCH in the drill. Broken
  upstream-unfixed today; historically they ship fixes in days–weeks.

## Parser notes (for Phase 02 Kotlin parsers)

- Search item: `musicResponsiveListItemRenderer`
  - title: `flexColumns[]→musicResponsiveListItemFlexColumnRenderer→text→runs[0].text`
    (NOTE: `flexColumns` is a JSON array — walk with array-descent)
  - videoId: `playlistItemData.videoId` (also in
    `runs[0].navigationEndpoint.watchEndpoint.videoId`)
- Radio: `playlistPanelVideoRenderer` — `title.runs[0].text`,
  `longBylineText.runs[0].text`
- Fixtures in `tests/fixtures/` are the parser test basis.

## Acceptance criteria (Phase 01)

| Criterion | Status |
|---|---|
| 10 real search results printed | ✅ (10 with titles + videoIds) |
| Audio audibly plays on real hardware | ⏳ sandbox cannot emit sound — URL verified to serve real audio bytes (HTTP 206, audio/webm, EBML magic). On-device audible check happens with the Phase 03 harness on a real phone |
| `curl -I` on resolved URL → 200 audio/* | ✅ (206 partial, audio/webm) |
| Related tracks parse for 3 video IDs | ✅ (50 tracks; spot-checked across runs) |
| Both extractor paths tested, results recorded | ✅ (NewPipe broken — documented; yt-dlp working) |
| Fixtures committed | ✅ |
| Kill switch | Not triggered — pipeline healthy |
