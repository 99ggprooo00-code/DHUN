---
id: about
title: About Dhun
effectiveDate: 2026-10-28
status: current
---

## What {{appTitle}} is

{{appTitle}} ({{appTitleDevanagari}}) is a free, open-source, cross-platform,
**multi-source music client**. YouTube / YouTube Music is the first
implementation priority. Spotify is a planned additional source, not implemented;
its work is deferred pending technical, account/Premium, policy, legal, playback,
privacy and cost checks. Other sources may be evaluated on the same evidence-based
basis. Capabilities can differ by platform. **[source]**

**This native Android/desktop build currently uses YouTube Music only** for music
catalogue and playback, through an unofficial InnerTube client and the project's
own stream-resolver chain. Desktop can also use an optional, user-installed
yt-dlp fallback; it needs system VLC/libVLC for audio. There is no provider sign-in,
OAuth, cookie import or PO-token flow. Downloads are stored on your device;
permissions for extraction and offline use remain unresolved, not approved by a
disclaimer. **[source] [legal]**

The separate browser client currently uses JioSaavn through unofficial community
API wrappers, not YouTube or Spotify. It has an offline interface, not offline
audio, and its own browser-specific legal drafts. Live catalogue access and
audible playback from the deployed browser origin remain unverified. **[source]**

The software is licensed under the GNU General Public License v3.0. There is no
paid DHUN tier or DHUN account. That licence does not grant rights to third-party
music, artwork, lyrics, streams, APIs or trademarks. Future providers may have
their own account and subscription requirements. **[source] [legal]**

## This build

| | |
| --- | --- |
| Version | {{appVersion}} |
| Version code | {{appVersionCode}} |
| Release channel | {{releaseChannel}} |
| Platform | {{platform}} |

The version above is read from this build's own metadata at run time; it is not
typed into the app. On Android it comes from the installed package; on Windows
from the installer version passed to the JVM. The platform row distinguishes
Android, Windows and other JVM desktop builds; Linux/macOS hardware acceptance
is not established. **[source]**

**Pre-release status.** This build is experimental. The Android package is signed
with a public test key that is committed to the repository, so the signature does
not prove who built the file. The Windows installer is not Authenticode-signed,
so Windows may warn on first run. **[source]**

## Where to look next

- **Privacy Policy** — what this build stores and what it sends, with the
  evidence for each statement.
- **Terms of Use** — the licence, the pre-release limits, and the unresolved
  YouTube/Google question.
- **Open-Source Licenses** — the full licence texts that ship with this build.
- **Third-Party Notices** — every dependency and what it is used for.
- **Support & Feedback** — how to report a problem and what your data controls
  actually do.
- **Security Reporting** — how to report a vulnerability privately.

## Independence

{{appTitle}} is **not affiliated with, endorsed by, authorised by, or connected to
YouTube, Google, JioSaavn or Spotify.** Provider names and trademarks belong to
their respective owners; mentioning a planned source is not a partnership. Song metadata
and artwork belong to their respective owners and are fetched at run time; they
are never bundled with the app. **[source]**

## Project status

Experimental, not a stable or hardware-accepted release. Existing unit tests and
historic playback reports are not a guarantee that a current provider request
will succeed on your network. Health-check results are dated observations in the
repository, not a live verdict embedded in this offline page. **[source]**

Current direction and provider evidence:
[Product requirements](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/PRD.md),
[capability matrix](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/research/05-source-capability-matrix.md)
and [ADR-010](https://github.com/99ggprooo00-code/DHUN/blob/main/docs/decisions/ADR-010-multi-source-provider-direction.md).
