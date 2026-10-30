# 34 — Translucent (glass) UI correction

Date: 2026-10-30. Branch: `arena/97df3ebc-dhun`. Base: `main` @ `3ce83dd`.
Scope: the Compose Multiplatform design system (`shared/src/commonMain/.../design`),
the shared screens that render it, and the Android/desktop shells.

## 1. The problem

The owner reported, repeatedly, that DHUN keeps drifting back to an ordinary
Material 3-looking interface even though its identity is premium glassmorphism.

The cause was not a missing colour token, and it was not the docs: they already
say Material 3 is an implementation detail. The cause was the **material**.
`GlassCard` — the surface behind every dialog, Settings group, legal card and
notice — painted a single flat `readingSurfaceFill()` with a uniform 1dp border,
and it accepted a `blurRadius` that it ignored. `dhunContentSurface` did the
same for rows. "Glass" existed only as variable names, so a screen could be
"built with the glass system" and still render as a translucent slab.

On top of that, default-Material widgets kept reaching shipped screens: a raw
`AlertDialog` for playback diagnostics, an `OutlinedTextField` inside the search
bar and inside the lyrics picker, a Material `TextButton`/`Switch`/`Slider`/
`Checkbox`/`CircularProgressIndicator` wherever a screen needed one, and a flat
`Surface` strip for the offline banner.

## 2. What the glass system is now

`GlassMaterialPolicy` (in `design/components/GlassCard.kt`) holds the numbers;
`docs/design/glass-system.md` is the normative description of when to use them.

| Constant | Value | Meaning |
| --- | --- | --- |
| `BODY_TOP_LIFT` | `0.06` | white added at the top of the body gradient |
| `BODY_BOTTOM_DEEPEN` | `0.10` | black added at the bottom |
| `BODY_MID_STOP` | `0.55` | where the lifted top returns to the base fill |
| `SHEEN_TOP` / `SHEEN_MID` | `0.16` / `0.05` | specular white at the top edge |
| `SHEEN_EXTENT` | `0.45` | how far down the sheen survives |
| `EDGE_TOP_ALPHA` / `EDGE_BOTTOM_ALPHA` | `1.0` / `0.34` | gradient hairline: bright top, faint bottom |
| `DIALOG_BODY_ALPHA_DARK` / `_LIGHT` | `0.91` / `0.93` | dialog opacity **floor** (milky glass, not a plate) |

Every panel is painted in this order: soft shadow → clip → gradient hairline edge
→ frosted artwork (dialogs/menus) → body gradient → caller tint (capped at
`0.20`) → specular sheen → sharp content. The blur never sits on text.

Rows use the same recipe at a third of the sheen (`ROW_SHEEN_SCALE = 0.32`) so a
list stays scannable; chips carry the gradient and the edge but skip the frost
cut, because a pill is too small for a readable frost.

`AmbientBackdrop` (two artwork-derived blooms over the theme base) is painted
under every page, so a screen with nothing playing — first run, Library,
Settings, an offline device — is still artwork-led instead of one flat colour.

## 3. What changed, file by file

### Design system

| File | Change |
| --- | --- |
| `design/components/GlassCard.kt` | `GlassMaterialPolicy`; `GlassCard` repainted (gradient body, capped tint, sheen, gradient edge, optional frost); `GlassDialog` (frosted dialog); `Modifier.dhunGlassBody(fill)` for chrome that is not a card; `GlassDock` and `FrostedChipSurface` rebuilt on the same material; `LocalNowPlayingArtwork` |
| `design/components/DhunControls.kt` | `DhunControlPolicy`, `DhunSwitch`, `DhunSlider`, `DhunCheckbox`, `DhunProgressBar` |
| `design/components/DhunSpinner.kt` | `DhunSpinnerPolicy`, `DhunSpinner` (thin accent arc in a faint glass track, indeterminate semantics preserved) |
| `design/components/DhunTextField.kt` | became the one input control (label, single/multi-line, IME action, shape, leading/trailing, error hairline, focus registration) and paints the glass body |
| `design/components/ContentRowSurface.kt` | `dhunContentSurface` = body gradient + 32% sheen |
| `design/components/Chip.kt` | chips paint the glass body (selected stays the accent) |
| `design/components/NowPlayingBackdrop.kt` | `AmbientBackdrop` + `AmbientBackdropPolicy` |
| `design/components/DhunNavigation.kt` | `DhunNavigationPolicy`, `DhunNavigationBar`, `DhunNavigationBarItem`, `DhunNavigationRail`, `DhunNavigationRailItem` — the shell's tab bar and rail, replacing Material's |
| `design/components/DhunPullToRefresh.kt` | `DhunPullToRefresh` — Material's gesture mechanics, DHUN's glass disc and accent arc |
| `design/components/DhunDivider.kt` | `DhunDivider` — the tapered hairline that replaced `HorizontalDivider` |
| `design/DhunIcons.kt` | new `Check` glyph for the glass checkbox (web icon mirror regenerated) |

### Screens and shells

- Playback diagnostics: raw `AlertDialog` → `GlassDialog`.
- Search bar and the lyrics TTML field: `OutlinedTextField` → `DhunTextField`.
- Settings: `OutlinedTextField`/`Switch`/`Slider`/`TextButton` → the DHUN
  equivalents (country picker, all switches, every tuning/EQ slider, reset).
- Library: selection checkboxes → `DhunCheckbox`; progress/storage bars →
  `DhunProgressBar`; placeholder cover tile and storage groove are translucent.
- Player: volume slider → `DhunSlider`; lyrics provider and romanization menus
  paint into `TrackMenuSurface` via a transparent popup; collapse toolbar and
  navigation rail use `Modifier.dhunGlassBody`.
- Banners: offline strip and the Android readiness banner are tinted
  `GlassCard`s (no flat `Surface`); the Android battery rationale is a
  `GlassDialog`.
- Pages: the Android/desktop shell paints `AmbientBackdrop` under the blurred
  cover instead of a flat background.
- Every `CircularProgressIndicator` → `DhunSpinner` (Home pagination, Artist
  radio, button loading states).
- Navigation: Material's `NavigationBar`/`NavigationRail` and their items →
  `DhunNavigationBar`/`DhunNavigationRail`. The last `HorizontalDivider`s
  (Search suggestions, lyrics controls, lyrics matches) → `DhunDivider`. Home's
  `PullToRefreshBox` indicator → `DhunPullToRefresh`.
- The component catalogue now shows the checkbox, progress bar, spinner,
  divider and a rendered tab bar, so the spec cannot drift from the widgets.

## 4. Docs and guardrails

- `docs/design/glass-system.md` (new): the normative contract — the four cues,
  the component map, the numbers, the legibility floor, the fallbacks, the
  enforcement, and a table of the older instructions it supersedes (kept so they
  cannot be resurrected).
- `docs/ui-ux-design.md`: "sufficiently opaque" restated as an opacity floor, not
  a flat surface; the gate is named.
- `.ai/MASTER_PROMPT.md`: binding correction bullet plus the component list, so a
  future session reads the glass system before touching UI.
- `docs/decisions/ADR-002-fullscreen-player-design.md`: the two remaining
  "M3 translucent" phrasings corrected; the historical addenda marked.
- `scripts/test_design_system.py` (new, runs in the existing
  `python3 -m unittest discover -s scripts` gate): fails when a banned
  default-Material composable is called from a screen (`AlertDialog`,
  `Surface`, `Card`, `OutlinedTextField`, `Button`/`TextButton`/`IconButton`/
  `OutlinedButton`/`FilledTonalButton`, `Switch`/`Slider`/`Checkbox`/
  `RadioButton`, `ListItem`, `TopAppBar`, `Snackbar`, `ModalBottomSheet`,
  `LinearProgressIndicator`, `CircularProgressIndicator`, `DropdownMenuItem`),
  when a layout container is left with a Material `containerColor`, or when a
  screen re-invents the glass gradient. `scripts/design_system_allowlist.txt`
  is the (currently empty) debt register.
- `GlassSystemTest` (JVM): pins the material numbers, the control palette, the
  spinner geometry and the ambient-bloom bounds.

## 5. Verification

| Level | Result |
| --- | --- |
| Static audit | Every default-Material widget removed from `shared/.../ui`, `app-android` and `app-desktop` (the gate is the audit). |
| Repo Python gate | `python3 -m unittest discover -s scripts -p 'test_*.py'` — 504 tests OK (includes the six design-system tests). |
| Web mirror | `npm --prefix app-web run check:icons` and `npm --prefix app-web test` — 340 tests OK after the new icon. |
| Kotlin compile + unit tests | CI (`build-and-test`, `Build APK`, `test-release`) — see the PR checks; this sandbox has no JDK, Gradle or Maven egress, so nothing was compiled locally. |
| Emulator / physical device | **NOT RUN.** No emulator, no Windows desktop, no device in this environment: contrast, blur fallback on API < 31, and the actual rendered look on Android and Windows remain unverified and must be reviewed on hardware. |

The legibility floor is unchanged and still enforced by
`DhunThemeContrastTest` (text ladder vs. row/reading fills over black and white
artwork, both themes, all accents) — the new material adds depth *inside* the
existing opacity band rather than lowering it.

## 6. Not done / deliberately out of scope

- No new dependency, no Liquid-Glass-style renderer, no platform glass API.
- No layout, navigation, behaviour, storage-schema or provider change; the only
  functional deltas are the loading indicator (custom arc) and the input
  controls (same callbacks, same validation, same submit actions).
- Per-screen visual tuning on hardware (blur strength, bloom balance in light
  theme) once a device is available.
