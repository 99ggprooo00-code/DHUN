# Provider integration — DHUN Web (`/app/`)

How the browser build reaches music metadata and audio, which sources it uses,
how it fails over, and which sources are planned but not built. The native
multi-source architecture is decided in
[ADR-010](../decisions/ADR-010-multi-source-provider-direction.md); this file is
the browser's own description of what is in `app-web/src/js/api.js`.

## 1. The one catalogue

The browser build reads a single catalogue family: **JioSaavn**, through public
community-run wrappers. It is the only source that answers a browser request
without a key, a sign-in, or a server operated by DHUN.

| Order | Source id | Origin | Label |
|---|---|---|---|
| 1 (preferred) | `saavn` | `https://saavn.dev` | saavn.dev |
| 2 | `sumit` | `https://saavn.sumit.co` | saavn.sumit.co |
| 3 | `saavnx` | `https://saavnx.vercel.app` | saavnx.vercel.app |

These three origins are also the only `connect-src` entries of the page's CSP,
along with `https://*.saavncdn.com` for audio and artwork. The list is
`API_SOURCES` in `api.js`.

**These are not contracted services.** Each is run by a third party, can change
or stop, and its terms are not reviewed by the project (baseline finding F5 in
[`docs/research/02-baseline-audit-2026-10-20.md`](../research/02-baseline-audit-2026-10-20.md)).

## 2. Endpoints

Paths are built in `ENDPOINTS`, relative to the active origin's `/api`.

| Purpose | Path |
|---|---|
| Search songs | `/search/songs?query=…&page=…&limit=…` |
| Search albums | `/search/albums?query=…&page=…&limit=…` |
| Search artists | `/search/artists?query=…&limit=…` |
| Search playlists | `/search/playlists?query=…&page=…&limit=…` |
| One song | `/songs/{id}` |
| Several songs | `/songs?ids=…` |
| Song suggestions (radio-like) | `/songs/{id}/suggestions` |
| Album | `/albums?id={id}` |
| Artist | `/artists/{id}` |
| Artist songs (paged) | `/artists/{id}/songs?page=…` |
| Artist albums | `/artists/{id}/albums` |
| Playlist | `/playlists?id={id}&limit=…` |

Home shelves are defined by `HOME_RAILS`: trending, Hindi, chill, Punjabi, new
albums, artists and playlists. Each shelf is a search with a fixed query.

A stream URL is read from the song's download list and chosen by
[PLAYBACK.md](PLAYBACK.md) §3.

## 3. Requests

| Setting | Value |
|---|---|
| Method | `GET` |
| Credentials | `omit` — no cookies are sent |
| Request timeout | 15 s (`REQUEST_TIMEOUT_MS`) |
| Probe timeout (failover) | 8 s (`PROBE_TIMEOUT_MS`) |
| Page size | 20 (`PAGE_SIZE`) |

## 4. Failure kinds

Every failed request is classified as one of five kinds. The kind is what the
interface branches on.

| Kind | Meaning |
|---|---|
| `offline` | The browser reports no connection. |
| `timeout` | The request passed its time limit or was aborted. |
| `http` | The server answered with a non-success status. The status is kept. |
| `shape` | The answer was not the JSON the contract promises (not JSON, no data field, no results array). It is **not** treated as an empty catalogue. |
| `network` | Any other network-level failure. |

## 5. Failover

1. A request goes to the active origin. The active origin starts as the one saved
   in `settings.sourceId` (the last origin that answered, see
   [DATA-SCHEMA.md](DATA-SCHEMA.md) §2), or as the first in the list if none is
   saved. The visitor does not choose the origin; Settings → Catalogue shows the
   order and the current endpoint.
2. If it fails, the other origins are probed **at the same time**, each with the
   8-second probe timeout. The first success becomes the active origin, and the
   interface is told.
3. While a switch is running, other requests wait for it. Home asks for seven
   shelves at once, and the design avoids starting seven races against services
   that are already struggling.
4. If every origin fails, the error reported is the first failure's kind, with
   each attempt listed under "Technical detail".

Failover changes which community server answers. It does not change the source
of the music, which is the same catalogue on all three.

## 6. Sample catalogue

When every origin fails, Home can show a **sample catalogue** built from fixed,
invented rows (`demo.js`). These rows:

- are labelled as samples where they are drawn (`sourceBadge`);
- carry **no audio URL**, and playing one says so.

They exist so a visitor sees what the screens look like while the catalogue is
down. They are not a catalogue.

## 7. Sources not in the browser build

| Source | Status in the browser | Where it is decided |
|---|---|---|
| YouTube / YouTube Music | **Not used.** The web build does not embed the YouTube IFrame player and does not call YouTube's APIs. | Owner decision D2, [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md). The native YouTube path has its own open policy questions in [`docs/research/03-youtube-youtube-music.md`](../research/03-youtube-youtube-music.md). |
| Spotify | Not used by the browser build, and planned only in the native design. | Owner decision D3: deferred, not removed. The developer account needs a Premium owner, and policy §III.5 is open. Not used here, see `docs/research/04-spotify.md`. |

No second catalogue is added to the browser build without a research entry, an
ADR, and a decision on how it will be tested. Until then, the browser build reads
JioSaavn only.

## 8. Not verified

- Whether the three origins keep answering with the shapes the code expects.
  The contract is checked when a response arrives, not in advance.
- Whether the community wrappers' terms allow this use. Not reviewed.
- Stream URL lifetime. Whether a URL returned earlier is still valid later has
  not been measured.
