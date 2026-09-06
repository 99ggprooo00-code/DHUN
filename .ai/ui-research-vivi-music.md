# UI research — `vivizzz007/vivi-music` (reference only, no fork)

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
