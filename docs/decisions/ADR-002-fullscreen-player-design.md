# ADR-002: Full-Screen Now Playing — design lock (Apple clarity × ViMusic immersion × Material 3 glass)

## Status
Accepted (2026-09-05) — **design contract**. Implementation is **incremental polish
on existing Phase 08/11 code**, not a greenfield player. Does not reorder the
locked 14-phase plan.

**Visual system (user lock 2026-09-05): Material 3 only. No Liquid Glass.**
Translucent M3 surfaces + one-shot `Modifier.blur` on artwork backgrounds +
gradient scrims. Glass tokens (`DhunColors.glass`) remain the atmosphere
layer — never a separate Liquid Glass renderer, never continuous full-res
reblur, never platform-private glass APIs.

## Context

DHUN already ships:

| Layer | Location | Status |
|---|---|---|
| Source-neutral provider boundary | `MusicProvider` — UI never sees InnerTube types | ✅ Phase 02 |
| Player engine + state | `DhunPlayer`, `QueueManager`, Media3 / vlcj | ✅ Phase 03/04 |
| MiniPlayer + FullPlayer | `shared/ui/player/{Mini,Full}Player.kt` | ✅ Phase 08 code; hardware OPEN |
| Blurred artwork bg + tint + scrim | `FullPlayer` + `ArtworkColorExtractor` + `Modifier.blur` | ✅ Phase 06/08 |
| Glass surfaces | `GlassCard`, tokens in `shared/design/` | ✅ Phase 06 |
| Lyrics domain | `Lyrics` sealed (`Synced`/`Unsynced`/`NotAvailable`), `LyricsLine(startTimeMs)` | ✅ Phase 02/11 |
| Lyrics providers | cache → YTM → LRCLIB, `LrcParser`, tab with tap-to-seek | ✅ Phase 11 code; hardware OPEN |
| Queue / Related tabs | `PlayerTabs.kt` | ✅ Phase 08 |

A design brief (2026-09-05) proposed elevating Full-Screen Now Playing +
CC/Lyrics immersion as a signature DHUN experience, referencing Apple Music
hierarchy, ViMusic immersion, and VIVI Material 3 / dynamic artwork — with an
explicit **lightweight** glass path (2D blur + translucent M3 + gradients),
**not** a full Liquid Glass renderer.

That brief also used milestone labels (M1/M2/M3) and stack assumptions
(Tauri/Web) that **do not match this repository**. This ADR maps the useful
design intent onto the **actual** KMP stack (Android + Desktop JVM only; Web
deferred per MASTER_PROMPT).

## Decision — design philosophy (locked)

> **Apple Music's clarity + ViMusic's immersive philosophy + VIVI's Material 3 /
> dynamic-artwork direction + DHUN's lightweight cross-platform implementation.**

Rules:

1. **Material 3 is structure. Glass is atmosphere.**  
   Glass only on: player controls, lyrics surface, queue sheet, transient
   chrome, floating actions. Home/Search/Library stay conventional M3 dark.
2. **Material 3 only — Liquid Glass is forbidden (user lock).**  
   Path: artwork → (optional downscale) → blur **once** per track change →
   tint from `ArtworkColorExtractor` → gradient scrim → translucent M3
   surface (`surfaceElevated` / `DhunColors.glass`) → cache via
   `BlurredArtworkCache`. Re-blur only on track/URL change, never every
   frame. Do not adopt iOS Liquid Glass, Windows Acrylic-as-primary, or
   any continuous backdrop-filter fashion that fights battery/low-end.
3. **UI never knows the stream source.**  
   `PlayerScreen → PlayerViewModel → DhunPlayer → StreamResolver → MusicProvider`.
   InnerTube / yt-dlp stay behind the provider/extraction boundary (already true).
4. **Lyrics are first-class, not a side panel afterthought.**  
   Domain already has timed lines; UI must support both synced karaoke-style
   emphasis and unsynced scrollable text (already true in `LyricsTabContent`).
5. **Gestures stay simple** (do not copy every experimental ViMusic gesture):
   - swipe down / back → collapse FullPlayer (never exit app)
   - horizontal swipe on artwork stage → previous / next (optional polish)
   - tap Lyrics/CC → lyrics-dominant mode (see below)
   - tap lyric line → seek (already)
   - queue via existing tab / sheet

## Decision — lyrics-dominant mode (the new interaction)

Today FullPlayer uses bottom tabs `Lyrics | Queue | Related`. Keep that.

**Add** a lyrics-dominant presentation when Lyrics is active (or when a
dedicated CC control is tapped):

```
NORMAL                          LYRICS-DOMINANT
┌─────────────────────┐         ┌─────────────────────────────┐
│  large centered art │         │ full-bleed blurred artwork  │
│  title / artist     │   →     │ dark scrim + M3 translucent │
│  seek + transport     │         │ surface over it             │
│  tabs               │         │ previous / CURRENT / next   │
└─────────────────────┘         │ transport remains reachable │
                                └─────────────────────────────┘
```

- Artwork **recedes** (scale down / fade) rather than navigating away.
- Background = **cached** blurred artwork (same pipeline as FullPlayer bg).
- Lyrics surface = translucent M3 (`GlassCard` / token glass), **not** opaque.
- Synced: current line large/bright/centered; neighbors dim; auto-scroll;
  tap-to-seek (already implemented — polish motion only).
- Unsynced: scrollable plain text (already).
- Empty / error: existing `EmptyView` / `ErrorView`.

This is **polish on Phase 08/11**, tracked as player UX slices P3–P8 below —
not a new phase that invalidates completed work.

## Architecture (already matches; keep it)

```
PlayerScreen (FullPlayer / MiniPlayer)
  ├── PlayerBackground (blurred art + tint + scrim)   # exists
  ├── ArtworkStage                                     # exists
  ├── TrackMetadata / Progress / Controls              # exists
  ├── SecondaryControls (shuffle / repeat / volume)    # exists
  └── PlayerTabs → LyricsOverlay | Queue | Related     # exists
         └── LyricsList (synced | unsynced | empty)    # exists

UI → PlayerViewModel → DhunPlayer → platform engine
                  ↘ LyricsRepository → cache / YTM / LRCLIB
                  ↘ MusicProvider.relatedTracks / getStreamInfo
```

Background processor contract (implement if missing as an explicit type):

```
onTrackArtworkChanged(url)
  → load bitmap (Coil)
  → downscale
  → blur off-main
  → derive ArtworkColors
  → cache by trackId (+ generation)
  → FullPlayer / Lyrics-dominant only read the cache
```

Do **not** re-process full-resolution art continuously.

## Implementation order (safe; does not rewrite the 14-phase plan)

The locked plan is still MASTER_PROMPT Phases 01–14. Player polish inserts
**inside** remaining Phase 08 hardware acceptance and Phase 14 robustness,
and as post-v0.1.0 trajectory items if needed — **after** extraction is
honestly classified (CI-network vs residential) and domain/provider stay
source-neutral (already).

| Slice | Name | Depends on | Notes |
|---|---|---|---|
| P0 | Extraction truthfulness | live | Rot-drill + expanded tokenless client chain (in flight). Do not build glass on a red stream path without residential evidence. |
| P1 | Player state already exists | — | Idle/Playing/Paused/Buffering/Error via `PlaybackState` — do not reinvent. |
| P2 | Basic player | done | Mini + Full transport — Phase 08. |
| P3 | Full-screen Now Playing hierarchy polish | P2 | 🟨 lyrics-dominant weight shift in FullPlayer (2026-09-05). |
| P4 | Dynamic artwork background cache | P2 | 🟨 `BlurredArtworkCache` key once-per-track (Compose blur still on layer). |
| P5 | Lyrics domain + providers | done | Phase 11. |
| P6 | Lyrics-dominant mode | P3+P5 | 🟨 FullPlayer: artwork recedes + M3 translucent lyrics surface when Lyrics tab selected. |
| P7 | Blur + translucent M3 refinement | P4+P6 | Token-only; <API 31 scrim fallback already in KNOWN_LIMITATIONS. |
| P8 | Smooth lyric sync motion | P5 | Spring emphasis, better scroll anchoring. |
| P9 | Gesture / animation polish | P3–P8 | Horizontal skip swipe optional; keep simple. |

**Do not** start P7–P9 Liquid-adjacent work before P0 residential/extraction
classification and P1–P2 hardware smoke. Beautiful player on a broken stream
path is how the previous attempt died (docs-first).

## Branch policy (this repo)

Arena sessions are **pinned to one branch** (`arena/<id>-dhun`). Do not create
parallel long-lived branches from the agent for the same session.

Human / multi-session policy:

| Keep while active | Delete after merge |
|---|---|
| `arena/*` session branches until PR merges | stale `test-player-final-v2` style names |
| topic PRs: extraction, player-polish, lyrics-motion | anything not representing coherent work |

`main` stays release-grade. One rolling `test` pre-release (existing policy).

## Non-goals (explicit)

- Web / PWA / Tauri player (Web deferred; Desktop is Compose JVM + vlcj).
- **Liquid Glass** (any form), continuous full-res blur, per-frame blur,
  platform-private glass APIs as a hard dependency.
- Cookies / PO-token minting without a separate ADR + user sign-off.
- Rewriting `MusicProvider` / `DhunPlayer` for aesthetics.
- Replacing the 14-phase MASTER_PROMPT with M1/M2/M3 labels.

## Consequences

- Design reviews judge FullPlayer against this ADR.
- Phase 08 / 11 hardware checklists gain lyrics-dominant + blur-cache items
  when those slices land.
- KNOWN_LIMITATIONS keeps the <API 31 blur floor honest.
- Trajectory table (Phase 15+) may list "player immersion polish" as a
  candidate **after** v0.1.0 — not before extraction truth + soaks.

## References

- MASTER_PROMPT §Phase 08 / 11, AI Behavior Rules (code-first)
- `docs/verification/08-player.md`, `docs/verification/11-lyrics.md`
- ADR-001 (extraction; rot-drill category-8 CI-network rule)
- Design brief 2026-09-05 (Apple / ViMusic / VIVI / lightweight glass)

## Addendum — 2026-09-05 execution (Material 3 only)

Shipped on `arena/01a07170-dhun` without Liquid Glass:

- `PlaybackState.Recovering` + `StreamRecoverySignal` + Android 403 path →
  FullPlayer / MiniPlayer **"Reconnecting…"** chip (M3 surface).
- `BlurredArtworkCache` — once-per-track key (unit-tested).
- FullPlayer lyrics-dominant: Lyrics tab shrinks artwork stage, expands
  lyrics surface with `surfaceElevated` translucent fill.

Still OPEN: residential stream smoke, hardware Phase 08/11 checklists,
audio-segment cache, soaks, v0.1.0.
