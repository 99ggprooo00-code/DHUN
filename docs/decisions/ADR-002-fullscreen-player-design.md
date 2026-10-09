# ADR-002: Full-Screen Now Playing — translucent frosted-glass design contract

## Status

**Accepted design direction, updated 2026-10-09.** This supersedes the 2026-09-05 wording “Material 3 only” as the visual target. DHUN's intended identity is **translucent frosted glass**, not the former Material 3 visual direction.

This is an incremental refinement of the existing player and shared design system, not a greenfield rewrite. The Kotlin Multiplatform architecture remains unchanged.

## Decision

> **Artwork-led, dark-first, translucent frosted glass.** Borrow the clarity of Apple Music's player hierarchy and the immersive approach of ViMusic/Vivi Music as inspiration, while keeping DHUN's own identity and using a lightweight 2D implementation.

Material 3 / Compose components may remain implementation primitives, but **Material 3 is not the product's visual target**. The final appearance must follow DHUN's glass design tokens and components rather than default M3 colors, elevations or surfaces.

“Frosted glass” means a controlled combination of cached artwork blur, translucent tinted surfaces, subtle highlights/borders, gradients and careful typography. It does **not** authorize a heavy 3D renderer, continuous full-resolution blur, platform-private glass APIs or a silent platform-stack change.

## Context and current implementation

| Layer | Location | Status |
|---|---|---|
| Source-neutral provider boundary | `MusicProvider` and stream resolver | Existing; UI must not depend on InnerTube payload types |
| Player engine/state | `DhunPlayer`, `QueueManager`, Media3 / VLCJ | Existing; platform verification remains separate |
| MiniPlayer + FullPlayer | `shared/ui/player/` | Existing; keep polishing in place |
| Blurred artwork background | FullPlayer + artwork color/cache path | Existing; blur must be cached and invalidated safely |
| Glass surfaces and design tokens | `shared/design/` | Source of truth for material, type, shape, spacing, motion and icons |
| Lyrics domain/providers | lyrics repository/parser and UI | Existing; hardware acceptance must be recorded separately |
| Website | `website/` | Separate static marketing site, not the native app |
| Browser mirror | `app-web/` | Separate engineering preview; no production playback claim while its limits remain |

## Visual rules

1. **Glass is the signature material.** Use a coherent family of translucent frosted surfaces, restrained tint, soft edge highlight, controlled shadow and gradient scrim. Do not treat every content card as a glass panel.
2. **Legibility wins.** Increase opacity/scrim or use an opaque fallback whenever text, controls, progress, focus rings or error states are hard to read.
3. **Sharp artwork stays sharp and fitted.** Cover art uses fit-to-card sizing and must not crop faces to fill the screen. The blurred background may extend full-bleed independently.
4. **Blur is prepared, not continuously recomputed.** Load/downscale artwork, process off the UI thread, cache by stable artwork/track key plus generation, and invalidate on artwork URL change. Late results for a previous track must not paint over the current track.
5. **Dialogs and sheets need a stable base.** Glass dialogs, form fields and sheets must use the shared components and sufficient opaque base so content behind them cannot compromise text entry or confirmation copy.
6. **Fallbacks are first-class.** On Android versions without supported real blur, low-end hardware, reduced-motion settings or browsers without backdrop-filter, use tint/gradient/opaque material that preserves the hierarchy.
7. **Motion stays lightweight.** Use short opacity/scale/position transitions; no continuous liquid distortion, per-frame full-resolution blur or heavy parallax.
8. **Use the shared tokens.** `DhunAppearance.kt`, `DhunTypography.kt`, `DhunShapes.kt`, `DhunSpacing.kt`, `DhunAnimations.kt`, `DhunIcons.kt` and existing glass policies are the source of truth. Do not introduce raw one-off styles without documenting the reason.
9. **Accessibility is mandatory.** Contrast, focus, keyboard/back, touch targets, reduced motion and high-contrast behavior must remain usable over both bright and dark artwork.
10. **Do not infer a web product from the design.** The marketing site and `app-web/` are separate surfaces; production browser playback remains governed by ADR-008.

## Full-screen player behavior

### Normal mode
- MiniPlayer expands into FullPlayer without restarting audio or resetting queue/position.
- Show fitted artwork, track/artist, progress, primary transport, secondary controls and Lyrics/Queue/Related access.
- Keep the bottom control cluster anchored; conditional content must not cause controls to jump or become clipped.
- The blurred artwork/scrim provides atmosphere behind the sharp artwork and player chrome.

### Lyrics / CC mode
- A clear Lyrics/CC control enters a lyrics-dominant presentation.
- Sharp artwork recedes with a lightweight scale/fade; the same track's cached blurred artwork remains behind a translucent frosted lyrics surface.
- Synced lyrics emphasize the current line, keep neighboring lines readable, scroll smoothly and allow tap-to-seek.
- Unsynced lyrics remain scrollable plain text and must not display fake timing emphasis.
- Playback controls and Back/dismiss remain reachable on short screens and landscape layouts.
- Entering or leaving lyrics mode must not restart playback, change queue order or reset seek position.
- Loading, unavailable, empty and error states each have explicit copy and a recovery/exit path.

## Navigation and overlay contract

- Back/swipe down closes FullPlayer without exiting the app or stopping audio.
- Back/Escape closes only the topmost layer.
- The player overlay must not intercept navigation-rail hit targets on wide layouts.
- Queue and related-track sheets must have measured, usable geometry and enough opacity for row text.
- Action controls inside rows/sheets must have distinct semantic handlers; opening a sheet and confirming a selection cannot share the same action identifier.

## Implementation and verification order

1. Preserve current player/provider architecture.
2. Fix correctness and error feedback for destructive actions (Clear downloads is an urgent example; see `docs/app-flow.md` and `docs/implementation-plan.md`).
3. Verify FullPlayer geometry, stable bottom controls, artwork fit and overlay hit-testing on real Android and Windows layouts.
4. Verify lyrics/CC mode transition, synced/unsynced states, no-lyrics/error states and tap-to-seek.
5. Verify blur caching, invalidation and low-end fallback; no per-frame blur.
6. Run contrast, focus, reduced-motion, high-contrast and responsive checks.
7. Update verification records and known limitations; do not call a design slice complete from compilation alone.

## Explicit non-goals

- React/Tauri migration or changing the Kotlin Multiplatform app architecture.
- A production web/PWA player or hosted proxy; follow ADR-008 and require explicit approval.
- iOS Liquid Glass, a heavyweight 3D renderer, continuous full-resolution blur or platform-private glass APIs.
- Rewriting player/provider logic purely for visual polish.
- Treating default Material 3 visual styling as the final design.

## Consequences

- `docs/ui-ux-design.md` is the detailed visual/interaction specification; this ADR is the binding design decision.
- When older references say “Material 3 only” or “M3 is the target,” treat them as superseded by this accepted update.
- Implementation components may still use Material libraries, but must be styled to the DHUN frosted-glass contract.
- Record remaining platform limitations honestly in `.ai/KNOWN_LIMITATIONS.md` and verification docs.
