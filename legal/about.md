---
id: about
title: About Dhun
effectiveDate: 2026-10-10
status: current
---

## What {{appTitle}} is

{{appTitle}} ({{appTitleDevanagari}}) is a free, open-source music player for
Android and Windows. It plays YouTube Music through the project's own tokenless
client chain: there is no sign-in to create, no cookie to hand over, and no PO
token to solve. Tracks you save for offline listening are written to storage on
your own device. **[source]**

It is licensed under the GNU General Public License v3.0. There is no paid tier
and no account. **[source]**

## This build

| | |
| --- | --- |
| Version | {{appVersion}} |
| Version code | {{appVersionCode}} |
| Release channel | {{releaseChannel}} |
| Platform | {{platform}} |

The version above is read from this build's own metadata at run time; it is not
typed into the app. On Android it comes from the installed package; on Windows
from the installer version passed to the JVM. **[source]**

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
YouTube or Google.** YouTube Music is a trademark of Google LLC. Song metadata
and artwork belong to their respective owners and are fetched at run time; they
are never bundled with the app. **[source]**

## Project status

Release status for this build: **blocked — not ready for a new public release.**
A scheduled extraction-health check has been failing since 2026-10-06 because the
hosted runner is blocked by YouTube's bot detection; that failure is visible in
the project's own issue tracker and is not hidden. **[runtime]**
