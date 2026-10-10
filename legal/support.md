---
id: support
title: Support & Feedback
effectiveDate: 2026-10-10
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

When you report a playback or download problem, the fastest path is to include
the app's own log lines and your build's version, shown on the About page.

**Never include** cookies, tokens, signed media URLs or account credentials in an
issue. **[source]**

## Privacy requests

There is no {{appTitle}} account, so there is no account data to request,
correct, export or delete. **[source]**

The data this app keeps is on your device, and you control it directly — see
*Your data controls* below. Uninstalling the app removes its private database,
downloads and caches. **[source]**

Requests about data held by YouTube or Google must go to Google. This app cannot
make that request on your behalf, and clearing your local history does not delete
anything they hold. **[source] [legal]**

A privacy contact address is **not yet published.** Until the maintainer provides
one, use the public issue tracker and keep technical detail out of it.
**[maintainer]**

## Your data controls

These are the controls that actually exist in this build. Each was read from the
code, not from a design document. **[source]**

| Control | Where | What it removes | What remains |
| --- | --- | --- | --- |
| Clear downloads | Library → Downloads | Downloaded audio files, saved artwork, leftover partial files, and the download records | Your playlists, favourites, history and cached lyrics |
| Clear history | Library → History | Every listening-history row in the local database | Your downloads, playlists, favourites and recent searches |
| Clear recent searches | Search → recent searches | Every saved recent search term | Your history, playlists and favourites |

All three ask for confirmation before they run. If a clear fails, the app says
so and keeps the confirmation open so you can retry — it does not claim success
after a failure. **[source]**

**Controls that do not exist:** there is no way to clear the streamed-audio cache
from the UI (only to cap its size), and the lyrics cache has a clear method in
code with no screen wired to it. Neither is offered here. **[source]**

## Security problems

Report security problems privately. See **Security Reporting** for the interim
procedure while no private channel is enabled. **[source]**

## Contact

A direct contact address is **not yet published.** **[maintainer]**
