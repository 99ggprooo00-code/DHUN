# DHUN

A serious, cross-platform music application streaming from YouTube Music.
Android (primary) · Desktop via Compose Multiplatform (Windows/Linux/macOS).

> **Status:** planning complete, implementation starting. See
> [MASTER_PROMPT.md](MASTER_PROMPT.md) (the plan) and
> [PROBLEMS_AND_FIXES.md](PROBLEMS_AND_FIXES.md) (why the plan looks like this).

## The two facts that define this project

1. **Extraction is maintenance, not implementation.** YouTube enforces PO
   tokens / SABR; hand-rolled InnerTube extraction is what killed ViMusic,
   RiMusic, InnerTune and others. DHUN wraps **NewPipe Extractor**
   (Android + Desktop) with a **yt-dlp** desktop fallback, keeps its own
   InnerTube client for metadata only, and runs a **daily CI rot-drill**
   against live YouTube.
2. **Code-first.** The previous attempt produced great documents and an app
   that never attempted its core mission. Every phase here ships running
   code on real hardware before it is "done."

## License

GPL-3.0 — required for legitimate reuse of the ecosystem's maintained
extractors (NewPipe Extractor is GPL-3.0). See THIRD_PARTY.md.

## Build (once Phase 03 lands — until then the plan is the deliverable)

```bash
./gradlew :app-android:assembleDebug     # Android
./gradlew :app-desktop:packageDistributionForCurrentOS  # Desktop installers
./gradlew :tools:playback-probe:run      # extraction spike CLI
```

## Repo map

- `MASTER_PROMPT.md` — the 14-phase engineering plan (the contract)
- `PROBLEMS_AND_FIXES.md` — audit of the original plan + evidence
- `RISK_REGISTER.md` — what will go wrong and the pre-agreed responses
- `ROADMAP.md` — live phase status
- `KNOWN_LIMITATIONS.md` — honest gaps, updated every phase
