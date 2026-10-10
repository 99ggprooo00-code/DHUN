---
id: privacy
title: Privacy Policy
effectiveDate: 2026-10-10
status: draft
---

> **DRAFT — not yet in force.** This page describes what the {{appTitle}} code in
> this build actually does. It has not been reviewed by a lawyer, no data
> controller is named, and no contact channel has been published. Read it as an
> accurate technical description, not as a legal guarantee.

Every factual sentence below carries a status tag:

- **[source]** — read from the app's own code in this build.
- **[runtime]** — observed by running the app.
- **[third-party]** — taken from the third party's own published documentation.
- **[maintainer]** — awaiting a decision from the project maintainer.
- **[legal]** — awaiting legal review.

## 1. Summary

{{appTitle}} ({{appTitleDevanagari}}) is a free, open-source music player for
Android and Windows. It has no account system, no server of its own, no
analytics, no advertising and no crash reporting. **[source]**

Everything it remembers about you is stored on your device. When you search,
browse or play, the app talks directly to YouTube and, for synced lyrics, to
LRCLIB — and those requests reveal your IP address to those services, as any
internet request does. **[source]**

## 2. Who operates {{appTitle}}

{{appTitle}} is free software under the GNU General Public License v3.0. The
repository contains no {{appTitle}}-operated server, account system or telemetry
endpoint. **[source]**

No legal entity and no privacy contact have been published.
**[maintainer]** — contact details are not yet published. Until they are, this
page cannot tell you whom to write to, and that is stated here rather than filled
in with a placeholder address.

## 3. Data stored only on your device

Nothing in this section leaves your device by the app's own design. **[source]**

| Data | Where it lives |
| --- | --- |
| Liked songs, playlists, playlist entries | local SQLite database |
| Listening history | local SQLite database |
| Recent searches | local SQLite database |
| Track metadata you have browsed | local SQLite database |
| Queue and now-playing position | local SQLite database |
| Cached lyrics | local SQLite database |
| Settings (theme, accent, cache size, backdrop) | local SQLite database |
| Downloaded audio and saved artwork | Android: the app's private files directory. Windows: the per-user install folder. |
| Streamed-audio cache (size-capped) | Android: the app cache directory |
| Desktop startup log | Windows: the per-user install folder, or the system temp folder if that is unavailable |

Android sets `allowBackup="false"`, `hasFragileUserData="false"` and
`usesCleartextTraffic="false"` in its manifest, so the system backup mechanism
does not copy this data off the device. **[source]**

**Not audited line by line:** the exact contents of the desktop startup log and of
the in-app "Playback details" text. Do not assume they are free of personal data
before you share one. **[maintainer]**

## 4. Data sent to third parties

Every item below is sent by the app to a third party. {{appTitle}} does not
control how those parties process it.

| Recipient | What the app sends | When |
| --- | --- | --- |
| YouTube / Google | Search terms; track, album, artist and playlist identifiers; the client name and version; the country code from settings; and a visitor identifier that YouTube returns and the app then reuses | Browsing, searching, resolving playback **[source]** |
| YouTube / Google | Ordinary media requests, which include your IP address | Playing or downloading a track **[source]** |
| YouTube / Google | Requests for artwork, which include your IP address | Showing artwork **[source]** |
| LRCLIB (`lrclib.net`) | Track title, artist, album and duration | Every time a track loads **[source]** |

Also: every network request carries your IP address and a user-agent string, as
with any internet request. **[source]**

### The lyrics lookup has no working off switch

The app defines a `lyrics_enabled` setting, but no code reads it: the lyric
lookup runs on every track change regardless of that key. **[source]** Turning
the setting off in a future build will not, by itself, stop the lookup — the
setting must be wired to the lookup first. Until that is fixed, treat every track
change as a metadata request to LRCLIB. **[maintainer]**

### What is not sent

The app does not send a {{appTitle}} account identifier, because there is no
account. No HTTP client in the source installs a cookie plugin, and the
extraction design uses no cookies and no PO tokens. **[source]**

**Not verified:** the exact headers the OkHttp engine adds on Android, and the
exact contents of every response YouTube returns. **[source]**

### Developer-only exception

The internal design catalogue screen loads sample images from `picsum.photos`. It
is not reachable from the navigation bar, but it can be reached through restored
state. **[source]**

### Optional desktop fallback

If you install `yt-dlp` yourself and the desktop build uses it, that tool contacts
YouTube under its own behaviour and its own privacy terms. The app runs it with
`--ignore-config`, so it does not read your personal yt-dlp configuration files.
This was not verified end to end on a user machine. **[source] [runtime]**

## 5. Analytics, advertising and crash reporting

A search of the Gradle build files and the Kotlin sources found no analytics,
advertising or crash-reporting SDK (no Firebase, Crashlytics or Sentry).
**[source]**

This is a source-level search. The fully resolved dependency tree was not
inspected, because the Gradle build does not run in the audit environment.
**[maintainer]**

## 6. Permissions

Android declares: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`,
and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. The last one only lets the app show
the system battery-exemption dialog; it does not grant the exemption.
**[source]**

The manifest declares no storage, location, contacts, camera, microphone or
account permission, and no deep-link intent filter. **[source]**

One playback service is declared `exported="true"`, which is wider than a media
session service strictly needs. **[source]**

Windows has no declared permission model. The per-user installer writes under the
user's local application data folder. **[source]**

## 7. Keeping, exporting and deleting your data

- **Android:** uninstalling removes the app-private database, downloads and
  caches. **[source]**
- **Windows:** the data lives in the per-user install folder, so an uninstall
  removes it. Upgrades are designed to preserve it; the hosted upgrade check
  passed in CI, but that check is packaging-only and does not prove behaviour on a
  user machine. **[source] [runtime]**
- **In app:** you can clear your downloads, your listening history and your recent
  searches. Each is described on the Support & Feedback page. **[source]**
- **Export:** no general data-export feature exists. **[source]**
- **Account deletion:** not applicable — there is no {{appTitle}} account.
  **[source]**

Clearing your local history removes your local record only. It does not delete
anything YouTube or Google may hold, and this app cannot ask them to.
**[source] [legal]**

## 8. The website and the browser preview

The marketing site is static: its only `<script>` elements are JSON-LD data
blocks, and it loads nothing from a third-party origin. GitHub, as the host,
processes requests under its own policies. **[source]**

The browser preview sets a strict Content-Security-Policy and makes no
third-party request. It is an engineering preview, not a product surface, and
audio playback from that origin is unproven. **[source]**

## 9. Open questions

1. Name the data controller, or state that the project is a personal,
   non-commercial open-source effort, and publish a contact channel.
   **[maintainer]**
2. Wire the lyrics setting to the lookup, or keep this disclosure.
   **[maintainer]**
3. Decide whether the desktop yt-dlp fallback needs its own disclosure.
   **[maintainer]**
4. Name the jurisdictions the project intends to serve, so the correct notices can
   be drafted by a lawyer. **[maintainer] [legal]**
5. Have a qualified lawyer review this page and the YouTube/Google terms item on
   the Terms of Use page. **[legal]**

