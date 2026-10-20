/**
 * The web build's own legal pages.
 *
 * These are **not** the Android app's documents, and deliberately so. The
 * shared corpus in `legal/*.md` describes an installed app that downloads
 * audio, keeps a SQLite database and talks to YouTube; rendering it inside a
 * browser tab would put sentences on screen that this build cannot make true.
 * The user asked for the web-only material to be rewritten, so this file is
 * written for the browser and checked against the browser's behaviour.
 *
 * Every factual sentence below is traceable to code in `src/js`:
 *
 *   localStorage, the storage key, export/import   → library.js, transfer.js
 *   saavn.dev, saavn.sumit.co, saavnx.vercel.app,
 *   *.saavncdn.com, the API paths                  → api.js (API_SOURCES), index.html (CSP)
 *   shell-only service-worker cache                 → sw.js
 *   the absent lyrics                               → player-ui.js
 *   the sample catalogue's own label                → demo.js
 *
 * `legal.js` renders the Markdown; `main.js` hands the chosen document to the
 * About screen. Tokens (`{{appVersion}}` and friends) are filled at render
 * time from `version.js`, so a release cannot ship a page that names the wrong
 * build.
 */

export const WEB_LEGAL_DOCUMENTS = Object.freeze([
  {
    id: "about",
    title: "About this build",
    markdown: `## What this is

DHUN's web build: the same music player as the Android app, running as a static
site in your browser. There is no install, no account and no DHUN server. The
pages, styles and scripts are served from the project's GitHub Pages site and
everything you save stays in this browser. **[source]**

It is free software under the GNU General Public License v3.0. There is no paid
tier. **[source]**

## This build

| | |
| --- | --- |
| Version | {{appVersion}} |
| Release channel | {{releaseChannel}} |
| Platform | {{platform}} |

The version is read from the build's own \`version.js\` at run time, so this page
cannot name a build other than the one you are using. **[source]**

**Pre-release status.** The web build is experimental and unverified against
every browser. There is no service-level promise and no support desk. **[source]**

## Where the music comes from

Search, albums, artists and playlists are answered by the public JioSaavn API,
which this build reads through three community deployments of the same
open-source wrapper — \`saavn.dev\`, \`saavn.sumit.co\` and
\`saavnx.vercel.app\` — tried in that order, with the one that answered
remembered for next time. Audio and artwork are fetched from
\`*.saavncdn.com\`. DHUN operates none of these services and cannot control
their availability, their catalogue or their policies. When none answers, the app
says which host said what and shows a clearly labelled sample catalogue whose
rows have no audio. **[source]**

## Independence

DHUN is **not affiliated with, endorsed by, authorised by, or connected to
JioSaavn, YouTube or Google.** JioSaavn and YouTube Music are trademarks of
their respective owners. Song metadata and artwork belong to their owners and
are fetched at run time; they are never bundled with this build. **[source]**

## What this build does not have

No downloads, no offline audio and no saved copies of tracks: signed stream URLs
expire, and a browser tab cannot honestly promise a file it does not own. The
service worker caches the app's own interface files only. There are no lyrics:
the player's lyrics tab says the API returned no lyric text rather than showing
invented lines. **[source]**
`,
  },
  {
    id: "privacy",
    title: "Privacy",
    markdown: `> **DRAFT — not yet in force.** This page describes what the code in this
> build actually does. It has not been reviewed by a lawyer, no data controller
> is named, and no privacy contact has been published. Read it as an accurate
> technical description, not a legal guarantee.

Every factual sentence carries a status tag:

- **[source]** — read from this build's own code.
- **[runtime]** — observed by running the build.
- **[third-party]** — taken from the third party's published documentation.
- **[maintainer]** — awaiting a decision from the project maintainer.

## 1. Summary

This build has no account system, no DHUN-operated server, no analytics, no
advertising, no crash reporting and no telemetry endpoint. **[source]**

Everything it remembers about you is stored in this browser. When you search,
browse or play, the page talks directly to JioSaavn's public API and CDN from
your browser, and those requests reveal your IP address to those services as any
internet request does. **[source]**

## 2. Who operates DHUN

DHUN is free software under the GNU General Public License v3.0. The repository
contains no DHUN-operated server, account system or telemetry endpoint.
**[source]**

No legal entity and no privacy contact have been published. **[maintainer]** —
until they are, this page states that rather than filling in a placeholder
address.

## 3. Data stored only in your browser

Nothing in this section leaves your browser by the build's own design.
**[source]**

| Data | Where it lives |
| --- | --- |
| Liked songs, playlists and playlist entries | \`localStorage\`, key \`dhun.web.v3\` |
| Listening history and recent searches | \`localStorage\`, key \`dhun.web.v3\` |
| Track metadata you have browsed (cached so a reload does not re-request it) | \`localStorage\`, key \`dhun.web.v3\` |
| Settings: theme, accent, volume, repeat, shuffle | \`localStorage\`, key \`dhun.web.v3\` |
| The app's own files, for offline opening | the browser's Cache Storage, written by the service worker |
| Audio and artwork | not stored by DHUN; the browser may cache media under its own policy |

**Exporting** writes the same JSON to a file you keep. **Importing** reads such a
file only after you choose it. **Erasing the library** deletes the local record
immediately. Clearing this site's data in the browser does the same and also
removes the offline copy of the interface. **[source]**

If this browser refuses storage — private mode, or a full quota — the app says
so and keeps working for the visit instead of pretending the library was saved.
**[source] [runtime]**

## 4. Data sent to third parties

| Recipient | What the page sends | When |
| --- | --- | --- |
| \`saavn.dev\`, \`saavn.sumit.co\`, \`saavnx.vercel.app\` (JioSaavn API deployments) | Search terms; album, artist, playlist and song identifiers — sent only to the deployment being tried | Searching, browsing, resolving a stream |
| \`*.saavncdn.com\` (JioSaavn CDN) | Media and artwork requests, which include your IP address | Showing artwork and playing a track |

Every request also carries the ordinary headers a browser sends, including your
IP address and a user-agent string. **[source] [runtime]**

There is no lyric provider: the build sends no lyric request to anyone.
**[source]**

**Not verified:** what the JioSaavn API and CDN do with those requests after
they arrive. Their operators' own policies apply. **[third-party]**

## 5. Analytics, advertising and crash reporting

A search of the shipped files finds no analytics, advertising, crash-reporting
or error-reporting code, and no request to any origin other than the two named
above. The page's Content-Security-Policy restricts \`connect-src\` and
\`media-src\` to those origins, so a future edit that tried to add a third one
would be refused by the browser. **[source]**

## 6. Cookies

This build sets no cookies and reads none. **[source]**

## 7. Keeping, exporting and deleting your data

- **Export:** Settings → Library data → Export writes \`dhun-library.json\`.
- **Import:** Settings → Library data → Import reads such a file back.
- **Erase:** Settings → Library data → Erase, after confirmation.
- **Browser-wide:** clearing this site's storage removes the library and the
  offline interface files together.

Clearing your local record does not delete anything JioSaavn or its API may hold.
This build cannot ask them to, and does not claim to. **[source] [third-party]**

## 8. The project site

The site at \`/DHUN/\` is static and ships no client-side JavaScript. GitHub, as
the host, processes requests under its own policies. **[source]**
`,
  },
  {
    id: "terms",
    title: "Terms of use",
    markdown: `> **DRAFT — not yet in force.** No lawyer has reviewed this page. It describes
> the terms the code actually implements, not a negotiated agreement.

## 1. Licence

DHUN is free software under the **GNU General Public License, version 3.0 or
later**. You may run it, study it, change it and share it under that licence.
The source is in the project's public repository. **[source]**

## 2. No warranty

This is an experimental pre-release provided **as is**, without warranty of any
kind, express or implied, including fitness for a particular purpose. Nothing
here is a promise that the service will be available, that a track will play, or
that the catalogue is complete. **[source]**

## 3. What the build does not offer

- No downloads and no offline listening: audio is streamed from signed URLs
  that expire, and the app never presents a copy you do not have.
- No lyrics: when the API returns none, the app says so.
- No account, no sync, no cloud library: the library is this browser's.
- No support commitment and no response time. See **Help & feedback**. **[source]**

## 4. The catalogue

Metadata, artwork and audio belong to their owners and are supplied by
JioSaavn's public API and CDN. This build does not host, mirror, redistribute or
store any of it. Your use of that catalogue is between you and its rights
holders. **[source] [third-party]**

## 5. Independence

DHUN is not affiliated with, endorsed by, authorised by, or connected to
JioSaavn, YouTube or Google. Names and marks belong to their owners and are used
only to say where the data comes from. **[source]**

## 6. Acceptable use

Do not use this build to redistribute audio, to circumvent a service's access
controls, or in any way that breaks the law where you are. The project cannot
grant rights to content it does not own. **[source]**

## 7. Changes

These pages are versioned with the build. If the build's behaviour changes, the
description on these pages has to change with it, and the repository's history
shows both. **[source]**
`,
  },
  {
    id: "licences",
    title: "Licences",
    markdown: `## DHUN

**GNU General Public License v3.0 or later.** The full text ships with the
project repository in \`LICENSES/\`; the Android and desktop builds distribute
the same terms. **[source]**

## Third-party services

| Service | What it is used for | Terms |
| --- | --- | --- |
| JioSaavn public API deployments (\`saavn.dev\`, \`saavn.sumit.co\`, \`saavnx.vercel.app\`) | search, albums, artists, playlists, stream URLs | each deployment's operator's own terms apply; DHUN controls none of them |
| JioSaavn CDN (\`*.saavncdn.com\`) | audio and artwork delivery | the CDN operator's own terms apply |

This build makes no claim that either service permits every use; it asks them
directly, from your browser, and shows their answers. **[third-party]**

## Bundled code

This build has **no runtime dependency**: no framework, no CDN, no analytics
package and no webfont file. Every module in \`src/js\` is DHUN's own code.
**[source]**

| Asset | Origin | Licence |
| --- | --- | --- |
| Interface icons | Material Design Icons, path data copied into \`DhunIcons.kt\` | Apache-2.0 |
| Typeface | the operating system's own sans-serif stack | none bundled |

## Development tools

The build and test tools are development-only and never reach a browser: Node's
built-in test runner, a small bundler script, and the repository's Python
gates. The site's browser checks use Playwright and axe-core in CI only.
**[source]**
`,
  },
  {
    id: "support",
    title: "Help & feedback",
    markdown: `> **DRAFT.** There is no support commitment. This is an experimental
> pre-release and nothing here promises a reply.

## Report a problem

Bugs and feature requests go to the project's public issue tracker. **[source]**

- [Report a bug](https://github.com/99ggprooo00-code/DHUN/issues/new) — say what
  you did, what happened, and what you expected.
- [Browse open issues](https://github.com/99ggprooo00-code/DHUN/issues).
- [Read the source](https://github.com/99ggprooo00-code/DHUN) — every claim on
  these pages can be checked against it.

When reporting a playback problem, include the build version from **Settings**
and the exact message the app showed. **Never include** signed stream URLs,
cookies or tokens in an issue. **[source]**

## Why a track will not play

The three usual causes, in the app's own words:

- the API could not be reached — the app offers **Retry**;
- the API answered but has no audio for that song — the row says so;
- the browser refused the media request — the player says so instead of showing
  a progress bar that never moves.

There is no fourth, hidden mode where the app pretends to play. **[source]**

## Privacy requests

There is no DHUN account, so there is no account data to request, correct or
delete. The library is in your browser: **Settings → Library data** exports or
erases it. Requests about data held by JioSaavn must go to its operator.
**[source]**

## Contact

A direct contact address is **not yet published.** **[maintainer]** Until it is,
use the public issue tracker and keep technical detail out of it.
`,
  },
]);

export function webLegalById(id) {
  return WEB_LEGAL_DOCUMENTS.find((document) => document.id === id) ?? null;
}
