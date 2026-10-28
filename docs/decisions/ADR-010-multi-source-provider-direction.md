# ADR-010: Multi-source provider direction — capability model, YouTube primary, Spotify planned

> **Read the [2026-10-28 amendment](#amendment--2026-10-28-multi-source-product-direction-and-evidence-gates) with D1–D6.**
> Original owner quotations and dated analysis below are preserved, not a current
> single-source definition or permission to load new third-party runtime.

## Status

**ACCEPTED for the owner's delegated decisions D1, D2, D4, D6 (2026-10-20). D3 accepted as
"Spotify stays planned, deferred". D5 accepted as design only.** The owner is a student without a legal budget. They asked for these calls to be made
from the repository evidence, and said: *"do the way you think it is the best and working way
without compromising any features on the webapp"*, *"you can review yourself and continue
development"*, *"it was primarily meant to be yt music client"*, and *"I am student, I am not
able to pay."*

These decisions are an engineering reading of the evidence. **They are not legal advice.**
Nothing here establishes that any YouTube or Spotify use complies with the developer policies.
The residual risk is recorded in D1 and in [05 §4](../research/05-source-capability-matrix.md#4-risk-register-provider-specific).

Written 2026-10-20 by the research session that produced
[02](../research/02-baseline-audit-2026-10-20.md),
[03](../research/03-youtube-youtube-music.md),
[04](../research/04-spotify.md) and
[05](../research/05-source-capability-matrix.md). ADR-007 is still reserved by PR #54 and is not used.

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

> The recommendations in this section are the **pre-decision analysis**. The owner's decisions
> are recorded in the Decisions section below and take precedence where they differ.


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

- **Recommendation (pre-decision):** document only. Do not implement yet; the owner's decision keeps Spotify planned and deferred until a Premium account exists. The Spotify work that could proceed
  without the open decisions is the research already done (04) and a capability-only
  placeholder in the vocabulary (A2). No adapter, no OAuth flow, no client ID.

## Decisions

| ID | Question | Decision (2026-10-20) | Reason and residual risk |
|---|---|---|---|
| **D1** | Does the YouTube developer policy bind the native InnerTube and yt-dlp audio path (§III.I.07, §III.I.14, §III.I.09, §III.E.4)? | **Keep the native YouTube Music path as it is. Do not expand it. Do not add new YouTube audio features that depend on the extraction path.** Record the risk plainly. No legal review will be obtained (the owner cannot fund one). | The owner asked for the working product to be kept, and the native app is the YouTube Music client. Removing or migrating the audio path would remove working features, which the owner ruled out. **Residual risk, unresolved:** the literal reading of §III.I.14 prohibits this path. If YouTube enforces it, the native audio and offline features are at risk. Revisit if a rights holder, YouTube, or counsel raises it. |
| **D2** | May the web app add the YouTube IFrame player (wider CSP, third-party script)? | **No.** The web app keeps its JioSaavn source and the current CSP. | The IFrame player displays video and cannot provide the web app's `<audio>` queue, seek and media-session behaviour. Adopting it would remove web features and break the no-third-party-runtime promise. The owner also asked that web features not be compromised. |
| **D3** | Should Spotify be pursued? | **Spotify stays a planned second source, deferred.** Nothing is removed. No code, no OAuth, no client ID now. The owner will add it later, when they hold a Premium account and the open policy question below is answered. | The owner cannot pay for Premium today (D4), so there is nothing to build yet. Developer policy §III.5 still conflicts with the multi-source goal; that question stays open as a residual risk and must be resolved before any Spotify work starts. |
| **D4** | Is the Premium cost acceptable, and is there commercial intent? | **No paid services now.** The $0.00 budget holds for current work. The owner will obtain a Spotify Premium account later, when they can pay for it. Dhun is non-commercial. | Owner's stated constraint today. Spotify Development Mode needs the owner's Premium account, so Spotify work is blocked until then. |
| **D5** | Accept the capability vocabulary (A2) as design? | **Accepted as design only.** No implementation is scheduled. | Implementing it would add abstraction for one source per platform, which the prompt warns against. It becomes worth building when a second source is actually approved, which is not now. |
| **D6** | Who owns any future YouTube or Spotify developer account? | **The owner (the user), when Spotify work starts.** No developer account is created now. | Credentials are never requested in chat or stored in the repository. |

**D2 follow-up (2026-10-21): research answer.** The owner asked whether YouTube Music can be the web app's main source, and to fall back to other platforms only if it cannot. Findings from the official policy text [P]:

- Developer Policies §I.9 prohibits features that play audio or video from "a background player, meaning a player that is not displayed in the page, tab, or screen". §I.8 (the guide) prohibits separating or offering audio apart from video. The Required Minimum Functionality rules forbid overlays on an embedded player and require it to be visible before autoplay.
- So a YouTube-first web client built on the official IFrame Player API cannot offer the web app's core behaviour: audio that keeps playing with the tab hidden, a queue with its own player, and a minimised player. Making YouTube the main source would also drop web features, which the owner ruled out.
- The unofficial route (InnerTube from the browser) was the web build's earlier source. `app-web/src/js/api.js` records that it was unreliable from a browser, and it is the route §I.14 prohibits. It is not an option.

**Answer: not possible compliantly. JioSaavn stays the web app's main source.** Alternatives checked, all as of 2026-10-21:

| Option | Cost | Terms and fit |
|---|---|---|
| Jamendo API | Free for non-commercial apps; up to 35,000 requests a month; `client_id` from the developer portal | Creative Commons catalogue (about half a million tracks), not mainstream. Must credit the artist and Jamendo, link each track, and not design the app around caching or offline access. Each track's licence is per track. Fits the non-commercial project. [1][2] |
| SoundCloud API | New registration needs a paid Artist Pro subscription, per a 2026 guide | Excluded by D4 ($0 budget). [3] |
| Apple MusicKit (web) | Apple Developer Program, $99 a year, required for the developer token | Excluded by D4. [4] |
| Spotify | Premium for the developer account; Web Playback SDK for playback | Already deferred (D3, D4); §III.5 open. |

Sources: [1] https://developer.jamendo.com/v3.0 ; [2] https://devportal.jamendo.com/api_terms_of_use (§3.3) ; [3] https://developers.soundcloud.com/docs ; [4] https://developer.apple.com/programs/ . Primary policy text: https://developers.google.com/youtube/terms/developer-policies (§I.8, §I.9) and https://developers.google.com/youtube/terms/required-minimum-functionality .

Not decided here: whether to add Jamendo as a second web source. That needs `api.jamendo.com` and the audio host in the CSP (a change to D2's "current CSP" wording), a per-track licence display, and attribution. The owner decides.

**Open point on D2 (raised 2026-10-20; answered 2026-10-21 above, D2 stands).** The owner's reply to the
D2 question was "It was primarily meant to be yt music client so do accordingly", with
"do not compromise web app features". The reading recorded above (no IFrame player,
features kept) follows the second half. The first half can be read as asking for a
YouTube Music client on the web. The owner should confirm which is intended; until
then the recorded decision stands.

**Decision rule the owner delegated:** where a choice is engineering, make it and record it;
where it is a legal or paid question the owner cannot answer, default to the choice that keeps
working features and costs nothing, and say so plainly in the report.

## Follow-up 2026-10-21 (owner): visible YouTube player and Jamendo

**Question 1: can YouTube play without separating audio from video?** Yes, as a matter of the
published text. The official IFrame Player API plays the video together with its own audio, so no
audio track is separated, extracted or offered alone. The limits are what make it a different
product from the web app's audio player:

- The player must be **displayed** on the page. Policy §I.9 bans features that play audio or video
  "from a background player, meaning a player that is not displayed in the page, tab, or screen".
  So playback must stop when the tab is hidden or the player is minimised.
- The player must not be covered or obscured by overlays (Required Minimum Functionality).
- Autoplay only starts when more than half of the player is visible.
- It cannot be an audio-only player, and the web app's `<audio>` queue, seek and media-session
  behaviour do not apply to it. The web app's audio path cannot be moved onto it without losing
  those features, which the owner ruled out.

[P] Developer Policies §I.9 and Required Minimum Functionality, as quoted in the search results
of 2026-10-21. Not legal review. The owner cannot fund counsel.

**Question 2: Jamendo.** Not added. Jamendo describes itself as a platform for independent
artists under Creative Commons licences, with royalty-free licensing for businesses, and one
review states that "nothing here is a chart hit". Under the owner's rule (add it only if it has
popular music), it stays out. Reopen if the owner wants a free Creative Commons source anyway.

**Plan for the next session: a visible YouTube "Watch" mode (not built now).**

1. **Spike, no product change.** On the deployed origin, load the stock IFrame player at its
   minimum supported size and check that it plays. Confirm that the browser sends a Referer that
   YouTube accepts (the app sets no referrer policy; the default is origin-only). Record the
   date, browser and result in `docs/web-player/IMPLEMENTATION-STATUS.md`. If it fails, stop
   here and record the failure.
2. **Owner approval before any build.** The player adds `www.youtube.com` to `script-src` and
   `frame-src`. That is a third-party runtime, which the privacy promise forbids today (D2).
   Without explicit approval, the build does not start.
3. **Build as a separate mode, not a replacement.** The JioSaavn audio path stays the default and
   unchanged. Watch mode is a visible player with the YouTube controls, driven through the IFrame
   API (`loadVideoById`, play, pause, state events). It pauses when hidden, covered or scrolled
   out of view. It has no audio-only option, no separation and no download. Its queue is its own.
   Background audio stops in this mode, and the UI must say so.
4. **Tests.** `scripts/test_app_web.py` for the CSP change (only the named YouTube hosts). A browser
   check in `website/tests/browser.mjs` that the player is at least the minimum size, pauses on
   hide, and is not overlaid. Docs updated with the evidence.
5. **Still open.** Whether the deployed origin's Referer is accepted (step 1), and the policy
   reading, which has no legal review. Not a compliance claim.

## Consequences

- No code changes result from this ADR. Current behaviour of the native apps and the web app
  is unchanged.
- The capability vocabulary (D5) is available as a design if a second approved source appears.

**The main open risk (D1):**

- If YouTube enforces §III.I.14 against the native audio path, native audio and offline
  features would be affected. The decision to keep them is recorded here, so a future
  change can be made deliberately in a new ADR.

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


## Amendment — 2026-10-28: multi-source product direction and evidence gates

**Status: owner-directed clarification, accepted for documentation/instruction
alignment; not a new adapter, legal approval, budget or architecture rewrite.**
DHUN has always been intended as multi-source, not a YouTube Music-only product.
[PRD §1](../PRD.md#1-product-definition-and-source-of-truth) is the authoritative
vision; this ADR records decisions and their history rather than duplicating it.
The native KMP/Compose architecture and separate dependency-free JS browser remain.

- **D1 stands:** maintain the existing native YouTube/YouTube Music path as first
  priority without expanding extraction-based audio features. Source, historical
  playback, current health and legal permission are separate evidence. Extraction,
  background playback, persistent audio/lyrics/artwork/metadata storage and
  downloads have unresolved permission/policy risks. Owner acceptance and a
  disclaimer do not confer compliance. No funded legal review is invented.
- **D2 current runtime stands:** browser uses JioSaavn via three unofficial wrapper
  instances; current CSP/no-third-party-runtime and no-IFrame remain. Native and
  browser need not use identical providers/features. The earlier “primarily yt
  music client” intent question is resolved by **multi-source product, YouTube
  first**, not an order to replace JioSaavn or secretly add YouTube runtime.
  The 2026-10-21 Watch-mode plan remains a **proposal**. Explicit authorisation
  must precede adding a third-party script/CSP or live runtime spike, not follow it.
  Official IFrame controls include queues and seeking: the earlier blanket
  loss-of-queue/seek wording is too strong. They do not provide raw audio or
  hidden/background/offline parity; visibility/Referer/branding/autoplay and
  deployed-origin feasibility need separate design/tests.
- **D3 stands:** Spotify is **Planned**, with implementation **Deferred**; this is
  project-level intent, not approval of any native/browser route. No code, SDK,
  OAuth, tokens or active Spotify traffic. Technical, account/Premium, policy,
  legal, playback, privacy, licence and cost gates must pass. Premium alone or
  UI/source separation does not resolve policy §III.5's multi-service restriction.
  Current development access also limits users and public scale; July 2026
  increased client IDs to 25 while sharing quotas per developer account.
- **D4 stands:** $0/noncommercial constraint; no new backend, paid hosting,
  mandatory subscription or other recurring service is authorised. Do not buy
  Premium, use someone else's credentials or assume extended access will be granted.
- **D5 stays design-only:** native `MusicProvider` still imports InnerTube filters
  and assumes video/browse IDs; entity/database/queue/download/lyrics callers and
  browser snapshots are source-shaped. Provider-scoped IDs, migration,
  capability/error/account/player contracts need a separate bounded design before
  adding a provider. This audit corrects its KDoc, not interfaces or user data.
- **D6 stands:** any future account/dashboard/legal agreement decisions belong to
  the owner with verified prerequisites. Never request credentials/tokens in chat
  or commit them. Controller/private contacts and draft adoption are uncompleted.
- **Other providers:** evaluate value, permissions, maintenance, privacy, platform,
  licence and cost evidence; no automatic acceptance. Prior Jamendo decision is
  historical and unchanged. Existing JioSaavn is not newly approved by this audit:
  its current Terms restrict unofficial/automated interfaces, and wrapper/content
  permissions and retention remain unresolved. Preserve functionality pending an
  explicit rollout/removal decision; disclosures do not cure the risk.

Current primary-source rechecks are in [03](../research/03-youtube-youtube-music.md),
[04](../research/04-spotify.md) and [05](../research/05-source-capability-matrix.md),
all dated 2026-10-28. The [repository-wide audit](../research/06-multi-source-claims-audit.md)
records changes, regressions/validation and ordered follow-up. No commit, push,
release publication or broad application rewrite is part of this authorisation.
