---
id: terms
title: Terms of Use
effectiveDate: 2026-10-28
status: draft
---

> **DRAFT — not yet in force.** Nothing on this page has been adopted by a legal
> entity or reviewed by a lawyer. It is not legal advice, and it makes no claim
> that {{appTitle}} complies with any law or any platform's terms.

Every factual sentence below carries a status tag:

- **[source]** — read from the app's own code in this build.
- **[runtime]** — observed by running the app.
- **[third-party]** — taken from the third party's own published documentation.
- **[maintainer]** — awaiting a decision from the project maintainer.
- **[legal]** — awaiting legal review.

## 1. What {{appTitle}} is

{{appTitle}} is free software licensed under the GNU General Public License
v3.0. The software is provided by its contributors as described in sections 15
and 16 of that licence, **without any warranty**. There is no paid DHUN tier,
DHUN account, or DHUN-operated API/account service. **[source]**

{{appTitle}} is a cross-platform, multi-source music client by product direction.
YouTube / YouTube Music is the first implementation priority. This native build
currently uses YouTube Music; the separate browser build uses JioSaavn through
unofficial community wrappers and has different features and legal drafts.
Spotify is planned, not implemented, and deferred pending technical,
account/Premium, policy, legal, playback, privacy, licence and cost gates. There is no Spotify sign-in, playback or data processing in
this build. Other providers are evaluated, not promised. **[source]**

The GPL licence applies to software, not third-party music, artwork, lyrics,
streams, APIs or trademarks. Public accessibility of a URL, or an open-source
wrapper's licence, does not establish permission to access, download or
redistribute the underlying content. Applicable provider terms, account,
territorial and licensing restrictions remain relevant. **[legal]**

## 2. This is a pre-release

The public build is an experimental pre-release:

- It is not stable, not store-signed, and has not been accepted on real hardware
  as a release. **[maintainer]**
- The Android APK is signed with a **public test key** that is committed to this
  repository, so a matching signature does not prove who built the file.
  **[source]**
- The Windows MSI is **unsigned**, so Windows may warn when you run it.
  **[source]**
- No promise of support, fixes or upgrade paths is made. **[source]**

## 3. YouTube and Google content — this section needs legal review

{{appTitle}} plays and browses YouTube Music content by calling YouTube's own
endpoints. Those endpoints and that content are provided by Google LLC and its
licensors, not by {{appTitle}}. {{appTitle}} is **not affiliated with, endorsed
by, authorised by, or connected to YouTube, Google, JioSaavn or Spotify.**
Provider names and marks belong to their respective owners. **[source]**

The [YouTube Terms of Service](https://www.youtube.com/static?template=terms),
re-read on 2026-10-28, include restrictions relevant to this app. They are
paraphrased for review, not a legal opinion: **[third-party] [legal]**

- Users may not access, reproduce, download, distribute or transmit any part of
  the service or any content except as expressly authorised, or with prior
  written permission. {{appTitle}} has an offline **download** feature.
- Users may not access the service by automated means without prior written
  permission. {{appTitle}} uses an unofficial, non-API client.
- Users may view or listen to content for personal, non-commercial use.

**Open question for legal review:** whether this app's features — downloads, an
unofficial client, its extraction chain, and the optional yt-dlp fallback — are
permitted under those terms, and what must be disclosed or changed. Until that
review happens, {{appTitle}} **does not claim** that its use of YouTube content
is authorised or compliant. Read the full YouTube Terms and your own local law.
**[legal] [maintainer]**

The [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies)
also restrict non-API retrieval (§III.I.14), audio/video separation (§III.I.7),
background players (§III.I.9), and storage of much API data (§III.E.4). Their
application to this unofficial path, local metadata, downloads and caching is
unresolved. ADR-010 keeps the existing path without expanding extraction-based
audio features; it is an engineering risk decision, not permission or compliance.
**[third-party] [legal]**

The browser's JioSaavn wrapper/content permissions are separately unresolved.
Planned Spotify has its own Developer Policy restrictions, including integration
with other services' content (§III.5) and Premium-only music streaming (§IV.1).
No provider agreement or approval is asserted, and no Spotify functionality is
provided by these drafts. **[third-party] [legal]**

## 4. Your responsibilities

- Use {{appTitle}} only where you are allowed to access the content it plays.
- Do not use it to circumvent access controls, redistribute content, or make
  content available to others.
- Keep your own backups of anything you cannot replace.
- When you report a problem, do not include cookies, tokens, signed media URLs or
  account credentials.

## 5. Changes and availability

Playback depends on third-party services that can change or block access at any
time. {{appTitle}} may stop working without notice, and a release may be
withdrawn. **[source]**

Provider and browser capabilities vary. A working test or past playback report
is not an availability guarantee; scheduled health results are dated repository
evidence rather than a current status feed in this offline page. **[source]**

## 6. Contact

Contact for legal or policy matters: **not yet published.** **[maintainer]**

## 7. Open items before this page can be in force

1. Legal review of section 3. **[legal]**
2. A decision on the legal entity or personal name that issues these terms.
   **[maintainer]**
3. A decision on jurisdiction and governing law, if any. **[maintainer]**
4. A contact channel. **[maintainer]**
5. A decision on where these terms are linked from. **[maintainer]**
