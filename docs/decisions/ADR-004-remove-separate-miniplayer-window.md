# ADR-004 — Remove the separate desktop mini-player window

- **Status:** ACCEPTED (user decision, 2026-09-06)
- **Supersedes:** MASTER_PROMPT.md Phase 12 step 3 ("Mini-player window: 320×88
  always-on-top second window")
- **Affected code:** `app-desktop` — `Main.kt`, `ui/MiniPlayerWindow.kt`
  (deleted), `smct/Smct.kt` (`moveWindow` + `GetWindowRect`/`SetWindowPos`
  section removed), `shared/design/DhunSpacing.kt` (two window-only tokens
  removed)

## Context

Phase 12 specified a separate 320×88 always-on-top mini-player **window** for
the desktop app. It was implemented as a second Compose `Window` in
`Main.kt`, visible from startup, toggled with Ctrl+M.

User report on Windows (the rolling `test` build published
`2026-09-06T06:51:40Z`): **opening DHUN also opens a second small mini-player
window.** The user's direction: the separate mini-player is **not needed**,
because the app already ships a native in-app mini-player — the docked
`MiniPlayer` composable (Phase 08) that sits above the bottom nav inside the
main window.

This is a product decision by the owner, recorded here per MASTER_PROMPT AI
rule 8 (a locked plan item is being removed, not silently diverged from).

## Decision

**Remove the separate mini-player window entirely.** The docked in-app
MiniPlayer (Phase 08, shared `dev.dhun.ui.player.MiniPlayer`) is the
product's mini-player. The second window, its Ctrl+M toggle, and all code
that existed only for it are deleted — no hidden window, no dead toggle, no
stub left behind.

## Alternatives considered

1. **Keep the window, default hidden, Ctrl+M opens it.** Rejected: the user
   does not want the window at all; an opt-in window that still shows in the
   taskbar (Compose Desktop 1.8.2 has no `skipTaskbar`) preserves the exact
   annoyance. Also leaves code with no owner.
2. **Keep the window visible only while the main window is minimized.**
   Rejected: more window-management complexity (minimize/restore state,
   focus juggling) to preserve a feature the user has said is unwanted.
3. **Remove it (chosen).** Matches the user's direction and AI rule 7
   (no dead code). Reversal is trivial: restore `MiniPlayerWindow.kt`,
   `Smct.moveWindow`, and the window block from git history.

## Consequences

- Desktop DHUN is a single-window app; the tray icon and SMTC still cover
  the "app hidden but playing" case (close-to-tray, default on).
- Ctrl+M is freed; the remaining shortcuts are Space / ←→ / Ctrl+←→ /
  Ctrl+F / Ctrl+Q.
- Phase 12 acceptance item 3 (mini-player window mirrors state live) is
  **superseded**; the docked mini-player is covered by Phase 08 verification.
- KNOWN_LIMITATIONS loses the "mini-player shows in the taskbar" limitation;
  the `Smct.moveWindow`-based window dragging only existed for this window.
- Documentation updated in the same PR: `.ai/ROADMAP.md`,
  `.ai/KNOWN_LIMITATIONS.md`, `CHANGELOG.md`, `docs/verification/12-desktop-native.md`,
  `docs/verification/14-release.md`, `.ai/MASTER_PROMPT.md` (Phase 12 step 3
  pointer).
