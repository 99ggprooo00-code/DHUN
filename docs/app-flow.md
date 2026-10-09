# Dhun — Application Flow

## First launch
Launch → load local preferences → initialize platform capabilities → show Home. If offline, keep the shell usable and show a non-blocking notice. Basic local use should not require sign-in.

## Main navigation
- Home: recently played or curated sections where available.
- Search: query, results, result details.
- Library: saved items/playlists/history according to implemented features.
- Now Playing: mini-player opens the full-screen player.
- Settings/About: appearance, playback preferences, cache controls, privacy, licenses, version and upstream disclaimer.

Use bottom navigation on compact mobile layouts, a rail on tablet, and sidebar/optional queue panel on desktop. Preserve the information architecture.

## Search
Enter query → cancel/debounce prior request → call provider → normalize results → show result list. Handle empty query, no results, offline, upstream rate limit, unsupported item, and retry. Selecting a result opens details; Play is an explicit action.

## Playback
Play selected item → resolve metadata/playable source → show loading → initialize platform adapter → start playback → update mini-player and full player. On failure, stop loading, preserve context, show recoverable error, and avoid repeated automatic retries. Queue and engine must stay consistent.

## Full-screen player and lyrics
Mini-player tap → full-screen player. Show artwork, title/artist, progress, transport controls, queue, shuffle/repeat where supported, and lyrics/CC. Tapping lyrics/CC transitions to lyrics mode with blurred artwork behind a translucent readable surface. Playback continues. Lyrics can be timed, plain, unavailable, loading, or error. Seek from a lyric line only when valid timestamps and seek support exist.

## Library
Save/favorite/create local playlist → update local persistence → reflect immediately. Cloud sync requires a separate decision covering accounts, conflicts, export/deletion, privacy, and security.

## Platform-specific behavior
Android: system back, audio focus, media notification/session, headphone controls and lifecycle. Windows/Tauri: window behavior, keyboard shortcuts, safe native commands and media keys where supported. Web: browser history, autoplay restrictions, tab suspension, and capability checks.

## Global states
Every network-driven screen supports loading, content, empty, error/retry, offline, and stale cache if implemented. Do not discard queue/library state because of a recoverable error.
