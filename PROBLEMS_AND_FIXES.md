# DHUN — Plan Audit: Problems Identified & Feasible Replacements

> This document replaces the original plan's assumptions with evidence.
> Written 2026-09-01. Every "Problem" below was found in the original 30-phase
> DHUN master plan and in the previous repository
> (`99ggprooo00-code/dhun-music`, now `dhun-music-failed`).
> Each problem is paired with the replacement that MASTER_PROMPT.md implements.

---

## P1 — FATAL: The InnerTube extraction strategy is a generation out of date

**What the original plan said:**
Phase 04 told us to implement `/youtubei/v1/player` with `WEB_REMIX` /
`ANDROID_MUSIC` / `IOS_MUSIC` clients, and to "research whether URL signature
decoding applies to the MUSIC client." It treated stream extraction as a
one-time implementation risk, solvable in one phase.

**Why that fails now (evidence):**
- YouTube now enforces **PO Tokens (Proof of Origin)** for stream URLs
  (`gvs`) on most InnerTube clients. Without one, formats are silently
  withheld or return HTTP 403.
  Ref: https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
- **`android_music` and `ios_music` now require sign-in for every video**
  (documented directly in yt-dlp's client table). The exact clients the old
  plan told us to lean on are the ones that died.
- **SABR** (YouTube's server-side adaptive streaming) is being rolled out
  per-client and breaks the "get a progressive audio URL, HTTP-range it"
  model. Ref: https://github.com/yt-dlp/yt-dlp/issues/12482
- BotGuard attestation means a plain HTTP client cannot mint PO tokens;
  the token generator is obfuscated JS that must run in a JS engine.
  Ref: https://github.com/Tyrrrz/YoutubeExplode/issues/933
- The clients that still work tokenless (e.g. `tv`, `android_vr` at time of
  writing) are **a moving target** — this is explicitly a cat-and-mouse.

**Feasible replacement (implemented in MASTER_PROMPT.md):**
1. **Do not hand-roll the extraction layer.** Wrap maintained extractors:
   - **NewPipe Extractor** (GPL-3.0, pure Java — runs in-process on BOTH
     Android and Desktop JVM). Maintained daily by the NewPipe team; their
     whole purpose is absorbing YouTube's breakage.
   - **yt-dlp** (Unlicense) as a *subprocess fallback on Desktop only* —
     it is the industry's fastest-moving extractor.
2. DHUN's own InnerTube client is **restricted to metadata** (search, browse,
   next/related, suggestions, lyrics browsing) — these calls still work
   tokenless via `WEB_REMIX` and are far more stable than stream resolution.
3. **Rot drill:** a scheduled CI job runs the extraction suite daily against
   live YouTube. Breakage is detected within 24h, not by angry users.
4. **Kill switch:** if extraction fails for 14 consecutive days with no
   upstream fix, the documented pivot (documented in the risk register) is
   triggered — not silent rot.

---

## P2 — FATAL: The "research list" is a graveyard, and the plan didn't notice

**What the original plan said:**
"Study ViMusic, SimpMusic, InnerTune deeply. Reimplement — do not fork."

**Why that fails now (evidence):**
- ViMusic — **archived Oct 2024**, last release Nov 2024.
- RiMusic (ViMusic continuation) — **archived Jul 2025**.
- InnerTune (original) — discontinued/obsolete; the Malopieds fork also
  discontinued.
- OuterTune — YT Music development paused (maintainer statement, Feb 2026).
- SmPm — the only serious Compose-Multiplatform YTM client (Android+Desktop)
  — **discontinued**.
- SimpMusic — still maintained, but survived multiple "it's all broken" waves
  precisely because extraction maintenance is the whole job.

**Why this matters:** the graveyard tells us two things the old plan ignored:
(a) every death was caused by extraction rot + maintainer burnout, not by UI
or architecture; (b) any DHUN plan that does not budget for *permanent
extraction maintenance* is planning its own archive date.

**Feasible replacement:**
- Research phase is compressed from 10 documents to a **2-day time-boxed
  spike** with fixed outputs (see Phase 01).
- DHUN minimizes its maintained surface: extraction outsourced upstream
  (P1), UI shared across platforms via Compose Multiplatform, scope cut
  to two platforms (P3).
- README and docs state the maintenance contract honestly: streaming clients
  rot; DHUN ships fast patch releases when extraction breaks.

---

## P3 — FATAL: Three-platform day-one scope (Windows native + Android + Web PWA)

**What the original plan said:**
"All three platforms are designed from day one. No 'port it later' thinking."
30 phases across Windows (tray, SMTC, jpackage, jump lists), Android (Media3,
Auto, widgets), Web (React PWA, service workers, Lighthouse > 90).

**Why that fails:**
- The previous attempt was **Windows-only in C# and still stalled** — with
  half this scope.
- SmPm's discontinuation shows even Android+Desktop in one codebase is a
  heavy lift for a small team.
- The Web platform triples UI cost for the **least capable playback
  platform**: in-browser playback requires PO tokens minted by BotGuard JS —
  effectively unavailable to a third-party web app; CORS + SABR make direct
  `<audio>` unreliable even when URLs resolve.

**Feasible replacement:**
- **Android = primary platform** (where every ViMusic-class app actually
  lives; Media3 gives media session, lock screen, notification, background
  audio for free).
- **Windows Desktop = second platform via Compose Multiplatform**, sharing
  ~90% of UI and 100% of domain/extraction code with Android — NOT a
  separate native app (the Nagi mistake), NOT Electron.
- **Web = cut from v1.** Explicitly deferred; revisit only after v1 ships.
  If ever built: browse/library/queue UI is feasible; streaming playback is
  documented best-effort.

---

## P4 — FATAL: The previous repository could never become DHUN

**What the audit of `99ggprooo00-code/dhun-music-failed` found:**
- It is a **GPL-3.0 fork of Nagi** (C# / .NET 10 / WinUI 3 / LibVLCSharp) —
  a **local-file** player.
- Its own rules state: *"Do not implement unofficial extraction … or
  unauthorized offline/background playback"* and *"Future lawful providers
  (licensed / public-domain catalogs)"* only.
- So the old repo's direction is **structurally incompatible** with DHUN's
  YouTube-Music-source vision. It didn't fail at DHUN — it quietly became a
  different, narrower app with DHUN's name.
- What WAS good there: CI discipline, ADRs, security/privacy docs, code-first
  review rules. Keep the habits, not the code.

**Feasible replacement:**
- **Clean-room restart.** New repo `DHUN`, language Kotlin, license GPL-3.0.
- **Zero code** from the old repo (C# is useless to a Kotlin app anyway).
- One postmortem document (this file) records the lessons; nothing else is
  inherited.

---

## P5 — Licensing contradictions (Apache/MIT assumptions vs GPL ecosystem)

**What the original plan said:**
"Apache 2.0 and MIT licensed code can generally be reused… GPL: study the
implications." — while listing NewPipe Extractor, ViMusic, SimpMusic,
InnerTune as sources to study and reuse from. All of them are **GPL-family**.

**Why that fails:** a Kotlin/Compose app that links NewPipe Extractor (even
as a Maven dependency) is a combined work → must be GPL-compatible. The old
plan's licensing section would have poisoned the repo's license the moment
Phase 04 was implemented.

**Feasible replacement:**
- **DHUN is GPL-3.0. Decided. Not open for debate.**
- GPL unlocks the ecosystem: NewPipe Extractor as a real dependency,
  legitimate reference into ViMusic/InnerTune/SimpMusic code with
  attribution.
- THIRD_PARTY.md records every reused library, license, and commit hash.

---

## P6 — Documentation ceremony killed the last attempt

**What the original plan said:**
30 phases; Phases 01–02 produce 10 research docs + 10 ADRs + a full UI spec
**before any code exists**. Phase 27 is a 60-item manual audit.

**Why that fails:** the failed repo is the proof — excellent
`docs/ARCHITECTURE.md`, ADRs, PRIVACY.md, SECURITY.md, implementation-status
checklists… and an app whose core mission (online music) was never started.
Docs-first AI-driven projects deliver documents and starve the product.

**Feasible replacement (code-first discipline):**
- 14 phases instead of 30. **Every phase ships runnable code on a device.**
- Research = 2-day spike, one findings file. ADRs = only for decisions with
  real alternatives (max ~6, written when the decision is made, not before).
- Definition of Done per phase: *feature runs on real hardware*, not
  "documents updated."
- UI spec lives as design tokens + a component catalogue screen in the app
  (Phase 05) — living code, not a 40-page markdown.

---

## P7 — Web playback optimism

**Why in-browser YouTube streaming is effectively dead for third parties:**
BotGuard/PO tokens can't be minted in a plain web app; SABR replaces
progressive URLs; CORS is uncontrolled. The old plan's Phase 09 told the Web
app to call InnerTube directly from `apps/web/src/api` — that cannot stream.

**Feasible replacement:** Web cut from v1 (P3). Any future web effort is
browse/library-only or requires a self-hosted proxy component, which is a
separate, explicit project decision.

---

## P8 — Desktop playback + Windows integrations were hand-waved

**What the original plan said:** "If using VLC via Kotlin (vlcj or
similar)… If using a different approach (document which and why)" — the
stack's highest-risk platform decision left undecided, while SMTC, tray,
mini-window, jump lists and global hotkeys were all promised.

**Feasible replacement:**
- **vlcj (libVLC)** locked in for desktop audio — battle-tested, plays HTTP
  streams, Windows/macOS/Linux for free.
- **SMTC via JNA/WinRT** behind a feature flag, with an explicit, documented
  degradation path (system tray controls + registered media-key handling).
- Phase 10 spikes SMTC *first*; if unstable, ship the fallback and say so in
  KNOWN_LIMITATIONS.md. No silent partial integrations.

---

## P9 — v1 feature list was fantasy-scope

Cut from v1 (moved to explicit v2 candidates): Android Auto, Cast/AirPlay,
equalizer, cross-device sync, downloads, account library sync, PWA,
Windows jump lists, widgets. Kept in v1: the core loop — find music, play
it, keep it organized (queue, favorites, playlists, history, lyrics), on two
platforms, with background playback done properly.

---

## P10 — Auth phase was fragile and unneeded for v1

Cookie-pasting auth is brittle and ToS-gray. Replacement: v1 is fully
anonymous (search/stream/library all work). Optional cookie sign-in becomes
Phase 13 (optional, last, cuttable) and exists only to unlock
age/region-restricted content and personal playlists.

---

## P11 — No maintenance story (the thing that actually kills these apps)

The old plan had zero mention of what happens after v1.0 when YouTube
changes. Replacement:
- Pinned `extraction.properties` (client constants) in one file.
- Daily rot-drill CI (P1).
- Fast patch-release policy documented in README.
- Risk register with explicit kill-switch/pivot criteria.

---

## P12 — Internal contradictions in the old plan

- Phase 03 demanded a `jsBrowserDevelopmentRun` web build while Phase 01
  research concluded Compose Web is experimental. → Cut. No JS target.
- SQLDelight "browser via sql.js/OPFS" — gone with Web.
- "REST/gRPC shared backend" alternative — pointless infra for a client-only
  app. Cut.
- DI "Koin or Hilt" left open while demanding finality → locked: **Koin**
  (KMP-first).
- Database "SQLDelight or Room" → locked: **SQLDelight 2.x**, Android + JVM
  targets only.
- Navigation "Decompose or Navigation Compose" → locked:
  **Navigation Compose on Android; Compose Desktop uses a small custom
  navigator over the same screen models.**

---

## Result

The rewrite in [MASTER_PROMPT.md](MASTER_PROMPT.md) implements every
replacement above. 30 phases → 14. Three platforms → two (Web deferred).
Hand-rolled extraction → maintained extractors + rot drill. Docs-first →
code-first. License ambiguity → GPL-3.0.
