# KNOWN_LIMITATIONS

Updated every phase. Nothing hidden.

- Web platform intentionally absent from v1 (browser YouTube streaming is
  blocked by PO tokens/SABR for third-party apps — see
  PROBLEMS_AND_FIXES.md P7).
- Stream extraction depends on maintained upstream extractors; when YouTube
  changes, playback breaks until a patch release. The daily rot-drill CI
  detects this within 24h.
- NewPipeExtractor v0.26.5 stream extraction is broken upstream-unfixed
  (2026-09-01). Desktop streams go through yt-dlp; Android uses DHUN's
  minimal own player-endpoint client until upstream recovers (ADR-001).
- Datacenter/server IPs are bot-flagged by YouTube's player endpoint more
  aggressively than residential IPs; the rot drill may show resolve-step
  failures on CI runners that do not affect normal users. Repeated red +
  local green = investigate; both red = rot.
