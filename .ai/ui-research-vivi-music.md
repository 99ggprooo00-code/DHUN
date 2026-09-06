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

## Not yet done, and why

- **No specific patterns have been extracted yet.** The user is sending
  screenshots of what looks wrong in DHUN; researching a redesign before
  knowing the complaint risks fixing the wrong thing (this already bit once
  — see `.ai/ROADMAP.md`, "UI quality" row).
- Concrete next step once screenshots exist: read
  `ui/player/PlaybackError.kt` + `Player_v2.kt` + `Theme.kt` first (they map
  onto the reported complaints: player quality, error surfacing, overall
  flatness), then translate the relevant decisions into
  `shared/src/commonMain/kotlin/dev/dhun/design/`.

## Rules for whoever picks this up

1. Read, don't clone. No submodule, no vendored copy, no fork.
2. Attribute in `THIRD_PARTY.md` (project, path, commit) for anything used.
3. Nothing from the `musixmatch` module.
4. Re-express in Compose Multiplatform common code — no Android-only APIs in
   `shared/`.
5. Design tokens stay in `shared/design/` (MASTER_PROMPT: no raw hex/px
   outside it).
