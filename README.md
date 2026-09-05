# DHUN

A serious, cross-platform music application streaming from YouTube Music.
Android (primary) · Desktop via Compose Multiplatform (Windows/Linux/macOS).

> **Status:** Phases 01–11 merged, Phase 12 (desktop native) in progress —
> live status in [.ai/ROADMAP.md](.ai/ROADMAP.md). Plan:
> [.ai/MASTER_PROMPT.md](.ai/MASTER_PROMPT.md); why it looks like this:
> [.ai/PROBLEMS_AND_FIXES.md](.ai/PROBLEMS_AND_FIXES.md).

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

## Build (real, as of Phase 03)

Requires JDK 17 and an Android SDK (`ANDROID_HOME`).

```bash
./gradlew :app-android:assembleDebug   # Android debug APK
./gradlew :shared:jvmTest              # domain + parser + queue + data-layer unit tests
./gradlew :tools:playback-probe:run    # extraction probe (needs PYTHONPATH w/ yt-dlp for the resolve step)
./gradlew :tools:playback-probe:run -PmainClass=dev.dhun.tools.smoke.SmokeMainKt  # live provider smoke
./gradlew :app-desktop:run             # desktop app (needs libVLC + yt-dlp; see KNOWN_LIMITATIONS.md)
./gradlew :app-desktop:compileKotlinJvm  # desktop compile check (what CI should run)
```

APK output: `app-android/build/outputs/apk/debug/app-android-debug.apk`

## Test builds policy

ONE rolling test **pre-release** exists — tag `test`, assets
`dhun-test.apk` (Android) and `dhun-test.msi` (Windows, needs libVLC
installed), auto-replaced on every push to main. No versioned releases for
unfinished builds; nothing in Releases is stable or store-ready. Install
test builds only on devices where that is acceptable (not your daily
phone). Stable URLs:
`https://github.com/99ggprooo00-code/DHUN/releases/download/test/dhun-test.apk`,
`…/dhun-test.msi`.

## Repo map

- `.ai/` — agent operating files (moved out of the project root 2026-09-05):
  - `.ai/MASTER_PROMPT.md` — the 14-phase engineering plan (the contract)
  - `.ai/PROMPT_SEQUENCE.md` — audit of the original 30-phase prompt set + rewritten prompts
  - `.ai/PROBLEMS_AND_FIXES.md` — audit of the original plan + evidence
  - `.ai/RISK_REGISTER.md` — what will go wrong and the pre-agreed responses
  - `.ai/ROADMAP.md` — live phase status (CURRENT ACTIVE TASK at the top)
  - `.ai/KNOWN_LIMITATIONS.md` — honest gaps, updated every phase
  - `.ai/DEBUG_LOG.md` — incidents: stack → root cause → fix
  - `.ai/README.md` — boot protocol + permanent maintenance contract
