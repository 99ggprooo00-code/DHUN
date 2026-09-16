# THIRD_PARTY — dependencies & licenses

Maintained every phase. Rule: nothing enters the build unless its license is
GPL-3.0-compatible. Reused code gets: project, file, license, commit, date.

| Dependency | License | Used for | Since |
|---|---|---|---|
| NewPipe Extractor | GPL-3.0 | monitored extraction engine; not the current production primary (ADR-001) | Phase 01/02 |
| yt-dlp (optional desktop subprocess, user-provided binary) | Unlicense | extraction fallback | Phase 02 |
| Kotlin / KMP | Apache-2.0 | language | Phase 02 |
| Compose Multiplatform | Apache-2.0 | UI | Phase 03 |
| Media3 / ExoPlayer | Apache-2.0 | Android playback | Phase 03 |
| vlcj (+ libVLC, LGPL-2.1) | LGPL-2.1 | desktop playback (dynamic link) | Phase 04 |
| SQLDelight | Apache-2.0 | persistence | Phase 05 |
| Ktor | Apache-2.0 | networking | Phase 02 |
| Koin | Apache-2.0 | DI | Phase 03 |
| Jetpack Compose / activity-compose | Apache-2.0 | Android UI | Phase 03 |
| kotlinx-coroutines / serialization | Apache-2.0 | concurrency + JSON | Phase 02 |
| nanojson (TeamNewPipe fork) | Apache-2.0 | extractor's JSON parser | Phase 01 |
| Coil 3 | Apache-2.0 | images | Phase 06 |
| ~~Kermit~~ (removed — no build reference or code usage remains; row kept for history) | Apache-2.0 | was: logging | Phase 02–? |
| LRCLIB (API/service) | open API | synced lyrics source | Phase 11 |
| JNA | dual LGPL-2.1 / EPL-1.0 | SMTC spike WinRT interop (desktop, Windows paths) | Phase 12 |
| Material Design Icons (24px vector paths, embedded in `shared/.../design/DhunIcons.kt`) | Apache-2.0 | dependency-free UI iconography; paths adapted from the Material Icons set | Icon pass |
| Robolectric (test-only) / JUnit4 (test-only) / androidx-test (test-only) | MIT / EPL-1.0 / Apache-2.0 | unit-test runtimes; ship in no artifact | Phase 03+ |
| vivi-music (`vivizzz007/vivi-music`) | GPL-3.0 (+ musixmatch-only exception) | **UI reference only** — read in place via the GitHub API for design research; no fork, no vendored copy, nothing copied yet. Any future adaptation must stay GPL-3.0, be attributed here, and exclude the musixmatch module. See `.ai/ui-research-vivi-music.md` | UI research 2026-09-06 |

## Material transport paths — 2026-09-06

Copied the filled 24px `shuffle`, `repeat` and `repeat_one` path data into
`shared/src/commonMain/kotlin/dev/dhun/design/DhunIcons.kt` from
[google/material-design-icons](https://github.com/google/material-design-icons/tree/0cbb08816df07faaae3dca060d4ebb10b66c214f/src/av),
commit **`0cbb08816df07faaae3dca060d4ebb10b66c214f`** (upstream commit date
2026-09-04; retrieved 2026-09-06). Source files:

- `src/av/shuffle/materialicons/24px.svg`
- `src/av/repeat/materialicons/24px.svg`
- `src/av/repeat_one/materialicons/24px.svg`

License: **Apache-2.0**, Google/Material Design Icons contributors. The full
upstream license is in `LICENSES/MaterialDesignIcons-Apache-2.0.txt`. The
existing local SVG parser and renderer are DHUN code; no additional runtime
icon library is introduced. Ktor MockEngine, coroutines-test and the Compose
Desktop headless test runtime added in this batch are test-only components
of the already-attributed Apache-2.0 dependencies above.

## S5 license review — 2026-09-16

Reviewed the table against the actual `dependencies {}` blocks
(`shared`, `app-android`, `app-desktop`, `tools`): every runtime
dependency is listed with a GPL-3.0-compatible license; lifecycle /
core-ktx / media3-database / media3-datasource ride under their existing
Jetpack/Media3 rows (same artifacts groups, Apache-2.0). S4/S5 added no
third-party dependency (platform APIs + first-party code only). Two fixes
in this pass: Kermit row marked removed (dead reference), test-only
runtimes listed explicitly. Full version currency in
`.ai/DEPENDENCY_AUDIT.md`.
