# Playback — DHUN Web (`/app/`)

The browser's player: what plays, how the queue moves, what each state means, and
how a failure is reported. The code is in `app-web/src/js/player.js` (rules and
transport, no DOM), `app-web/src/js/player-ui.js` (drawing), and
`app-web/src/js/api.js` (the stream URL). The tests are in
`app-web/tests/player.test.mjs`.

## 1. The rule this document enforces

**A "playing" state is only shown when an `<audio>` element is playing.** The
progress bar is drawn from that element's own clock. The build never animates a
bar or sets a status to playing on its own. This is the main honesty requirement
for the player; the test file checks the state transitions.

## 2. Statuses

| Status | Meaning | What the visitor sees |
|---|---|---|
| `idle` | Nothing loaded. | The player bar in its empty form. |
| `loading` | A stream URL is being requested or the element is buffering. | A loading state on the track. |
| `playing` | The element is playing. | Clock moving, pause control shown. |
| `paused` | The element is paused. | Play control shown. |
| `ended` | The queue finished, with repeat off. | Stopped at the end. |
| `error` | Playback failed. A title and a sentence say why. | The error, with the browser's text under "Technical detail". |
| `blocked` | The browser refused to start audio without a gesture. | "The browser blocked playback" and a request to press play again. |

## 3. Choosing a stream

`pickStream` reads the catalogue's list of audio files for a track and picks the
best one in this order:

`320kbps` → `160kbps` → `96kbps` → `48kbps` → `12kbps`

If none of those labels is present, the first usable entry is taken. If the list
has no usable URL, the track has no stream and the player says so (§5).

The stream URL is passed to the element as-is. The build does not transcode,
proxy or store it.

## 4. The queue

- `play(songs, index, context)` replaces the queue with a list and starts at
  `index`. `context` records where the list came from (an album, a playlist, a
  search).
- `enqueue(song)` adds to the end. `playNext(song)` adds after the current track.
  A track that is already in the queue is not added again; the call says so and
  nothing changes.
- `removeAt(index)` removes a track. Removing a track before the current one keeps
  the current track playing. Removing the current track starts the track that
  takes its place, or the new last track if it was last. Removing the only track
  stops playback.
- `queueView()` returns the queue as the Queue tab draws it.

### 4.1 Next and previous

These rules are in `nextIndex` and `previousIndex`, and the tests cover each
branch.

| Action | Repeat off | Repeat all | Repeat one |
|---|---|---|---|
| Next, at the last track | Stops (`-1`). | Goes to the first track. | Repeats the same track. |
| Next, in the middle | Next track. | Next track. | Repeats the same track. |
| Previous, after 3 s | Restarts the current track. | Restarts the current track. | Restarts the current track. |
| Previous, within 3 s, first track | Stays on the first track. | Goes to the last track. | Stays. |

With **shuffle** on, next and previous follow the shuffled order. Repeat-all wraps
inside that order. Shuffle is a permutation of the queue, not a random choice on
each press, so every track plays once before any repeats.

Repeat is cycled by the Repeat control and the `R` key through `off`, `all`,
`one`. Shuffle is toggled by the Shuffle control and `S`.

### 4.2 Seeking and volume

- `seekBy(±5)` is bound to the arrow keys. Seeking is clamped to the track.
- Volume is 0 to 1, with a default of `0.8`. `M` mutes and unmutes without
  changing the stored volume.

## 5. Failures

`playRejection` turns the browser's error from `play()` into one of three
outcomes. The distinction matters to the visitor: only the first can be fixed by
pressing play again.

| Browser error | Status | Title shown | Visitor's next step |
|---|---|---|---|
| `NotAllowedError` | `blocked` | The browser blocked playback | Press play once more. |
| `NotSupportedError` | `error` | This browser cannot play that file | None in this build. The format was refused. |
| `AbortError` | none | (no message) | None. A track interrupted by the next track is not an error. |
| Anything else | `error` | Playback stopped | The browser's message is shown under "Technical detail". |

Two cases are not browser errors:

- **No stream URL.** The row has no audio. The message says the row came from
  the saved library and that audio is re-read from the catalogue, so the visitor
  should open the track from its source.
- **A sample row.** Sample rows are invented when the catalogue is unreachable.
  Playing one shows "This is a sample row" and states that it has no audio.

## 6. The operating system's controls

Where the browser exposes the Media Session API, the player sends the track's
title, artist, artwork, playback state and position, and registers handlers for
`play`, `pause`, `previous`, `next`, `seekBackward` and `seekForward` (10 seconds
each), and `seekTo`. The feature is detected at run time. The code comment notes
that the API is absent in some browsers, including Safari; where it is absent,
the feature is simply not offered. Nothing else is assumed.

## 7. Lyrics and sound features not offered

- **Lyrics tab:** the catalogue marks whether a track has lyrics, but its public
  API gives no lyric text. The tab says so. The build does not fetch words from
  another service.
- **Equaliser, crossfade, gapless, downloads, offline audio:** not offered. A
  browser audio element cannot provide them honestly in this design. See
  [KNOWN-LIMITATIONS.md](KNOWN-LIMITATIONS.md).

## 8. Not verified

Whether audio is heard on the deployed origin has not been recorded by a person.
The browser checks in CI drive the page and check states; they do not listen.
See [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md).
