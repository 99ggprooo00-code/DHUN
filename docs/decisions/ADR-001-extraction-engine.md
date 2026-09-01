# ADR-001: Extraction engine selection after the 2026-08/09 YouTube breakage wave

## Status
Accepted (2026-09-01) — supersedes the MASTER_PROMPT.md doctrine table's
"NewPipe Extractor as THE extraction engine" in the interim, until the rot
drill proves upstream recovery.

## Context
The doctrine chose NewPipeExtractor as the single extraction engine for both
platforms. During the Phase 01 spike (2026-09-01), live testing found
NewPipeExtractor v0.26.5 broken in two independent layers:

1. Music client-version discovery: its `sw.js_data` source no longer carries
   the version, and the homepage key was renamed
   (`INNERTUBE_CONTEXT_CLIENT_VERSION` → `INNERTUBE_CLIENT_VERSION`);
   v0.26.5's regexes match neither. Upstream master is 0 commits ahead —
   no fix exists on 2026-09-01.
2. Stream resolution: the ANDROID client path fails with "JSON response is
   too short" (bot gating).

Meanwhile, measured the same day, from the same hostile datacenter IP:
- DHUN's own InnerTube metadata calls (WEB_REMIX, version scraped from the
  homepage HTML): working.
- yt-dlp 2026.08.19 default path (vision_platform client): working,
  tokenless, end to end (HTTP 206, audio/webm, EBML magic verified).
- Direct TV-client and android_vr player calls: bot-gated from this IP.

Evidence: docs/research/01-extraction-spike.md,
docs/verification/01-extraction-spike.md.

## Options considered
1. **Wait for NewPipe upstream fix** — blocks Phase 02+ on someone else's
   timeline; the project has no schedule control over it.
2. **Fork/patch NewPipe locally** — maintenance surface grows by the exact
   amount the doctrine tried to avoid; patch rots.
3. **Route streams through yt-dlp everywhere** — impossible on Android
   (Python runtime), fine on Desktop.
4. **Selected: two-tier resolver behind `StreamResolver`**
   - Metadata: DHUN's own InnerTube client (WEB_REMIX, fresh-scraped client
     version). Proven working.
   - Desktop streams: yt-dlp subprocess. Proven working.
   - Android streams: in-JVM resolver using the currently-working client
     shape (vision_platform) implemented from yt-dlp's request shape —
     minimal surface: ONE endpoint (`/youtubei/v1/player`), no signature
     crypto needed while URLs arrive plain. Isolated, drill-tested.
   - NewPipeExtractor: dependency retained, health WATCHED by the drill;
     re-enters as an implementation option the moment upstream recovers.

## Consequences
- Phase 02 implements `StreamResolver` with three impls:
  `YtDlpStreamResolver` (jvm), `OwnClientStreamResolver` (both targets),
  `NewPipeStreamResolver` (watched, currently fails — kept out of the
  active chain until the drill goes green on it).
- The rot drill reports each path independently, so engine priority is
  evidence-driven, never hope-driven.
- KNOWN_LIMITATIONS.md gains: "Android stream resolution uses DHUN's own
  minimal player-endpoint client until upstream extractor recovery."

## Known risks
- The vision_platform client can break any week; mitigation is the drill +
  fast patch releases + NewPipe recovery as second engine.
- The own-client Android path is hand-rolled extraction — the thing the
  doctrine says not to do. It is limited to one endpoint, kept tiny, and
  loses its job the moment a maintained engine (NewPipe fixed, or yt-dlp
  via any future in-JVM story) tests healthy on Android.
