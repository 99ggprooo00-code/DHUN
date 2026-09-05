# Roadmap audit — 2026-09-05 (development-stage, file-backed)

This is an **honest** audit against the **locked 14-phase plan** in
`.ai/MASTER_PROMPT.md` and live status in `.ai/ROADMAP.md`. It is **not**
an M1/M2/M3 matrix from a different product brief.

**Stack fact (do not unlearn):** Kotlin Multiplatform · Compose Multiplatform ·
Android (Media3) + Desktop JVM (vlcj) · **no** Tauri, **no** Web target in v1,
**no** npm app. "npm run build" ≙ Gradle CI; "installer" ≙ jpackage MSI.

**Audit method:** repository tree + verification docs + CI/rot-drill evidence
on 2026-09-05. Hardware acceptance remains OPEN where marked 🟨 even when
code is merged — per ROADMAP rules (done = pushed + CI green + hardware
where required).

---

## Phase matrix (01–14)

| # | Phase | Code | CI | Live/hardware | Verdict | Primary paths |
|---|-------|------|----|---------------|---------|---------------|
| 01 | Extraction spike | ✅ | ✅ | probe PASS historically; 2026-09-05 CI-IP gated | ✅ spike done; rot is maintenance | `tools/playback-probe`, `docs/research/01`, ADR-001 |
| 02 | Provider & domain | ✅ | ✅ tests | smoke historically PASS | ✅ | `MusicProvider`, `core/Entities`, `innertube/`, `extraction/` |
| 03 | Android + Media3 | ✅ | ✅ APK | on-device partial; OEM soak OPEN | 🟨 | `app-android/`, `AndroidDhunPlayer`, `DhunPlaybackService` |
| 04 | Desktop + vlcj | ✅ | ✅ compile via probe | on-desktop checklist OPEN | 🟨 | `app-desktop/`, `DesktopDhunPlayer` |
| 05 | Data layer | ✅ | ✅ | restore/favorites paths coded | ✅ code; more HW optional | `sqldelight/`, `SqlDelightRepositories`, `NowPlayingPersistence` |
| 06 | Design system | ✅ | ✅ | catalogue; blur floor documented | ✅ | `shared/design/` (`GlassCard`, tokens, Coil) |
| 07 | Home & Search | ✅ | ✅ merged | HW OPEN | ✅ code | `ui/home`, `ui/search`, VMs |
| 08 | Player UI | ✅ | ✅ merged | **16-check HW OPEN** | 🟨 signature surface exists | `ui/player/FullPlayer`, `MiniPlayer`, `PlayerTabs` |
| 09 | Browse pages | ✅ | ✅ merged | HW OPEN | 🟨 | `ui/browse/*`, browse parsers |
| 10 | Library & history | ✅ | ✅ merged | HW OPEN | 🟨 | `ui/library`, `RecordPlay` |
| 11 | Lyrics | ✅ | ✅ parser tests | **5-accept HW OPEN** | 🟨 **not "unimplemented"** | `lyrics/*`, `LyricsTabContent`, LRCLIB+YTM+cache |
| 12 | Desktop native | 🟨 | compile green historically | tray/SMTC/MSI HW OPEN | 🟨 | `desktop/native`, `smct/`, `MiniPlayerWindow` |
| 13 | Android polish | 🟨 | CI green | rotation/shortcuts/soak OPEN | 🟨 | `MainActivity`, `shortcuts.xml`, shell rail |
| 14 | Robustness + rot + v0.1.0 | 🟨 | PR CI green; **live drill RED** | soaks/release OPEN | 🔴 stream path on CI-IP | `rot-drill.yml`, issue #14, expanded client chain |

**Deferred (not designed, not stubbed):** Web/PWA, Android Auto, Cast, EQ,
sync, downloads beyond cache, widgets, jump lists, cookie sign-in, themes
beyond dark-first — see ROADMAP trajectory 15–30.

---

## Mapping the external brief → DHUN reality

| Brief label | What it meant in the brief | DHUN equivalent | Actual status |
|---|---|---|---|
| M1 native local-player regression | regression matrix / probe | Phase 01 probe + Phase 14 rot-drill | 🟢 infrastructure; 🔴 live resolve on Actions IP (cat-8) |
| M2 Fluent navigation shell | app chrome | Phase 07 shell + Phase 13 rail/insets | 🟨 in code; not "Fluent"/WinUI |
| M3 source-neutral domain | contracts | Phase 02 `MusicProvider` + entities | ✅ **already the law** — do not rebuild |
| Playback/extraction | streams | ADR-001 resolvers + rot-drill | 🔴 CI-IP bot gate 2026-09-05; residential OPEN |
| Full-screen player | signature UI | Phase 08 FullPlayer | 🟨 **implemented**; polish per ADR-002 |
| Lyrics | synced/unsynced | Phase 11 | 🟨 **implemented**; HW OPEN — not 🔴 absent |
| Cross-platform polish | multi-OS | Phases 12–13 | 🟨 after core stability (agree) |
| Tauri / Web | — | **out of v1** | do not plan as if in-tree |

---

## Risks (ordered)

1. **Extraction on CI datacenter IPs** (cat 8) — both own-client and yt-dlp
   AuthRequired on run 33968950214; metadata still PASS. Expanded tokenless
   chain is the proper fix attempt; cookies need separate ADR. Residential
   is the user-impact gate — never fake a green drill.
2. **Hardware debt** — Phases 08–13 code-complete markers without device
   evidence. Player glass/lyrics polish must not outrun a single residential
   play smoke.
3. **Docs-first relapse** — designing Liquid Glass / P9 animation before P0
   extraction truth repeats the failed prior attempt (MASTER_PROMPT doctrine).
4. **Branch sprawl** — Arena pins one session branch; humans should delete
   merged topic branches; avoid `player-final-v2` names (ADR-002 policy).

---

## Recommended sequence **now** (agrees with brief §13, mapped)

```
P0  Extraction truth (expanded clients + residential classify)     ← NOW
P1  Player state (already exists — smoke only)
P2  Basic player HW smoke (Phase 08 checklist subset)
P3  Full-screen hierarchy polish (ADR-002)
P4  Artwork blur cache (explicit, once-per-track)
P5  Lyrics providers (done — verify on device)
P6  Lyrics-dominant mode (ADR-002)
P7  Glass refinement (tokens only)
P8  Lyric motion polish
P9  Gesture polish
── then ──
Phase 14 audio cache + soaks + v0.1.0
Phase 12/13 hardware closeout
Trajectory 15+ only after v0.1.0
```

**Do not** reorder the entire roadmap around UI. Design FullPlayer now
(ADR-002); implement polish **after** stream path is honestly classified.

---

## What this audit is not

- Not a claim that Phase 08/11 hardware acceptance is done.
- Not a claim that live rot-drill is green.
- Not an M1–M30 rewrite. MASTER_PROMPT 14 phases remain authoritative.

## Addendum — ADR-002 M3 polish (same day)

User lock: **No Liquid Glass**. Code on branch:

- `PlaybackState.Recovering` + Reconnecting chip (Android 403 path).
- Lyrics-dominant FullPlayer + `BlurredArtworkCache`.
- Still does **not** close residential extraction (drill 33970045379 CDN 403).
