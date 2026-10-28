# Using DHUN in a browser

DHUN's [multi-source direction](../PRD.md#1-product-definition-and-source-of-truth)
prioritises YouTube Music, but this separate experimental browser build currently
uses unofficial JioSaavn wrappers/CDN. Spotify is planned/deferred, not available.
Live catalogue and audible deployed-origin playback are unverified; provider
permission is unresolved. Terms/Privacy in About are drafts, not legal approval.

The web counterpart of [`docs/INSTALL.md`](../INSTALL.md). The browser build needs
no install and no account. It is at:

**https://99ggprooo00-code.github.io/DHUN/app/**

## Start

1. Open the address above.
2. Search (the search field at the top, or the Search tab on a phone).
3. Press play on a song.

Music is streamed from the catalogue each time it plays. The app needs an internet
connection to play music. It does not need one to open.

## Your library

Likes, playlists and history are kept **in this browser, on this device**. Nothing
is uploaded as a library to DHUN; searches/IDs/media requests still go to third parties. You can:

- **Like** a song, or **add** it to a playlist, from the song's menu.
- See everything in **Library** (Playlists, Liked songs, Recently played).
- **Export** the library to a file (Settings → This browser → Export library). Keep
  the file somewhere safe.
- **Import** a file you exported earlier (Settings → This browser → Import
  library). This replaces the library in this browser.
- **Erase** the library (Settings → This browser → Erase library). This removes likes, playlists and history, and keeps your settings.
  Settings and shell cache remain; clear this origin's site data to remove those too.
  It cannot be undone. Export first if you may want it back.

Each browser keeps its own library. A library exported on one device can be
imported on another.

## Install it

Where the browser offers it, the app shows an install button. Once installed it
opens in its own window. If there is no button, your browser may not support
installing web apps; the app still works in a tab.

## Keyboard

| Key | Action |
|---|---|
| Space | Play or pause |
| N / P | Next / previous track |
| → / ← | Seek forward / back 5 seconds |
| ↑ / ↓ | Volume up / down |
| M | Mute or unmute |
| S | Shuffle |
| R | Repeat (off, all, one) |
| / | Focus search |
| ? | Show the shortcut list |
| Esc | Close a sheet or the player |

Keys are not taken while you are typing in a text box.

## When something does not work

- **"The browser blocked playback."** Press play again. Browsers need one press
  before they allow sound.
- **"The catalogue could not be reached."** The music service is a third-party
  service and can be down. Wait and try again. The app keeps your library.
- **Showing sample songs.** The catalogue could not be reached, so the app shows
  example rows. They have no audio.
- **"This browser cannot play that file."** Your browser does not support the
  file's format. Try another browser.
- **An old version.** Close every DHUN tab and open the address again.

## What the web version does not do

- It does not download music for offline listening.
- It does not have an equaliser or synced lyrics. The Lyrics tab says so.
- It does not play YouTube or Spotify music. The Android and Windows versions are
  documented in the main [`docs/`](../) folder.
- It has no accounts and does not sync between devices.

## Privacy, in short

No account, no analytics, no advertising. Your library is stored only in your
browser. Searches and playback requests go from your browser to the catalogue
service. For detail, see the About screen (Privacy and Terms) and
[SECURITY-PRIVACY.md](SECURITY-PRIVACY.md).
