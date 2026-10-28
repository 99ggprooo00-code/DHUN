/**
 * Browser-specific legal corpus, last reconciled 2026-10-28.
 * Native drafts live in legal/*.md; data/legal-content.js is a generated native
 * parity fixture excluded by tools/build.mjs, not this browser's policies.
 * Evidence: api.js/API_SOURCES + index.html/CSP; library.js + transfer.js;
 * sw.js shell caching; player-ui.js; version.js render-time tokens.
 * Provider permissions and published policies are not inferred from code.
 */

export const WEB_LEGAL_DOCUMENTS = Object.freeze([
  {
    id: "about",
    title: "About this build",
    markdown: `## What this is

DHUN (धुन) is a free, open-source, cross-platform, **multi-source music client**.
YouTube / YouTube Music is the first implementation priority. Spotify is a
planned additional source, not implemented; its work is deferred pending
technical, account/Premium, policy, legal, playback, privacy and cost gates.
Other sources may be evaluated using the same evidence-based process.
Capabilities can differ by platform. **[source]**

**This is the separate browser client, currently using JioSaavn**, not the native
YouTube Music app compiled for browsers. It is a static site with no DHUN account
and no DHUN server: no DHUN-operated server or API/backend, no native installer and no paid DHUN tier. Where a
browser offers installation it can install the PWA shell, not native features.
The interface is served from GitHub Pages; the saved library stays in this
browser, while searches and media requests go to third parties. **[source]**

## This build

| | |
| --- | --- |
| Version | {{appVersion}} |
| Release channel | {{releaseChannel}} |
| Platform | {{platform}} |

These values come from this build's \`version.js\`, not a hand-typed release
number. **[source]**

**Experimental.** Live catalogue access and audible playback from the deployed
origin remain unverified. Unit tests and silent sample rows are not playback
proof; there is no availability or support guarantee. **[source]**

## Where the music comes from

Search, albums, artists, playlists and stream resolution use **unofficial
community JioSaavn API wrappers** at \`saavn.dev\`, \`saavn.sumit.co\` and
\`saavnx.vercel.app\`, with the successful instance preferred next time. These
are three instances for one catalogue family, not three music providers.
Failover may send the same query/IDs to several operators. Audio and artwork
come from \`*.saavncdn.com\`. DHUN operates none of these services. **[source]**

Failures show the hosts and their responses and offer a clearly labelled
sample catalogue with no audio. No YouTube API/IFrame, Spotify adapter, OAuth or
Spotify playback is implemented here. **[source]**

## Independence and rights

DHUN is **not affiliated with, endorsed by, authorised by, or connected to
JioSaavn, YouTube, Google or Spotify.** Names and trademarks belong to their
owners. Song metadata and artwork are fetched at runtime, not bundled music.
**[source]**

DHUN is GNU General Public License v3.0 software; the repository licence and existing notices govern.
Software licences do not grant rights to music, artwork, lyrics, streams, APIs
or trademarks. Provider and wrapper permissions remain unresolved; a successful
request or a disclaimer is not authorisation. See the Terms draft. **[legal]**

## What this build does not have

No audio downloads, offline audio, equaliser or lyric provider. The service
worker caches the app's own interface files only. The lyrics tab's unavailable
state does not mean this build sent a lyric request. Native Android/desktop
features are not promises for this browser client. **[source]**
`,
  },
  {
    id: "privacy",
    title: "Privacy",
    markdown: `> **DRAFT — not yet in force.** Last updated 2026-10-28. Not reviewed by a
> lawyer; no data controller or privacy contact is published. This is a
> source-based technical description, not legal approval or a runtime packet audit.

Evidence labels: **[source]** = repository code; **[runtime]** = specifically
recorded execution; **[third-party]** = linked provider documentation;
**[maintainer]** = decision still needed; **[legal]** = legal review needed.

## 1. Summary

The saved library is local, not all activity is private to the device. This
build has no DHUN account/backend, analytics, advertising, crash reporter or
telemetry endpoint. It requests catalogue data from unofficial JioSaavn wrappers
and media/artwork from JioSaavn's CDN. Those operators receive request/network
data. No account does not mean anonymous or unlinkable requests. **[source]**

The project prioritises YouTube Music, but the browser has no YouTube integration.
Spotify is planned and deferred, not implemented: no Spotify OAuth, tokens, SDK
or requests exist here. Native data flows are described in the native app's own
drafts, not imported as browser policy. **[source]**

## 2. Who operates DHUN

No legal entity or privacy contact is published. The project must resolve those
before adopting a final policy; this draft invents neither. **[maintainer]**

## 3. Local browser data

The library is not uploaded/synced to DHUN. Individual saved queries, IDs and
artwork URLs may be reused in third-party requests; “local” is not a promise
that the same values never leave the device. **[source]**

| Data | Where it lives |
| --- | --- |
| Likes, playlists and playlist entries | \`localStorage\`, key \`dhun.web.v3\` |
| Listening history and recent searches | \`localStorage\`, key \`dhun.web.v3\` |
| Referenced track-metadata snapshots: titles, artists, albums, duration, IDs and artwork URLs | \`localStorage\`, key \`dhun.web.v3\`; persisted stream URLs are blanked, unreferenced snapshots pruned |
| Settings: theme, accent, volume, repeat, shuffle | \`localStorage\`, key \`dhun.web.v3\` |
| Queue/current stream | In memory; no persisted offline audio |
| Interface files for offline opening | Cache Storage / service worker, same-origin shell only |
| Media/artwork | No DHUN audio-download store; normal browser caching may occur |

Export/import use a user-chosen JSON file; the app never uploads it. Storage
refusal/quota failure leaves a memory-only session and a visible warning.
**[source]**

## 4. Requests to third parties

| Recipient | What is sent / purpose |
| --- | --- |
| \`saavn.dev\`, \`saavn.sumit.co\`, \`saavnx.vercel.app\` | Search terms and album/artist/playlist/song IDs for catalogue reads and stream resolution; failover may contact multiple wrapper operators |
| \`*.saavncdn.com\` | Artwork/audio URLs for images and playback; network/request data |
| GitHub Pages | Requests for the hosted interface, scripts, styles, manifest and updates |
| GitHub issues, only if you choose to report | Text and files you submit publicly; no automatic diagnostic uploader |

API fetch uses \`credentials: "omit"\` and \`cache: "no-store"\`. Destinations
can see the network address and browser request headers; browser-managed media,
images and referrer/cache behaviour are not fully packet-audited here. CSP's
three wrapper origins and CDN are network restrictions, not proof of permission
or third-party retention limits. The service worker does not cache cross-origin
API responses, artwork or audio. **[source]**

DHUN cannot inspect or delete provider/wrapper/hosting logs. JioSaavn's
[Privacy Policy](https://www.jiosaavn.com/corporate/privacy/) describes its own
platform, not a verified retention policy for each independent wrapper.
Wrapper retention/deletion schedules remain unestablished. **[third-party] [legal]**

No lyric request, YouTube request or Spotify request is implemented. **[source]**

## 5. Tracking and cookies

DHUN adds no tracking/device identifier, reads or sets no cookies, and has no
analytics/crash-reporting integration. This is not a guarantee about third-party
server logs or every browser/provider cookie, cache or transport header.
**[source]**

## 6. Export, import and deletion

- **Export:** Settings → This browser → Export library writes \`dhun-library.json\` locally.
- **Import:** Settings → This browser → Import library validates a chosen file.
- **Erase library:** after confirmation, clears likes, playlists, history,
  recent searches and snapshots but **keeps settings** and the shell cache.
- **Remove all local site data:** use the browser's controls for this origin to
  remove settings, storage, caches and service-worker registration too.

None of these deletes third-party logs or data you voluntarily post on GitHub.
No DHUN account exists to disconnect; no remote library copy exists for a DHUN
maintainer to delete. **[source]**

## 7. Hosting and remaining decisions

The \`/DHUN/\` product site is static with no shipped client-side JavaScript;
\`/app/\` is a separate JavaScript client. GitHub hosting still processes requests
under its own policies. Controller/contact, applicable-law review, wrapper
permissions/retention and browser/hardware verification remain open.
**[source] [maintainer] [legal]**
`,
  },
  {
    id: "terms",
    title: "Terms of use",
    markdown: `> **DRAFT — not yet in force.** Last updated 2026-10-28. Not lawyer-reviewed,
> adopted, provider-approved or a negotiated content licence.

## 1. Software and scope

DHUN is free software under the GNU General Public License v3.0. The repository licence and existing
per-file/package notices govern; this page does not change that grant.
[Read the source and licence](https://github.com/99ggprooo00-code/DHUN).
**[source]**

Product direction is multi-source: YouTube / YouTube Music first; Spotify
planned and deferred pending technical, account/Premium, policy, legal,
playback, privacy and cost gates. This browser build currently uses JioSaavn
through unofficial community wrappers only. There is no YouTube/Spotify
playback or sign-in. Native features are not browser promises. **[source]**

## 2. No warranty

Experimental pre-release, **as is**, without warranty or service-level promise.
Catalogue access and audible playback from the deployed origin remain
unverified. Provider/browser restrictions and third-party outages can prevent
playback. A unit test does not establish live music playback. **[source]**

## 3. Not provided

No audio downloads, offline audio, equaliser, lyric provider, DHUN account,
cloud library, sync, support commitment or response time. Offline shell opening
is not offline music. Failures and silent sample rows are labelled. **[source]**

## 4. Content and provider restrictions

Music, artwork, lyrics, streams, APIs and trademarks have their own owners;
**the software GPL grants no rights to them**. DHUN does not host or mirror the
music. Referenced metadata snapshots and artwork URLs persist locally; normal
browser caching can occur. **[source] [legal]**

JioSaavn's [Terms of Use](https://www.jiosaavn.com/corporate/terms/) (last updated
2026-10-07; accessed 2026-10-28) restrict accessing its services through unofficial
or automated interfaces unless separately allowed. Application to the wrappers,
content and this client, and any needed permissions, remain unresolved. Wrapper
software licences and successful HTTP responses are not provider permission.
Independent deployment terms/retention are not established here.
**[third-party] [legal]**

Use must respect applicable provider, account, territorial, content and legal
restrictions. Do not circumvent access controls, rip or redistribute content.
A disclaimer, private use or owner acceptance of risk does not make an
integration compliant. Planned providers require their own feasibility and
permission review before any implementation. **[legal]**

## 5. Independence

DHUN is **not affiliated with, endorsed by, authorised by, or connected to
JioSaavn, YouTube, Google or Spotify**. Names/marks belong to their owners;
mentioning a planned source is not a partnership. **[source]**

## 6. Changes

Drafts are versioned with the build and must change when behaviour changes.
No legal adoption is implied by an update date. **[source] [maintainer]**
`,
  },
  {
    id: "licences",
    title: "Licences",
    markdown: `## DHUN software

GNU General Public License v3.0. [LICENSE](https://github.com/99ggprooo00-code/DHUN/blob/main/LICENSE)
and existing per-file/package notices govern; this page makes no new grant.
Bundled licence texts live under \`LICENSES/\` in the repository. The GPL covers
software, not music, artwork, lyrics, streams, APIs or trademarks. **[source] [legal]**

## Services, not bundled libraries

| Service | Use | Limits |
| --- | --- | --- |
| Unofficial community wrappers: \`saavn.dev\`, \`saavn.sumit.co\`, \`saavnx.vercel.app\` | One JioSaavn catalogue family: search, albums, artists, playlists, stream URLs | Separate operators; deployment terms/retention unestablished |
| \`*.saavncdn.com\` | Audio and artwork | Provider/content rights and terms apply |
| GitHub Pages | Static hosting | GitHub's hosting/privacy policies apply |

JioSaavn restricts unofficial access. The wrapper project's open-source code
licence does not establish permission for the content, API use or this product.
No provider agreement, endorsement or completed legal review is asserted.
**[third-party] [legal]**

## Bundled code and assets

No third-party runtime framework, CDN script, analytics package or webfont is
shipped. The browser uses its system font stack; icons use Material Design icon
path data, Apache-2.0. Native libraries have separate notices in the native app
and \`THIRD_PARTY.md\`. Distribution-notice completeness needs release review.
**[source] [legal]**

Node's built-in runner/bundler, Python gates, Playwright and axe-core are
build/test tooling, not client runtime dependencies. **[source]**
`,
  },
  {
    id: "support",
    title: "Help & feedback",
    markdown: `> **DRAFT.** Experimental pre-release; no support commitment or response promise.

## Report a problem

- [Report a bug](https://github.com/99ggprooo00-code/DHUN/issues/new).
- [Browse issues](https://github.com/99ggprooo00-code/DHUN/issues).
- [Read source](https://github.com/99ggprooo00-code/DHUN).

Include build version from Settings/About, provider/source badge, browser/OS,
steps and exact visible error. Wrapper access failures, missing audio and
browser media refusal are different outcomes. Sample rows are fictional and
silent. No hidden fallback pretends to play. **[source]**

Public issues are not confidential. **Never post** signed stream URLs, cookies,
tokens, library exports, private paths or personal information. Inspect and
redact console/diagnostic text first; nothing uploads automatically.
**[source]**

## Local data and privacy requests

Settings → This browser offers Export library, Import library and Erase library. **Erase
keeps settings and the shell cache**; browser site-data controls remove those
too. DHUN has no account or remote library to delete. Provider/wrapper/CDN and
GitHub hosting/issue requests belong with their respective operators; local
erase cannot delete their logs. **[source]**

No private support, privacy or security contact is published. Request a private
channel in a public issue without sensitive information; do not mistake it for
a confidential inbox. **[maintainer]**
`,
  },
]);

export function webLegalById(id) {
  return WEB_LEGAL_DOCUMENTS.find((document) => document.id === id) ?? null;
}
