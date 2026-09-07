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

The probe outputs structured, machine-parseable log lines:
- `PROBE|environment|INFO|<java/os info>`
- `PROBE|version|PASS|WEB_REMIX <version>`
- `PROBE|engine-version|PASS|yt-dlp <version>`
- `PROBE|engine-version|PASS|NewPipeExtractor <version>`
- `PROBE|search|PASS|<count> results`
- `PROBE|resolve|PASS|via <resolver-chain> ("<title>" by <artist>)`
- `PROBE|stream|PASS|HTTP <code> | <content-type> | <bytes>B | <magic> | <container>`
- `PROBE|related|PASS|<count> related tracks`
- `PROBE|offline-resolve|PASS|<file-uri> | network-calls=0`
- `PROBE|offline-load|PASS|<header-size>B WAV header | <file-size>B local file`
- `PROBE|offline-verdict|PASS|completed-download-resolves-and-loads-without-network`
- `WATCH|<engine>|OK|...` / `WATCH|<engine>|BROKEN|...`
- `PROBE|verdict|PASS|extraction-pipeline-healthy` / `PROBE|verdict|FAIL|extraction-pipeline-broken`

## Rot-Drill Integration

The daily `.github/workflows/rot-drill.yml` runs both `:tools:playback-probe:offlineProbe` and `:tools:playback-probe:run` on schedule (`17 4 * * *`).
- If extraction fails or is bot-gated on runner IPs, the workflow captures diagnostic logs into an artifact and opens or comments on the tracking GitHub issue.
- When live extraction passes, the workflow automatically resolves and closes the issue.
