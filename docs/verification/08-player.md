# Phase 08 verification — Player UI (MiniPlayer + FullPlayer)

Status: 🟨 **CODE COMPLETE** (+ ADR-002 lyrics-dominant / Recovering chip 2026-09-05) — on-hardware checklist below is OPEN (needs a
physical run on Android + desktop, like Phases 03/04/05 checklists).

## What was built (code-level, auditable)

| Spec item | Implementation |
|---|---|
| MiniPlayer: 72dp glass bar above bottom nav (Android) / docked bottom (desktop) | `shared/ui/player/MiniPlayer.kt` — docks via `GlassBottomBar` inside `DhunAppShell` bottomBar |
| Artwork, marquee title, artist, play/pause, next | `MiniPlayer` — `Modifier.basicMarquee()` on title (ExperimentalFoundationApi), Coil `ArtworkImage` (crossfade) |
| 1dp accent progress line | top of MiniPlayer: `Box(height = DhunSpacing.divider)` + accent fill fraction |
| Tap / swipe-up opens FullPlayer (animated) | tap + `detectVerticalDragGestures` (threshold 80px) → `AppNavState.playerExpanded`; overlay via `AnimatedVisibility(slideInVertically(300ms) + fadeIn)` in `DhunAppShell` |
| FullPlayer: full-bleed blurred artwork background + dark scrim | `FullPlayer.kt`: `Crossfade(500ms)` of artwork under `Modifier.blur(48.dp)` (glassBlur × 3) + artwork-tinted vertical gradient + scrim gradient |
| Color crossfade on track change (500ms) | `ArtworkColorExtractor.extractFromSeed()` → `animateColorAsState(500ms)` for bg tint + accent |
| Large artwork + shadow, playing-vs-paused spring scale | artwork `shadow(16.dp, extraLarge)` + `animateFloatAsState(1f ↔ 0.84f, springSpec)` (MediumBouncy) |
| Custom progress bar (4dp→8dp on drag, thumb on touch only) | `DhunSeekBar` — `animateDpAsState(4↔8dp)`, thumb drawn only while `dragging`; tap-to-seek + scrub |
| prev/next hold-to-seek | `HoldTapTransportButton` (≥350ms hold) → `PlayerViewModel.beginHoldSeek` stepping `duration/120` (clamped 1–15s) every 140ms via `player.seekTo` |
| Animated play/pause morph | 72dp accent disc, `Crossfade(300ms)` glyph swap + shadow |
| Shuffle + repeat (cycle 3) | `DhunPlayer.setShuffle/setRepeatMode`; VM `toggleShuffle()` / `cycleRepeatMode()` (OFF→ALL→ONE) — persisted via `NowPlayingPersistence` mirror |
| Volume slider (desktop) | material3 `Slider` → `DhunPlayer.setVolume` (desktop: vlcj `audio().setVolume`; Android: hidden) |
| Bottom tabs Lyrics \| Queue \| Related | `ui/player/PlayerTabs.kt` — custom tab row (accent underline) |
| Related wired to `/next` parsing | `PlayerViewModel.loadRelated` → `MusicProvider.relatedTracks` (Phase 02 parser); "Play radio" + tap-to-play-as-queue |
| Lyrics tab | provider's YTM lyrics; handles Synced/Unsynced/NotAvailable/Error with retry (LRCLIB synced arrives Phase 11) |
| Queue tab: drag reorder | `ui/components/ReorderableList.kt` — long-press ≡ handle, pointer-follow (translationY), one `moveInQueue(from,to)` per drop |
| Queue tab: swipe remove | custom `detectHorizontalDragGestures` + reveal "Remove" layer, 35%-width threshold, `removeFromQueue` |
| Queue tab: tap-to-jump | `player.playAt(index)` |
| Queue current highlight + equalizer | accent tint + "NOW" chip + `EqualizerBars` (animates only while Playing) |
| Track-change choreography | artwork `AnimatedContent` slide in `SkipDirection` (+fade), bg tint crossfade, title `Crossfade`; direction tracked in `PlayerViewModel` (next/prev/playAt/index drift) |
| Android edge-to-edge insets | `safeDrawingPadding()` on FullPlayer root |
| BACK from FullPlayer collapses (never exits) | `AppNavState.closeTop()` in `MainActivity.BackHandler`: overlay → detail stack → `moveTaskToBack` |

## Player UI state provenance

`DhunPlayer` gained Phase-08 members: `currentQueueIndex`, `repeatMode`,
`shuffleEnabled`, `volume` StateFlows + `playAt`, `removeFromQueue`,
`moveInQueue`, `setVolume`. Android = Media3 pass-through
(`moveMediaItem`/`removeMediaItem`/`seekTo(i, TIME_UNSET)`); Desktop =
`QueueManager` + vlcj (publishQueueLocked keeps flows in sync).

## Unit tests

- `PlayerViewModelTest` — repeat cycle (OFF→ALL→ONE→OFF), shuffle/volume
  flows, 3s-rule on previous, queue ops delegation, related+lyrics load on
  track change, related error mapping, hold-seek start/stop.
- `NowPlayingPersistenceTest.FakePlayer` updated for the new interface.

## On-hardware checklist (OPEN)

- [ ] Android: blurred artwork bg visibly real (Pixel, API 31+); scrim below on <API31
- [ ] 16 visual/interaction checks executed with screenshots
- [ ] Rapid 10× skip stress — no state inconsistency / crash
- [ ] Queue drag/swipe/tap on touch (Android) and mouse (desktop)
- [ ] Desktop: volume slider drives vlcj; blur over artwork (Skiko)
- [ ] BACK collapses FullPlayer; app never finishes while expanded

## ADR-002 polish (2026-09-05) — Material 3 only

| Item | Status |
|---|---|
| Liquid Glass | 🚫 forbidden |
| Lyrics-dominant mode (Lyrics tab) | 🟨 code — artwork recedes, M3 translucent surface |
| BlurredArtworkCache once-per-track | 🟨 code + unit tests |
| Reconnecting chip (`PlaybackState.Recovering`) | 🟨 code — Android 403 path |
| Hardware re-check of 16-item list | ⬜ still OPEN |

## Lyrics motion (ADR-002 P8) — 2026-09-05

| Item | Status |
|---|---|
| Active line scale + accent wash | 🟨 code |
| Frosted player tab selected state | 🟨 |
| Hardware lyric sync check | ⬜ |
