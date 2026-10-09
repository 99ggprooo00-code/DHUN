# DHUN — UI/UX Design Specification

**Status:** Design contract for review, aligned to the user's updated direction (2026-10-09). This supersedes the stale “Material 3 only” wording in ADR-002 once the ADR update in this branch is accepted.

## 1. Design identity

DHUN's visual identity is **translucent frosted glass**, dark-first, artwork-led, calm and immersive. It is inspired by the clarity of Apple Music's player hierarchy and the immersive feel of ViMusic/Vivi Music, but must remain recognizably DHUN and must not copy proprietary assets or imply affiliation.

Material 3 is **not the visual target**. Compose Material components can remain implementation primitives, but their default surface, elevation, shape, typography and color treatment must not define the final look. The design target is layered glass, soft blur, artwork-derived color, controlled gradients and clear typography. “Frosted glass” does not require a heavyweight 3D/liquid renderer, platform-private glass APIs or continuous full-resolution blur.

## 2. Visual principles

1. **Legibility beats translucency.** If text, icons, focus indicators or progress are hard to read over artwork, increase scrim/opacity or use an opaque fallback.
2. **Blur is atmosphere, not content.** Sharp artwork is shown separately and must not be cropped to fill the screen; blurred artwork can extend behind the screen.
3. **Consistent material.** One shared family of frosted surfaces, edge highlights, soft borders, controlled tint and depth; no random “glass” treatment on every card.
4. **Content first.** Home/Search/Library remain scannable; reserve stronger glass and immersive transitions for player, navigation chrome, dialogs, sheets and floating controls.
5. **Low-cost rendering.** Prepare/downscale/cache artwork blur per track/artwork generation; never run full-resolution blur every animation frame.
6. **Graceful fallback.** Older Android, low-memory devices, reduced-motion preferences and browsers without reliable backdrop filters use a legible translucent/opaque surface with gradients.
7. **Accessible by design.** Glass cannot reduce contrast, hide focus, make controls hard to hit or create motion that causes discomfort.

## 3. Source of truth

Use the existing shared design tokens and components, not ad hoc styling:
- `shared/design/DhunAppearance.kt`
- `DhunTypography.kt`, `DhunShapes.kt`, `DhunSpacing.kt`, `DhunAnimations.kt`, `DhunIcons.kt`
- Existing glass component and material policies, including dialog opacity policy and lyrics material policy.
- `app-web/src/css/tokens.css` and generated `app-web/src/js/icons.js` must mirror native values; icons are generated from native source, not manually duplicated.

When tokens conflict with an old prose document, the accepted updated design contract and current token source must be reconciled in a deliberate commit. Do not quietly reintroduce Material 3 default styling.

## 4. Color, typography, geometry

- Dark-first neutral base with restrained artwork-derived accent colors. Accent color must not determine the only meaning of state.
- Frosted surfaces use a controlled combination of backdrop blur, tint, translucent fill, subtle edge/highlight and gradient scrim. Document token values in code, not scattered inline constants.
- Text hierarchy distinguishes screen title, track title, artist, secondary metadata, section labels, timestamps and helper/error text.
- Track titles may wrap only where designed; list rows use consistent alignment, stable row height, ellipsis where appropriate and a reachable overflow target.
- Use a consistent spacing scale, shape/radius scale, icon family and motion scale. No one-off radii/spacing for a single dialog without an explicit reason.
- Artwork thumbnails preserve subject/faces with fit-to-card sizing. Ambient blurred background may fill/crop independently.
- Touch targets meet platform accessibility expectations; desktop controls have visible focus and keyboard operation.

## 5. Glass material rules

### Player backdrop
Artwork changes → load/downscale artwork → blur off the UI thread once → derive a restrained accent/tint → compose gradient scrim → cache by stable artwork/track key and generation. Invalidate the cache when the artwork URL changes; guard against late async results painting the previous track's image.

### Surfaces
Use glass for player chrome, lyrics, navigation band/rail where designed, queue sheets, dialogs and floating actions. Avoid turning every content list/card into a translucent slab. Use opaque base under dialogs and sheets when content behind would compromise reading or text fields.

### Fallbacks
- Android versions where real blur is unavailable: gradient/tint/opaque material fallback.
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
- A clear Lyrics/CC control enters lyrics-dominant mode.
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

### Known urgent example: Clear all downloads
The current code calls the clear callback and dismisses the dialog immediately, while the ViewModel launches the operation and swallows errors. The design requirement is pending → observable result → success/error, not instant dismissal. This is a behavior defect as well as a visual UX defect.

## 8. Navigation and website design

### Native app
- Home/Search/Library remain the primary navigation; detail/settings/player destinations follow shared AppNavState rules.
- Active/selected state must be unmistakable in glass surfaces without relying on color alone.
- On two-pane desktop layout, FullPlayer overlay must not cover the navigation rail's hit area.
- Back, Escape and dismiss actions close only the topmost layer.

### Marketing website
- The site is a separate static marketing/download discovery surface, not the app itself.
- Shared header/footer on Home, Features, Interface and 404. Brand → Home; every route links to other internal routes; active route has accessible current-page semantics.
- All internal links must include the `/DHUN/` project base path at deployment. Hard-coded root links like `/features/` can send visitors to the wrong location.
- Verify direct URL, refresh, back/forward, CTA and footer links on the deployed canonical origin. A local test at `/` alone cannot prove project-page URLs work.
- Do not imply that the browser mirror is a production music player.

## 9. Motion and interaction

- Short, purposeful transitions for player expansion, lyrics mode, sheets and feedback; no gratuitous continuous movement.
- Avoid blur recalculation during animation. Animate opacity/scale/position on cached material.
- Every pressable control provides pressed, focused, disabled and pending states as applicable.
- Pointer, touch, keyboard and screen-reader operation must reach equivalent core actions.
- Do not place a clickable control inside another clickable row without clear event separation and test coverage.

## 10. Required design QA

For each screen/dialog: check populated, empty, loading, error, offline, disabled and pending states; light/dark where supported; short and wide viewports; large text; keyboard focus; screen reader labels; contrast on varied artwork; reduced motion; blur fallback; hit-target overlap; clipping/scrollability; back/dismiss behavior. Use screenshots/visual review on actual devices where possible and record unverified environments honestly.
