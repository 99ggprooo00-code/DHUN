---
id: support
title: Support & Feedback
effectiveDate: 2026-10-28
status: draft
---

> **DRAFT.** No support commitment exists. This project is an experimental
> pre-release, and nothing here is a promise of a reply.

## Report a bug or ask for a feature

Bugs and feature requests are handled in the project's public issue tracker on
GitHub. There is no support desk, no service-level agreement and no response
time. **[source]**

- [Report a bug](https://github.com/99ggprooo00-code/DHUN/issues/new) — describe
  what you did, what happened, and what you expected.
- [Request a feature](https://github.com/99ggprooo00-code/DHUN/issues/new) — say
  what problem it solves.
- [Browse open issues](https://github.com/99ggprooo00-code/DHUN/issues) — your
  problem may already be reported.
- [Read the source](https://github.com/99ggprooo00-code/DHUN) — DHUN is
  GPL-3.0; every claim on these pages can be checked against the code.

When you report a playback or download problem, include the source/provider, platform, build version (About), steps and the visible
error. Share only a minimal, redacted diagnostic excerpt if needed; logs can
contain paths, track identifiers and URLs.

**Never include** cookies, tokens, signed media URLs or account credentials in an
issue. **[source]**

## Privacy requests

There is no {{appTitle}} account, so there is no account data to request,
correct, export or delete. **[source]**

The data this app keeps is on your device, and you control it directly — see
*Your data controls* below. Android uninstall removes app-private data; packaged desktop uninstall is
designed to remove managed data, but unpackaged data and temp logs may remain.
See Privacy for paths and verification limits. **[source]**

Requests about data held by YouTube/Google, LRCLIB, KuGou, the browser
JioSaavn wrapper/CDN operators or GitHub must go to the relevant operator. This app cannot
make that request on your behalf, and clearing your local history does not delete
anything they hold. **[source] [legal]**

A privacy contact address is **not yet published.** Until the maintainer provides
one, the issue tracker can request a private contact channel without personal or
sensitive information; it is public, not a confidential privacy channel.
**[maintainer]**

## Your data controls

These are the controls that actually exist in this build. Each was read from the
code, not from a design document. **[source]**

| Control | Where | What it removes | What remains |
| --- | --- | --- | --- |
| Clear downloads | Library → Downloads | Downloaded audio files, saved artwork, leftover partial files, and the download records | Your playlists, favourites, history and cached lyrics |
| Clear history | Library → History | Every listening-history row in the local database | Your downloads, playlists, favourites and recent searches |
| Clear recent searches / Clear search history | Search → recent searches; Settings → Privacy & History | Saved recent search terms | History, playlists, favourites and other caches |
| Clear play history | Settings → Privacy & History | Local listening-history rows | Saved playlists, likes, downloads and queue restoration |
| Clear audio cache | Settings → Storage, Cache & Data, when the platform callback is available | Attempts to remove streamed-audio cache; files in use/failures may remain | Persistent downloads, library and lyrics |
| Clear lyrics cache | Settings → Lyrics & Providers | Automatic cached lyrics | Explicit per-song choices, local TTML imports, offsets and other library data |
| Use automatic | Player → lyrics match picker, per song | Saved lyric choice for that song | Automatic cache and other songs' choices |
| Record listening history | Settings → Privacy & History | Off prevents new local history entries; does not erase existing entries | Provider requests, queue restore, searches and caches |
| Reset settings to defaults | Settings → About, Privacy & Legal | Resets preferences | Library, downloads, history, caches and saved lyric choices |

Bulk clears ask for confirmation. **Not all Settings clears wait for completion
or expose errors uniformly.** Audio clearing reports bytes/failure, but its
confirmation closes immediately; lyrics and Settings history clears are launched
asynchronously without a full pending/error contract. Recheck the affected data;
report failures rather than assuming deletion succeeded. Fixing those handlers is
a separate implementation task, not a privacy guarantee. **[source]**

There is no general native data export or erase-all control, and no provider
account disconnect/delete flow because no provider account is connected. None
of these local controls deletes third-party logs. The separate browser app has
its own Settings export/import/erase controls and browser-specific drafts.
**[source]**

## Security problems

Report security problems privately. See **Security Reporting** for the interim
procedure while no private channel is enabled. **[source]**

## Contact

A direct contact address is **not yet published.** **[maintainer]**
