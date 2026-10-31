# UI research — `vivizzz007/vivi-music` (reference only, no fork)

> **Applicability note — 2026-10-28:** dated reference/attribution research,
> not renewed permission to copy code, a requested UI rewrite, or a complete
> resolved-licence review. The old instruction below is retained as history.
> Current scope and [multi-source direction](../docs/PRD.md#1-product-definition-and-source-of-truth)
> govern; software licensing never grants third-party content/service rights.

Standing user instruction (2026-09-06): *"you can do these both with proper
planning and research for ui if (https://github.com/vivizzz007/vivi-music)
has ui codes visible then copy from there (no forking)."*

This file records what was verified before any DHUN styling is touched, so
the attribution and the constraints survive across sessions.

## Licence — checked first, because it decides whether copying is allowed

- `gh api repos/vivizzz007/vivi-music` → `license.spdx_id = "NOASSERTION"`,
  which only means GitHub could not auto-classify it.
- The `LICENSE` file itself is **GNU GPL v3.0** (29 June 2007) with one
  preamble: a *special exception for the proprietary `musixmatch` module*
  (inspect but do not copy that module).
- README confirms: *"This project is licensed under the terms specified in
  the LICENSE (GPL-3.0) file."*

**Conclusion:** compatible with DHUN (GPL-3.0, see `LICENSE`). Code outside
the `musixmatch` module may be studied and adapted **with attribution**, and
any adaptation must stay GPL-3.0. **Do not touch or copy anything under the
musixmatch exception.** No fork was made; files were read in place through
`api.github.com` (this sandbox blocks `raw.githubusercontent.com`).

Attribution must land in `THIRD_PARTY.md` for anything actually used.

## Platform mismatch — the single biggest constraint

vivi-music is an **Android-only** app: everything lives under
`app/src/main/kotlin/com/music/vivi/`, `minSdk = 26`, and it opts into
`androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`.

DHUN's UI is **Compose Multiplatform** (`shared/src/commonMain/…/ui/`,
shared by Android *and* Desktop, Compose MP 1.8.2, Material3 without the
Expressive API).

So vivi-music code **cannot be pasted in**. What transfers is design
decisions and structure; what does not is any Android-framework-only API
(`androidx.media3` UI bits, `WindowInsets` specifics, Expressive-only
components, Activity/Fragment glue). Anything borrowed has to be
re-expressed in common code with `expect`/`actual` only where genuinely
platform-specific.

## What is actually there (verified file listing, 1673 paths)

Player (the area DHUN's Phase 08 targets):

| File | Why it is worth reading |
|---|---|
| `ui/player/Player.kt`, `Player_v2.kt` | two generations of the full player — the diff between them is effectively a record of what they improved and why |
| `ui/player/MiniPlayer.kt`, `AppleMiniPlayer.kt` | mini-player variants, incl. an Apple-Music-style one |
| `ui/player/Thumbnail.kt`, `ThumbnailSnapUtils.kt` | artwork paging/snap behaviour |
| `ui/player/CanvasArtworkPlayer.kt`, `PlayerV2Canvas.kt` | artwork-as-canvas treatment |
| `ui/player/PlaybackError.kt` | **directly relevant right now** — how they surface playback errors |
| `ui/player/Queue.kt`, `Queue_v2.kt` | queue sheet, two generations |

Theme / design tokens:

| File | Why |
|---|---|
| `ui/theme/Theme.kt`, `Type.kt`, `Font.kt` | token structure, type scale, custom font wiring |
| `ui/theme/PlayerColorExtractor.kt` | artwork → palette extraction (DHUN has `ArtworkColorExtractor`; worth comparing) |
| `ui/theme/PlayerSliderColors.kt` | progress-slider treatment |
| `ui/screens/settings/ThemeScreen.kt`, `viewmodels/ThemeViewModel.kt` | user-facing theme controls (DHUN is dark-first only, v2 candidate) |

Screens: `HomeScreen.kt`, `ExploreScreen.kt`, `AlbumScreen.kt`,
`BrowseScreen.kt`, `artist/ArtistScreen.kt`, `MoodAndGenresScreen.kt`,
`NewReleaseScreen.kt`, `ChartsScreen.kt`, `HistoryScreen.kt`,
`NavigationBuilder.kt`, `Screens.kt`.

## Outcome (2026-09-06): the screenshots made most of this unnecessary

The screenshots arrived and the restyle shipped as **PR #26**. Notably,
**nothing was adapted from vivi-music** — and that was the right call, not an
oversight.

The defects the screenshots revealed were not design-vocabulary problems that
a reference implementation could have solved. They were **bugs wearing a
styling costume**:

| Complaint, as reported | What it actually was |
|---|---|
| "bad terrible UI", every screen a different colour | `extractFromSeed` mapping a hash across the whole hue wheel at 62–92% saturation, applied to the app-wide wash *and* raw to transport controls |
| player looks broken / alarming | that same raw colour on the play disc — one seed in six landed on red, i.e. identical to the error affordance |
| — (not reported, found in the images) | `SkipPrevious`/`SkipNext` **glyph paths were swapped** |
| flat, grey, lifeless | glass tokens were 55–82% **opaque** near-black composited on near-black — arithmetically flat grey |
| dialogs unreadable | `GlassCard` floating over a `Dialog` scrim with nothing behind it |

Reading `Theme.kt` or `Player_v2.kt` would not have surfaced any of these. The
lesson is the one already recorded in `.ai/ROADMAP.md`: **get the artefact
before theorising about the fix.**

Where vivi-music remains genuinely worth reading, for later work:

- `ui/theme/PlayerColorExtractor.kt` — DHUN's `ArtworkColorExtractor` now
  separates ambient colour from control colour; comparing how vivi constrains
  its extracted palette is still useful input if the seeded-hash fallback is
  ever replaced with true bitmap extraction on the hot path.
- `ui/player/PlaybackError.kt` — DHUN's error surface now carries the
  resolve-chain `detail`; worth a comparison pass when the audio defect is
  closed and error UX gets a proper review.

Constraints below are unchanged and still binding.

## Rules for whoever picks this up

1. Read, don't clone. No submodule, no vendored copy, no fork.
2. Attribute in `THIRD_PARTY.md` (project, path, commit) for anything used.
3. Nothing from the `musixmatch` module.
4. Re-express in Compose Multiplatform common code — no Android-only APIs in
   `shared/`.
5. Design tokens stay in `shared/design/` (MASTER_PROMPT: no raw hex/px
   outside it).

---

## 2026-10-30 — second pass: how the artwork-driven colour *experience* works

Requested because the owner wants DHUN's background customisation to match the
*experience* of VIVI Music (not ViMusic). Read in place through `api.github.com`;
no code was copied. Files inspected: `ui/theme/Theme.kt`,
`ui/theme/PlayerColorExtractor.kt`, `ui/component/AlbumGradient.kt`,
`ui/player/Player.kt`, `constants/PreferenceKeys.kt`,
`ui/screens/settings/ThemeSettings.kt`.

**1. How colour is extracted.** `Bitmap.extractThemeColor()` builds an
`androidx.palette.Palette` (max 8 colours), scores swatches with
`androidx.palette.graphics.Palette.Score` and takes the first. The player
gradient (`PlayerColorExtractor`) instead ranks *dominant × vibrant* swatches by
`population * 2 * vibrancyBonus * (saturation + brightness) / 2`, then boosts
saturation (1.1–1.3×) and clamps brightness to 0.4–0.85 so the result cannot be
mud or glare. The bitmap it needs is a 100×100 Coil decode of the same artwork
URL the UI already loads.

**2. What the colour touches.** Two different things, and this is the part worth
copying conceptually:

- the **whole app's tonal scheme** comes from a *seed colour the user picked*
  (`rememberDynamicColorScheme`, materialKolor `TonalSpot`/`Monochrome`), or, on
  Android 12+ when the seed is the default, from the system wallpaper
  (`dynamicDarkColorScheme`). It does **not** follow the track;
- the **player and mini-player backgrounds** follow the artwork, as a vertical
  gradient painted by `AlbumGradient`: artwork colour at α≈0.5 → a darker
  variant at α≈0.3 → the theme's `surface` at the bottom.

So in VIVI the artwork colour lives in the *surface wash*, while text, icons and
controls keep the theme's own on-colours. That is the same separation DHUN's
glass system already makes (artwork identity in the wash and glows, text on its
measured ladder).

**3. How the transition works.** Each gradient stop is an
`animateColorAsState(tween(800))`: switching tracks crossfades the wash over
0.8 s instead of snapping. There is no flash to black and no intermediate
default palette, because the *old* colour is the animation's start value.

**4. How the user turns it off.** Two unrelated switches, which is the part DHUN's
previous pass got wrong in the opposite direction: `DynamicThemeKey` enables
system/wallpaper colour, and the *player* picks `PlayerBackgroundStyle`
(`DEFAULT`, `GRADIENT`, `BLUR`, `GLOW_ANIMATED`, `APPLE_MUSIC`, `LIVE_MESH`).
`GRADIENT` and `BLUR` are **alternatives in one enum** — VIVI cannot show blurred
artwork *and* its own chosen colour, and it cannot show artwork colours with the
artwork removed. The owner's brief asks DHUN for exactly the four states that enum
cannot express, so the enum is not the model to copy.

**5. How it differs from a conventional primary/secondary theme.** There is no
"accent colour picker" applied to surfaces: the *surfaces themselves* are tinted,
continuously, from the current track, while the semantic roles (on-background,
on-surface-variant, outline…) are generated from the tonal scheme so contrast is
preserved. The user perceives "the app is this song's colour", not "the app has
this song's accent".

**What DHUN took from this (and what it deliberately did not).**

- Took: artwork colour as a *surface wash* with the palette layered (base, main
  glow, companion glow), an animated crossfade per track, saturation/brightness
  discipline on the source colour, and the vocabulary of "dynamic colour" as a
  switch the user can turn off.
- Did not take: the single-enum background style (DHUN needs blur × colour as two
  independent switches — four states), the bitmap decode on the hot path (DHUN
  keeps one extraction pipeline keyed by seed; see `DhunBackgrounds`), and the
  materialKolor tonal-scheme generator (DHUN has its own token system and
  measured contrast gates).
