# 04 — Spotify: source research

## Current recheck — 2026-10-28

**Spotify is Planned; implementation is Deferred.** This is a project-level
additional-source intention, not “native-only by definition,” and not implemented
on any platform. The [authoritative vision](../PRD.md#1-product-definition-and-source-of-truth)
and [ADR-010 D3/D4, amended 2026-10-28](../decisions/ADR-010-multi-source-provider-direction.md)
control. No adapter, OAuth, SDK, token store, account or Spotify request exists.
The old “until ADR-010 is decided” and word-ban statements below are historical.

`[P]` = primary text read 2026-10-28; `[R]` = repository inspection;
`[U]` = unverified. These are feasibility findings, not legal approval or live
account/dashboard/SDK tests. **Premium is necessary for some paths, not sufficient
permission to ship DHUN as a combined multi-source Spotify product.**

### Six different capabilities — no DHUN implementation yet

| Capability | Official surface / evidence | What it does NOT establish |
| --- | --- | --- |
| Search / catalogue metadata | Web API; [2026 migration guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide). Dev-mode search max 10/default 5; fields and endpoint set changed. `[P]` | No playable audio URL, general catalogue mirroring permission or unlimited public access. API access still requires an app and appropriate authorisation. |
| User library / playlist operations | Web API + user OAuth/scopes; migration replaces playlist `tracks` with `items`, restricts playlist content to owned/collaborative contexts in dev mode, removes numerous user/batch/browse endpoints and adds generic library operations. `[P]` | Metadata access is not in-client playback, account sync already in DHUN, or general cross-service data transfer permission. Verify every endpoint/scope at implementation time. |
| Playback control | Web API Player commands on an eligible Spotify/Connect device; Android App Remote. `[P]` | Control of Spotify playback is not raw audio delivery to DHUN. Developer Terms classify some background-app control as Streaming; metadata-only vs streaming policy must not be guessed. |
| In-browser playback | [Web Playback SDK](https://developer.spotify.com/documentation/web-playback-sdk) creates a browser Spotify Connect device and streams within the page, subject to eligible Premium, supported browser/DRM/iframe/autoplay constraints. Prior written approval required for commercial projects. `[P]` | Not a Web API stream URL or drop-in `<audio>` source; not compatible with current no-third-party-runtime/CSP without a decision. No guarantee for all browsers/mobile tiers. |
| Native Android playback | [Android SDK](https://developer.spotify.com/documentation/android): Beta Authorization and **App Remote** libraries; App Remote controls the **installed Spotify app**, which owns playback/cache/network/system integration. `[P]` | Not Media3 PCM/URLs or a native in-DHUN audio SDK. Its “offline” feature is Spotify-owned functionality, not permission to download Spotify into DHUN. Exact installed-app eligibility and current limits need platform tests. |
| Desktop / offline downloads | No official JVM/libVLC direct-audio route established in this research. [Developer Terms](https://developer.spotify.com/terms) prohibit stream ripping/permanent copying and indefinite content storage, subject to specific temporary metadata/art and conditional Premium exceptions. `[P][U]` | Do not declare all official desktop options impossible, promise libVLC support, invoke an unofficial decryptor, or treat narrow Premium exceptions as authorisation for DHUN audio downloads. |

### Account, quota and recurring-cost gates

Primary sources: [quota modes](https://developer.spotify.com/documentation/web-api/concepts/quota-modes),
[February 2026 migration](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide),
[July 2026 changelog](https://developer.spotify.com/documentation/web-api/references/changes/july-2026), all read `[P]`.

- Development app owner must maintain **Spotify Premium**; lapse stops the app.
  Up to **5 allowlisted users**. An unallowlisted user may authenticate but get
  API 403. Do not use OAuth success as access or playback proof.
- February limits applied to new apps from 2026-02-11 and existing apps migrated
  from 2026-03-09; extended-quota apps are treated separately. The migration page
  is not the last change: **July increased client IDs per developer from 1 to 25**.
  Development quotas are **shared per developer account**, not independently per
  client ID. 429 may contain `reason: QUOTA_EXCEEDED`. Specific numeric rate
  allowances remain unestablished and must not be invented.
- Extended access is **not an automatic route for a student hobby client**.
  Since 2025-05-15 only organisations may apply; the published criteria include
  an established entity, launched service, **at least 250,000 MAUs**, key markets,
  commercial viability and compliance. Meeting/applying is no guarantee of approval.
- Owner Premium and eligible listener Premium are separate requirements. No
  Nepal/local subscription price, developer account readiness, agreement or
  existing quota approval is verified. No mandatory subscription, new backend,
  paid API/hosting or other recurring cost is approved by this audit (D4).
  A page not listing an API fee is not a contractual zero-cost guarantee.

### Policy and licensing gates

Read [Developer Policy](https://developer.spotify.com/policy) (effective
2025-05-15) and [Developer Terms](https://developer.spotify.com/terms) on 2026-10-28 `[P]`.

- **§III.5:** no product/service integrated with another service's streams or
  content. This is central to DHUN's multi-source direction; source-separated
  queues/UI, metadata-only use or no crossfade are **not automatically approved**
  workarounds. Determine a permitted scope/seek provider clarification before
  implementing; this audit neither approves nor removes the planned source.
- **§III.7:** no segue/mix/remix/overlap, including between Spotify tracks.
  Shared EQ, crossfade, DSP, unified radio, caches and recommendation features
  need capability-specific evaluation; never reuse native YouTube assumptions.
- **§III.9** has a narrow personal/playlist-metadata transfer exception, not a
  blanket cross-service import/sync/content redistribution permission.
- **§III.11** restricts replicating/replacing core Spotify UX without permission;
  **§III.13/14** restrict content analysis/profiles and AI/ML ingestion. A generic
  history-based recommendation layer cannot simply ingest Spotify data.
- Music Streaming is Premium-only (§IV.1); previews/widgets have separate limited
  uses and are not full-playback substitutes. Commercial streaming has additional
  restrictions; “free/open source/noncommercial” does not waive other policies.
- Attribution/link-back, branding and non-affiliation, enforceable user terms,
  least scopes, security, disconnect/revocation and deletion must be designed.
  No Spotify music/SDK/trademark rights follow from DHUN's GPL. SDK licence and
  resolved dependency compatibility with GPL/native distribution need review.

### Authentication, privacy and platform design — future only

[PKCE flow](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)
recommends Authorization Code + PKCE for SPAs/mobile/clients unable to protect a
secret. [Redirect URI rules](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)
require exact registration and HTTPS, except explicit loopback-IP HTTP; `localhost`
is disallowed and dynamic loopback ports have a specific exception. `[P]`
Do not commit a secret, collect user cookies, assume any native custom scheme is
valid across SDKs, or choose a redirect before platform-specific verification.

Proposed work would require scope review, state/PKCE validation, secure platform
credential storage (not ordinary SQLDelight/localStorage), expiry/refresh/revocation,
logout/disconnect/delete, account-access/403/Premium/429 errors, licensing, player
state ownership and truthful UI per capability. Browser third-party SDK/CSP and
storage risk decisions differ from Android App Remote and desktop research.
No backend is automatically required by PKCE; no backend is authorised either.

Only **if approved and implemented**, tokens/account/library/playback data would
flow to Spotify with its own retention and privacy obligations. Those are design
considerations, **not current DHUN data processing**. Re-read official docs,
permissions and dashboard rules before any spike. Preserve D3's planned status
and D4's no-recurring-cost boundary until genuine owner decisions change them.

---

## Historical research record — 2026-10-20

> Retained as dated evidence, not a current implementation order or legal conclusion.
> The 2026-10-28 recheck above supersedes conflicting present-tense claims below.

**Date of research:** 2026-10-20. **Status:** research only. **No Spotify code exists in
this repository, and none is proposed for implementation until ADR-010 is decided.**

**Evidence labels:** `[P]` primary Spotify page read in this session · `[R]` repository ·
`[S]` search-result excerpt of a page or a secondary article, not read in full ·
`[U]` not verified.

---

## 1. Summary

The website already states that Dhun does not import from Spotify (`website/src/_data/site.js`, line 749), and `scripts/website_claims.py` forbids claims of Spotify support. Nothing in this document changes that position.


- Spotify's Web API is **metadata and control**. Full audio playback inside a third-party
  app is possible only through the **Web Playback SDK** (browser) or a native SDK, and
  only for users who hold **Premium**. [P]
- Since **February 2026**, Development Mode (the default for new apps) requires the app
  **owner** to hold Premium, limits the app to **five authorised users**, and removes a
  number of endpoints and fields. Some of these changes also apply to existing apps. [P]
- Spotify's Developer Policy contains clauses that **conflict with a multi-source
  product**: §III.5 prohibits a product "integrated with streams or content from another
  service", and §III.7 prohibits mixing or overlapping Spotify content with other audio.
  These must be resolved before any Spotify code is written. [P]
- Extended quota (the route beyond Development Mode) is, per Spotify's stated criteria,
  limited to organisations with an active launched service and a large user base. Dhun
  is not eligible on those criteria. [S]
- **Owner decision (2026-10-20, ADR-010 D3):** Spotify stays a planned source. It is deferred, not removed, until the owner holds a Premium account and the §III.5 question is answered.
- **Recommendation for Dhun:** do not implement Spotify playback yet. Treat Spotify as a
  possible metadata-only source, and only after the policy questions in ADR-010 D3 are
  answered. Playback would also cost a Premium subscription, which conflicts with the
  $0.00 budget (ADR-010 D4).

## 2. The six capabilities, kept separate

The prompt asks that these be distinguished. Each one has a different answer.

| # | Capability | What Spotify documents | Evidence | Dhun implication |
|---|---|---|---|---|
| 1 | **Catalogue search** | `GET /search` ("Search for Item") is listed as still available in Development Mode, for albums, artists, playlists, tracks, shows, episodes and audiobooks. The `limit` maximum was reduced to **10** in February 2026 (search pagination must be handled). | [P] Feb 2026 changelog, "Endpoints still available"; [P] migration guide, search pagination item | Metadata search is possible. Results are not playable in Dhun. |
| 2 | **Metadata display** | `GET /tracks/{id}` and `GET /albums/{id}` remain. Removed in Feb 2026 (changelog): track `popularity`, track and show `available_markets`, track `linked_from`, show `publisher`. Bulk lookups (e.g. several tracks in one call) are absent from the "still available" list. | [P] changelog "Track" and "Endpoints still available" | Metadata shown in Dhun must tolerate missing fields and must not sort by popularity. |
| 3 | **Playlist and library access** | Available: current user's playlists, saved tracks, saved albums, followed artists, `PUT /me/library` (save) and `DELETE /me/library`. `GET /playlists/{id}` returns an `items` object **only for the user's own playlist**; other playlists return metadata only. | [P] changelog "Library", "Playlist" | A user can see their own playlists. Other users' playlist contents are not available. |
| 4 | **Playback control of an existing Spotify session** | Player endpoints remain listed: get playback state, devices, transfer, start/resume, pause, next, previous, seek, volume, repeat, shuffle, queue, recently played. These act on a Spotify Connect device (the Spotify app or a Web Playback SDK device). | [P] changelog "Player"; [U] whether each control requires Premium on the account | This controls Spotify's own playback. It does not give Dhun audio. Under §III.5 it is still an integration with another service's stream and needs review. |
| 5 | **Actual audio playback inside Dhun** | Web Playback SDK: "requires a Spotify Premium subscription (mobile only types of premium subscriptions are excluded)", supported in Chrome, Firefox, Safari and Edge on desktop and mobile. "This SDK must not be used in commercial projects without Spotify's prior written approval." Native SDKs were **not** researched. | [P] `developer.spotify.com/documentation/web-playback-sdk` | Possible in a browser for a Premium user only. Requires an SDK script from Spotify, which would change Dhun's CSP (see §5). Not built. |
| 6 | **Saving or synchronising user data** | Writes to the library and playlists. Policy §I.2 requires an easy way to disconnect and deletion of that user's data on disconnect. §III.9 restricts transfer of data to another service, with an exception for a user's own playlist metadata. | [P] developer policy §I.2, §III.9 | Any sync must be user-initiated, disconnectable, and deletable. Cross-source playlist transfer needs its own review. |

## 3. Access, accounts and cost

- **Development Mode (default).** The app owner must hold an active Spotify Premium
  subscription for the app to work. If it lapses, the app stops working. Up to five
  authorised users may use the app, each added to an allowlist. [P] February 2026
  migration guide and quota-modes page (quota-modes read as search excerpt [S]).
- **Client IDs.** The February 2026 announcement limited each developer to one Development
  Mode Client ID. Secondary coverage dated 2026-08-18 says Spotify raised this to 25 per
  developer account on 2026-07-23, with one shared quota across them. This could not be
  confirmed on a primary page in this session. [S] — **treat as unverified.**
- **Quota.** Requests above the quota return HTTP 429 with `reason: QUOTA_EXCEEDED`.
  Quota is per developer account, not per Client ID. [S]
- **Extended quota.** Spotify's quota-modes page states that, since 15 May 2025, only
  organisations are accepted, applying from a company email, with a launched service and
  at least 250,000 monthly active users. [S] The developer policy §VII.3.2 says eligibility
  "may change at any time". [P]
- **Cost.** Development Mode has no API fee, but the owner must pay for Premium. The
  Premium price was not verified in this session. [U]
- **Commercial use.** Developer Policy §IV.2 limits commercial use of streaming SDAs (a
  "Streaming SDA" is one that streams Spotify content). The Web Playback SDK page
  requires written approval for commercial projects. Dhun is free and GPL-3.0; the
  repository does not record any commercial intent, and this should be confirmed by the
  maintainer (ADR-010 D4). [P]

## 4. Policy constraints that apply to Dhun

From the Spotify Developer Policy, `developer.spotify.com/policy`, effective 15 May 2025,
read 2026-10-20 [P]. Paraphrased; read the page before relying on it.

| Clause | Requirement | Dhun status |
|---|---|---|
| **III.5** | "Do not create any product or service which is integrated with streams or content from another service." | **Conflicts with the multi-source goal.** A single Dhun product that combines Spotify with YouTube or another source is, read literally, prohibited. Needs a written legal opinion or a Spotify approval. |
| **III.7** | Do not permit any device or system to "segue, mix, re-mix, or overlap any Spotify Content with any other audio content". | Constrains any queue that mixes sources, and gapless/crossfade work. |
| **III.11** | Do not build products "that mimic, or replicate or attempt to replace a core user experience of Spotify" without permission. | Dhun's player and library must add independent value. Not assessed in detail. |
| **III.4 / III.10** | No non-interactive webcasting; no products targeted at businesses. | Dhun is an interactive personal player. Compatible on the face of it. |
| **III.9** | No transfer of data to another service, except the user's own personal data or their playlist metadata. | Constrains cross-source playlist import and export. |
| **III.13 / III.14** | No analysis of Spotify content for listener metrics; no ingestion into ML or AI models. | Constrains any recommendation or analytics feature that uses Spotify data. |
| **IV.1** | "Streaming of music sound recordings through the Spotify Platform shall only be made available to subscribers to the Premium Spotify Service." | Free-tier users cannot be given Spotify playback in Dhun. |
| **II.4 / II.5** | Display Spotify metadata and cover art with attribution using the Spotify Marks; metadata and artwork must link back to the Spotify item; when streaming, show cover art and metadata with every playback. | Required if Spotify metadata is shown. Branding not reviewed. |
| **II.4.3 / V** | Metadata, cover art and preview clips must not be offered as a standalone product. | Dhun must not present Spotify browsing as a separate product. |
| **I.2** | Give an easily accessible way to disconnect the Spotify account and delete the user's data on disconnect. | Requires a disconnect and delete flow in the app before any connection. |
| **VII.3** | Quotas may be applied; extended access requires meeting Spotify's criteria, which may change. | Dhun must not assume extended quota. |

## 5. Browser, CSP and platform constraints

- **Web Playback SDK in a static page.** The SDK is a client-side script; the page must
  load it from Spotify's origin, and the SDK's own iframes must allow `encrypted-media`
  and `autoplay` for cross-origin frames (Spotify troubleshooting notes). [P] The exact
  script and frame origins were **not** read [U]. Adding them would widen the
  `script-src`, `frame-src` and `media-src` directives. This conflicts with Dhun's
  no-third-party-runtime rule and needs ADR-010 D2 and D3.
- **Spotify on iOS.** The SDK does not start playback automatically after a transfer; the
  user must interact with the SDK's events. [P]
- **Native platforms.** Android and Windows Spotify SDK paths were not researched. [U]
- **OAuth.** The tutorial index lists "Authorization code PKCE" among its flows and
  marks the implicit grant as deprecated, with a separate migration page for insecure
  redirect URIs. [P, navigation text] The redirect-URI rules themselves were not read [U].
  The Client Credentials flow is described in a secondary source as being moved away from
  for metadata endpoints [S].
- **Secrets.** A Spotify client secret must never be shipped in browser code (prompt Part
  3.4). PKCE avoids the need for one in the browser. [P, flow tutorial title; detail U]

## 6. Maintenance and breakage risk

- Spotify changed Development Mode rules in February 2026, postponed some endpoint
  changes for existing apps, and later changed the Client ID limit (secondary sources). [P,S]
- A Dhun Spotify adapter would depend on a surface that changed materially within six
  months. Expect breakage on a schedule Dhun does not control.
- Every endpoint in §2 must be re-checked against the changelog before implementation.

## 7. Primary sources read on 2026-10-20

- Migration guide, February 2026 Dev Mode changes: `https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide` (checklist and patterns; "Search pagination" item)
- Web API changelog, February 2026: `https://developer.spotify.com/documentation/web-api/references/changes/february-2026` ("Track", "User", "Playlist", "Endpoints still available")
- Web Playback SDK overview: `https://developer.spotify.com/documentation/web-playback-sdk` (Premium requirement, supported browsers, commercial-use legal note, troubleshooting)
- Spotify Developer Policy: `https://developer.spotify.com/policy` (sections I, II, III, IV, V, VII)
- Quota modes: `https://developer.spotify.com/documentation/web-api/concepts/quota-modes` (search excerpt only [S])
- Secondary, dated 2026-08-18 and quoted in §3 [S]: itechguides.com coverage of the Client ID change; vorplabs.com summary

## 8. Open questions for the maintainer or counsel

1. Does §III.5 prohibit a multi-source Dhun? (ADR-010 D3.)
2. Would Spotify approve a non-commercial, GPL, open-source client that plays Spotify only
   for Premium users? (Developer Terms; not asked.)
3. Who would own the Spotify Developer account and its Premium subscription, and does the
   maintainer accept the recurring cost? (ADR-010 D4.) Credentials are never requested in chat.
4. Native SDK support, redirect-URI rules, and the Premium requirement for each player
   endpoint were not verified.
