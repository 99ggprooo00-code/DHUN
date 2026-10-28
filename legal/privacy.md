---
id: privacy
title: Privacy Policy
effectiveDate: 2026-10-28
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

{{appTitle}} ({{appTitleDevanagari}}) is a free, open-source, cross-platform,
multi-source music client by direction. YouTube / YouTube Music is the first
implementation priority; this page describes the **native Android/desktop**
build, which currently uses YouTube Music. The browser build uses JioSaavn and
is described separately in section 8. Spotify is planned and deferred, with no
adapter, OAuth, SDK, token storage or Spotify requests in either build. No
hypothetical Spotify data flow is active. **[source]**

The native build has no DHUN account system, DHUN server, analytics, advertising
or crash-reporting integration found in its source. **[source]**

The library, settings and caches DHUN persists are local. This does not mean
providers receive no data. When you search,
browse or play, the app talks directly to YouTube and, when lyrics are enabled,
to configured lyric providers including LRCLIB and KuGou — and those requests reveal your IP address to those services, as any
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

The database is not uploaded as a library or synced to a DHUN service. However,
saved identifiers and track metadata can be reused for provider requests (including
recommendations, artwork, lyrics and playback). Local storage is not a claim that
these values never leave the device. **[source]**

| Data | Where it lives |
| --- | --- |
| Liked songs, playlists, playlist entries | local SQLite database |
| Listening history | local SQLite database |
| Recent searches | local SQLite database |
| Track metadata you have browsed | local SQLite database |
| Queue and now-playing position | local SQLite database |
| Cached lyrics, explicit per-song lyric choices (including local TTML imports), timing offsets and romanization preferences | local SQLite database |
| Settings (theme, accent, cache size, backdrop) | local SQLite database |
| Download records, downloaded audio and saved artwork | Records in SQLite; Android app-private files; packaged desktop `userdata/downloads` next to the launcher |
| Streamed-audio / prebuffer cache (separate from persistent downloads) | Android app cache (Media3 segments); desktop `cache/audio` under the data directory (whole tracks, temporary and partial files) |
| Artwork cache and widget/OS media metadata | Image-loader/platform caches; titles/artwork can appear on notifications, lock screens, widgets, shortcuts and desktop media controls |
| Desktop startup log | `dhun-startup.log` in the data directory; system temp fallback |

Android sets `allowBackup="false"`, `hasFragileUserData="false"` and
`usesCleartextTraffic="false"` in its manifest, so the system backup mechanism
does not copy this data off the device. **[source]**

**Desktop locations:** packaged builds use `<installDir>/userdata`; unpackaged
builds use `%APPDATA%/DHUN` (Windows), `~/Library/Application Support/DHUN` (macOS)
or `$XDG_DATA_HOME/dhun` / `~/.local/share/dhun` (Linux). Platform hardware and
uninstall behaviour have not been newly verified in this audit. **[source]**

**Diagnostics are local, not automatically uploaded.** Desktop startup failures
write timestamps, exceptions/stack traces, OS/JVM versions and user/install/working
paths, which may contain an OS username. Android Logcat and playback details can
include track identifiers, resolver identity/outcome and error text; artwork
failures can log URLs. Redaction is not proven for every exception or external
yt-dlp message. Inspect and redact logs before voluntarily posting a GitHub issue.
DHUN does not send them automatically. Public issues are processed by GitHub and
can be read by others. **[source]**

### Listening-history controls

Settings → Privacy & history → **Record listening history** is on by default.
Turning it off prevents new local listening-history entries and playback-completion
updates after the preference is saved. Existing history stays until you clear it;
plays skipped while recording is off are not added later. **[source]**

This is not an incognito or network-blocking mode. Queue restoration, searches,
track metadata, audio/artwork/lyrics caches and requests to providers remain
independent. Category search in Settings filters category labels locally and is
not saved or sent to providers. **[source]**

## 4. Data sent to third parties

Every item below is sent by the app to a third party. {{appTitle}} does not
control how those parties process it.

| Recipient | What the app sends | When |
| --- | --- | --- |
| YouTube / Google | Search terms; track, album, artist and playlist identifiers; the client name and version; the country code from settings; and visitor identifiers returned by YouTube and reused in memory during the client process (including `X-Goog-Visitor-Id` for applicable requests) | Browsing, searching, resolving playback **[source]** |
| YouTube / Google | Ordinary media requests, which include your IP address | Playing or downloading a track **[source]** |
| YouTube / Google | Requests for artwork, which include your IP address | Showing artwork **[source]** |
| YouTube / Google | Current track identifier for lyrics | When lyrics are enabled and this configured source is used **[source]** |
| LRCLIB (`lrclib.net`) | Track title, artist, album and duration; manual match-search query; a DHUN version/project User-Agent | When lyrics are enabled, no saved/cache result satisfies the request, and this configured source is tried; also explicit match searches **[source]** |
| KuGou (`lyrics.kugou.com`) | Track title, artist and duration; returned candidate identifiers for lyric download | When lyrics are enabled and this configured source is tried; also explicit match searches **[source]** |

Recipients see your network address and request headers (including a client or
transport User-Agent where supplied); media requests can include signed URLs and
range headers. YouTube homepage/watch/player-JavaScript requests also discover
client version and signature timestamp. No packet capture was performed here;
response headers and provider retention are not exhaustively verified. **[source]**

DHUN does not control providers' logging, retention, deletion or subsequent use.
For Google's stated practices see [Google Privacy Policy](https://policies.google.com/privacy).
LRCLIB, KuGou and other service operators have their own practices; their
retention schedules and content permissions have not been established here.
**[third-party] [legal]**

### Lyrics controls and on-device romanization

The `lyrics_enabled` setting is read by both the player and lyrics repository.
Turning it off prevents new lyric lookups through those paths; already transmitted
requests cannot be recalled. Enabled lookups use saved choices and cache first, then
configured providers with bounded fallback. **[source]**

The match picker sends queries/track metadata only when Search is requested. A
per-song choice is stored locally and survives clearing the automatic lyric cache;
choose **Use automatic** in the match picker to remove that choice. **[source]**

**Paste local TTML lyrics** accepts a document you explicitly paste into the match
picker. Preview parsing runs on-device, without provider requests or automatic
clipboard reads. Only **Choose this match** saves the parsed text/timing for that
song; the raw XML is not saved. Unsupported or unsafe XML is rejected, and a failed
import leaves the previous choice intact. These controls do not establish rights
to redistribute imported lyrics. **[source]**

Optional romanization uses bundled ICU4J transforms on the device. It does not send
lyric text to a transliteration service. Its bounded in-memory cache is keyed by
source text and mode; it is not translation. The toggle and mode persist locally.
Ambiguous Han/Cyrillic text needs an explicit mode override, and Japanese Kanji is
not converted to phonetic readings. **[source]**

### What is not sent

The app does not send a {{appTitle}} account identifier, because there is no
account. No HTTP client in the source installs a cookie plugin, and the
extraction design uses no cookies and no PO tokens. **[source]**

Android metadata/download clients use Ktor/OkHttp; desktop metadata uses Ktor/CIO
and some media/artwork paths use platform networking. Absence of cookie import
or sign-in does not mean absence of YouTube visitor identifiers or server logs.
Exact transport-added headers and every third-party response are not verified.
**[source]**

### Developer-only exception

Only the explicitly channelled test release offers the Settings → Test tools →
Theme catalogue preview. Opening it loads sample images from `picsum.photos`.
Ordinary development and public-release builds do not offer the entry, and public
Android builds discard a restored catalogue route. **[source]**

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
- **Packaged Windows:** the installer is designed to remove its managed
  `userdata` on uninstall and preserve it on upgrade. Packaging tests do not
  prove every user's filesystem outcome. Unpackaged OS-convention data and
  system-temp logs may remain and require manual deletion. Linux/macOS removal
  has not been hardware-verified. **[source]**
- **In app:** clear downloads, listening/search history, automatic lyrics cache
  and, when the platform callback is available, audio cache. Saved lyric choices
  survive automatic-cache clearing; select **Use automatic** per song to remove
  a choice. Reset settings is not erase-all. Completion/failure feedback is not
  uniformly implemented for all Settings clear actions; verify the result.
  See Support & Feedback for scope and remaining limits. **[source]**
- **Export:** no general data-export feature exists. **[source]**
- **Account deletion:** not applicable — there is no {{appTitle}} account.
  **[source]**

**Known native deletion limitation:** current download storage implementations
can ignore a failed filesystem delete while download rows are removed. Some
Settings dialogs also close before asynchronous work finishes. Files/data can
therefore remain after apparent completion; verify the local result. These are
open engineering defects, not a guarantee of erasure or a claim of legal review.
**[source] [runtime]**

Clearing your local history removes your local record only. It does not delete
anything YouTube or Google may hold, and this app cannot ask them to.
**[source] [legal]**

## 8. The website and the web app

The marketing site is static: its only `<script>` elements are JSON-LD data
blocks, and it loads nothing from a third-party origin. GitHub, as the host,
processes requests under its own policies. **[source]**

The web app at `/app/` is a **separate browser client** of the same product,
not the native YouTube client compiled for browsers. Search/catalogue/stream
resolution requests go to `saavn.dev`, `saavn.sumit.co` and
`saavnx.vercel.app`, unofficial community JioSaavn wrappers; audio/artwork are
restricted by CSP to `*.saavncdn.com`. Failover may send the same query/IDs to
multiple wrapper operators. API fetch uses `credentials: "omit"` and `cache: "no-store"`; browser media,
image, header and cache behaviour remain browser-managed. No YouTube or Spotify
API, embed or playback requests are implemented in this browser build. **[source]**

Likes, playlists, history, recent searches, referenced track snapshots (including
artwork URLs but no persisted stream URL) and settings live in browser
`localStorage` under `dhun.web.v3`. Export/import use a user-chosen JSON file.
Erasing the library keeps settings and the offline interface cache; clearing the
origin's site data removes those too. The service worker caches the shell, no
cross-origin audio or API responses. The browser may separately cache media.
There is no browser audio-download, lyrics, equaliser, account or telemetry
integration. **[source]**

Requests disclose network/request data to those operators; DHUN cannot delete
their logs. GitHub Pages processes hosting requests. Read the browser's own
Privacy and Terms drafts in About for build-specific detail; nothing here
establishes provider authorisation or retention guarantees. **[source] [legal]**

## 9. Open questions

1. Name the data controller, or state that the project is a personal,
   non-commercial open-source effort, and publish a contact channel.
   **[maintainer]**
2. Review lyric providers' current content rights, retention and rate-limit terms
   before adding new providers or external translation services.
   **[maintainer]**
3. Review the existing desktop yt-dlp disclosure and diagnostic redaction against
   the version users actually install.
   **[maintainer]**
4. Name the jurisdictions the project intends to serve, so the correct notices can
   be drafted by a lawyer. **[maintainer] [legal]**
5. Have a qualified lawyer review this page and the YouTube/Google terms item on
   the Terms of Use page. **[legal]**

