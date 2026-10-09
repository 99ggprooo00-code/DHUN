# Dhun — UI/UX Design Specification

## Design direction
A music-first Material 3-inspired interface with a restrained glassy layer. Use other apps as research references, not assets to copy. Dhun must have its own identity and must not imply affiliation with Apple, YouTube, or Google.

## Visual system
- Surfaces: layered tonal backgrounds; translucent panels only when contrast remains strong.
- Glass: lightweight backdrop blur, subtle gradient tint, thin highlight/border, restrained elevation.
- Colors: centralized light/dark tokens; artwork accents must not compromise text contrast.
- Typography: clear hierarchy for screen titles, track title, artist, metadata, labels and timestamps.
- Consistent spacing, corner radii, icons, and interaction states.
- Opaque fallback for devices/browsers where blur is unsupported or expensive.

## Key screens
1. Home: top bar, search entry, content sections, skeletons.
2. Search: persistent query, optional type filters, result rows and overflow actions.
3. Detail: artwork/header, primary play action, track list.
4. Library: saved content, local-data indicators.
5. Mini-player: artwork, title/artist, play/pause, next.
6. Full-screen player: artwork, metadata, progress, transport, queue, shuffle/repeat and lyrics/CC.
7. Lyrics mode: blurred artwork backdrop, translucent panel, active-line emphasis, smooth transition.
8. Settings/About: preferences, privacy, licenses, version and upstream disclaimer.

## Full-screen player behavior
Artwork should scale responsively and essential controls must remain visible on short screens. Lyrics/CC toggles lyrics without stopping playback. Use lightweight blur and 2D transitions; avoid heavy 3D/page-flip effects as default. Lyrics need a scrim or surface for readability, with explicit loading/unavailable/error states. Respect reduced-motion settings.

## Responsive behavior
Compact mobile: bottom navigation and thumb-friendly controls. Tablet: navigation rail and wider content. Desktop: sidebar, centered content, optional queue panel, keyboard shortcuts/help. Web remains usable without native capabilities.

## Accessibility
Target WCAG 2.2 AA for the website: contrast, keyboard operation, visible focus, semantic landmarks/headings, form labels, alt text, reduced motion, and states not communicated by color alone. Test Android screen readers and enlarged text. No hover-only actions.

## Component states and acceptance
Buttons need focus/pressed/disabled/loading states. Lists need loading/empty/error/content. Player needs idle/loading/playing/paused/buffering/error. Lyrics need loading/timed/plain/unavailable/error. Menus/dialogs need keyboard dismissal and focus management. Test long titles, missing artwork, large text, reduced motion, offline mode, and narrow viewports.
