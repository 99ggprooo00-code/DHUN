# ROADMAP — live status

## CURRENT ACTIVE TASK (updated 2026-09-04, session arena/01a06a42-dhun)

**Branch:** `arena/01a06a42-dhun` · **PR:** TBD — "Phase 07: Home & Search"

**Phase:** 07 — Home & Search. Phase 06 merged to `main@8a675a3` (#5). Phase 07 code complete; awaiting hardware verification.

**Exact next step:**
1. Push branch → PR → CI must be green (`shared:jvmTest`, `shared:compileKotlinMetadata`, `assembleDebug`, `app-desktop:compileKotlinJvm`).
2. Merge PR → verify on hardware: HomeScreen loads real YTM content + greeting + quick picks; SearchScreen: type → suggestions ≤300ms → results per filter → track tap plays; overflow menu (favorite, add to queue).
3. Start **Phase 08 — Player UI (MiniPlayer + FullPlayer)**.

### Phase 07 step-by-step status

| Step | Status |
|---|---|
| InnerTubeClient: `homeFeed()`, `browse()`, `artist()`, `searchContinuation()` endpoints | ✅ done |
| InnerTube parsers: `parseHomeSections()`, `parseTwoRowItem()`, `parseResponsiveListItem()`, `SearchContinuation` data class + `parseSearchContinuation()` | ✅ done |
| Core entities: `HomeSection`, `HomeItem` sealed interface (`TrackItem/AlbumItem/ArtistItem/PlaylistItem`) | ✅ done |
| `HomeViewModel`: YTM sections + greeting + recently played + "listen again" + overflow | ✅ done |
| `HomeScreen`: shared Compose — greeting, quick picks, section rows/grids, shimmer loading, error/empty, pull-to-refresh | ✅ done |
| `SearchViewModel`: debounced suggestions (300ms), filter chips, paginated results, recent searches, overflow | ✅ done |
| `SearchScreen`: shared Compose — search bar, suggestions overlay, filter chips, results with infinite scroll | ✅ done |
| `AppShell` + `BottomNavBar` (shared navigation, no library dependency) | ✅ done |
| Android wiring: `AppModule` (ViewModelFactories in Koin), `MainActivity` → `AppShell` on Ready | ✅ done |
| Desktop wiring: `desktopModule`, `Main.kt` → `AppShell` | ✅ done |
| Unit tests: `HomeParserTest` + `SearchFilterTest` | ✅ done |
| CI green + merged | ⬜ OPEN — awaiting CI |
| ON-DEVICE: HomeScreen real YTM content | ⬜ OPEN — user hardware |
| ON-DEVICE: Search: suggestions ≤300ms, results, track plays, overflow | ⬜ OPEN — user hardware |

---

| # | Phase | Status | Evidence |
|---|-------|--------|----------|
| 01 | Extraction spike | ✅ | docs/research/01-extraction-spike.md · docs/verification/01-extraction-spike.md · ADR-001 |
| 02 | Provider & domain core | ✅ | docs/verification/02-provider-core.md · 34/34 tests |
| 03 | Android skeleton + Media3 | 🟨 | docs/verification/03-android-skeleton.md |
| 04 | Desktop skeleton + vlcj | 🟨 | docs/verification/04-desktop.md |
| 05 | Data layer (SQLDelight) | ✅ | docs/verification/05-data-layer.md |
| 06 | Design system | ✅ | docs/verification/06-design.md |
| 07 | Home & Search | 🟨 CODE COMPLETE | `HomeScreen`, `SearchScreen`, `HomeViewModel`, `SearchViewModel`, `AppShell`, parsers, tests |
| 08 | Player UI | ⬜ not started | — |
| 09 | Artist / Album / Playlist | ⬜ not started | — |
| 10 | Library & History | ⬜ not started | — |
| 11 | Lyrics | ⬜ not started | — |
| 12 | Desktop native | ⬜ not started | — |
| 13 | Android polish | ⬜ not started | — |
| 14 | Release v0.1.0 | ⬜ not started | — |

Deferred to v2: Web/PWA, Android Auto, Cast, equalizer, sync, downloads, widgets.
