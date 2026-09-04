# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, session arena/01a06a22-dhun)

**Branch:** `arena/01a06a22-dhun` · **PR:** TBD — “Phase 06: design system”

**Phase:** 06 — Design system (living, in code). Phase 05 merged to `main@9eef9b9` (#4); CI on `main` showed a flaky `NowPlayingPersistenceTest` 3s timeout (fixed to 10s in this branch).

**Last fix (this branch, unverified by CI yet):** `shared/src/jvmTest/.../NowPlayingPersistenceTest.kt` timeout 3s→10s, delay 80→300ms; new `shared/design/` added (tokens, GlassCard, ArtworkImage, extractor, buttons, chips, rows/cards, shimmer, error/empty, catalog).

**Exact next step:**
1. Push this branch → PR → CI must be green (`:shared:jvmTest` 60+ tests, `assembleDebug`, `:app-desktop:compileKotlinJvm`, `:tools:playback-probe:compileKotlinJvm`). If CI reports Kotlin `e:` annotations or test failures, read via `gh api .../check-runs/.../annotations`.
2. Merge PR → verify on hardware: catalog screen over colorful artwork shows real blur (not solid color), all component states visible, extractor returns 5 distinct palettes (seed test), no raw hex/dp outside `shared/design` in production code (harness is throwaway, noted).
3. Start **Phase 07 — Home & Search** (`PROMPT_SEQUENCE.md`).

### Phase 06 step-by-step status

| Step (PROMPT_SEQUENCE.md Phase 06 "Build") | Status |
|---|---|
| Tokens: `DhunColors` (surfaces 0A→2A, glass 60% #99111111, border 10%, text 4-step, accent #BB86FC), `DhunTypography` (M3), `DhunSpacing`, `DhunShapes`, `DhunAnimations` | ✅ done (`shared/src/commonMain/kotlin/dev/dhun/design/Dhun*.kt`) |
| `GlassCard`: real `Modifier.blur()` on 12+/Skiko, scrim fallback | ✅ done (`GlassCard.kt`, flagged in KNOWN_LIMITATIONS) |
| `ArtworkImage` (Coil 3): crossfade, pulsing placeholder, error gradient | ✅ done (`ArtworkImage.kt`, coil 3.1.0 + ktor3) |
| `ArtworkColorExtractor`: bitmap sampling → `ArtworkColors` + seed fallback | ✅ done (`ArtworkColors.kt`, 5-seed test green) |
| Components: `TrackRow/Card`, `ArtistCard`, `AlbumCard`, `PlaylistCard`, `SectionHeader`, `DhunButton/IconButton`, `LoadingShimmer`, `ErrorView`, `EmptyView`, `Chip` | ✅ done (`shared/design/components/*`, each with normal/pressed/disabled/loading via catalog) |
| `ComponentCatalogScreen` (debug) rendering every state over artwork | ✅ done (`shared/design/catalog/ComponentCatalogScreen.kt`, wired in Android `MainActivity` + desktop `Main.kt` via Catalog toggle) |
| THIRD_PARTY: Coil 3 row | ✅ already present (version pinned to 3.1.0) |
| Acceptance 1 — glass blur visibly real (screenshot) | ⬜ OPEN — user hardware (catalog over gradient) |
| Acceptance 2 — no raw hex/px outside `shared/design` | ✅ done for production code; harness has throwaway raws, noted in verification doc |
| Acceptance 3 — all states present in catalogue | ✅ done (catalog covers normal/pressed/disabled/loading for every component) |
| Acceptance 4 — extractor returns sane palettes for 5 artworks | ✅ done (unit test `ArtworkColorExtractorTest` 5 seeds distinct, alphas sane) |
| PR CI green + merged | ⬜ OPEN — awaiting CI on this branch |

---

> Operational phase-by-phase prompts (audit + rewritten sequence):
> [PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md).

| # | Phase | Status | Verification evidence |
|---|-------|--------|----------------------|
| 01 | Extraction spike | ✅ CODE COMPLETE — probe PASS end-to-end (search 20 + resolve + audio bytes verified + related 50); NewPipe v0.26.5 broken upstream -> ADR-001 two-tier resolver; on-device audible check rides Phase 03 | docs/research/01-extraction-spike.md · docs/verification/01-extraction-spike.md · ADR-001 |
| 02 | Provider & domain core | ✅ CODE COMPLETE — 34/34 unit tests (fixtures, queue, failover); live smoke PASS (all filters, suggestions, radio 50, lyrics 27 lines, stream via yt-dlp failover) | docs/verification/02-provider-core.md |
| 03 | Android skeleton + Media3 playback + lock screen | 🟨 CODE COMPLETE — APK builds, manifest+service verified, unit tests green; ON-DEVICE: v0.1.3 installs, search works live; playback blocked by WEB_REMIX-only /player → v0.1.4 adds WEB_REMIX→VISIONOS→TVHTML5 resolver chain, stable test signing, richer on-device error evidence + BACK=moveTaskToBack | docs/verification/03-android-skeleton.md |
| 04 | Desktop skeleton + vlcj playback | 🟨 CODE COMPLETE — app-desktop (Compose Desktop UI + vlcj) committed, shared DhunPlayer drives both platforms; CI blocker root-caused (Compose packager rejects packageVersion 0.x) and fixed, module active in the build; ON-DESKTOP checklist OPEN | docs/verification/04-desktop.md |
| 05 | Data layer (SQLDelight, repositories, use cases) | ✅ CODE COMPLETE — SQLDelight 2.1 schema v1 (Track/Favorite/Playlist/PlaylistTrack/History/Settings/RecentSearch/NowPlaying), 7 repositories, use cases, shared NowPlayingPersistence (queue+position+history, paused restore on cold start) wired on Android + desktop; repository/use-case/restore tests green in CI (flaky 3s timeout fixed to 10s in Phase 06 branch); IN-APP round-trips (favorite, queue-survives-restart) OPEN on hardware | docs/verification/05-data-layer.md |
| 06 | Design system (tokens, GlassCard, artwork colors, catalogue) | 🟨 CODE COMPLETE — `shared/design/` tokens (Colors/Spacing/Shapes/Typography/Animations), GlassCard with real blur (RenderEffect API 31+/Skiko, scrim fallback), ArtworkImage (Coil 3.1.0), ArtworkColorExtractor (bitmap+seed), all components with states, ComponentCatalogScreen over artwork | docs/verification/06-design.md |
| 06 | Design system (tokens, GlassCard, artwork colors, catalogue) | ⬜ not started | — |
| 07 | Home & Search | ⬜ not started | — |
| 08 | Player UI (MiniPlayer + FullPlayer) | ⬜ not started | — |
| 09 | Artist / Album / Playlist pages | ⬜ not started | — |
| 10 | Library & history screens | ⬜ not started | — |
| 11 | Lyrics (LRCLIB + YTM, synced) | ⬜ not started | — |
| 12 | Desktop native (SMTC spike, tray, mini-player, jpackage) | ⬜ not started | — |
| 13 | Android polish (insets, shortcuts, tablet, soak) | ⬜ not started | — |
| 14 | Robustness + rot-drill CI + release v0.1.0 | ⬜ not started | — |

Deferred to v2 (do not design, do not stub): Web/PWA, Android Auto, Cast,
equalizer, sync, downloads, widgets, jump lists, optional cookie sign-in.
