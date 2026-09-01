# Phase 02 — Provider & Domain Core — verification evidence

Date: 2026-09-01T10:40Z. Environment: same hostile datacenter IP as Phase 01.

## Unit tests (no network) — shared:jvmTest
```
34 tests, 0 failures
- QueueManagerTest (21 tests): linear nav, repeat ALL/ONE boundaries, shuffle
  (seeded Random, current-first, set preservation, wrap), addNext/addToQueue,
  remove current/before-current, move, playAt, upcoming, empty/single edges
- ResolvingStreamResolverTest (4): primary-success skips fallback, failover,
  double-failure reports PRIMARY error, no-fallback path
- OwnClientStreamResolverTest (7): best-format pick, video skipping, urlless
  skipping, codec extraction (quoted + unquoted), no-audio failure, DhunResult
- ParserFixtureTest (6): live-captured fixtures — 20 songs w/ correct
  title/artist/album/duration(5:55=355s)/videoId, 50 radio tracks, suggestions,
  lyrics browseId (MPLYt_), lyrics shelf, NotAvailable shape
NOTE: fixture regression found + fixed: the radio fixture captured during the
spike was the REQUEST body (162B) — recaptured a real 557KB response.
```

## Live provider smoke (real network, provider stack only)
```
SMOKE|search-songs|PASS|20 songs; top: A Sky Full of Stars ? Coldplay
SMOKE|search-artists|PASS|artists=7 albums=0 playlists=0
SMOKE|search-albums|PASS|artists=0 albums=20 playlists=0
SMOKE|search-playlists|PASS|artists=6 albums=3 playlists=6
SMOKE|suggestions|PASS|yellow coldplay | yellow coldplay subtitulada en espa?ol | yellow coldplay lyrics | yellow coldplay acoustic
SMOKE|related|PASS|50 tracks; top: A Sky Full of Stars ? Coldplay
SMOKE|lyrics|PASS|unsynced, 27 lines; first: 'Cause you're a sky, 'cause you're a sky full of s
SMOKE|stream|PASS|audio/webm ?kbps codec=opus url=https://rr4---sn-nx5e6nle.googlevideo.com/videoplayback?
SMOKE|queue|PASS|size=4 current=Song One next=Song Two
exit code: 0
```

Findings:
- Own-client stream resolver correctly reports AuthRequired from this gated IP;
  ResolvingStreamResolver transparently failed over to yt-dlp — ADR-001 verified in production.
- YTM lyrics pipeline (next -> MPLYt_ browseId -> browse shelf) delivers real unsynced lyrics.
- PLAYLISTS filter returns mixed types (community + YTM-owned); classifier routes by browseId.
- Known production bug caught by tests and fixed: codec extraction against codecs="opus" quoting.
