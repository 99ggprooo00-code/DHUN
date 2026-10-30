# DHUN glass system — the visual source of truth

Status: **authoritative for native UI** (`shared/src/commonMain/kotlin/dev/dhun/ui`,
`app-android`, `app-desktop`). If any other document, older session note, task
prompt or comment disagrees with this page about how a surface looks, **this
page wins** and the other text should be corrected (see §7).

DHUN is a **premium translucent frosted-glass** interface: artwork-led,
dark-first, layered, with soft blur behind sharp content. Material 3 is an
**implementation detail**, never the look. A screen that renders stock Material
3 is a defect, exactly like a broken button.

---

## 1. The one rule

> Every surface that visually belongs to DHUN is painted with the glass
> material in `design/components/`, composited over artwork. A flat opaque fill,
> a Material `Card`/`Surface`/`AlertDialog`, a default `OutlinedTextField`, or a
> `background(DhunColors.surface)` panel is drift, not a shortcut.

"Glassy" means **all four** of these are present, not a variable called `glass`:

| Cue | Where it lives | Why it matters |
| --- | --- | --- |
| Frosted artwork behind the panel | `LyricsArtworkLayer` via `GlassCard(frostArtwork = true, artworkUrl = …)`, `LyricsMaterial`, `AcrylicSurface`, `TrackMenuSurface` | the surface is visibly *made of* the cover, not just tinted |
| Ambient artwork colour under everything | `AmbientBackdrop(section)` (two blooms from the section palette over its base) | a page with no cover is still artwork-led, never one flat background |
| Body gradient (lifted top → deepened bottom) | `GlassMaterialPolicy.bodyStops` | one flat colour is the M3 tell |
| Specular sheen along the top edge | `GlassMaterialPolicy.sheenStops` | reads as light hitting glass |
| Gradient hairline edge | `GlassMaterialPolicy.edgeStops` + `DhunColors.glassEdge` | a uniform stroke reads as a Material outline |

## 2. Component map — use these, not raw Material

| Need | Use | File |
| --- | --- | --- |
| Card, group, notice, generic panel | `GlassCard` | `design/components/GlassCard.kt` |
| Dialog (confirm, rename, diagnostics, pickers) | `GlassDialog` | `design/components/GlassCard.kt` |
| List row / settings row body | `Modifier.dhunContentRowSurface()` | `design/components/ContentRowSurface.kt` |
| Lyrics card, queue sheet, floating player | `LyricsMaterial`, `LyricsArtworkSheet`, `AcrylicSurface` | `design/components/LyricsMaterial.kt` |
| Overflow menu surface | `TrackMenuSurface` + `MenuActionRow` | `ui/components/TrackOverflowDialog.kt` |
| One-line text input | `DhunTextField` | `design/components/DhunTextField.kt` |
| Switch / slider | `DhunSwitch` / `DhunSlider` | `design/components/DhunControls.kt` |
| Multi-select box (offline selection) | `DhunCheckbox` | `design/components/DhunControls.kt` |
| Progress bar (download, storage) | `DhunProgressBar` | `design/components/DhunControls.kt` |
| Loading spinner (buttons, pagination) | `DhunSpinner` (thin accent arc in a faint glass track) | `design/components/DhunSpinner.kt` |
| Page base under the artwork backdrop | `AmbientBackdrop(component)` (that surface's palette) | `design/components/NowPlayingBackdrop.kt` |
| Surface tint for dock / navigation / player chrome | `Modifier.backgroundComponentTint(component)` | `design/background/BackgroundSurface.kt` |
| Background blur and colour resolution | `DhunBackgrounds`, `BackgroundComponent`, `BackgroundPalette`, `BackgroundPalettePolicy` | `design/background/` |
| Anchored menu (provider picker, mode picker, overflow) | `DropdownMenu(containerColor = Color.Transparent)` + `TrackMenuSurface` + `MenuActionRow` | `ui/components/TrackOverflowDialog.kt` |
| Collapsible page toolbar / any chrome strip | `Modifier.dhunGlassBody(fill)` | `design/components/GlassCard.kt` |
| Bottom tab bar (glass band + tabs) | `DhunNavigationBar` + `DhunNavigationBarItem` | `design/components/DhunNavigation.kt` |
| Desktop navigation rail | `DhunNavigationRail` + `DhunNavigationRailItem` | `design/components/DhunNavigation.kt` |
| Button family | `DhunButton`, `DhunTextButton`, `DhunOutlinedButton`, `DhunIconButton` | `design/components/DhunButton.kt` |
| Chips | `DhunFilterChip` / `FrostedChipSurface` | `design/components/Chip.kt` |
| Separator line | `DhunDivider` (tapered hairline, not a full-width rule) | `design/components/DhunDivider.kt` |
| Pull-to-refresh | `DhunPullToRefresh` (glass disc + accent arc) | `design/components/DhunPullToRefresh.kt` |
| Bottom dock | `GlassDock` | `design/components/GlassCard.kt` |
| Error strip at screen level | `ActionErrorBanner` (glass + error tint) | `ui/components/ActionErrorBanner.kt` |

Material components **may** be used inside these wrappers — a `Switch` inside
`DhunSwitch`, a `Slider` inside `DhunSlider`, a `Checkbox` inside
`DhunCheckbox`, `DropdownMenu`, `LinearProgressIndicator` — with DHUN tokens
driving colours, shapes and elevation. What is forbidden is their *default
appearance* reaching the user.

Two rules follow from that and are enforced mechanically
(`scripts/test_design_system.py`):

1. **Never call the raw control from a screen.** `Switch(...)`, `Slider(...)`,
   `Checkbox(...)`, `Button(...)`, `TextButton(...)`, `IconButton(...)`,
   `LinearProgressIndicator(...)`, `CircularProgressIndicator(...)`,
   `DropdownMenuItem(...)` outside `design/` is a defect; the wrapper exists so
   the palette, shape and metrics are defined once (`DhunControlPolicy`,
   `DhunSpinnerPolicy`, `DhunNavigationPolicy`).
   Navigation is not an exception: `NavigationBar` / `NavigationRail` and their
   items are banned too. Their *container* was never the problem — the selection
   pill, item metrics and label type inside them are Material's, so the tab bar
   and the rail are DHUN components now.
2. **Anchors stay transparent.** `DropdownMenu` is only an anchor: it must pass
   `containerColor = Color.Transparent` (and zero tonal/shadow elevation) and let
   `TrackMenuSurface` be what you see. A Material `containerColor` in a menu is
   a flat plate inside the glass.

### What Material is still allowed to be

A census of the app after the correction, so nobody mistakes the remainder for
drift — or re-opens it as work:

| Left in `shared/.../ui`, `app-android`, `app-desktop` | Why it is not the Material look |
| --- | --- |
| `Text` | A text primitive. It renders `LocalTextStyle`, which `DhunTheme` fills from `DhunTypographyTokens` — DHUN's family, sizes, tracking and colours. |
| `MaterialTheme.typography` (215 reads) | Reads DHUN's own scale through Material's holder (`DhunTypography` is built from `DhunTypographyTokens`). There is no `MaterialTheme.colorScheme` read left in `shared/ui`: colour always comes from `DhunColors`. |
| `DropdownMenu` (3 sites) | The anchor only: transparent container, zero tonal/shadow elevation, `TrackMenuSurface` + `MenuActionRow` draw the glass. |
| `Scaffold` (1 site) | Slot layout in the shell, container transparent, so the ambient + artwork backdrops show through. |
| Wrappers in `design/components/` | `Button`, `Switch`, `Slider`, `Checkbox`, `FilterChip`, `IconButton`, `Scaffold`-free progress, `PullToRefreshBox` — used as *mechanics*; every colour, shape, size and indicator is DHUN's, and screens may not call them. |

That is the whole list. Anything else from Material reaching a screen fails
`scripts/test_design_system.py`.

## 3. Numbers (do not re-invent per screen)

All glass constants live in `GlassMaterialPolicy` and `LyricsMaterialPolicy`.
Text must never sit on a blur; blur is always the backdrop.

- Content/reading body opacity: `ContentSurfacePolicy` (0.82–0.89 band).
- Dialog body opacity floor: `GlassMaterialPolicy.DIALOG_BODY_ALPHA_DARK/LIGHT`
  (0.91 / 0.93) — "sufficiently opaque" is this number, **not** a flat plate.
- Sheen: `SHEEN_TOP 0.16` → `SHEEN_EXTENT 0.45` of the panel height; rows use
  32% of that (`ROW_SHEEN_SCALE`).
- Edge: glass edge token at full alpha on top, `0.34×` at the bottom.
- Blur: `DhunSpacing.glassBlur` × `LyricsMaterialPolicy.BLUR_SCALE`, computed
  once per artwork and cached (`BlurredArtworkCache`). Never per frame.

## 3a. Backgrounds: blur and colour are two independent switches

Two questions, answered separately, and four surfaces the user can colour:

| Switch | On | Off |
| --- | --- | --- |
| **Background blur** | the playing track's artwork is painted, blurred, behind the interface | **no artwork is painted at all** — not sharp, not dimmed — and every surface is its own colour |
| **Dynamic colours** | surfaces take their colour from that artwork | surfaces use the colour the user picked for them |

They are independent by construction: `BackgroundBlurPolicy` never reads a colour
switch and `BackgroundPalettePolicy.resolve` never reads a blur switch
(`scripts/test_design_system.py` asserts both). All four combinations are valid:

```
blur off + colours off   solid backgrounds, the user's colours, no artwork
blur off + colours on    artwork-derived colours, still no artwork
blur on  + colours off   blurred artwork tinted with the user's colours
blur on  + colours on    blurred artwork with artwork-derived colours
```

**Four surfaces, not five sections and not three palette layers.** The
customisation unit is the thing the user recognises
(`BackgroundComponent`):

| Component | What it paints |
| --- | --- |
| `PAGES` | Home, Search, Library and the pages opened from them (album/artist/playlist) |
| `NAVIGATION` | the bottom tab band, or the side rail on a wide window |
| `MINI_PLAYER` | the docked now-playing bar |
| `FULL_PLAYER` | the immersive player |

Each one saves a colour (or "DHUN default") and its own blur switch, and each has
its own "take colour from artwork" switch so one surface can keep a manual colour
while the rest follow the cover. This replaced the earlier five-section,
`primary`/`secondary`/`tertiary` model: asking a user to assemble a palette was
asking them to think like the renderer.

**The palette is derived, never assembled.** `BackgroundPalettePolicy.paletteFrom`
turns one colour into the three layers the background is painted from — a base
wash plus a main and a companion glow, the last two related by a 38° hue shift —
and the settings swatches render the result of that same derivation. Artwork
colour and a manual pick therefore cannot drift into different recipes.

**The base is luminance-clamped, not contrast-clamped by eye.**
`BASE_LUMINANCE_DRIFT` (0.05) keeps the base inside the band the text ladder was
measured against in both themes. With no artwork behind it the surface needs more
of the hue to be visible at all, so `SOLID_BASE_TINT_ALPHA` (0.55) is used instead of
`BASE_TINT_ALPHA` (0.30) — chroma, not brightness, which is why the contrast band
still holds in the "blur off, my colours" state. The two glows get a little more
presence there as well (`AmbientBackdropPolicy.SOLID_BLOOM_SCALE`).

**Blur off means no image layer.** `BackgroundBlurPolicy.paintsArtwork(percent)`
is the single answer every artwork layer asks for: `NowPlayingBackdrop`,
`LyricsArtworkLayer` (dock, lyrics card, dialogs, queue sheet), `GlassCard`'s
frost, `PlayerBackdrop`, the player's `ArtworkBackdrop` and its lyrics card all
return before drawing when it is false. A sharp or dimmed copy of the cover is
*not* the fallback for "off" — that was the behaviour that made the switch
indistinguishable from a weaker blur.

**A backdrop is never pinned to a surface.** `NowPlayingBackdrop`,
`AmbientBackdrop`, `PageArtworkBackdrop` and `LyricsArtworkLayer` take a *nullable*
component that resolves to `LocalBackgroundComponent.current`, so panels, menus
and detail pages inherit the surface they are composed on; surfaces that own one
(the dock is `MINI_PLAYER`, the player is `FULL_PLAYER`, the tab band is
`NAVIGATION`) pass it explicitly.

**Blur strength cannot impersonate the switch.** The slider scales the radius and
is floored at `MIN_EFFECTIVE_STRENGTH` (20%) for an enabled blur; the switch is
the only off control. `Adaptive blur` is a sub-choice of an enabled blur (a bright,
busy cover keeps the full frost; a dark, calm one is blurred slightly less), never
a third feature.

**One image pipeline.** `DhunBackgrounds.setArtworkSeed(...)` is the only artwork
input: it reuses `ArtworkColorExtractor` (the palette the player tint already
computes), caches per seed, and is called once per track from the shell. No
decode, no sampling, no per-frame work; palette changes animate on
`DhunAnimations.slowTween`.

**Fallbacks.** A device without real blur (Android < 12, no Skiko RenderEffect)
still saves the switches and draws the dimmed cover, and says so in Settings.
Nothing playing, or an extraction that fell back to the accent, leaves each
surface on its own colour — never a transparent hole. The pre-API-31 path adds no
CPU blur.

## 4. Legibility beats translucency (unchanged, and it is not a licence to go flat)

If text, icons, focus or progress become hard to read over artwork, raise the
**opacity of the body** (or use an opaque tonal rung) — never remove the
gradient, sheen, edge or frost. `docs/TRD.md` and the design contract's "opaque
enough base under dialogs" mean: dialogs use `GlassCard(opaqueBase = true)`,
which is milky glass at the documented floor, **not** a stock `AlertDialog`
container.

Contrast gates: `shared/src/jvmTest/.../DhunThemeContrastTest.kt` asserts text
ladder vs. row/reading fills over black and white artwork in both themes. A
change to a fill must keep those green — and must not lower a fill's opacity to
buy "more glass".

## 5. Fallbacks

- No real blur (Android < 12, no Skiko RenderEffect): draw the dimmed cover or
  the gradient + sheen + edge alone. Never skip the material.
- Nothing playing / no artwork: `LocalNowPlayingArtwork` is null and the frost
  cut falls back to gradient + sheen over the scrim. That is intended — and the
  page still has the `AmbientBackdrop` colour, so "nothing playing" is not the
  same thing as "plain surface".

`LocalNowPlayingArtwork` is provided once by `DhunAppShell`; `GlassDialog` reads
it, so every dialog in the app frosts over the playing cover without a call site
threading a URL. Call sites that know better pass `artworkUrl` explicitly.
- Light theme: the same recipe, white veil, weaker sheen (`light` branches in
  `GlassMaterialPolicy`).

## 6. Enforcement (so this cannot silently rot again)

- `scripts/test_design_system.py` (runs in `python3 -m unittest discover -s scripts`)
  fails the repo gate when a banned Material-default composable appears in
  `shared/.../ui/`, `app-android` or `app-desktop` outside the design system,
  unless it is listed with a reason in `scripts/design_system_allowlist.txt`; it
  also fails when a menu anchor is left with a default `containerColor` or when a
  screen re-invents the glass gradient. Navigation is covered by the same list:
  `NavigationBar`/`NavigationRail` and their items are banned outright, because
  what looked wrong was inside them, not their container.
- `shared/src/jvmTest/.../design/GlassSystemTest.kt` asserts the material
  numbers (gradient ordered, sheen capped, edge fading, dialog floor above the
  row fill, light/dark asymmetry), the control palette, the spinner geometry, the
  ambient bloom bounds, the navigation pill (never a solid accent, rim stronger
  than the fill, fades to nothing) and the fact that the rail is exactly the
  width `DhunShellPolicy.railAllowance` reserves for it.
- Screens are reviewed visually on hardware when hardware exists; a lack of
  device access is recorded in `.ai/KNOWN_LIMITATIONS.md`, not used to justify a
  flat build.

## 7. Instructions that this page supersedes

Kept on purpose so the next agent does not resurrect them:

| Older text | Correction |
| --- | --- |
| `.ai/MASTER_PROMPT.md` roadmap row "GlassCard real blur" | `GlassCard` had **no** blur for a long time; it does now for the frost cut (`opaqueBase`/`frostArtwork`). Treat this file as the description of what ships. |
| "Content panels above the dock stay neutral" (older shell notes) | Neutral *tint*, never a flat **material**: rows use `dhunContentRowSurface()` with body gradient + sheen. |
| "Use an opaque base under dialogs" (design contract §5, TRD) | Still true as an opacity floor (`GlassMaterialPolicy.DIALOG_BODY_ALPHA_*`); it does not permit `AlertDialog` or a flat rectangle. |
| "Material 3 only" (pre-ADR-002 wording, historical) | Superseded: Material 3 is not the visual target (`docs/ui-ux-design.md` §Status). |
| "Keep the layout container and pass `containerColor = Color.Transparent`" (earlier §2, applied to the navigation bar and rail) | Not enough for navigation: Material's `NavigationBarItem`/`NavigationRailItem` paint their **own** selection pill, metrics and label type, so a transparent container still rendered a stock Material tab bar. The shell uses `DhunNavigationBar`/`DhunNavigationRail`; only `DropdownMenu` remains a transparent anchor. |
| Visual-tuning sliders / test channel | These change *opacity percentages*; they never change the material recipe. A tuning value of 100% is a contrast fallback, not the design. |
