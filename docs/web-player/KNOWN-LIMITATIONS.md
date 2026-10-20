# DHUN Web — known limitations

What a visitor can actually meet at `/app/`, and what the build deliberately does
not do. Every item is either enforced by a gate or visible in the interface; none
is an aspiration.

## The catalogue

- **It is a community wrapper, not an official API.** The app reads JioSaavn
  through three deployments of the same open-source project — `saavn.dev`,
  `saavn.sumit.co`, `saavnx.vercel.app` — none of which this project operates or
  has an agreement with. Any of them can rate-limit, change shape, or disappear,
  and all of them can be down at once because they share upstreams. The app
  shows what each host answered, labelled as technical detail, and offers a
  retry. Three hosts reduce the chance of a dead catalogue; they do not make it
  impossible.
- **Some networks cannot reach it.** A blocked origin, a corporate proxy or an
  ad-blocker produces a failure the browser reports as a generic fetch error. The
  sentence the app writes names the request it made, because the browser's own
  message names nothing.
- **A search can come back empty.** An empty shelf is drawn as an empty state
  naming the query, never as a spinner that never ends.
- **The API is unofficial and so is the licence situation.** The app states what
  it calls; it does not claim the service's content is licensed for it. See
  `#/about/terms`.

## The sample catalogue

- With no reachable catalogue, Home draws a **sample catalogue**: fixed rows,
  chosen so the interface can be seen and judged. Every one is labelled
  (`demo: true`), the screen carries a notice saying the rows are examples, and
  the source badge says "Sample catalogue".
- **Sample rows have no audio.** Pressing play on one says so. Nothing is
  simulated: no fake progress, no invented queue, no pretend buffering.

## What this build does not have

These exist in the Android and Windows applications and **not** here. Each is
absent rather than stubbed:

- **No lyrics.** The player's Lyrics tab says the words are not available in this
  build. There is no lyric service, no parser, and no invented line.
- **No equaliser.** Its bands are a platform audio binding. Routing
  cross-origin audio through a browser Web Audio graph can fail silently and mute
  the product, so the control does not exist.
- **No downloads and no offline audio.** ADR-006's device-side store has no
  browser equivalent. The service worker caches the app's own files so the shell
  opens without a network; it caches no audio and no API response, and there is no
  "download" control to find.
- **No cache-budget setting.** There is no audio cache to budget.
- **No account, no sync, no server copy.** The library is this browser's
  `localStorage`, under one key. Another device, another browser or a cleared
  profile is an empty library. Export and import are the only way to move it.
- **No catalog browser.** The Android build has a browsable catalogue; the web
  build has Home, Search, and the pages a row or a card links to.

## Playback in a browser

- **Stream URLs are signed and expire.** A track paused for hours may refuse to
  resume; the app then says the stream could not be played rather than silently
  stopping.
- **The browser owns the audio element.** A codec, a headphone change, a revoked
  autoplay permission or an OS audio interruption produces an error the browser
  reports. The app labels that text as technical detail; it does not invent a
  cause.
- **Autoplay is not guaranteed.** Browsers may refuse to start audio without a
  gesture; every start in this build comes from a press.
- **Background playback is the platform's decision.** On a phone, locking the
  screen or switching tabs may pause audio. Media Session metadata is attached
  where the browser supports it; nothing is promised beyond that.
- **No gapless playback, no crossfade, no volume normalisation.** The build plays
  one stream at a time and draws its real clock.

## This browser, not that one

- **Storage is per browser and per origin.** A private window may throw on write
  or forget what it wrote; the app detects that, keeps the library in memory, and
  says so on Settings instead of promising a save.
- **The keyboard map is global to the page.** It is not taken from text fields
  and not from combinations the browser owns, but a browser extension can take
  those keys first.
- **The app is one tab.** It refuses to boot inside another page's frame and says
  why; `frame-ancestors` cannot be sent from a static host.

## Not verified

- **Audio from the deployed origin.** The path is real; nobody has heard it. See
  [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md).
- **Browsers other than the Chromium build CI launches.** Safari, Firefox and a
  phone browser are untested.
- **Screen readers.** axe passes; nobody has listened.
- **Slow networks.** The request timeout is 15 seconds; what a 2G connection does
  to the interface has not been measured.
