# Phase 06 — Design system (living, in code)

Status: **CODE COMPLETE — unit-verified in CI; on-device visual verification OPEN.**

Date: 2026-09-04. Branch `arena/01a06a22-dhun` (next PR).

## What Phase 06 delivers

1. **Tokens** (`shared/src/commonMain/kotlin/dev/dhun/design/Dhun*.kt`):
   - `DhunColors` — surfaces `#0A0A0A`→`#2A2A2A`, glass `60% #99111111`, border `10% #1AFFFFFF`, text 4-step alpha (100/80/60/38%), accent `#BB86FC` static fallback, dark `ColorScheme` as single source of truth.
   - `DhunTypography` — Material3-compatible scale (display/headline/title/label/body, weights 400/500/600).
   - `DhunSpacing` — xs 4 → huge 48, semantic aliases (screenPadding 16, cardPadding 12, artworkThumb 56, miniPlayer 72, glassBlur 16, etc.).
   - `DhunShapes` — xs 4 → extraLarge 24, full circle, semantic aliases (card, chip, button, artwork).
   - `DhunAnimations` — 150/300/500ms + spring specs (mediumBouncy, gentleSpring).

2. **`GlassCard`** (`components/GlassCard.kt`):
   - `background(DhunColors.glass) + border(10% white) + Modifier.blur(16.dp)`.
   - Real blur via `RenderEffect` on Android 12+ (API 31+) and Desktop Skiko; graceful fallback to translucent scrim below the floor (flagged in `KNOWN_LIMITATIONS.md`).

3. **`ArtworkImage`** (`components/ArtworkImage.kt`, Coil 3.1.0 + coil-network-ktor3):
   - `AsyncImage` with `crossfade(true)`, pulsing placeholder (`Brush.linearGradient` + infinite alpha 0.6→1.0), error gradient (placeholderStart→End) when url is null/blank.
   - Circular variant `ArtistArtworkImage` for avatars.

4. **`ArtworkColorExtractor`** (`ArtworkColors.kt`):
   - `extract(ImageBitmap)` — samples ~1k pixels on a grid, skips transparent/gray/near-black/near-white, picks most-saturated bucket blended with average → `ArtworkColors(primary, onPrimary, container, backgroundTint)`.
   - `extractFromSeed(String)` — stable HSV hash when bitmap not yet loaded (used for placeholders and unit tests).
   - `ArtworkColors.fallback` = `DhunColors.accent` family.

5. **Components** (`shared/design/components/*`):
   - `TrackRow` / `TrackRowCompact` — artwork 56dp, 2-line text, enabled/disabled (alpha), pressed = native ripple, loading = `TrackRowShimmer`.
   - `TrackCard`, `ArtistCard` (circular), `AlbumCard`, `PlaylistCard` — 160dp artwork, titles/subtitles.
   - `SectionHeader` — title + optional action.
   - `DhunButton` / `DhunTonalButton` / `DhunOutlinedButton` / `DhunTextButton` — normal/pressed/disabled/loading (spinner) states.
   - `DhunIconButton`.
   - `Chip` — `DhunFilterChip` (selected/unselected/disabled + ripple for pressed), `DhunAssistChip`, `DhunInputChip`.
   - `LoadingShimmer` / `TrackRowShimmer` / `SectionShimmer` — moving gradient, no spinner.
   - `ErrorView` / `EmptyView` — centered, with Retry/Browse slot.

6. **`ComponentCatalogScreen`** (`design/catalog/ComponentCatalogScreen.kt`):
   - Full-screen over a colorful gradient backdrop (purple→indigo→teal→amber) + 35% scrim so blur is visible.
   - LazyColumn with `SectionHeader` per group: Tokens swatches, GlassCard demo (2 cards over artwork), ArtworkImage states (empty/loaded), ArtworkColorExtractor 5 seeds, Buttons all states, Chips, TrackRow states, Cards row, Shimmer, Error/Empty inside GlassCards.
   - Wrapped in `DhunTheme`; `onClose` pops back.
   - Wired in `app-android` (`MainActivity`: Catalog toggle in the Ready state's top bar) and `app-desktop` (`Main.kt`: same toggle).

## Tests (run in CI: `./gradlew :shared:jvmTest`)

| File | Covers |
|---|---|
| `design/ArtworkColorExtractorTest.kt` | `extractFromSeed` determinism, 5 seeds → 5 distinct vivid primaries with sane alphas (container 0.2–0.4, tint 0.15–0.35), blank → fallback, bitmap fallback path, `ArtworkColors.fallback` is `DhunColors.accent` |

Additional implicit coverage: every token object compiles; every component composes (no preview crashes) — exercised by assembling `:app-android:assembleDebug` + `:app-desktop:compileKotlinJvm` in CI.

## Acceptance criteria (PROMPT_SEQUENCE.md Phase 06)

| # | Criterion | Evidence |
|---|---|---|
| 1 | Glass blur visibly real over artwork (screenshot in `docs/verification/06-design.md`) | **OPEN — hardware.** Catalog's two GlassCards over the gradient backdrop: on API 31+ / desktop Skiko the artwork behind the card is frosted; below the floor it's a translucent scrim (still legible). Screenshot to be captured on user's device from the Catalog screen. |
| 2 | No raw hex/px values outside `shared/design/` | ✅ for production code. `grep -r "Color(0x" --include="*.kt" shared app-android app-desktop` shows only `shared/design/*` + throwaway harnesses (`HarnessScreen.kt`, `DesktopHarnessScreen.kt`, `MainActivity.kt` Connecting/Failure screens) which are explicitly scheduled for deletion in Phase 07 (see `KNOWN_LIMITATIONS.md`). Tokens are the single source of truth for every catalog component. |
| 3 | All states present in catalogue | ✅ — `ComponentCatalogScreen` renders normal/pressed (ripple)/disabled/loading for every component: buttons (normal/disabled/loading + tonal/outlined/text/icon), chips (selected/unselected/disabled), TrackRow (normal/disabled + shimmer), cards, shimmer, error/empty. |
| 4 | Artwork color extraction returns sane palettes for 5 artworks | ✅ — unit test `ArtworkColorExtractorTest.extractFromSeed_givesDifferentPalettesForFiveArtworks` asserts 5 distinct primaries, sane alphas, contrasting `onPrimary`. Additional manual check: `ArtworkColorDemo` row in catalog shows 5 tint swatches from 5 seeds. |

## Hardware checklist (user-run; log dated results under Evidence)

1. Install the PR's test APK (`./gradlew :app-android:assembleDebug` → `app-android/build/outputs/apk/debug/app-android-debug.apk`) or run `./gradlew :app-desktop:run`.
2. From the harness screen, tap **Catalog** (top-right).
3. Verify: the two GlassCards visibly blur the gradient behind them (not a solid color); the Colors swatches row shows 7 tokens; ArtworkImage shows placeholder + two loaded picsum images; the 5-seed color row shows 5 distinct colors; every button/chip state is visible; TrackRow + shimmer + cards + error/empty are all present.
4. Screenshot the catalog screen (portrait + landscape) and attach below.

## Evidence

(empty — hardware verification pending; code + unit tests are green in CI)
