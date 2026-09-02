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

## Build (real, as of Phase 03)

Requires JDK 17 and an Android SDK (`ANDROID_HOME`).

```bash
./gradlew :app-android:assembleDebug   # Android debug APK
./gradlew :shared:jvmTest              # domain + parser + queue unit tests
./gradlew :tools:playback-probe:run    # extraction probe (needs PYTHONPATH w/ yt-dlp for the resolve step)
./gradlew :tools:playback-probe:run -PmainClass=dev.dhun.tools.smoke.SmokeMainKt  # live provider smoke
# Desktop (Phase 04): first uncomment include(":app-desktop") in settings.gradle.kts
./gradlew :app-desktop:run             # desktop app (needs libVLC + yt-dlp; see KNOWN_LIMITATIONS.md)
```

APK output: `app-android/build/outputs/apk/debug/app-android-debug.apk`

## Test builds policy

ONE rolling test release exists — tag `test`, asset `dhun-test.apk`,
auto-replaced on every push to main. No versioned releases for unfinished
builds; nothing in Releases is stable or store-ready. Install test builds
only on devices where that is acceptable (not your daily phone).

## Repo map

- `MASTER_PROMPT.md` — the 14-phase engineering plan (the contract)
- `PROMPT_SEQUENCE.md` — audit of the original 30-phase prompt set + the rewritten phase-by-phase prompts
- `PROBLEMS_AND_FIXES.md` — audit of the original plan + evidence
- `RISK_REGISTER.md` — what will go wrong and the pre-agreed responses
- `ROADMAP.md` — live phase status
- `KNOWN_LIMITATIONS.md` — honest gaps, updated every phase
