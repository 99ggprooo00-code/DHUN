# DHUN Playback & Extraction Probe

The probe harness validates DHUN's extraction and playback stack across live YouTube extraction and deterministic offline file playback.

## Entry Points

1. **`MainKt` (`./gradlew :tools:playback-probe:run`)**:
   - Live extraction and stream check driving the production resolver chain (`own-client` -> `yt-dlp`).
   - Executes search, stream URL resolution, HTTP Range byte fetch & container magic verification, related tracks, deterministic offline `file://` assertion, and per-engine diagnostic watch.
   - Emits structured `PROBE|<step>|PASS/FAIL` output and final `PROBE|verdict|PASS/FAIL`.

2. **`OfflineMainKt` (`./gradlew :tools:playback-probe:offlineProbe`)**:
   - Deterministic ADR-006 local-download playback check.
   - Uses real JVM SQLDelight database and bundled WAV fixture.
   - Verifies `OfflineFirstStreamResolver`, `file://` URI resolution, zero network calls, and container header validation without network.

3. **`SmokeMainKt` (`./gradlew :tools:playback-probe:smokeProbe`)**:
   - Live provider-level smoke across home feed, search filters, search suggestions, related tracks, lyrics, and stream resolution.

## Output Protocol & Verdicts

The probe emits structured, machine-parseable `PROBE|<step>|<status>|<detail>`
lines. This list is the contract the `extraction-health` classifier greps, so
it is kept in sync with `Main.kt` / `OfflineMain.kt` line for line.

Live probe (`:run`, `MainKt`):
- `PROBE|environment|INFO|Java <version> (<vendor>) on <os> <osVersion> (<arch>)`
- `PROBE|version|PASS|WEB_REMIX <version> (scraped from homepage HTML)` — or `PROBE|version|FAIL|...`, which short-circuits to `PROBE|verdict|FAIL|metadata path broken (could not scrape InnerTube client version)`
- `PROBE|engine-version|PASS|yt-dlp <version>` / `PROBE|engine-version|INFO|yt-dlp not available in environment`
- `PROBE|engine-version|PASS|NewPipeExtractor v0.26.5` (pinned dependency; watch-only, never a production conclusion)
- `PROBE|search|PASS|<count> music-song results`, then `SEARCH|<n>|<title> | <artist> | <id>` for up to 10 results. An empty/failed search is fatal: `PROBE|verdict|FAIL|search broken` with exit code 1
- `PROBE|home-feed|PASS|sections=<n> continuation=<true|false>; <kind>` / `PROBE|home-feed|FAIL|<error>`
- `PROBE|home-more|PASS|sections=<n> ...` / `PROBE|home-more|SKIP|first page exhausted (no continuation token)` / `PROBE|home-more|FAIL|<error>`
- `PROBE|resolve|PASS|via <chain name> ("<title>" by "<artist>")`, or `PROBE|resolve|ENVIRONMENT_BLOCKED|via ...`, `PROBE|resolve|UNAVAILABLE|via ...`, `PROBE|resolve|FAIL|via ...` per `classifyResolverFailure`
- `PROBE|stream|PASS|HTTP <code> | <contentType> | <bytes>B | <magicHex> | <container>` / `PROBE|stream|FAIL|...` / `PROBE|stream|SKIP|resolve failed`
- `PROBE|related|PASS|<count> related tracks` plus `RELATED|<n>|<title> | <artist>` for up to 5
- `WATCH|own-client|...`, `WATCH|ytdlp|...`, `WATCH|newpipe-stream|...` with status `OK` / `ENVIRONMENT_BLOCKED` / `UNAVAILABLE` / `BROKEN` — diagnostic only, never folded into the verdict
- Final line `PROBE|verdict|<PASS|FAIL|ENVIRONMENT_BLOCKED|UNAVAILABLE>|<statusText>` where statusText is `extraction-pipeline-healthy` / `extraction-pipeline-broken` / `youtube-runner-bot-gated` / `external-extraction-service-unavailable`. **Exit code is 0 only for `PASS`.**

Offline probe (`:offlineProbe`, `OfflineMainKt`) — deterministic, no network:
- `PROBE|offline-resolve|PASS|<file-uri> | network-calls=0`
- `PROBE|offline-load|PASS|<headerSize>B WAV header | <fileSize>B local file`
- `PROBE|offline-verdict|PASS|completed-download-resolves-and-loads-without-network`
- Each failed assertion prints its own `PROBE|offline-resolve|FAIL|...` /
  `PROBE|offline-load|FAIL|...` plus a `PROBE|offline-verdict|FAIL|<reason>`
  (missing fixture, URI scheme mismatch, resolved-path mismatch, network
  leakage, size mismatch, incomplete header, corrupt WAV container).

`classifyResolverFailure` maps only explicit gate evidence (`LOGIN_REQUIRED`,
`SIGN IN TO CONFIRM`, `NOT A BOT`, `BOT DETECTED`, `BOT-GATING`) to
`ENVIRONMENT_BLOCKED`; it never changes the shared production error taxonomy.

## Extraction-Health Integration

The daily drill is **`.github/workflows/extraction-health.yml`** (workflow id
**360655315**), running `:tools:playback-probe:offlineProbe` then
`:tools:playback-probe:run` on schedule `17 4 * * *` UTC (observed start
~04:28–04:30 UTC). The former `rot-drill.yml` / `rot-drill-daily.yml` files are
deleted — do not cite or dispatch them.

- The job greps the probe log for `PROBE|offline-verdict|` and `PROBE|verdict|`
  and cross-checks each command's exit status, then classifies
  `FAIL` / `ENVIRONMENT_BLOCKED` / `UNAVAILABLE` / `PASS` into the step summary.
- Offline verdict `PASS` is mandatory: a red `offlineProbe` is always `FAIL`.
- Only `FAIL` opens or comments on the `[rot-drill] Live extraction probe failed`
  tracking issue; `PASS` comments on and closes it.
- `ENVIRONMENT_BLOCKED` / `UNAVAILABLE` post a warning annotation and the final
  gate exits non-zero (2 / 3) on purpose, so an unverified live stream can never
  look green. The artifact `rot-drill-<run_id>` carries `rot-drill.log` for 14 days.
- A run with **zero jobs** is trigger noise, not a verdict.
