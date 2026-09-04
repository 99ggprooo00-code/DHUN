# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, end of session)

**Branch:** `arena/01a06537-dhun` · **PR:** [#4](https://github.com/99ggprooo00-code/DHUN/pull/4) (draft) — "Phase 05: data layer + desktop CI activation"

**Phase:** 05 — Data layer (SQLDelight, repositories, use cases). Code is complete; the PR is one CI fix away from mergeable.

**File we were working on:** `app-desktop/src/jvmMain/kotlin/dev/dhun/desktop/Main.kt`

**Last error (CI run `33713453544` on commit `f8a3958`, step "Probe compiles", which now also compiles `:app-desktop`):**
```
app-desktop/.../Main.kt:63  Unresolved reference 'forDesktop'
app-desktop/.../Main.kt:14  Unresolved reference 'exitApplication'
```
Cause: `forDesktop` is an extension on `YouTubeMusicProvider.Companion` living in `shared/jvmMain/.../ProviderFactoriesJvm.kt` and needs its own import; `exitApplication` is a member of `ApplicationScope`, not a top-level import (that import line was never valid — the desktop module had simply never been compiled in CI before this session). Shared tests (55) and the Android build were **green** in that same run.

**Fix already applied in this commit (unverified by CI yet):** added `import dev.dhun.provider.forDesktop`, removed the bogus `exitApplication` import.

**Exact next step tomorrow:**
1. `gh run list --branch arena/01a06537-dhun --limit 1` → confirm the run for THIS commit is green. If not, read the annotations (`gh api repos/99ggprooo00-code/DHUN/check-runs/<job-id>/annotations`) — the build scripts now publish Gradle causes, Kotlin `e:` lines and failing test names there, since CI logs are unreachable from the sandbox.
2. Mark PR #4 ready (`gh pr ready 4`) and merge it (squash) → Phase 05 code lands on `main`; the rolling `test` release rebuilds the APK.
3. Ask the user for the hardware checks listed in `docs/verification/05-data-layer.md` (favorite survives restart; queue restored paused at position) plus the still-open Phase 03-C / Phase 04 checklists. These do not block Phase 06.
4. Start **Phase 06 — Design system** (`PROMPT_SEQUENCE.md`): new `shared/design/` tokens, GlassCard with real blur, ArtworkImage (Coil 3), ComponentCatalogScreen. One PR.

**Note for whoever resumes:** the sandbox git history was reset to `f215a1e` between turns while the files were kept; `git reset --hard origin/arena/01a06537-dhun` restored it. Always trust the remote branch.

### Phase 05 step-by-step status

| Step (PROMPT_SEQUENCE.md Phase 05 "Build") | Status |
|---|---|
| SQLDelight schema v1: Track, Playlist, PlaylistTrack, Favorite, History, Settings, RecentSearch (+ NowPlayingQueue/State), `cachedAt` reserved | ✅ done (`shared/src/commonMain/sqldelight/dev/dhun/database/*.sq`) |
| Migrations from v1 onward | ✅ infrastructure in place (schema v1, no `.sqm` yet — first needed at v2; `verifyMigrations` to enable then) |
| Drivers: Android `AndroidSqliteDriver`, JVM `JdbcSqliteDriver` | ✅ done, FKs on, per-OS DB path on desktop |
| Repositories: Track, Library, Playlist, History, Settings, Search (+ NowPlaying) | ✅ done (`dev.dhun.data`) |
| Use cases: ToggleFavorite, CreatePlaylist, AddToPlaylist, RemoveFromPlaylist, RecordPlay, GetRecentlyPlayed, GetHistory, UpdateSetting, … | ✅ done (`dev.dhun.domain`) |
| Settings keys object | ✅ done (`SettingsKeys`) |
| Now-playing persistence: restore last queue + position on cold start, both platforms | ✅ done (shared `NowPlayingPersistence`, wired in Android `MainActivity` + desktop `Main.kt`; restores **paused**) |
| Tests: every repository on in-memory DB, every use case, queue-restore round-trip | ✅ done, 21 new tests green in CI (run `33713067921`) |
| THIRD_PARTY: SQLDelight row | ✅ already present |
| Acceptance 1 — repo tests green | ✅ JVM in CI; Android target compiles the same code |
| Acceptance 2 — favorite round-trip verified in-app, both platforms | ⬜ OPEN — user hardware check |
| Acceptance 3 — queue survives app restart on Android | ⬜ OPEN — user hardware check |
| PR #4 CI fully green + merged | ⬜ OPEN — desktop compile fix pushed, awaiting CI |

---

> Operational phase-by-phase prompts (audit + rewritten sequence):
> [PROMPT_SEQUENCE.md](PROMPT_SEQUENCE.md).

| # | Phase | Status | Verification evidence |
|---|-------|--------|----------------------|
| 01 | Extraction spike | ✅ CODE COMPLETE — probe PASS end-to-end (search 20 + resolve + audio bytes verified + related 50); NewPipe v0.26.5 broken upstream -> ADR-001 two-tier resolver; on-device audible check rides Phase 03 | docs/research/01-extraction-spike.md · docs/verification/01-extraction-spike.md · ADR-001 |
| 02 | Provider & domain core | ✅ CODE COMPLETE — 34/34 unit tests (fixtures, queue, failover); live smoke PASS (all filters, suggestions, radio 50, lyrics 27 lines, stream via yt-dlp failover) | docs/verification/02-provider-core.md |
| 03 | Android skeleton + Media3 playback + lock screen | 🟨 CODE COMPLETE — APK builds, manifest+service verified, unit tests green; ON-DEVICE: v0.1.3 installs, search works live; playback blocked by WEB_REMIX-only /player → v0.1.4 adds WEB_REMIX→VISIONOS→TVHTML5 resolver chain, stable test signing, richer on-device error evidence + BACK=moveTaskToBack | docs/verification/03-android-skeleton.md |
| 04 | Desktop skeleton + vlcj playback | 🟨 CODE COMPLETE — app-desktop (Compose Desktop UI + vlcj) committed, shared DhunPlayer drives both platforms; CI blocker root-caused (Compose packager rejects packageVersion 0.x) and fixed, module active in the build; ON-DESKTOP checklist OPEN | docs/verification/04-desktop.md |
| 05 | Data layer (SQLDelight, repositories, use cases) | 🟨 CODE COMPLETE — SQLDelight 2.1 schema v1 (Track/Favorite/Playlist/PlaylistTrack/History/Settings/RecentSearch/NowPlaying), 7 repositories, use cases, shared NowPlayingPersistence (queue+position+history, paused restore on cold start) wired on Android + desktop; repository/use-case/restore tests green in CI; IN-APP round-trips (favorite, queue-survives-restart) OPEN on hardware | docs/verification/05-data-layer.md |
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
