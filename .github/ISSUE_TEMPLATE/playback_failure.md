---
name: Playback failure
about: A track that will not play, plays the wrong thing, or reports an error the app did not explain
title: "[PLAYBACK] "
labels: playback, bug
---

<!--
  Read this first. DHUN's web app resolves streams from YouTube's own endpoints
  in your browser. That request can be refused by YouTube's origin, and when it
  is, the app shows a refusal instead of pretending to play. That refusal is not
  a bug — but "it said nothing and nothing happened" always is.

  What makes a playback report actionable: the exact URL, what the app said on
  screen, and what the browser console said. If you have no console output, say
  so; "no console output" is itself useful evidence.
-->

## What I tried to play

- **What I typed or clicked:**
- **Where the row came from** (the app shows the source above the list — one of
  "YouTube Music", "Your endpoint", or "Demo data"):
- **Was it the demo catalogue?** The demo rows are fictional and have no audio
  by design; the app labels them. If the source badge said "Demo data", say so.

## What happened

<!-- Did audio play at all? Did the progress bar move while silent? Was there an
     error strip, and what did it say? Copy the words on screen. -->

## What I expected

<!-- e.g. "I expected audible audio, or an error saying why not." -->

## Environment

- **Browser and version:**
- **Device and OS:**
- **URL** (the full address, including the `#/…` part):
- **Network** (home, mobile, VPN, corporate proxy — a proxy explains some refusals):

## Console output

<!--
  Open the browser's developer tools (F12 or Ctrl+Shift+I), choose Console, and
  reproduce. Paste the errors. The most useful lines mention `googlevideo`,
  `CORS`, `blocked`, or a status code.

  If the console is empty, write "nothing in the console".
-->

```text
paste console output here, or write "nothing in the console"
```

## Screenshots

<!-- A screenshot of the player state, the error strip, or the Network tab helps.
     Please do not paste personal information (your real name, email, exact
     location) into a public issue. -->
