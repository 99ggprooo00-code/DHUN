# ADR-010: Multi-source provider direction — capability model, YouTube primary, Spotify planned

## Status

**PROPOSED — NOT ACCEPTED.** 2026-10-20. Written by the research session that produced
[02](../research/02-baseline-audit-2026-10-20.md),
[03](../research/03-youtube-youtube-music.md),
[04](../research/04-spotify.md) and
[05](../research/05-source-capability-matrix.md).

Nothing in this ADR changes code. Acceptance of the *capability model* (D5) would
authorise design work only. Every other item needs the maintainer's decision first.
ADR-007 is still reserved by PR #54 and is not used here.

## Context

The project's uploaded master prompt asks for a multi-source client: YouTube Music as the
primary source, Spotify as a planned second source, and a shared provider architecture
with explicit capability declarations. Three facts from the repository and from primary
sources change what can be decided now.

1. **The sources differ more than the prompt assumes.** The native apps use DHUN's own
   InnerTube client plus a stream resolver chain (ADR-001). The web app uses three
   community JioSaavn deployments (`app-web/src/js/api.js`) because a browser could not
   reliably use the InnerTube endpoint. The two platforms do not share a provider. [R]
2. **The policies of the two primary sources restrict the product as currently described.**
   - YouTube API Services Developer Policies §III.I.14 forbid any technology other than the
     API to access audiovisual content, and §III.I.07 forbids separating audio from video.
     Read literally, the native audio path falls under both clauses. [P]
   - Spotify Developer Policy §III.5 forbids a product "integrated with streams or content
     from another service". Read literally, a multi-source Dhun falls under it. Spotify
     streaming is limited to Premium subscribers (§IV.1). [P]
   No repository document cites either policy (grep, 2026-10-20). [R]
3. **The official playback routes are narrow.** The YouTube IFrame Player API is the only
   official browser playback mechanism found. It displays a player and needs a third-party
   script. The Spotify Web Playback SDK needs Premium and written approval for commercial
   use. [P]

The project's constraints still hold: no DHUN backend, proxy, accounts or telemetry; $0.00
operating budget; no third-party runtime on the web ([ADR-008](ADR-008-browser-web-player-target.md)
boundaries 1–3; [ADR-009](ADR-009-marketing-site.md)).

## Options considered

### Question A — How should providers be modelled?

- **A1. Status quo.** Each platform keeps its own source code, with no shared abstraction.
  Cheapest now. Adding a source means editing every screen. Rejected as the long-term model;
  acceptable as the interim state.
- **A2. Shared capability-declaring interface, implemented per platform.** A small interface
  states, per source, which capabilities exist (search, metadata, direct playback, embedded
  playback, account required, library access, playlist write, lyrics). Native code uses the
  existing `MusicProvider` (`shared/.../provider/MusicProvider.kt`) as the starting point,
  adding capability declarations. The web app mirrors the same vocabulary in JavaScript.
  Shared code is limited to identifiers, capability names and error categories.
- **A3. One generic framework that every platform imports.** Would force a Kotlin/JS or
  Wasm decision that ADR-008 has not made and would widen the web build. Rejected.

**Recommendation: A2**, limited to the capability vocabulary and the error categories. It
is reversible and does not change behaviour. It is also what makes the matrix in
[05](../research/05-source-capability-matrix.md) enforceable rather than descriptive.

### Question B — Keep, change, or remove the YouTube audio path (native)?

- **B1. Keep as is, with a documented risk.** Preserves the working product. Leaves the
  literal conflict with §III.I.14 and §III.I.07 unresolved in writing.
- **B2. Keep pending a written legal review, and do not extend it.** No new YouTube
  audio features until counsel answers the question in [03 §8](../research/03-youtube-youtube-music.md#8-open-questions-for-the-maintainer-or-counsel).
  Existing functions remain. Keeps the working source available, as the prompt requires.
- **B3. Migrate native playback to the official IFrame player.** Video displayed in a WebView;
  no separate audio stream; no background playback (§III.I.09). Loses the current
  audio-only experience. Requires a product decision.
- **B4. Remove the audio path.** Rejected without the maintainer's instruction. It is working
  code that the prompt says to preserve.

**Recommendation: B2 now, with B3 as the fallback if counsel's answer is unfavourable.**
This decision needs the maintainer and counsel. It is not an engineering choice.

### Question C — Should the web app adopt YouTube (IFrame) or Spotify?

- **C1. No, keep JioSaavn-web.** No change to the CSP or the privacy promise. The JioSaavn
  terms question stays open (`docs/web-player/KNOWN-LIMITATIONS.md`).
- **C2. Adopt the YouTube IFrame player.** Adds `https://www.youtube.com` to `frame-src` and
  a third-party script to `script-src`. Breaks the "no third-party runtime" rule. Requires
  the displayed player and a Referer policy. Gives the web app a video-based YouTube path
  that the official documentation describes; compliance with the policy still needs review.
- **C3. Adopt Spotify metadata (search, library) only.** No playback. Still requires the
  §III.5 answer if it is combined with other sources.
- **C4. Adopt Spotify playback through the Web Playback SDK.** Premium-only. Adds Spotify
  origins to the CSP. Requires written commercial-use approval and the §III.5 answer.

**Recommendation: C1 until D2 and D3 are answered.** No new web source is added by this ADR.

### Question D — Spotify as a planned source

- **Recommendation:** document only. Do not implement. The Spotify work that could proceed
  without the open decisions is the research already done (04) and a capability-only
  placeholder in the vocabulary (A2). No adapter, no OAuth flow, no client ID.

## Decision requested from the maintainer

| ID | Question | Why it is not an engineering decision | Options |
|---|---|---|---|
| **D1** | Does the YouTube developer policy bind the native InnerTube and yt-dlp audio path (§III.I.07, §III.I.14, §III.I.09, §III.E.4)? If yes, what happens to the native audio and offline features? | A legal interpretation, and a product trade-off if the answer is unfavourable | (a) accept risk in writing; (b) counsel review first (recommended); (c) migrate to IFrame (B3); (d) remove (B4) |
| **D2** | May the web app add the YouTube IFrame player, which widens the CSP and adds a third-party script? | Changes the product's stated privacy and "no third-party runtime" promise | Yes / no / later |
| **D3** | Is a Spotify source wanted, given §III.5 (no product integrated with other services' streams) and §IV.1 (Premium-only streaming)? | Under the literal policy, a multi-source Dhun is prohibited. Either a legal answer or Spotify's written approval is needed | Drop Spotify; metadata-only (C3) after legal review; pursue approval |
| **D4** | Will the project carry the recurring cost of a Premium subscription for the Spotify developer account, and is there any commercial intent? | The $0.00 budget is the maintainer's stated constraint; commercial use needs approval | Accept cost; decline; confirm non-commercial |
| **D5** | May the project accept the capability vocabulary (A2) as design, with no behaviour change? | Low risk; needs a sign-off to start | Accept / revise |
| **D6** | Who owns any future YouTube or Spotify developer account and its credentials? | Credentials must be provided by the owner; they are never requested in chat or stored in the repository | Owner named / not yet |

No credential, key or account is requested here.

## Consequences

**If the maintainer accepts D5 and rejects nothing else:**

- A later session can implement the capability vocabulary in `MusicProvider` and in the
  web app without changing behaviour. Tests would cover capability declarations and error
  categories only.

**If D1 is answered unfavourably:**

- Native audio and offline features would be reviewed for removal or migration. This is
  the largest user-visible consequence in this ADR. It would be a product decision, and it
  would be recorded in a new ADR.

**Regardless of the answers:**

- Documentation must not describe YouTube or Spotify as "compliant", "officially
  supported" or "integrated" until the relevant decision is recorded.
- The web app keeps its current honesty rules. It must not show a source as playable
  that the policy or the platform does not permit.

## Evidence and related documents

- Baseline: [02](../research/02-baseline-audit-2026-10-20.md)
- YouTube: [03](../research/03-youtube-youtube-music.md)
- Spotify: [04](../research/04-spotify.md)
- Matrix, cost, privacy, cross-platform, risks: [05](../research/05-source-capability-matrix.md)
- Existing: [ADR-001](ADR-001-extraction-engine.md), [ADR-006](ADR-006-offline-music-downloads-architecture.md), [ADR-008](ADR-008-browser-web-player-target.md), [ADR-009](ADR-009-marketing-site.md)
- Web documentation: [docs/web-player/](../web-player/README.md)
