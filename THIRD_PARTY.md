# THIRD_PARTY — dependencies & licenses

Maintained every phase. Rule: nothing enters the build unless its license is
GPL-3.0-compatible. Reused code gets: project, file, license, commit, date.

| Dependency | License | Used for | Since |
|---|---|---|---|
| NewPipe Extractor | GPL-3.0 | stream URL resolution (Android+Desktop) | Phase 01/02 |
| yt-dlp (optional desktop subprocess, user-provided binary) | Unlicense | extraction fallback | Phase 02 |
| Kotlin / KMP | Apache-2.0 | language | Phase 02 |
| Compose Multiplatform | Apache-2.0 | UI | Phase 03 |
| Media3 / ExoPlayer | Apache-2.0 | Android playback | Phase 03 |
| vlcj (+ libVLC, LGPL-2.1) | LGPL-2.1 | desktop playback (dynamic link) | Phase 04 |
| SQLDelight | Apache-2.0 | persistence | Phase 05 |
| Ktor | Apache-2.0 | networking | Phase 02 |
| Koin | Apache-2.0 | DI | Phase 03 |
| Coil 3 | Apache-2.0 | images | Phase 06 |
| Kermit | Apache-2.0 | logging | Phase 02 |
| LRCLIB (API/service) | open API | synced lyrics source | Phase 11 |
