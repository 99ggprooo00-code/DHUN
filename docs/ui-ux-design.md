# DHUN — UI/UX Design Specification

**Status:** Supporting product and interaction contract (updated 2026-10-31). Native materials, component choices, background preferences, accessibility values, and fallback rules defer to the authoritative [DHUN glass system](design/glass-system.md).

## 1. Design identity

DHUN's visual identity is **translucent frosted glass**, dark-first, artwork-led, calm and immersive. It is inspired by the clarity of Apple Music's player hierarchy and the immersive feel of ViMusic/Vivi Music, but must remain recognizably DHUN and must not copy proprietary assets or imply affiliation.

Compose toolkit components can remain implementation primitives, but their default surface, elevation, shape, typography and color treatment must not define the final look. The design target is layered glass, soft blur, artwork-derived color, controlled gradients and clear typography. “Frosted glass” does not require a heavyweight 3D/liquid renderer, platform-private glass APIs or continuous full-resolution blur.

## 2. Visual principles

1. **Legibility beats translucency.** If text, icons, focus indicators or progress are hard to read over artwork, increase scrim/opacity or use an opaque fallback.
2. **Blur is atmosphere, not content.** Sharp artwork is shown separately and must not be cropped to fill the screen; blurred artwork can extend behind the screen.
3. **Consistent material.** One shared family of frosted surfaces, edge highlights, soft borders, controlled tint and depth; no random “glass” treatment on every card.
4. **Content first.** Home/Search/Library remain scannable; reserve stronger glass and immersive transitions for player, navigation chrome, dialogs, sheets and floating controls.
5. **Low-cost rendering.** Use list-tier artwork, stable keys and the existing artwork cache where provided; never add full-resolution or per-row blur work to animation frames.
6. **Graceful fallback.** Older Android, low-memory devices, reduced-motion preferences and browsers without reliable backdrop filters use a legible translucent/opaque surface with gradients.
7. **Accessible by design.** Glass cannot reduce contrast, hide focus, make controls hard to hit or create motion that causes discomfort.

## 3. Source of truth

Use the existing shared design tokens and components, not ad hoc styling:
- `shared/src/commonMain/kotlin/dev/dhun/design/DhunAppearance.kt`
- `DhunTypography.kt`, `DhunShapes.kt`, `DhunSpacing.kt`, `DhunAnimations.kt`, `DhunIcons.kt` in that directory
- The component map and material policies in [docs/design/glass-system.md](design/glass-system.md), including dialog opacity, quiet content surfaces, navigation, and lyrics material.
- `app-web/src/css/tokens.css` and generated `app-web/src/js/icons.js` must mirror native values; icons are generated from native source, not manually duplicated.
- `website/css/tokens.css` is **generated** from `app-web/src/css/tokens.css` by `website/tools/sync-tokens.mjs` (name the `--dhun-*` source in every declaration, carry both schemes in one `light-dark()` pair). Never hand-edit it; `npm run build` regenerates it and `scripts/website_quality.py` re-derives the comparison without Node.

When tokens conflict with an old prose document, reconcile the document with the authoritative glass page and current token source in a deliberate commit. Do not quietly reintroduce stock toolkit styling — `scripts/test_design_system.py` scans every native UI source so this is caught mechanically rather than only in review.

## 4. Color, typography, geometry

- Dark-first neutral base with restrained artwork-derived accent colors. Accent color must not determine the only meaning of state.
- Frosted surfaces use a controlled combination of backdrop blur, tint, translucent fill, subtle edge/highlight and gradient scrim. Document token values in code, not scattered inline constants.
- Text hierarchy distinguishes screen title, track title, artist, secondary metadata, section labels, timestamps and helper/error text.
- Track titles may wrap only where designed; list rows use consistent alignment, stable row height, ellipsis where appropriate and a reachable overflow target.
- Use a consistent spacing scale, shape/radius scale, icon family and motion scale. No one-off radii/spacing for a single dialog without an explicit reason.
- Ordinary music rows on Home, Search, Library and browse pages share `dhunContentRowSurface()` (translucent *mid-tone* fill, medium radius); About & Legal and Settings use the stronger `dhunContentSurface(reading = true)` where text needs a stable reading ground. Both follow the active dark/light palette without becoming black tiles or white sheets over artwork. The Queue/Related sheet keeps its own artwork/veil and row recipe; page-level surface tuning must not silently change it. Top-level filters, including Library tabs, use `DhunFilterChip` so selection has the same shape and accessible state everywhere.
- Artwork thumbnails preserve subject/faces with fit-to-card sizing. Ambient blurred background may fill/crop independently.
- Touch targets meet platform accessibility expectations; desktop controls have visible focus and keyboard operation.

## 5. Glass material rules

### Player backdrop
Artwork changes → resolve the list-tier URL → load with a stable artwork/track key → apply the shared blur gate behind sharp content → derive a restrained accent/tint → compose **one** theme-aware translucent veil over the screen artwork. Reuse `BlurredArtworkCache` where the component already provides it; do not claim one caching strategy for every layer. Do not stack a black dim, a light/dark scrim and an ambient wash on top of one another. The veil must fade with the image on load/failure, leaving the base color untouched. Invalidate keyed work when the artwork URL changes and guard against late async results painting the previous track's image.

### Surfaces
Use glass for player chrome, lyrics, navigation band/rail where designed, queue sheets, dialogs and floating actions. Avoid turning every content list/card into a translucent slab. Use opaque base under dialogs and sheets when content behind would compromise reading or text fields.

**"Sufficiently opaque" is an opacity floor, not a flat surface** (`GlassMaterialPolicy.DIALOG_BODY_ALPHA_DARK/LIGHT`). Dialogs use `GlassDialog` for a high-opacity body gradient, sheen and directional edge; artwork-bearing sheets use `LyricsArtworkSheet` with their own readable base. Not every dialog loads or frosts artwork. The component map and numbers live in [docs/design/glass-system.md](design/glass-system.md); that page is normative for appearance.

### Background preferences
The accepted preference model is deliberately small: **Background blur** and
**Background brightness**, with the existing stored defaults of 100% and 55%.
Blur is shown only where real blur is supported; brightness remains available
for the dimmed non-blur fallback. Zero blur means sharp, dimmed artwork rather
than no artwork. Dynamic artwork accents affect controls, not a per-surface
background palette.

Do not restore the historical five-palette editor or the later four-surface
custom-colour experiment. The authoritative glass page owns the exact fallback,
persistence, and correct/incorrect rules.

### Fallbacks
- Android versions where real blur is unavailable: sharp, dimmed artwork plus the theme veil and readable content surfaces; no CPU blur fallback.
- Browser: feature-detect backdrop-filter; never make text contrast depend on blur being supported.
- Reduced motion: remove nonessential spring/parallax transitions while preserving state feedback.
- High contrast/forced colors: preserve control borders, focus rings, selected state and error/success distinction.

## 6. Full-screen player

### Normal mode
- Full-screen Now Playing is a core signature screen.
- Keep sharp artwork fitted in the upper/central field; do not crop faces to make a full-bleed cover.
- Track title and artist remain readable; secondary metadata is visually quieter.
- Seek/progress and transport controls form a stable bottom cluster; essential controls must not jump vertically when lyrics or queue content appears.
- Like, queue, repeat, shuffle and overflow controls follow one shared spacing/touch-target contract.
- MiniPlayer expands to FullPlayer without resetting playback or queue.

### Lyrics / CC mode
- A clear Lyrics/CC control enters lyrics-dominant mode. In this mode, the player’s ⋮ opens lyrics controls (romanization, provider, synced timing and match search), not track actions; outside CC mode ⋮ still opens track actions. Keep these controls out of the lyric text area.
- Sharp artwork recedes by a lightweight scale/fade transition; the same track's blurred artwork remains behind a frosted lyrics surface.
- Current synced line is visually dominant, neighbors remain readable, auto-scroll is stable and line tap seeks.
- Unsynced lyrics are readable without false karaoke emphasis.
- Playback controls and Back/dismiss remain reachable; entering/leaving lyrics never restarts playback.
- Loading, unavailable, no-lyrics and error states each have designed copy and an escape path.

## 7. Dialogs, sheets and action controls

A control's design is incomplete without its behavior:
- Each action has a unique semantic action name; “open dialog” and “confirm action” are not the same event.
- Dialog has clear title, scope, body, primary action, secondary cancel, focus behavior, Escape/back behavior and result state.
- Destructive confirmation identifies the data/files affected and the count/scope. Pending state disables repeated confirmation.
- Dialog remains legible over the underlying screen: use an opaque enough base; form fields must not inherit transparent/low-contrast default styling.
- After success, show the actual resulting state; after failure, preserve state and show retry/recovery. Do not close a dialog and swallow an exception.
- Empty states include a useful next action; loading states use stable skeleton/progress; errors explain what happened and how to recover.

### Required example: Clear all downloads
The confirmation must remain pending until persistence/filesystem work returns, prevent duplicate submission, close or clear selection only after success, and keep a visible failure with retry available. This pending → observable result → success/error contract is behavior, not optional visual polish.

## 8. Navigation and website design

### Native app
- Home/Search/Library remain the primary navigation; detail/settings/player destinations follow shared AppNavState rules.
- Active/selected state must be unmistakable in glass surfaces without relying on color alone.
- On two-pane desktop layout, FullPlayer overlay must not cover the navigation rail's hit area.
- Back, Escape and dismiss actions close only the topmost layer.

### Marketing website
- The site is a static discovery surface — not the app — but it wears the app's clothes: since 2026-10-16 every route renders the application's shell (glass app bar, bottom navigation below 840 px and a rail above it, a docked bar into `/app/`) and the app's appearance controls, and it still ships zero client-side JavaScript.
- One navigation element on Home, Features, Interface and 404. Brand → Home; every route links to other internal routes; active route has accessible current-page semantics, exactly one per page.
- All internal links must include the `/DHUN/` project base path at deployment. Hard-coded root links like `/features/` can send visitors to the wrong location.
- Verify direct URL, refresh, back/forward, CTA and footer links on the deployed canonical origin. A local test at `/` alone cannot prove project-page URLs work.
- Do not imply that the browser client is guaranteed to play audio: it resolves streams and names its failures, and the native builds stay the supported players.

## 9. Motion and interaction

- Short, purposeful transitions for player expansion, lyrics mode, sheets and feedback; no gratuitous continuous movement.
- Avoid blur recalculation during animation. Animate opacity/scale/position on cached material.
- Every pressable control provides pressed, focused, disabled and pending states as applicable.
- Pointer, touch, keyboard and screen-reader operation must reach equivalent core actions.
- Do not place a clickable control inside another clickable row without clear event separation and test coverage.

## 10. Required design QA

For each screen/dialog: check populated, empty, loading, error, offline, disabled and pending states; light/dark where supported; short and wide viewports; large text; keyboard focus; screen reader labels; contrast on varied artwork; reduced motion; blur fallback; hit-target overlap; clipping/scrollability; back/dismiss behavior. Use screenshots/visual review on actual devices where possible and record unverified environments honestly.
