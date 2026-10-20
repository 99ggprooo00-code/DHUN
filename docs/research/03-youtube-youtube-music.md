# 03 — YouTube / YouTube Music: source research

**Date of research:** 2026-10-20. **Scope:** what the official YouTube surfaces allow
Dhun to do, what the existing native and web code does, and where they conflict.
**Source of truth for policy:** the primary pages listed in §7, read on the access
date above. Anything not read there is marked *not verified*.

**Evidence labels used below:** `[P]` primary page read in this session ·
`[R]` repository code or document · `[S]` search-result excerpt only (not read in full) ·
`[U]` not verified.

---

## 1. Summary

- YouTube is the **primary source for the native apps** today (ADR-001). Metadata
  comes from DHUN's own InnerTube client; audio URLs come from a resolver chain
  (yt-dlp on desktop, an in-JVM resolver on Android). This is **not** an official
  API path. [R]
- The **official** YouTube surfaces are: the YouTube Data API v3 (metadata, search,
  playlists, quota-limited), and the YouTube IFrame Player API (an embedded player
  that the page displays and scripts). [P]
- The Data API reference pages read in this session describe metadata and resource
  operations, and no stream-URL resource was found in them. The exhaustive method list was
  not checked [U]. The policy in §III.I.14 forbids using other technology to fetch
  audiovisual content. [P]
- The IFrame Player API is the only official browser playback mechanism identified.
  It plays a visible YouTube player inside the page. It does not give Dhun an
  `<audio>` element, a separate audio stream, or a background player. [P]
- **No repository document records a check of the native extraction path against these policies.** This is the most important open item. See §4 and ADR-010 D1.

## 2. Official capabilities

| Capability | Official surface | Evidence | Notes |
|---|---|---|---|
| Keyword search for videos, channels, playlists | Data API `search.list` | [P] quota calculator; search reference (`developers.google.com/youtube/v3/docs/search/list`, listed as "100 calls per day", costs 1 unit in the Search Queries bucket) [S for the reference page's quota text] | Search is a **separate 100-calls-per-day bucket** by default, not 100 units. Verified on the quota-calculator page, last updated 2026-10-15. |
| Video, playlist and channel metadata | `videos.list`, `playlists.list`, `playlistItems.list`, `channels.list` | [P] quota calculator (1 unit each) | Shared 10,000-unit daily pool with other read/write methods. |
| Playlist and subscription writes | `playlists.insert/update/delete`, `subscriptions.*` | [P] quota calculator (50 units each) | Writes require user OAuth; policy §III.D.2 applies (see §4). |
| Upload | `videos.insert` | [P] quota calculator: separate 100-calls-per-day bucket | Not relevant to Dhun's product today. |
| Embedded playback in a web page | YouTube IFrame Player API | [P] `developers.google.com/youtube/iframe_api_reference` (intro: queue, play, pause, stop, volume, status, event listeners) | Displayed player. Requirements in the required-minimum-functionality page: an HTTP Referer identifying the API client, and no suppression of the Referer. [P] |
| Audio-only stream URL, `<audio>` playback | **None found in the pages read** | [P] policy §III.I.14 (no non-API access to audiovisual content), §III.I.07 (no separating audio/video components) | Treated as *not permitted* for a third-party client, pending legal review. |
| Lyrics (timed) | **None found in official docs** | [U] not searched in this session | The existing `YouTubeLyricsSource` uses InnerTube browse data; see §4. |
| Likes, history, user library | Data API user resources, OAuth | [P] policy §III.A–C on user authorisation; [U] exact scope list not read | Requires OAuth; Dhun has no OAuth flow today. |
| YouTube Music catalogue API | **Not verified** | [U] no official YouTube Music catalogue endpoint was searched for or read in this session | Do not assume one exists. |

## 3. Quota and cost

- Default allocation for a Google Cloud project with the Data API enabled: **100
  `search.list` calls per day, 100 `videos.insert` calls per day, and 10,000 units per
  day for all other methods**, resetting at midnight Pacific Time. [P] quota
  calculator, last updated 2026-10-15.
- Per-call cost: `search.list` costs 1 unit in its own 100-calls-per-day bucket.
  `videos.list`, `channels.list`, `playlists.list` and `playlistItems.list` cost 1 unit
  each from the shared pool. Most writes (`playlists.insert`, `playlistItems.insert`,
  `subscriptions.insert`) cost 50 units. [P]
- Every request, including invalid ones, costs at least one unit. [P]
- **Consequence for Dhun:** at 100 searches per project per day, the default quota
  cannot serve more than a few dozen users of a public web client. An extension is
  granted through Google's review process; the quota page refers to "compliance
  audit" for allocations above the defaults [P, wording "anything above them requires
  passing YouTube's compliance audit" comes from a search excerpt, so treat it as [S]].
- **Cost:** the Data API has no listed fee. Extensions are not purchasable. [S] Not
  verified on a primary page in this session.
- A proxy or server-side search cache would be a DHUN-operated backend. That is
  excluded by the current product constraints (ADR-008 boundary 3) and needs a
  decision (ADR-010).

## 4. Policy constraints that apply to Dhun

These are quoted or paraphrased from the YouTube API Services Developer Policies,
`developers.google.com/youtube/terms/developer-policies`, read 2026-10-20 [P].

| Section | Requirement (paraphrase; read the policy before relying on it) | Dhun status |
|---|---|---|
| **III.I.14** | Must not "use any technology other than YouTube API Services to access or retrieve API Data, including to access any portion of any YouTube audiovisual content". | **Conflicts** with the native resolver chain (yt-dlp, DHUN's own InnerTube player call) if those count as "other technology" for "API Data" or "audiovisual content". Whether an unofficial client is covered is **unresolved**; the repository does not record a legal opinion. |
| III.I.07 | Must not "separate, isolate, or modify the audio or video components" of YouTube audiovisual content. | Audio-only playback extracted from the video stream is at risk under this clause. Same open question as III.I.14. |
| III.I.09 | Must not create features that play content "from a background player", meaning a player not displayed on the page, tab or screen. | Relevant to Android and desktop background audio and to the web app's play-while-hidden behaviour. Not checked against the app's actual behaviour in this session. |
| III.I.01 | Must not "create, offer, or act as a substitute for ... any YouTube Applications", or recreate YouTube's core user experience without significant independent value. | Dhun is a music client that plays YouTube audio. Whether it "acts as a substitute" is a matter of judgement; the repository has no recorded assessment. |
| III.I.13 | Must not circumvent geographic restrictions, including IP-based ones. | Relevant to the region-aware resolver behaviour and to any fallback that changes client identity to get around a block. Not assessed. |
| III.E.4 | Most stored API Data must be refreshed or deleted within **30 calendar days**. Non-authorised statistics must not be stored beyond 30 days. | Dhun's liked tracks, history and library keep YouTube-derived metadata indefinitely. Applicability depends on whether that data is "API Data" (see III.I.14). |
| III.D.1 (credential items 2–4) | Credentials must be used only by the project that created them, and must not be shared or embedded in open-source projects. | Dhun is GPL open source, so any Data API key committed to the repository or bundled into the web app would breach this. A grep of the repository on 2026-10-20 found no key-shaped string (`AIza…`) and no `googleapis.com/youtube` reference. The built web bundle was not separately scanned. |
| III.D.4–III.D.6 | Must clearly identify actions that insert, update or delete data on the user's behalf, obtain their express consent before execution, and identify the channel and visibility settings involved. | Relevant only if Dhun adds playlist or like writes to YouTube. None exist today. |
| III.H | YouTube may audit the client and request test accounts. | Applies to any shipped API client. Not a current action item. |

**Reading of the policies.** The clauses in §III.I are written broadly. A literal
reading prohibits the native resolver chain for audio. A narrower reading applies
only to "YouTube API Services", leaving unofficial InnerTube traffic outside the
clause. The repository does not contain the analysis that would choose between them.
This research records the question and does not answer it. Legal review is needed
before any new YouTube audio feature is built, and before the native apps are
described as compliant.

## 5. Browser and platform constraints

- **Web, static origin:** the IFrame Player API loads `www.youtube.com` and requires a
  third-party script. The current CSP (`app-web/src/index.html`) allows only the
  catalogue hosts and `'self'`. Adding YouTube would require widening `script-src`,
  `frame-src` and `connect-src`, which the repository's own privacy rules forbid
  without a decision (ADR-008 boundary 1; `docs/web-player/KNOWN-LIMITATIONS.md`).
  [R][P]
- **Referer:** the IFrame player needs an HTTP Referer that identifies the client [P].
  Whether a static GitHub Pages origin sends a Referer that YouTube accepts was not
  verified, and the Referrer-Policy of the deployed pages was not checked [U].
- **Web, direct stream from the browser:** the repository's own record says the
  InnerTube endpoint "refuses a browser's player request often enough that the app
  spent most of its life showing an error" (`app-web/src/js/api.js` header comment).
  That is a repository observation, not an official statement. [R]
- **Android and desktop:** Media3 and libVLC play resolved audio URLs. Native WebView
  playback of the IFrame API is possible but needs a `Referer` set by the app
  (required-minimum-functionality, "WebView Type" section) [P]. Not built or tested
  here.

## 6. Existing implementation, mapped to the sources above

| Native component | What it does | Policy position |
|---|---|---|
| `shared/.../innertube/InnerTubeClient.kt` | Search, browse, related, lyrics browse via DHUN's own InnerTube calls | Not the Data API. Covered by the III.I.14 question. |
| `shared/.../extraction/OwnClientStreamResolver.kt`, `OfflineFirstStreamResolver.kt` | Resolve audio URLs for playback and offline download | III.I.07 and III.I.14 question. Highest-risk component. |
| `shared/.../lyrics/YouTubeLyricsSource.kt` | Lyrics via InnerTube | III.I.14 question; also lyric content rights are not established (provider-integration-review.md). |
| `tools/playback-probe/` | Daily rot-drill against YouTube | Reads YouTube; same question. |
| `app-web/` | Does not use YouTube at all (JioSaavn) | Not subject to these clauses; its own terms question is in `docs/web-player/KNOWN-LIMITATIONS.md`. |

## 7. Primary sources read on 2026-10-20

- YouTube API Services — Developer Policies: `https://developers.google.com/youtube/terms/developer-policies` (sections III.D, III.E.4, III.H and III.I read; the reader returns the page in 14 parts and parts 0, 4, 7, 10 and 11 were read)
- YouTube API Services — Required Minimum Functionality: `https://developers.google.com/youtube/terms/required-minimum-functionality` (sections "API Client Identity and Credentials", "Referer format")
- YouTube Data API — Quota Calculator: `https://developers.google.com/youtube/data/determine_quota_cost` (last updated 2026-10-15 UTC)
- YouTube IFrame Player API reference: `https://developers.google.com/youtube/iframe_api_reference` (introduction only; the functions and events sections were not read)
- Search result excerpts only [S]: Data API `search.list` reference page; "anything above them requires passing YouTube's compliance audit" (secondary sites quoting the quota page).

## 8. Open questions for the maintainer or counsel

1. Does YouTube's developer policy bind DHUN's unofficial InnerTube and yt-dlp use for audio? (F1; ADR-010 D1.)
2. If it does, what is the replacement for audio-only playback in the native apps? (The
   official options reviewed here are the IFrame player, which shows video.)
3. Is the 30-day limit on stored YouTube data applicable to local library metadata? (F3.)
4. Does the web app adopt the IFrame player, widening the CSP and adding a third-party
   script? (ADR-010 D2.)
5. Whether a current official YouTube Music catalogue API exists was not verified.
