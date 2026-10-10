---
id: terms
title: Terms of Use
effectiveDate: 2026-10-10
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
and 16 of that licence, **without any warranty**. There is no paid tier, no
account, and no {{appTitle}}-operated service. **[source]**

## 2. This is a pre-release

The public build is an experimental pre-release:

- It is not stable, not store-signed, and has not been accepted on real hardware
  as a release. **[maintainer]**
- The Android APK is signed with a **public test key that is committed to the
  repository**, so a matching signature does not prove who built the file.
  **[source]**
- The Windows MSI is **unsigned**, so Windows may warn when you run it.
  **[source]**
- No promise of support, fixes or upgrade paths is made. **[source]**

## 3. YouTube and Google content — this section needs legal review

{{appTitle}} plays and browses YouTube Music content by calling YouTube's own
endpoints. Those endpoints and that content are provided by Google LLC and its
licensors, not by {{appTitle}}. {{appTitle}} is **not affiliated with, endorsed
by, authorised by, or connected to YouTube or Google.** **[source]**

The YouTube Terms of Service page fetched on 2026-10-10 — itself dated 15
December 2023 — includes restrictions that appear relevant to this app. They are
paraphrased here for review, not interpreted: **[third-party] [legal]**

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

A scheduled health check in this project has been failing since 2026-10-06
because the hosted runner is blocked by YouTube's bot detection. That is a known,
visible red signal and it is not hidden. **[runtime]**

## 6. Contact

Contact for legal or policy matters: **not yet published.** **[maintainer]**

## 7. Open items before this page can be in force

1. Legal review of section 3. **[legal]**
2. A decision on the legal entity or personal name that issues these terms.
   **[maintainer]**
3. A decision on jurisdiction and governing law, if any. **[maintainer]**
4. A contact channel. **[maintainer]**
5. A decision on where these terms are linked from. **[maintainer]**
