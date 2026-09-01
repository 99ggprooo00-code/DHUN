# RISK_REGISTER

| Risk | Likelihood | Detects via | Pre-agreed response |
|---|---|---|---|
| NewPipe Extractor breaks (PO token / SABR change) | High, recurring | rot-drill CI red | Pin last-good extractor; adopt upstream patch; patch release within 72h |
| Upstream fix slow (>14 days) | Medium | rot-drill red ≥14d | Desktop: enable yt-dlp fallback; Android: document impact + user decision (pivot criteria) |
| SMTC via JNA unstable | Medium | Phase 12 spike (3-day time-box) | Ship fallback: tray controls + media keys; record in KNOWN_LIMITATIONS.md |
| Real blur unavailable | Certain (<Android 12) | Phase 06 | Scrim+translucency fallback; note per-platform in limitations |
| Compose MP / Ktor / Coil regression | Low | CI | Pin versions; upgrade one dependency at a time |
| GPL compliance drift | Low | phase reviews | THIRD_PARTY.md updated per phase; no non-GPL-compatible deps enter |
