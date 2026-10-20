# Product requirements — DHUN Web (`/app/`)

This is the web counterpart of [`docs/PRD.md`](../PRD.md). It states what the
browser build is for, who it is for, and what it must and must not do. Where the
native PRD and this one disagree, this one governs the browser build; the native
PRD governs Android and Windows. The architecture that realises these
requirements is in [ARCHITECTURE.md](ARCHITECTURE.md); the limits a visitor can
meet are in [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md).

## 1. Purpose

DHUN Web lets a person search a public music catalogue, play a track in the
browser, and keep a small personal library (likes, playlists, history, settings)
on that device. It is a free, open-source, static web application published with
the project's marketing site at `/app/`.

The owner's statement on the web build (2026-10-20, recorded verbatim) is: "It was
primarily meant to be yt music client so do accordingly." The layout and flow
follow that intent: a home of shelves, a search with typed results, album, artist
and playlist pages, a persistent player bar, and a library. The catalogue behind
it is JioSaavn, not YouTube. How the owner's statement and the recorded decision
D2 fit together is open; see §8 and
[ADR-010](../decisions/ADR-010-multi-source-provider-direction.md).

## 2. Users

| Who | What they need from the browser build |
|---|---|
| A visitor with no account | Open a link, search, press play, without installing anything. |
| The project owner (a student, no budget) | A build that costs nothing to host, has no server to run, and can be audited from the repository. |
| A visitor on a phone | Touch controls on the same shell, and installable to the home screen where the browser offers it. |
| A future contributor | Documents that describe the build that exists and fail when they stop being true. |

## 3. Goals (in priority order)

1. **Honesty.** Every control either does what it says or says plainly why it
   cannot. No simulated results, no invented progress, no silent failure.
2. **A real player.** Audio comes from a URL the catalogue returned and plays in
   an `<audio>` element. Progress is that element's own clock.
3. **No running cost and no DHUN server.** Static hosting only. The request goes
   from the visitor's tab to the catalogue; nothing the project operates sits in
   between.
4. **The DHUN interface.** Dark first, frosted glass, the app's own tokens
   ([UI-UX-DESIGN.md](UI-UX-DESIGN.md)).
5. **Phone and desktop are both first-class.** Same shell, same controls.

## 4. Functional requirements

| ID | Requirement | Status in the build |
|---|---|---|
| W-1 | Home shows shelves of songs, albums, artists and playlists, loaded from the catalogue. | Built. Seven shelves, defined in `app-web/src/js/api.js` (`HOME_RAILS`). |
| W-2 | Search returns songs, albums, artists and playlists, with paging for songs, albums and playlists. | Built. |
| W-3 | Album, artist and playlist pages open from any result that names them. | Built (`album`, `artist`, `playlist` routes). |
| W-4 | A track plays from its catalogue stream; the player shows status, position and duration. | Built. Not verified by listening on the deployed origin (see IMPLEMENTATION-STATUS.md). |
| W-5 | Queue with next, previous, shuffle and repeat (off, all, one). | Built. Rules in [PLAYBACK.md](PLAYBACK.md). |
| W-6 | Likes, playlists (up to 200), history (up to 200 entries) and recent searches persist on the device. | Built. Schema in [DATA-SCHEMA.md](DATA-SCHEMA.md). |
| W-7 | The library can be exported to a JSON file and imported back from one this app made. | Built. |
| W-8 | Settings: appearance (theme dark, light or system; accent colour), playback (default volume, repeat), catalogue source, this browser (export and import of the library), and about. | Built. |
| W-9 | Keyboard shortcuts, with a sheet listing them. | Built. |
| W-10 | The shell opens without a network connection; music does not. | Built (shell cache). |
| W-11 | Installable as a standalone app where the browser offers it. | Manifest present; install prompt wired. Not verified on every browser. |
| W-12 | Media keys and the operating system's media panel show the current track where the browser supports the Media Session API. | Built, with feature detection. |
| W-13 | Audio download, offline audio, equaliser, lyrics and cross-device sync | Not offered, because a browser cannot do these honestly with this design. |
| W-14 | Sample rows, used only when the catalogue cannot be reached, are labelled as samples and carry no audio. | Built. |

## 5. Non-goals

- No YouTube or YouTube Music playback in the browser. The web build does not
  embed the YouTube IFrame player and does not call YouTube's APIs. This follows
  the owner's decision D2 in [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md).
  The native YouTube path is not part of this build.
- No Spotify in the browser, and Spotify remains a planned source in the native
  design, deferred and not removed (owner decision D3, ADR-010).
- No accounts, sign-in, cloud sync, or server-side storage.
- No analytics, telemetry, advertising or third-party script.
- No promise that the catalogue will remain available. It is a community-run
  service with no support contract.

## 6. Constraints

- **Cost:** the owner cannot pay for services or subscriptions. Hosting is the
  project's GitHub Pages site. No paid API key is required.
- **Legal review:** none has been obtained and none is planned at present (owner
  decision D1). The policy questions are recorded as open risks, not as settled
  compliance. See [SECURITY-PRIVACY.md](SECURITY-PRIVACY.md) and the research in
  [RESEARCH-REGISTER.md](RESEARCH-REGISTER.md).
- **Runtime:** no framework, no runtime dependency, no build step that needs an
  install. The browser build is copied from `app-web/src/` to `dist/`.

## 7. Success measures

These are the measures the repository can check. Anything not listed here is not
a success measure for this build.

- The JavaScript suite passes (`npm test` in `app-web/`), and the count of its
  tests matches the count the documents state (enforced by the docs gate).
- The build completes and the service worker's shell list names only files that
  exist.
- The browser checks in `website/tests/browser.mjs` pass on the served `/app/`
  route, including the offline check.
- The documents in this directory pass `scripts/web_player_docs.py`.

## 8. Open questions

- Whether a catalogue with a support contract can replace the community wrapper
  (F5 in the baseline audit; [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md)).
- Whether an audible check on the deployed origin will be recorded (see
  [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md)).
- Whether any second catalogue source is added to the browser build. Any such
  source needs its own research entry and an ADR before code.
- **The owner's D2 wording.** The recorded decision is no YouTube IFrame player on
  the web. The owner's words ("primarily meant to be yt music client so do
  accordingly") can be read as asking for the opposite. The owner should confirm
  which reading is intended. The recorded reading is in
  [DECISIONS-AND-CHANGES.md](DECISIONS-AND-CHANGES.md) §1.
