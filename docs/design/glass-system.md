# DHUN glass system — native visual source of truth

**Status: authoritative for the native UI on 2026-10-31.** This page governs
`shared/src/commonMain/kotlin/dev/dhun/ui`, `shared/src/commonMain/kotlin/dev/dhun/design`,
`app-android`, and `app-desktop`. Other active documents may explain product
flows, but must defer to this page for appearance, components, background
preferences, accessibility, and fallback behavior. Dated verification reports
and changelogs describe their own branches; they are not permission to restore
an old visual model.

DHUN has its own **translucent frosted-glass identity**: artwork-led,
dark-first, quiet and immersive, with an independently designed light mode.
The Compose toolkit is an implementation mechanism, not the product's visual
identity. Stock toolkit appearance must not reach a shipped screen when a DHUN
component owns that role.

This is a source specification, not a claim of visual acceptance. No emulator,
physical phone, or interactive desktop display was available for this stage.

---

## 1. Preserve the product while changing the paint

Visual work must not silently redesign the app.

- Keep Home, Search, and Library as the primary tabs. Keep the existing detail,
  Settings, player, queue, lyrics, and Related navigation and back order.
- Preserve control locations, information hierarchy, action names, callbacks,
  pending/error behavior, and data ownership unless a separate functional
  change explicitly requires otherwise.
- A visual commit must not change transport, seeking, queue mutation or
  reordering, lyrics selection/import, settings persistence, downloads, or
  playlist transactions.
- Use shared tokens and policies. Do not scatter one-off colors, radii, opacity
  values, or control metrics through screens.

## 2. The layer model — not everything is a glass card

DHUN uses several deliberate surface classes. Choosing the quiet one is part of
the design; adding frost, blur, gradients, and shadows everywhere is incorrect.

### A. Theme base and page artwork

`NowPlayingBackdrop` / `PageArtworkBackdrop` paints the current track's
list-tier artwork behind a page, with overscan and one theme-aware veil. The
base theme remains visible when artwork is absent, loading, or fails. Artwork
alpha and veil alpha fade together; a failed request must not leave a stale
veil or grey placeholder slab.

The page backdrop is one atmospheric layer. Do not add another full-screen
bloom, scrim, or independently loading copy of the same cover in each screen.

### B. Quiet content and reading surfaces

`Modifier.dhunContentRowSurface()` and
`Modifier.dhunContentSurface(reading = true)` provide stable tonal fills for
lists, forms, and reading groups. They intentionally do **not** blur every row
or paint a heavy specular treatment on every item.

Default fill opacity in `ContentSurfacePolicy` is:

| Surface | Dark | Light |
| --- | ---: | ---: |
| Ordinary content | 0.82 | 0.83 |
| Reading content | 0.88 | 0.89 |

Test-channel visual tuning may adjust values only through its existing clamped
policy; it is not a second product theme. Production defaults remain the design
baseline.

### C. Glass cards and dialogs

`GlassCard` owns the readable panel recipe:

1. optional restrained shadow;
2. clipped shape and directional hairline edge;
3. a three-stop body gradient;
4. an optional capped semantic tint;
5. a top sheen which fades before mid-panel;
6. sharp content above all paint.

`GlassDialog` uses the same recipe at a high-opacity floor. It does **not** take
an artwork URL and does not pretend to frost artwork itself. Artwork-bearing
menus and sheets use the materials in §2D instead.

`GlassCard.blurRadius` is retained only for call-site compatibility. Passing it
does not blur a card; do not add new call sites that rely on it. Blur belongs to
an artwork layer behind sharp content.

### D. Artwork-backed player, queue, dock, and menus

Use the existing material that matches the surface:

- `LyricsMaterial` for lyrics/Related content and `TrackMenuSurface` menus;
- `LyricsArtworkSheet` for the raised Queue/Related sheet, whose opaque base is
  an intentional readability floor;
- `AcrylicSurface` for a floating mini-player;
- `GlassDock` for the phone's continuous mini-player/navigation dock, so the
  artwork layer is shared rather than decoded once per child band;
- `lyricsVeil` / `acrylicGlass` only where the caller already owns the correct
  artwork layer.

These surfaces keep artwork behind the veil and content sharp. They are not a
licence to put an image and blur inside every settings row or music row.

### E. Navigation and small controls

`DhunNavigationBar` and `DhunNavigationRail` own the container, full-cell tab
semantics, label, and translucent selected pill. The pill has a stronger rim
than its fill and independently tuned light/dark alpha values. The rail width
must remain identical to `DhunShellPolicy.railAllowance(true)`; changing one
without the other breaks the two-pane layout.

Small controls use the shared component and policy rather than restyling a
stock control independently in each screen.

## 3. Component map

| Need | Required component/policy | Source |
| --- | --- | --- |
| Panel, Settings group, notice | `GlassCard` | `design/components/GlassCard.kt` |
| Confirmation, diagnostics, rationale | `GlassDialog` | `design/components/GlassCard.kt` |
| Glass numbers and blur gate | `GlassMaterialPolicy` | `design/components/GlassMaterialPolicy.kt` |
| Quiet list/reading surface | `dhunContentRowSurface` / `dhunContentSurface` | `design/components/ContentRowSurface.kt` |
| Page artwork atmosphere | `NowPlayingBackdrop` / `PageArtworkBackdrop` | `design/components/NowPlayingBackdrop.kt` |
| Lyrics, queue, mini-player artwork | `LyricsMaterial`, `LyricsArtworkSheet`, `AcrylicSurface`, `GlassDock` | `design/components/LyricsMaterial.kt`, `GlassCard.kt` |
| Anchored menu | transparent `DropdownMenu` + `TrackMenuSurface` + `MenuActionRow` | `ui/components/TrackOverflowDialog.kt` |
| Single/multiline input | `DhunTextField` | `design/components/DhunTextField.kt` |
| Button family | `DhunButton`, `DhunTonalButton`, `DhunOutlinedButton`, `DhunTextButton`, `DhunIconButton` | `design/components/DhunButton.kt` |
| Switch, slider, checkbox, progress | `DhunSwitch`, `DhunSlider`, `DhunCheckbox`, `DhunProgressBar` | `design/components/DhunControls.kt` |
| Spinner and refresh | `DhunSpinner`, `DhunPullToRefresh` | `design/components/DhunSpinner.kt`, `DhunPullToRefresh.kt` |
| Bottom tabs / desktop rail | `DhunNavigationBar(Item)`, `DhunNavigationRail(Item)` | `design/components/DhunNavigation.kt` |
| Filter chips | `DhunFilterChip` / `FrostedChipSurface` | `design/components/Chip.kt`, `GlassCard.kt` |
| Screen-level mutation failure | `ActionErrorBanner` | `ui/components/ActionErrorBanner.kt` |

Toolkit primitives may remain **inside these wrappers** for mechanics and
semantics. Their colors, shape, elevation, indicators, and metrics must come
from DHUN policies. `Text` and `MaterialTheme.typography` are allowed holders
for DHUN's own type scale. A simple `HorizontalDivider` is a separator, not a
surface. `Scaffold` is layout only and keeps a transparent container.

Every `DropdownMenu` is only an anchor: its container is transparent and tonal
and shadow elevation are zero. `TrackMenuSurface` is the visible material.

## 4. Numeric material contract

Keep these decisions in policy code rather than copying them into screens.

| Decision | Current policy |
| --- | --- |
| Body gradient | top white lift 0.06; middle stop 0.55; bottom black deepen 0.10 |
| Dialog sheen | top 0.16, mid 0.05, gone by 0.45 of panel height |
| Light sheen | 0.45× the dark sheen, not an inverted dark value |
| Ordinary dark-card sheen | quieter 0.05 top cut |
| Edge | full top alpha to 0.34 bottom; light top edge 0.70× |
| Minimum strengthened content-card body alpha | 0.70 |
| Dialog body alpha | dark 0.91; light 0.93 |
| Card shadow alpha | dark 0.18; light 0.10 |
| Dock shadow alpha | dark 0.28; light 0.16 |

The gradient preserves the input color's alpha. Increasing legibility means
raising the appropriate body/veil opacity, not stacking extra shadows or
turning every panel into an opaque rectangle.

## 5. Dark, light, artwork, and accessibility

### Independent schemes

Dark, OLED, and light tokens are deliberate peers. Light mode has its own
surface, text, border, sheen, gradient, scrim, and shadow values. Never derive
it by inverting the dark scheme or by reusing dark-mode white highlights at the
same strength.

Artwork may supply atmosphere and a tamed control accent. It must not choose
text colors directly or bypass contrast policy. Dynamic accent mode colors
controls; it is not a per-surface background-color system.

### Legibility and state

- Primary, secondary, tertiary, disabled, and hint text retain their defined
  hierarchy over both bright and dark artwork.
- Text and controls remain sharp; blur is never applied to the content layer.
- Selected state uses shape/semantics as well as color. Navigation items use
  `Role.Tab` and `selectable`; icon-only controls retain content descriptions.
- Touch targets, keyboard actions, focus registration, disabled state, pending
  state, and live-region error announcements must survive visual adoption.
- Do not communicate success, failure, selection, or progress by color alone.
- Large text and long diagnostics must remain scrollable and selectable where
  the existing interaction requires it.

`DhunThemeContrastTest` checks the token ladder, semantic colors, accents,
reading/content surfaces, and extreme backdrop stops in both schemes.
`GlassMaterialPolicyTest`, `LyricsMaterialPolicyTest`,
`NowPlayingBackdropPolicyTest`, `DhunControlPolicyTest`,
`DhunNavigationPolicyTest`, `DhunSpinnerPolicyTest`, and
`DhunRefreshPolicyTest` pin their respective pure policies. These are numeric
and source tests, not screenshots or proof of TalkBack/frame behavior.

## 6. Background preferences — deliberately simple

The current product exposes **Background blur** and **Background brightness**
under Appearance & Backdrop. Defaults are **100% blur** and **55% brightness**.
The existing `SettingsViewModel` persists both values and owns rollback after a
failed write; the live appearance update is not a second persistence engine.

- **Blur** scales artwork blur where real blur is supported. At **0%**, artwork
  is not removed: the non-blurred, dimmed artwork and veil remain. Do not label
  this control “artwork on/off.”
- **Brightness** adjusts the veil/artwork presentation independently of blur.
  It is not a custom-surface-color brightness control.
- Where real blur is unavailable, the blur slider is hidden and Settings
  explains that the sharp cover is dimmed instead. The brightness preference
  still applies.
- There is no current five-palette editor, four-surface color editor, HSL color
  pipeline, or global “dynamic surface colours” switch. Those were historical
  experiments, not requirements to restore. Do not reintroduce their control
  count or user-facing palette assembly.

This simple model is the accepted #216 outcome for this tree: useful defaults,
two understandable controls, existing preferences, and no new schema or
per-surface background pipeline.

## 7. Performance and fallback rules

- Request the existing list-tier artwork for atmospheric layers; do not decode
  full-resolution hero art for every row or menu.
- Reuse stable artwork keys and `BlurredArtworkCache` where the component
  already provides them. Guard against late results painting the previous
  track's artwork.
- Gate real blur with `GlassMaterialPolicy.shouldBlurArtwork`. Unsupported
  platforms and a zero blur preference must skip the blur modifier.
- The non-blur fallback is **sharp, dimmed artwork plus the theme veil**, not a
  CPU blur implementation and not an unmodulated cover under text. Its loaded
  artwork+veil layer uses **0.40 opacity over the theme base** (the real-blur
  path remains 1.0), protecting direct text as well as card-row text over
  extreme black/white artwork and the full brightness range.
- No artwork or a failed image leaves the theme base and readable surfaces;
  never substitute fake artwork or a placeholder cover as atmosphere.
- Animate opacity/position on established materials rather than animating blur
  radius or adding repeated full-screen effects. Do not add per-row blur.
- Reduced-motion, high-contrast, large-text, and platform fallback behavior
  requires real environment QA when supported; do not infer it from a JVM test.

## 8. Correct and incorrect implementation

| Correct | Incorrect |
| --- | --- |
| `GlassDialog(onDismissRequest = …) { … }` for a confirmation | A stock alert/dialog container with default colors and elevation |
| `DhunTextField` for search, names, and multiline TTML | A stock outlined field or a new one-off field decoration |
| `DhunSlider`, `DhunSwitch`, `DhunCheckbox`, `DhunProgressBar` | Calling their stock counterparts directly from a screen |
| `DhunNavigationBarItem` / `DhunNavigationRailItem` | Stock navigation containers/items with a transparent container but stock pill and metrics |
| Transparent `DropdownMenu` anchor around `TrackMenuSurface` | A visible stock popup plate inside the glass menu |
| Quiet `dhunContentRowSurface()` for ordinary rows | A shadowed, blurred glass card around every row |
| `GlassCard(opaqueBase = true)` or `GlassDialog` for high-opacity reading | Treating “opaque enough” as permission for a featureless flat plate |
| `GlassMaterialPolicy.shouldBlurArtwork` before blur | Installing a no-op blur modifier or adding a CPU blur fallback |
| Blur 0 = non-blurred dimmed artwork | Claiming blur 0 removes artwork |
| A separate light token/sheen/scrim policy | Inverting dark colors or copying dark highlights into light mode |
| Preserving callbacks and pending/error state during visual work | Closing dialogs, changing navigation, or dropping retry behavior for a visual rewrite |

A flat color is not inherently wrong: the theme base, quiet rows, and the
Queue/Related sheet's readability base intentionally use one. It is wrong when
it substitutes for the component that owns the requested glass surface.

## 9. Enforcement and review evidence

`python3 -m unittest discover -s scripts` runs
`scripts/test_design_system.py`. Its raw-call scan covers every Kotlin source in
the shared native UI, Android, and desktop roots; wrapper implementations under
`design/components` are intentionally outside that ban. The script also pins
critical callbacks and the product-identity wording. There is no current
allowlist of known stock screens.

Required review for a visual change:

1. inspect the stage-specific diff and confirm only intended surfaces changed;
2. run the available source/policy tests and exact-head CI compilation/tests;
3. capture real Android and desktop screenshots where a display/device exists;
4. check dark, OLED, and light over bright and dark artwork, plus large text,
   keyboard/screen-reader state, pending/error/empty/offline states, and the
   non-blur fallback;
5. record unsupported environments as **NOT RUN**. Source checks, green CI,
   package installation, and visual/audible acceptance are separate evidence.

Historical documents may record an older toolkit-centered target only when the
passage is clearly dated and labelled historical. Active instructions must link
here and must not revive that identity, the rejected background palettes, or
unimplemented component names.
