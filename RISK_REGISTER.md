# RISK_REGISTER

| Risk | Likelihood | Detects via | Pre-agreed response |
|---|---|---|---|
| NewPipe Extractor breaks (PO token / SABR change) | **REALIZED 2026-09-01** — v0.26.5 broken, no upstream fix yet (see ADR-001) | probe WATCH line / rot-drill | Two-tier resolver (ADR-001): own-client + yt-dlp carry traffic; NewPipe re-enters when drill-green |
| vision_platform client (current yt-dlp path) gated | High, recurring | rot-drill red on resolve step | Follow yt-dlp's active client; patch release within 72h |
| All maintained engines broken simultaneously | Low (never yet observed) | rot-drill red on every path ≥14d | Stop-and-decide per MASTER_PROMPT kill switch |
| SMTC via JNA unstable | Medium | Phase 12 spike (3-day time-box) | Ship fallback: tray controls + media keys; record in KNOWN_LIMITATIONS.md |
| Real blur unavailable | Certain (<Android 12) | Phase 06 | Scrim+translucency fallback; note per-platform in limitations |
| Compose MP / Ktor / Coil regression | Low | CI | Pin versions; upgrade one dependency at a time |
| GPL compliance drift | Low | phase reviews | THIRD_PARTY.md updated per phase; no non-GPL-compatible deps enter |
