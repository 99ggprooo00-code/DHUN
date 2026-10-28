---
name: Playback failure
about: A track that will not play, plays the wrong thing, or reports an error the app did not explain
title: "[PLAYBACK] "
labels: playback, bug
---

<!--
  DHUN is multi-source by direction, not feature-identical across builds.
  Native Android/desktop currently use YouTube Music. The separate browser
  client uses unofficial JioSaavn wrappers; failures may be service/CORS/media
  refusals, not a diagnosis of a native extractor bug. Spotify is planned only.
  Report the exact visible state and build/provider, not an assumed cause.
  Never post signed stream URLs, tokens, cookies, library exports or private paths.
-->

## What I tried to play

- **What I typed or clicked:**
- **Client and source/provider** (Android/desktop YouTube Music, or browser
  JioSaavn; for browser include the source/host badge and exact error):
- **Was it the demo catalogue?** Browser sample rows are fictional and silent
  by design; include the label you saw.

## What happened

<!-- Did audio play at all? Did the progress bar move while silent? Was there an
     error strip, and what did it say? Copy the words on screen. -->

## What I expected

<!-- e.g. "I expected audible audio, or an error saying why not." -->

## Environment

- **Build version and release channel** (About):
- **Browser and version** (browser reports only):
- **Device and OS:**
- **App page/route** (browser page path/hash only; redact private query values
  and do not paste CDN stream URLs):
- **Network** (home, mobile, VPN, corporate proxy — a proxy explains some refusals):

## Console output

<!--
  Open the browser's developer tools (F12 or Ctrl+Shift+I), choose Console, and
  reproduce. Inspect/redact errors first. Browser reports may mention the
  wrapper host, `saavncdn`, `CORS`, `blocked` or a status; native reports may
  mention YouTube/resolver details. Do not post full media URLs or secrets.

  If the console is empty, write "nothing in the console".
-->

```text
paste console output here, or write "nothing in the console"
```

## Screenshots

<!-- A screenshot of the player state, the error strip, or the Network tab helps.
     Please do not paste personal information (your real name, email, exact
     location) into a public issue. -->
