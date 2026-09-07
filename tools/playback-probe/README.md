# DHUN playback probe

The probe has three entry points:

- `MainKt` (default): live Phase 01 extraction/search/stream/related check.
- `SmokeMainKt`: live provider-level smoke check.
- `OfflineMainKt`: deterministic ADR-006 local-download check.

## ADR-006 offline check

Run from the repository root:

```bash
./gradlew :tools:playback-probe:offlineProbe --offline --no-daemon
```

The check uses the real JVM SQLDelight download repository and a valid WAV
fixture. It inserts a `COMPLETED` download row, resolves it through
`OfflineFirstStreamResolver`, requires a `file://` URI, opens the resolved file,
checks the RIFF/WAVE header, and fails if the network resolver is called.
A successful run prints `PROBE|offline-verdict|PASS|...` and exits zero.

This is a mechanical shared/JVM verification of local-path resolution and
file loading. It does **not** prove Android Media3 `FileDataSource` behavior,
Desktop vlcj decoding, or audible offline playback. Those remain mandatory
real-device/PC checks and cannot be completed by CI alone.
